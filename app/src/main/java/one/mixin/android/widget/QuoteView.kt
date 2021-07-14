package one.mixin.android.widget

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.core.widget.TextViewCompat
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.databinding.ViewQuoteBinding
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.formatMillis
import one.mixin.android.extension.generateConversationPath
import one.mixin.android.extension.getImagePath
import one.mixin.android.extension.getMediaPath
import one.mixin.android.extension.getVideoPath
import one.mixin.android.extension.loadImageCenterCrop
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.renderMessage
import one.mixin.android.extension.round
import one.mixin.android.session.Session
import one.mixin.android.ui.conversation.holder.BaseViewHolder
import one.mixin.android.util.mention.MentionRenderCache
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.QuoteMessageItem
import one.mixin.android.vo.SnakeQuoteMessageItem
import org.jetbrains.anko.dip
import java.io.File

class QuoteView constructor(context: Context, attrs: AttributeSet) :
    ConstraintLayout(context, attrs) {

    private val binding = ViewQuoteBinding.inflate(LayoutInflater.from(context), this)

    init {
        round(dip(4))
    }

    fun bind(quoteMessageItem: QuoteMessageItem?) {
        if (quoteMessageItem == null) {
            setBackgroundColor(context.getColor(R.color.colorAccent))
            background.alpha = 0x0D
            binding.startView.setBackgroundColor(context.getColor(R.color.colorAccent))
            binding.replyNameTv.visibility = View.GONE
            binding.replyIv.visibility = View.GONE
            binding.replyAvatar.visibility = View.GONE
            binding.replyContentTv.setText(R.string.chat_not_found)
            binding.replyContentTv.setTypeface(null, Typeface.ITALIC)
            setIcon(R.drawable.ic_type_recall)
            return
        }
        binding.replyContentTv.setTypeface(null, Typeface.NORMAL)
        binding.replyNameTv.visibility = View.VISIBLE
        binding.replyNameTv.text = quoteMessageItem.userFullName
        binding.replyNameTv.setTextColor(BaseViewHolder.getColorById(quoteMessageItem.userId))
        setBackgroundColor(BaseViewHolder.getColorById(quoteMessageItem.userId))
        background.alpha = 0x0D
        binding.startView.setBackgroundColor(BaseViewHolder.getColorById(quoteMessageItem.userId))
        when {
            quoteMessageItem.type.endsWith("_TEXT") -> {
                if (quoteMessageItem.mentions != null) {
                    binding.replyContentTv.renderMessage(
                        quoteMessageItem.content,
                        MentionRenderCache.singleton.getMentionRenderContext(quoteMessageItem.mentions)
                    )
                } else {
                    binding.replyContentTv.text = quoteMessageItem.content
                }
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                setIcon()
            }
            quoteMessageItem.type == MessageCategory.MESSAGE_RECALL.name -> {
                binding.replyContentTv.setText(
                    if (quoteMessageItem.userId == Session.getAccountId()) {
                        R.string.chat_recall_me
                    } else {
                        R.string.chat_recall_delete
                    }
                )
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                setIcon(R.drawable.ic_type_recall)
            }
            quoteMessageItem.type.endsWith("_IMAGE") -> {
                binding.replyIv.loadImageCenterCrop(
                    absolutePath(quoteMessageItem.mediaUrl, quoteMessageItem.type, quoteMessageItem.conversationId),
                    R.drawable.image_holder
                )
                binding.replyContentTv.setText(R.string.photo)
                setIcon(R.drawable.ic_type_pic)
                binding.replyIv.visibility = View.VISIBLE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
            }
            quoteMessageItem.type.endsWith("_VIDEO") -> {
                binding.replyIv.loadImageCenterCrop(
                    absolutePath(quoteMessageItem.mediaUrl, quoteMessageItem.type, quoteMessageItem.conversationId),
                    R.drawable.image_holder
                )
                binding.replyContentTv.setText(R.string.video)
                setIcon(R.drawable.ic_type_video)
                binding.replyIv.visibility = View.VISIBLE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
            }
            quoteMessageItem.type.endsWith("_LIVE") -> {
                binding.replyIv.loadImageCenterCrop(
                    quoteMessageItem.thumbUrl,
                    R.drawable.image_holder
                )
                binding.replyContentTv.setText(R.string.live)
                setIcon(R.drawable.ic_type_live)
                binding.replyIv.visibility = View.VISIBLE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
            }
            quoteMessageItem.type.endsWith("_DATA") -> {
                quoteMessageItem.mediaName.notNullWithElse(
                    {
                        binding.replyContentTv.text = it
                    },
                    {
                        binding.replyContentTv.setText(R.string.document)
                    }
                )
                setIcon(R.drawable.ic_type_file)
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
            }
            quoteMessageItem.type.endsWith("_POST") -> {
                binding.replyContentTv.setText(R.string.post)
                setIcon(R.drawable.ic_type_file)
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
            }
            quoteMessageItem.type.endsWith("_TRANSCRIPT") -> {
                binding.replyContentTv.setText(R.string.transcript)
                setIcon(R.drawable.ic_type_transcript)
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
            }
            quoteMessageItem.type.endsWith("_LOCATION") -> {
                binding.replyContentTv.setText(R.string.location)
                setIcon(R.drawable.ic_type_location)
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
            }
            quoteMessageItem.type.endsWith("_AUDIO") -> {
                quoteMessageItem.mediaDuration.notNullWithElse(
                    {
                        binding.replyContentTv.text = it.toLong().formatMillis()
                    },
                    {
                        binding.replyContentTv.setText(R.string.audio)
                    }
                )
                setIcon(R.drawable.ic_type_audio)
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
            }
            quoteMessageItem.type.endsWith("_STICKER") -> {
                binding.replyContentTv.setText(R.string.common_sticker)
                setIcon(R.drawable.ic_type_stiker)
                binding.replyIv.loadImageCenterCrop(
                    quoteMessageItem.assetUrl,
                    R.drawable.image_holder
                )
                binding.replyIv.visibility = View.VISIBLE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
            }
            quoteMessageItem.type.endsWith("_CONTACT") -> {
                binding.replyContentTv.text = quoteMessageItem.sharedUserIdentityNumber
                setIcon(R.drawable.ic_type_contact)
                binding.replyAvatar.setInfo(
                    quoteMessageItem.sharedUserFullName,
                    quoteMessageItem.sharedUserAvatarUrl,
                    quoteMessageItem.sharedUserId
                        ?: "0"
                )
                binding.replyAvatar.visibility = View.VISIBLE
                binding.replyIv.visibility = View.INVISIBLE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
            }
            quoteMessageItem.type == MessageCategory.APP_BUTTON_GROUP.name || quoteMessageItem.type == MessageCategory.APP_CARD.name -> {
                binding.replyContentTv.setText(R.string.extensions)
                setIcon(R.drawable.ic_type_touch_app)
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
            }
            else -> {
                binding.replyIv.visibility = View.GONE
            }
        }
    }

    private fun setIcon(@DrawableRes icon: Int? = null) {
        icon.notNullWithElse(
            { drawable ->
                AppCompatResources.getDrawable(context, drawable).let {
                    it?.setBounds(0, 0, context.dpToPx(10f), context.dpToPx(10f))
                    TextViewCompat.setCompoundDrawablesRelative(
                        binding.replyContentTv,
                        it,
                        null,
                        null,
                        null
                    )
                }
            },
            {
                TextViewCompat.setCompoundDrawablesRelative(
                    binding.replyContentTv,
                    null,
                    null,
                    null,
                    null
                )
            }
        )
    }

    fun bind(quoteMessageItem: SnakeQuoteMessageItem?) {
        if (quoteMessageItem == null) {
            setBackgroundColor(context.getColor(R.color.colorAccent))
            background.alpha = 0x0D
            binding.startView.setBackgroundColor(context.getColor(R.color.colorAccent))
            binding.replyNameTv.visibility = View.GONE
            binding.replyIv.visibility = View.GONE
            binding.replyAvatar.visibility = View.GONE
            binding.replyContentTv.setText(R.string.chat_not_found)
            binding.replyContentTv.setTypeface(null, Typeface.ITALIC)
            setIcon(R.drawable.ic_type_recall)
            return
        }
        binding.replyContentTv.setTypeface(null, Typeface.NORMAL)
        binding.replyNameTv.visibility = View.VISIBLE
        binding.replyNameTv.text = quoteMessageItem.userFullName
        binding.replyNameTv.setTextColor(BaseViewHolder.getColorById(quoteMessageItem.userId))
        setBackgroundColor(BaseViewHolder.getColorById(quoteMessageItem.userId))
        background.alpha = 0x0D
        binding.startView.setBackgroundColor(BaseViewHolder.getColorById(quoteMessageItem.userId))
        when {
            quoteMessageItem.type.endsWith("_TEXT") -> {
                if (quoteMessageItem.mentions != null) {
                    binding.replyContentTv.renderMessage(
                        quoteMessageItem.content,
                        MentionRenderCache.singleton.getMentionRenderContext(quoteMessageItem.mentions)
                    )
                } else {
                    binding.replyContentTv.text = quoteMessageItem.content
                }
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                setIcon()
            }
            quoteMessageItem.type == MessageCategory.MESSAGE_RECALL.name -> {
                binding.replyContentTv.setText(
                    if (quoteMessageItem.userId == Session.getAccountId()) {
                        R.string.chat_recall_me
                    } else {
                        R.string.chat_recall_delete
                    }
                )
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                setIcon(R.drawable.ic_type_recall)
            }
            quoteMessageItem.type.endsWith("_IMAGE") -> {
                binding.replyIv.loadImageCenterCrop(
                    absolutePath(quoteMessageItem.mediaUrl, quoteMessageItem.type, quoteMessageItem.conversationId),
                    R.drawable.image_holder
                )
                binding.replyContentTv.setText(R.string.photo)
                setIcon(R.drawable.ic_type_pic)
                binding.replyIv.visibility = View.VISIBLE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
            }
            quoteMessageItem.type.endsWith("_VIDEO") -> {
                binding.replyIv.loadImageCenterCrop(
                    absolutePath(quoteMessageItem.mediaUrl, quoteMessageItem.type, quoteMessageItem.conversationId),
                    R.drawable.image_holder
                )
                binding.replyContentTv.setText(R.string.video)
                setIcon(R.drawable.ic_type_video)
                binding.replyIv.visibility = View.VISIBLE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
            }
            quoteMessageItem.type.endsWith("_LIVE") -> {
                binding.replyIv.loadImageCenterCrop(
                    quoteMessageItem.thumbUrl,
                    R.drawable.image_holder
                )
                binding.replyContentTv.setText(R.string.live)
                setIcon(R.drawable.ic_type_live)
                binding.replyIv.visibility = View.VISIBLE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
            }
            quoteMessageItem.type.endsWith("_DATA") -> {
                quoteMessageItem.mediaName.notNullWithElse(
                    {
                        binding.replyContentTv.text = it
                    },
                    {
                        binding.replyContentTv.setText(R.string.document)
                    }
                )
                setIcon(R.drawable.ic_type_file)
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
            }
            quoteMessageItem.type.endsWith("_POST") -> {
                binding.replyContentTv.setText(R.string.post)
                setIcon(R.drawable.ic_type_file)
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
            }
            quoteMessageItem.type.endsWith("_TRANSCRIPT") -> {
                binding.replyContentTv.setText(R.string.transcript)
                setIcon(R.drawable.ic_type_file)
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
            }
            quoteMessageItem.type.endsWith("_LOCATION") -> {
                binding.replyContentTv.setText(R.string.location)
                setIcon(R.drawable.ic_type_location)
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
            }
            quoteMessageItem.type.endsWith("_AUDIO") -> {
                quoteMessageItem.mediaDuration.notNullWithElse(
                    {
                        binding.replyContentTv.text = it.toLong().formatMillis()
                    },
                    {
                        binding.replyContentTv.setText(R.string.audio)
                    }
                )
                setIcon(R.drawable.ic_type_audio)
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
            }
            quoteMessageItem.type.endsWith("_STICKER") -> {
                binding.replyContentTv.setText(R.string.common_sticker)
                setIcon(R.drawable.ic_type_stiker)
                binding.replyIv.loadImageCenterCrop(
                    quoteMessageItem.assetUrl,
                    R.drawable.image_holder
                )
                binding.replyIv.visibility = View.VISIBLE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
            }
            quoteMessageItem.type.endsWith("_CONTACT") -> {
                binding.replyContentTv.text = quoteMessageItem.sharedUserIdentityNumber
                setIcon(R.drawable.ic_type_contact)
                binding.replyAvatar.setInfo(
                    quoteMessageItem.sharedUserFullName,
                    quoteMessageItem.sharedUserAvatarUrl,
                    quoteMessageItem.sharedUserId
                        ?: "0"
                )
                binding.replyAvatar.visibility = View.VISIBLE
                binding.replyIv.visibility = View.INVISIBLE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(16)
            }
            quoteMessageItem.type == MessageCategory.APP_BUTTON_GROUP.name || quoteMessageItem.type == MessageCategory.APP_CARD.name -> {
                binding.replyContentTv.setText(R.string.extensions)
                setIcon(R.drawable.ic_type_touch_app)
                binding.replyIv.visibility = View.GONE
                binding.replyAvatar.visibility = View.GONE
                (binding.replyContentTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
                (binding.replyNameTv.layoutParams as LayoutParams).marginEnd =
                    dip(8)
            }
            else -> {
                binding.replyIv.visibility = View.GONE
            }
        }
    }

    fun absolutePath(
        mediaUrl: String?,
        type: String,
        conversationId: String,
        context: Context = MixinApplication.appContext
    ): String? {
        val mediaPath = context.getMediaPath()?.absolutePath ?: return null
        return when {
            mediaUrl == null -> null
            mediaUrl.startsWith(mediaPath) -> mediaUrl
            type == MessageCategory.SIGNAL_IMAGE.name || type == MessageCategory.PLAIN_IMAGE.name || type == MessageCategory.ENCRYPTED_IMAGE.name -> File(
                context.getImagePath().generateConversationPath(conversationId),
                mediaUrl
            ).toUri().toString()
            type == MessageCategory.SIGNAL_VIDEO.name || type == MessageCategory.PLAIN_VIDEO.name || type == MessageCategory.ENCRYPTED_VIDEO.name ->
                File(
                    context.getVideoPath().generateConversationPath(conversationId),
                    mediaUrl
                ).toUri().toString()
            else -> null
        }
    }
}
