package one.mixin.android.job

import com.birbit.android.jobqueue.Params
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import one.mixin.android.MixinApplication
import one.mixin.android.RxBus
import one.mixin.android.api.MixinResponse
import one.mixin.android.api.response.AttachmentResponse
import one.mixin.android.crypto.attachment.AttachmentCipherInputStream
import one.mixin.android.event.ProgressEvent.Companion.loadingEvent
import one.mixin.android.extension.copyFromInputStream
import one.mixin.android.extension.createAudioTemp
import one.mixin.android.extension.createDocumentTemp
import one.mixin.android.extension.createEmptyTemp
import one.mixin.android.extension.createGifTemp
import one.mixin.android.extension.createImageTemp
import one.mixin.android.extension.createVideoTemp
import one.mixin.android.extension.createWebpTemp
import one.mixin.android.extension.getAudioPath
import one.mixin.android.extension.getDocumentPath
import one.mixin.android.extension.getExtensionName
import one.mixin.android.extension.getImagePath
import one.mixin.android.extension.getVideoPath
import one.mixin.android.extension.isImageSupport
import one.mixin.android.job.MixinJobManager.Companion.attachmentProcess
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.okhttp.ProgressListener
import one.mixin.android.util.okhttp.ProgressResponseBody
import one.mixin.android.vo.AttachmentExtra
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.Message
import one.mixin.android.vo.MessageCategory
import one.mixin.android.widget.gallery.MimeType
import org.whispersystems.libsignal.logging.Log
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class AttachmentDownloadJob(
    private val message: Message,
    private val attachmentId: String? = null
) : MixinJob(
    Params(PRIORITY_RECEIVE_MESSAGE)
        .groupBy("attachment_download").requireNetwork().persist(),
    message.id
) {

    private val TAG = AttachmentDownloadJob::class.java.simpleName

    companion object {
        private const val serialVersionUID = 1L
    }

    @Transient
    private var call: Call? = null
    @Transient
    private var attachmentCall: retrofit2.Call<MixinResponse<AttachmentResponse>>? = null

    override fun cancel() {
        isCancelled = true
        call?.let {
            if (!it.isCanceled()) {
                it.cancel()
            }
        }
        attachmentCall?.let {
            if (!it.isCanceled) {
                it.cancel()
            }
        }
        messageDao.updateMediaStatus(MediaStatus.CANCELED.name, message.id)
        attachmentProcess.remove(message.id)
        removeJob()
    }

    override fun getRetryLimit(): Int {
        return 1
    }

    override fun onRun() {
        if (isCancelled) {
            removeJob()
            return
        }
        jobManager.saveJob(this)
        attachmentCall = conversationApi.getAttachment(attachmentId ?: message.content!!)
        val body = attachmentCall!!.execute().body()
        if (body != null && (body.isSuccess || !isCancelled) && body.data != null) {
            val attachmentResponse = body.data!!
            attachmentResponse.view_url?.let {
                val result = decryptAttachment(it)
                if (result) {
                    val attachmentExtra = GsonHelper.customGson.toJson(AttachmentExtra(attachmentResponse.attachment_id, message.id, attachmentResponse.created_at))
                    messageDao.updateMessageContent(attachmentExtra, message.id)
                }
            }
            removeJob()
        } else {
            removeJob()
            Log.e(TAG, "get attachment url failed")
            messageDao.updateMediaStatus(MediaStatus.CANCELED.name, message.id)
            attachmentProcess.remove(message.id)
        }
    }

    override fun onCancel(cancelReason: Int, throwable: Throwable?) {
        super.onCancel(cancelReason, throwable)
        messageDao.updateMediaStatus(MediaStatus.CANCELED.name, message.id)
        attachmentProcess.remove(message.id)
        removeJob()
    }

    override fun onAdded() {
        super.onAdded()
        messageDao.updateMediaStatus(MediaStatus.PENDING.name, message.id)
        RxBus.publish(loadingEvent(message.id, 0f))
    }

    private fun decryptAttachment(url: String): Boolean {
        val destination = createTempFile()
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain: Interceptor.Chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder().body(
                    ProgressResponseBody(
                        originalResponse.body,
                        ProgressListener { bytesRead, contentLength, done ->
                            if (!done) {
                                val progress = try {
                                    bytesRead.toFloat() / contentLength.toFloat()
                                } catch (e: Exception) {
                                    0f
                                }
                                attachmentProcess[message.id] = (progress * 100).toInt()
                                RxBus.publish(loadingEvent(message.id, progress))
                            }
                        }
                    )
                ).build()
            }
            .build()

        val request = Request.Builder()
            .addHeader("Content-Type", "application/octet-stream")
            .url(url)
            .build()
        call = client.newCall(request)
        val response = try {
            call!!.execute()
        } catch (e: Exception) {
            messageDao.updateMediaStatus(MediaStatus.CANCELED.name, message.id)
            attachmentProcess.remove(message.id)
            return false
        }
        if (response.code == 404) {
            messageDao.updateMediaStatus(MediaStatus.EXPIRED.name, message.id)
            attachmentProcess.remove(message.id)
            return true
        } else if (response.isSuccessful && !isCancelled && response.body != null) {
            val sink = destination.sink().buffer()
            sink.writeAll(response.body!!.source())
            sink.close()
            if (message.category.endsWith("_IMAGE")) {
                val attachmentCipherInputStream = if (message.category == MessageCategory.SIGNAL_IMAGE.name) {
                    AttachmentCipherInputStream.createForAttachment(destination, 0, message.mediaKey, message.mediaDigest)
                } else {
                    FileInputStream(destination)
                }
                val imageFile = when {
                    message.mediaMimeType?.isImageSupport() == false -> {
                        MixinApplication.get().getImagePath().createEmptyTemp(message.conversationId, message.id)
                    }
                    message.mediaMimeType.equals(MimeType.PNG.toString(), true) -> {
                        MixinApplication.get().getImagePath().createImageTemp(message.conversationId, message.id, ".png")
                    }
                    message.mediaMimeType.equals(MimeType.GIF.toString(), true) -> {
                        MixinApplication.get().getImagePath().createGifTemp(message.conversationId, message.id)
                    }
                    message.mediaMimeType.equals(MimeType.WEBP.toString(), true) -> {
                        MixinApplication.get().getImagePath().createWebpTemp(message.conversationId, message.id)
                    }
                    else -> {
                        MixinApplication.get().getImagePath().createImageTemp(message.conversationId, message.id, ".jpg")
                    }
                }
                imageFile.copyFromInputStream(attachmentCipherInputStream)
                messageDao.updateMediaMessageUrl(imageFile.name, message.id)
                messageDao.updateMediaSize(imageFile.length(), message.id)
                messageDao.updateMediaStatus(MediaStatus.DONE.name, message.id)
                attachmentProcess.remove(message.id)
            } else if (message.category.endsWith("_DATA")) {
                val attachmentCipherInputStream = if (message.category == MessageCategory.SIGNAL_DATA.name) {
                    AttachmentCipherInputStream.createForAttachment(destination, 0, message.mediaKey, message.mediaDigest)
                } else {
                    FileInputStream(destination)
                }
                val extensionName = message.name?.getExtensionName()
                val dataFile = MixinApplication.get().getDocumentPath()
                    .createDocumentTemp(message.conversationId, message.id, extensionName)
                dataFile.copyFromInputStream(attachmentCipherInputStream)
                messageDao.updateMediaMessageUrl(dataFile.name, message.id)
                messageDao.updateMediaSize(dataFile.length(), message.id)
                messageDao.updateMediaStatus(MediaStatus.DONE.name, message.id)
                attachmentProcess.remove(message.id)
            } else if (message.category.endsWith("_VIDEO")) {
                val attachmentCipherInputStream = if (message.category == MessageCategory.SIGNAL_VIDEO.name) {
                    AttachmentCipherInputStream.createForAttachment(destination, 0, message.mediaKey, message.mediaDigest)
                } else {
                    FileInputStream(destination)
                }
                val extensionName = message.name?.getExtensionName().let {
                    it ?: "mp4"
                }
                val videoFile = MixinApplication.get().getVideoPath()
                    .createVideoTemp(message.conversationId, message.id, extensionName)
                videoFile.copyFromInputStream(attachmentCipherInputStream)
                messageDao.updateMediaMessageUrl(videoFile.name, message.id)
                messageDao.updateMediaSize(videoFile.length(), message.id)
                messageDao.updateMediaStatus(MediaStatus.DONE.name, message.id)
                attachmentProcess.remove(message.id)
            } else if (message.category.endsWith("_AUDIO")) {
                val attachmentCipherInputStream = if (message.category == MessageCategory.SIGNAL_AUDIO.name) {
                    AttachmentCipherInputStream.createForAttachment(destination, 0, message.mediaKey, message.mediaDigest)
                } else {
                    FileInputStream(destination)
                }
                val audioFile = MixinApplication.get().getAudioPath()
                    .createAudioTemp(message.conversationId, message.id, "ogg")
                audioFile.copyFromInputStream(attachmentCipherInputStream)
                messageDao.updateMediaMessageUrl(audioFile.name, message.id)
                messageDao.updateMediaSize(audioFile.length(), message.id)
                messageDao.updateMediaStatus(MediaStatus.DONE.name, message.id)
                attachmentProcess.remove(message.id)
            }
            return true
        } else {
            messageDao.updateMediaStatus(MediaStatus.CANCELED.name, message.id)
            attachmentProcess.remove(message.id)
            return false
        }
    }

    private fun createTempFile(): File {
        val file = File.createTempFile("attachment", "tmp", applicationContext.cacheDir)
        file.deleteOnExit()
        return file
    }
}
