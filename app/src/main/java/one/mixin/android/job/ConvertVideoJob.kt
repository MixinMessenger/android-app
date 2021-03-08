package one.mixin.android.job

import android.net.Uri
import androidx.core.net.toUri
import com.birbit.android.jobqueue.Params
import one.mixin.android.MixinApplication
import one.mixin.android.RxBus
import one.mixin.android.extension.createVideoTemp
import one.mixin.android.extension.getFileNameNoEx
import one.mixin.android.extension.getMimeType
import one.mixin.android.extension.getVideoModel
import one.mixin.android.extension.getVideoPath
import one.mixin.android.extension.nowInUtc
import one.mixin.android.util.video.MediaController
import one.mixin.android.util.video.VideoEditedInfo
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.MessageStatus
import one.mixin.android.vo.createVideoMessage
import one.mixin.android.vo.toQuoteMessageItem
import one.mixin.android.widget.ConvertEvent
import java.io.File

class ConvertVideoJob(
    private val conversationId: String,
    private val senderId: String,
    private val uri: Uri,
    isEncrypted: Boolean,
    private val messageId: String,
    createdAt: String? = null,
    private val replyMessage: MessageItem? = null
) : MixinJob(Params(PRIORITY_BACKGROUND).groupBy(GROUP_ID), messageId) {

    companion object {
        private const val serialVersionUID = 1L
        const val GROUP_ID = "convert_video_group"
    }

    private val video: VideoEditedInfo? = getVideoModel(uri)
    private val category = if (isEncrypted) MessageCategory.ENCRYPTED_VIDEO.name else MessageCategory.SIGNAL_VIDEO.name
    private val createdAt: String = createdAt ?: nowInUtc()
    override fun onAdded() {
        val mimeType = getMimeType(uri)
        if (video == null) {
            return
        }
        if (mimeType != "video/mp4") {
            video.needChange = true
        }
        if (!video.fileName.endsWith(".mp4")) {
            video.fileName = "${video.fileName.getFileNameNoEx()}.mp4"
        }
        val message = createVideoMessage(
            messageId, conversationId, senderId, category, null,
            video.fileName, uri.toString(), video.duration, video.resultWidth,
            video.resultHeight, video.thumbnail, "video/mp4",
            0L, createdAt, null, null, MediaStatus.PENDING, MessageStatus.SENDING.name,
            replyMessage?.messageId, replyMessage?.toQuoteMessageItem()
        )
        // insert message with mediaSize 0L
        // for show video place holder in chat list before convert video
        val mId = messageDao.findMessageIdById(message.id)
        if (mId == null) {
            messageDao.insert(message)
        }
    }

    override fun onRun() {
        if (isCancelled) {
            removeJob()
            return
        }
        if (video == null) {
            return
        }
        jobManager.saveJob(this)
        val videoFile: File = MixinApplication.get().getVideoPath().createVideoTemp(conversationId, messageId, "mp4")
        val error = MediaController.getInstance().convertVideo(
            video.originalPath,
            video.bitrate,
            video.resultWidth,
            video.resultHeight,
            videoFile,
            video.needChange,
            video.duration,
            object : MediaController.VideoConvertorListener {
                override fun didWriteData(availableSize: Long, progress: Float) {
                    RxBus.publish(ConvertEvent(messageId, progress / 10f))
                }

                override fun checkConversionCanceled(): Boolean {
                    return isCancelled
                }
            }
        )
        if (isCancelled) {
            removeJob()
            return
        }
        val message = createVideoMessage(
            messageId, conversationId, senderId, category, null,
            video.fileName, videoFile.toUri().toString(), video.duration, video.resultWidth,
            video.resultHeight, video.thumbnail, "video/mp4",
            videoFile.length(), createdAt, null, null,
            if (error) MediaStatus.CANCELED else MediaStatus.PENDING,
            if (error) MessageStatus.FAILED.name else MessageStatus.SENDING.name
        )
        if (!error) {
            messageDao.updateMediaMessageUrl(videoFile.toUri().toString(), messageId)
            jobManager.addJobInBackground(SendAttachmentMessageJob(message))
        }

        removeJob()
    }

    override fun cancel() {
        isCancelled = true
        messageDao.updateMediaStatus(MediaStatus.CANCELED.name, messageId)
        removeJob()
    }
}
