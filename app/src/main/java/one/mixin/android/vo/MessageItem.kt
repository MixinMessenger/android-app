package one.mixin.android.vo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Parcelable
import android.view.View
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.paging.PositionalDataSource
import androidx.recyclerview.widget.DiffUtil
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.android.exoplayer2.util.MimeTypes
import kotlinx.parcelize.Parcelize
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.extension.copyFromInputStream
import one.mixin.android.extension.getFilePath
import one.mixin.android.extension.getPublicPicturePath
import one.mixin.android.extension.hasWritePermission
import one.mixin.android.extension.isImageSupport
import one.mixin.android.extension.nowInUtc
import one.mixin.android.extension.toast
import one.mixin.android.util.VideoPlayer
import java.io.File
import java.io.FileInputStream

@SuppressLint("ParcelCreator")
@Entity
@Parcelize
data class MessageItem(
    @PrimaryKey
    val messageId: String,
    val conversationId: String,
    val userId: String,
    val userFullName: String,
    val userIdentityNumber: String,
    override val type: String?,
    val content: String?,
    val createdAt: String,
    val status: String,
    val mediaStatus: String?,
    val userAvatarUrl: String?,
    val mediaName: String?,
    val mediaMimeType: String?,
    val mediaSize: Long?,
    val thumbUrl: String?,
    val mediaWidth: Int?,
    val mediaHeight: Int?,
    val thumbImage: String?,
    val mediaUrl: String?,
    val mediaDuration: String?,
    val participantFullName: String?,
    val participantUserId: String?,
    val actionName: String?,
    val snapshotId: String?,
    val snapshotType: String?,
    val snapshotAmount: String?,
    val assetId: String?,
    val assetType: String?,
    val assetSymbol: String?,
    val assetIcon: String?,
    val assetUrl: String?,
    val assetHeight: Int?,
    val assetWidth: Int?,
    @Deprecated(
        "Deprecated at database version 15",
        ReplaceWith("@{link sticker_id}", "one.mixin.android.vo.MessageItem.stickerId"),
        DeprecationLevel.ERROR
    )
    val albumId: String?,
    val stickerId: String?,
    val assetName: String?,
    val appId: String?,
    val siteName: String? = null,
    val siteTitle: String? = null,
    val siteDescription: String? = null,
    val siteImage: String? = null,
    val sharedUserId: String? = null,
    val sharedUserFullName: String? = null,
    val sharedUserIdentityNumber: String? = null,
    val sharedUserAvatarUrl: String? = null,
    val sharedUserIsVerified: Boolean? = null,
    val sharedUserAppId: String? = null,
    val mediaWaveform: ByteArray? = null,
    val quoteId: String? = null,
    val quoteContent: String? = null,
    val groupName: String? = null,
    val mentions: String? = null,
    val mentionRead: Boolean? = null
) : Parcelable, ICategory {
    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MessageItem>() {
            override fun areItemsTheSame(oldItem: MessageItem, newItem: MessageItem) =
                oldItem.messageId == newItem.messageId

            override fun areContentsTheSame(oldItem: MessageItem, newItem: MessageItem) =
                oldItem == newItem
        }
    }

    fun canNotForward() = this.type == MessageCategory.APP_BUTTON_GROUP.name ||
        this.type == MessageCategory.SYSTEM_ACCOUNT_SNAPSHOT.name ||
        this.type == MessageCategory.SYSTEM_CONVERSATION.name ||
        unfinishedAttachment() ||
        isCallMessage() || isRecall()

    fun canNotReply() =
        this.type == MessageCategory.SYSTEM_ACCOUNT_SNAPSHOT.name ||
            this.type == MessageCategory.SYSTEM_CONVERSATION.name ||
            unfinishedAttachment() ||
            isCallMessage() || isRecall()

    private fun unfinishedAttachment(): Boolean = !mediaDownloaded(this.mediaStatus) && isAttachment()
}

fun create(type: String, createdAt: String? = null) = MessageItem(
    "", "", "", "", "",
    type, null,
    createdAt
        ?: nowInUtc(),
    MessageStatus.READ.name, null, null,
    null, null, null, null, null, null, null, null,
    null, null, null, null, null, null, null, null,
    null, null, null, null, null, null, null, null, null, null
)

fun MessageItem.isLottie() = assetType?.equals(Sticker.STICKER_TYPE_JSON, true) == true

fun MessageItem.mediaDownloaded() = mediaStatus == MediaStatus.DONE.name || mediaStatus == MediaStatus.READ.name

fun MessageItem.showVerifiedOrBot(verifiedView: View, botView: View) {
    when {
        sharedUserIsVerified == true -> {
            verifiedView.isVisible = true
            botView.isVisible = false
        }
        sharedUserAppId != null -> {
            verifiedView.isVisible = false
            botView.isVisible = true
        }
        else -> {
            verifiedView.isVisible = false
            botView.isVisible = false
        }
    }
}

fun MessageItem.saveToLocal(context: Context) {
    if (!hasWritePermission()) return

    val filePath = mediaUrl?.toUri()?.getFilePath()
    if (filePath == null) {
        MixinApplication.appContext.toast(R.string.save_failure)
        return
    }

    val file = File(filePath)
    val outFile = if (MimeTypes.isVideo(mediaMimeType) || mediaMimeType?.isImageSupport() == true) {
        File(context.getPublicPicturePath(), mediaName ?: file.name)
    } else {
        val dir = if (MimeTypes.isAudio(mediaMimeType)) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }
        dir.mkdir()
        File(dir, mediaName ?: file.name)
    }
    outFile.copyFromInputStream(FileInputStream(file))
    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outFile)))
    MixinApplication.appContext.toast(MixinApplication.appContext.getString(R.string.save_to, outFile.absolutePath))
}

fun MessageItem.loadVideoOrLive(actionAfterLoad: (() -> Unit)? = null) {
    mediaUrl?.let {
        if (isLive()) {
            VideoPlayer.player().loadHlsVideo(it, messageId)
        } else {
            VideoPlayer.player().loadVideo(it, messageId)
        }
        actionAfterLoad?.invoke()
    }
}

class FixedMessageDataSource(private val messageItems: List<MessageItem>) : PositionalDataSource<MessageItem>() {
    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<MessageItem>) {
        callback.onResult(messageItems)
    }

    override fun loadInitial(
        params: LoadInitialParams,
        callback: LoadInitialCallback<MessageItem>
    ) {
        callback.onResult(messageItems, 0, 1)
    }
}
