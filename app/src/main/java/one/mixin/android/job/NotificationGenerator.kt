package one.mixin.android.job

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.extension.mainThread
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.supportsNougat
import one.mixin.android.ui.conversation.ConversationActivity
import one.mixin.android.ui.home.MainActivity
import one.mixin.android.util.ChannelManager
import one.mixin.android.util.mention.rendMentionContent
import one.mixin.android.vo.Message
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.SnapshotType
import one.mixin.android.vo.UserRelationship
import one.mixin.android.vo.isAudio
import one.mixin.android.vo.isContact
import one.mixin.android.vo.isData
import one.mixin.android.vo.isGroupConversation
import one.mixin.android.vo.isImage
import one.mixin.android.vo.isLive
import one.mixin.android.vo.isLocation
import one.mixin.android.vo.isPost
import one.mixin.android.vo.isRepresentativeMessage
import one.mixin.android.vo.isSticker
import one.mixin.android.vo.isText
import one.mixin.android.vo.isTranscript
import one.mixin.android.vo.isVideo
import org.jetbrains.anko.notificationManager

const val KEY_REPLY = "key_reply"
const val CONVERSATION_ID = "conversation_id"
const val IS_PLAIN = "is_plain"

object NotificationGenerator : Injector() {

    private val notificationManager: NotificationManager by lazy {
        MixinApplication.appContext.notificationManager
    }

    fun generate(
        lifecycleScope: CoroutineScope,
        message: Message,
        userMap: Map<String, String>? = null,
        force: Boolean = false,
        isSilent: Boolean = false
    ) = lifecycleScope.launch(Dispatchers.IO) {
        ChannelManager.updateChannelSound(MixinApplication.appContext)

        val context = MixinApplication.appContext
        val user = syncUser(message.userId) ?: return@launch
        if (user.relationship == UserRelationship.BLOCKING.name) {
            return@launch
        }
        val conversation = conversationDao.getConversationItem(message.conversationId) ?: return@launch
        if (conversation.category == null) {
            return@launch
        }
        if (!force && conversation.isMute()) {
            return@launch
        }
        val mainIntent = MainActivity.getSingleIntent(context)
        val conversationIntent = ConversationActivity
            .putIntent(context, message.conversationId)

        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (conversation.isGroupConversation()) {
                NotificationCompat.Builder(context, ChannelManager.getChannelId(true))
            } else {
                NotificationCompat.Builder(context, ChannelManager.getChannelId(false))
            }
        } else {
            NotificationCompat.Builder(context, ChannelManager.CHANNEL_MESSAGE)
        }

        notificationBuilder.setContentIntent(
            PendingIntent.getActivities(
                context,
                message.id.hashCode(),
                arrayOf(mainIntent, conversationIntent),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        supportsNougat {
            val remoteInput = RemoteInput.Builder(KEY_REPLY)
                .setLabel(context.getString(R.string.notification_reply))
                .build()
            val sendIntent = Intent(context, SendService::class.java)
            sendIntent.putExtra(CONVERSATION_ID, message.conversationId)
            sendIntent.putExtra(IS_PLAIN, user.isBot() || message.isRepresentativeMessage(conversation))
            val pendingIntent = PendingIntent.getService(
                context,
                message.conversationId.hashCode(),
                sendIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
            val action = NotificationCompat.Action.Builder(
                R.mipmap.ic_launcher,
                context.getString(R.string.notification_reply),
                pendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()
            notificationBuilder.addAction(action)
            val readAction = NotificationCompat.Action.Builder(
                R.mipmap.ic_launcher,
                context.getString(R.string.notification_mark),
                pendingIntent
            )
                .build()
            notificationBuilder.addAction(readAction)
        }

        when {
            message.isText() -> {
                if (conversation.isGroupConversation() || message.isRepresentativeMessage(conversation)) {
                    notificationBuilder.setContentTitle(conversation.getConversationName())
                    notificationBuilder.setTicker(
                        context.getString(R.string.alert_key_group_text_message, user.fullName)
                    )
                    notificationBuilder.setContentText("${user.fullName} : ${rendMentionContent(message.content, userMap)}")
                } else {
                    notificationBuilder.setContentTitle(user.fullName)
                    notificationBuilder.setTicker(context.getString(R.string.alert_key_contact_text_message))
                    notificationBuilder.setContentText(rendMentionContent(message.content, userMap))
                }
            }
            message.isImage() -> {
                if (conversation.isGroupConversation() || message.isRepresentativeMessage(conversation)) {
                    notificationBuilder.setTicker(
                        context.getString(R.string.alert_key_group_image_message, user.fullName)
                    )
                    notificationBuilder.setContentTitle(conversation.getConversationName())
                    notificationBuilder.setContentText(
                        context.getString(R.string.alert_key_group_image_message, user.fullName)
                    )
                } else {
                    notificationBuilder.setTicker(context.getString(R.string.alert_key_contact_image_message))
                    notificationBuilder.setContentTitle(user.fullName)
                    notificationBuilder.setContentText(context.getString(R.string.alert_key_contact_image_message))
                }
            }
            message.isVideo() -> {
                if (conversation.isGroupConversation() || message.isRepresentativeMessage(conversation)) {
                    notificationBuilder.setTicker(
                        context.getString(R.string.alert_key_group_video_message, user.fullName)
                    )
                    notificationBuilder.setContentTitle(conversation.getConversationName())
                    notificationBuilder.setContentText(
                        context.getString(R.string.alert_key_group_video_message, user.fullName)
                    )
                } else {
                    notificationBuilder.setTicker(context.getString(R.string.alert_key_contact_video_message))
                    notificationBuilder.setContentTitle(user.fullName)
                    notificationBuilder.setContentText(context.getString(R.string.alert_key_contact_video_message))
                }
            }
            message.isLive() -> {
                if (conversation.isGroupConversation() || message.isRepresentativeMessage(conversation)) {
                    notificationBuilder.setTicker(
                        context.getString(R.string.alert_key_group_live_message, user.fullName)
                    )
                    notificationBuilder.setContentTitle(conversation.getConversationName())
                    notificationBuilder.setContentText(
                        context.getString(R.string.alert_key_group_live_message, user.fullName)
                    )
                } else {
                    notificationBuilder.setTicker(context.getString(R.string.alert_key_contact_live_message))
                    notificationBuilder.setContentTitle(user.fullName)
                    notificationBuilder.setContentText(context.getString(R.string.alert_key_contact_live_message))
                }
            }
            message.isData() -> {
                if (conversation.isGroupConversation() || message.isRepresentativeMessage(conversation)) {
                    notificationBuilder.setTicker(
                        context.getString(R.string.alert_key_group_data_message, user.fullName)
                    )
                    notificationBuilder.setContentTitle(conversation.getConversationName())
                    notificationBuilder.setContentText(
                        context.getString(R.string.alert_key_group_data_message, user.fullName)
                    )
                } else {
                    notificationBuilder.setTicker(context.getString(R.string.alert_key_contact_data_message))
                    notificationBuilder.setContentTitle(user.fullName)
                    notificationBuilder.setContentText(context.getString(R.string.alert_key_contact_data_message))
                }
            }
            message.isAudio() -> {
                if (conversation.isGroupConversation() || message.isRepresentativeMessage(conversation)) {
                    notificationBuilder.setTicker(
                        context.getString(R.string.alert_key_group_audio_message, user.fullName)
                    )
                    notificationBuilder.setContentTitle(conversation.getConversationName())
                    notificationBuilder.setContentText(
                        context.getString(R.string.alert_key_group_audio_message, user.fullName)
                    )
                } else {
                    notificationBuilder.setTicker(context.getString(R.string.alert_key_contact_audio_message))
                    notificationBuilder.setContentTitle(user.fullName)
                    notificationBuilder.setContentText(context.getString(R.string.alert_key_contact_audio_message))
                }
            }
            message.isSticker() -> {
                if (conversation.isGroupConversation() || message.isRepresentativeMessage(conversation)) {
                    notificationBuilder.setTicker(
                        context.getString(R.string.alert_key_group_sticker_message, user.fullName)
                    )
                    notificationBuilder.setContentTitle(conversation.getConversationName())
                    notificationBuilder.setContentText(
                        context.getString(R.string.alert_key_group_sticker_message, user.fullName)
                    )
                } else {
                    notificationBuilder.setTicker(context.getString(R.string.alert_key_contact_sticker_message))
                    notificationBuilder.setContentTitle(user.fullName)
                    notificationBuilder.setContentText(context.getString(R.string.alert_key_contact_sticker_message))
                }
            }
            message.isContact() -> {
                if (conversation.isGroupConversation() || message.isRepresentativeMessage(conversation)) {
                    notificationBuilder.setTicker(
                        context.getString(R.string.alert_key_group_contact_message, user.fullName)
                    )
                    notificationBuilder.setContentTitle(conversation.getConversationName())
                    notificationBuilder.setContentText(
                        context.getString(R.string.alert_key_group_contact_message, user.fullName)
                    )
                } else {
                    notificationBuilder.setTicker(context.getString(R.string.alert_key_contact_contact_message))
                    notificationBuilder.setContentTitle(user.fullName)
                    notificationBuilder.setContentText(context.getString(R.string.alert_key_contact_contact_message))
                }
            }
            message.isLocation() -> {
                if (conversation.isGroupConversation() || message.isRepresentativeMessage(conversation)) {
                    notificationBuilder.setTicker(
                        context.getString(R.string.alert_key_group_location_message, user.fullName)
                    )
                    notificationBuilder.setContentTitle(conversation.getConversationName())
                    notificationBuilder.setContentText(
                        context.getString(R.string.alert_key_group_location_message, user.fullName)
                    )
                } else {
                    notificationBuilder.setTicker(context.getString(R.string.alert_key_contact_location_message))
                    notificationBuilder.setContentTitle(user.fullName)
                    notificationBuilder.setContentText(context.getString(R.string.alert_key_contact_location_message))
                }
            }
            message.isPost() -> {
                if (conversation.isGroupConversation() || message.isRepresentativeMessage(conversation)) {
                    notificationBuilder.setTicker(
                        context.getString(R.string.alert_key_group_post_message, user.fullName)
                    )
                    notificationBuilder.setContentTitle(conversation.getConversationName())
                    notificationBuilder.setContentText("${user.fullName}: ${rendMentionContent(message.content, userMap)}")
                } else {
                    notificationBuilder.setTicker(context.getString(R.string.alert_key_contact_post_message))
                    notificationBuilder.setContentTitle(user.fullName)
                    notificationBuilder.setContentText("${user.fullName}: ${rendMentionContent(message.content, userMap)}")
                }
            }
            message.isTranscript() -> {
                if (conversation.isGroupConversation() || message.isRepresentativeMessage(conversation)) {
                    notificationBuilder.setTicker(
                        context.getString(R.string.alert_key_group_transcript_message, user.fullName)
                    )
                    notificationBuilder.setContentTitle(conversation.getConversationName())
                    notificationBuilder.setContentText(
                        context.getString(R.string.alert_key_group_transcript_message, user.fullName)
                    )
                } else {
                    notificationBuilder.setTicker(context.getString(R.string.alert_key_contact_transcript_message))
                    notificationBuilder.setContentTitle(user.fullName)
                    notificationBuilder.setContentText(context.getString(R.string.alert_key_contact_transcript_message))
                }
            }
            message.type == MessageCategory.SYSTEM_ACCOUNT_SNAPSHOT.name -> {
                if (message.action == SnapshotType.transfer.name) {
                    notificationBuilder.setTicker(context.getString(R.string.alert_key_contact_transfer_message))
                    notificationBuilder.setContentTitle(user.fullName)
                    notificationBuilder.setContentText(context.getString(R.string.alert_key_contact_transfer_message))
                }
            }
            message.type == MessageCategory.APP_BUTTON_GROUP.name ||
                message.type == MessageCategory.APP_CARD.name -> {
                if (conversation.isGroupConversation() || message.isRepresentativeMessage(conversation)) {
                    notificationBuilder.setContentTitle(conversation.getConversationName())
                    notificationBuilder.setTicker(
                        context.getString(R.string.alert_key_group_text_message, user.fullName)
                    )
                    notificationBuilder.setContentText(context.getString(R.string.alert_key_contact_text_message))
                } else {
                    notificationBuilder.setContentTitle(user.fullName)
                    notificationBuilder.setTicker(context.getString(R.string.alert_key_contact_text_message))
                    notificationBuilder.setContentText(context.getString(R.string.alert_key_contact_text_message))
                }
            }
            message.type == MessageCategory.SYSTEM_CONVERSATION.name -> {
                notificationBuilder.setContentTitle(context.getString(R.string.app_name))
            }
            message.type == MessageCategory.WEBRTC_AUDIO_OFFER.name -> {
                notificationBuilder.setContentTitle(user.fullName)
                notificationBuilder.setContentText(context.getString(R.string.alert_key_contact_audio_call_message))
            }
            else -> {
                // No support
                return@launch
            }
        }
        notificationBuilder.setSmallIcon(R.drawable.ic_msg_default)
        notificationBuilder.color = ContextCompat.getColor(context, R.color.colorLightBlue)
        notificationBuilder.setWhen(System.currentTimeMillis())

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notificationBuilder.setSound(Uri.parse("android.resource://" + context.packageName + "/" + R.raw.mixin))
        }
        notificationBuilder.setAutoCancel(true)
        notificationBuilder.priority = if (isSilent) {
            NotificationCompat.PRIORITY_MIN
        } else {
            NotificationCompat.PRIORITY_HIGH
        }
        user.notNullWithElse(
            {
                context.mainThread {
                    val height = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
                    val width = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)

                    Glide.with(context)
                        .asBitmap()
                        .load(it.avatarUrl)
                        .apply(RequestOptions().fitCenter().circleCrop())
                        .listener(
                            object : RequestListener<Bitmap> {
                                override fun onResourceReady(
                                    resource: Bitmap?,
                                    model: Any?,
                                    target: Target<Bitmap>?,
                                    dataSource: DataSource?,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    notificationBuilder.setLargeIcon(resource)
                                    notificationManager.notify(message.conversationId.hashCode(), notificationBuilder.build())
                                    return false
                                }

                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Bitmap>?,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.default_avatar))
                                    notificationManager.notify(message.conversationId.hashCode(), notificationBuilder.build())
                                    return false
                                }
                            }
                        ).submit(width, height)
                }
            },
            {
                notificationManager.notify(message.conversationId.hashCode(), notificationBuilder.build())
            }
        )
    }
}
