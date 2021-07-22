package one.mixin.android.job

import android.net.Uri
import com.birbit.android.jobqueue.Params
import one.mixin.android.MixinApplication
import one.mixin.android.extension.copyFromInputStream
import one.mixin.android.extension.createDocumentTemp
import one.mixin.android.extension.getDocumentPath
import one.mixin.android.extension.getExtensionName
import one.mixin.android.extension.getFilePath
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.Message
import one.mixin.android.vo.MessageStatus
import one.mixin.android.vo.createAttachmentMessage

class ConvertDataJob(
    private val message: Message
) : MixinJob(Params(PRIORITY_BACKGROUND).groupBy(GROUP_ID), message.id) {

    companion object {
        private const val serialVersionUID = 1L
        const val GROUP_ID = "convert_data_group"
    }

    override fun onAdded() {
        messageDao.insert(message)
    }

    override fun cancel() {
        isCancelled = true
        messageDao.updateMediaStatus(MediaStatus.CANCELED.name, message.id)
        removeJob()
    }

    override fun onRun() {
        if (isCancelled) {
            removeJob()
            return
        }
        jobManager.saveJob(this)

        message.mediaUrl?.getFilePath()?.let { _ ->
            val inputStream = MixinApplication.appContext.contentResolver.openInputStream(Uri.parse(message.mediaUrl)) ?: return@let
            val extensionName = message.name?.getExtensionName()
            val file = MixinApplication.appContext.getDocumentPath().createDocumentTemp(message.conversationId, message.id, extensionName)
            file.copyFromInputStream(inputStream)
            messageDao.updateMediaMessageUrl(file.name, message.id)

            jobManager.addJobInBackground(
                SendAttachmentMessageJob(
                    createAttachmentMessage(
                        message.id, message.conversationId, message.userId, message.category,
                        null, message.name, file.name,
                        message.mediaMimeType!!, message.mediaSize!!, message.createdAt, null,
                        null, MediaStatus.PENDING, MessageStatus.SENDING.name, message.quoteMessageId, message.quoteContent
                    )
                )
            )
        }
    }
}
