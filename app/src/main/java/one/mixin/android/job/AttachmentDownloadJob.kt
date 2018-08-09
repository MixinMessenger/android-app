package one.mixin.android.job

import android.net.Uri
import com.birbit.android.jobqueue.Params
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Okio
import one.mixin.android.MixinApplication
import one.mixin.android.RxBus
import one.mixin.android.api.MixinResponse
import one.mixin.android.api.response.AttachmentResponse
import one.mixin.android.crypto.attachment.AttachmentCipherInputStream
import one.mixin.android.event.ProgressEvent
import one.mixin.android.extension.copyFromInputStream
import one.mixin.android.extension.createAudioTemp
import one.mixin.android.extension.createDocumentTemp
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
import one.mixin.android.util.okhttp.ProgressListener
import one.mixin.android.util.okhttp.ProgressResponseBody
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.Message
import one.mixin.android.vo.MessageCategory
import one.mixin.android.widget.gallery.MimeType
import org.whispersystems.libsignal.logging.Log
import org.whispersystems.libsignal.util.guava.Optional
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class AttachmentDownloadJob(private val message: Message)
    : MixinJob(Params(PRIORITY_RECEIVE_MESSAGE).addTags(AttachmentDownloadJob.GROUP)
    .groupBy("attachment_download").requireNetwork().persist(), message.id) {

    private val TAG = AttachmentDownloadJob::class.java.simpleName

    companion object {
        const val GROUP = "AttachmentDownloadJob"
        private const val serialVersionUID = 1L
    }

    @Transient
    private var call: Call? = null
    @Transient
    private var attachmentCall: retrofit2.Call<MixinResponse<AttachmentResponse>>? = null

    override fun cancel() {
        isCancel = true
        call?.let {
            if (!it.isCanceled) {
                it.cancel()
            }
        }
        attachmentCall?.let {
            if (!it.isCanceled) {
                it.cancel()
            }
        }
        messageDao.updateMediaStatus(MediaStatus.CANCELED.name, message.id)
        removeJob()
    }

    override fun onRun() {
        if (isCancel) {
            return
        }
        jobManager.saveJob(this)
        attachmentCall = conversationApi.getAttachment(message.content!!)
        val body = attachmentCall!!.execute().body()
        if (body != null && (body.isSuccess || !isCancel)) {
            decryptAttachment(body.data!!.view_url!!)
            removeJob()
        } else {
            removeJob()
            Log.e(TAG, "get attachment url failed")
            messageDao.updateMediaStatus(MediaStatus.CANCELED.name, message.id)
        }
    }

    override fun onCancel(cancelReason: Int, throwable: Throwable?) {
        super.onCancel(cancelReason, throwable)
        messageDao.updateMediaStatus(MediaStatus.CANCELED.name, message.id)
        removeJob()
    }

    override fun onAdded() {
        super.onAdded()
        messageDao.updateMediaStatus(MediaStatus.PENDING.name, message.id)
        RxBus.publish(ProgressEvent(message.id, 0f))
    }

    private fun decryptAttachment(url: String): Boolean {
        val destination = createTempFile()
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain: Interceptor.Chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder().body(ProgressResponseBody(originalResponse.body(),
                    ProgressListener { bytesRead, contentLength, done ->
                        if (!done) {
                            RxBus.publish(ProgressEvent(message.id,
                                bytesRead.toFloat() / contentLength.toFloat()))
                        }
                    })).build()
            }
            .build()

        val request = Request.Builder()
            .addHeader("Content-Type", "application/octet-stream")
            .url(url)
            .build()
        call = client.newCall(request)
        val response = call!!.execute()
        if (response.code() == 404) {
            messageDao.updateMediaStatus(MediaStatus.EXPIRED.name, message.id)
            return true
        } else if (response.isSuccessful && !isCancel && response.body() != null) {
            val sink = Okio.buffer(Okio.sink(destination))
            sink.writeAll(response.body()!!.source())
            sink.close()
            if (message.category.endsWith("_IMAGE")) {
                if (message.mediaMimeType?.isImageSupport() == true) {
                    val attachmentCipherInputStream = if (message.category == MessageCategory.SIGNAL_IMAGE.name) {
                        AttachmentCipherInputStream(destination, message.mediaKey, Optional.of(message.mediaDigest))
                    } else {
                        FileInputStream(destination)
                    }
                    val imageFile = when {
                        message.mediaMimeType.equals(MimeType.PNG.toString(), true) -> {
                            MixinApplication.get().getImagePath().createImageTemp("REC", ".png")
                        }
                        message.mediaMimeType.equals(MimeType.GIF.toString(), true) -> {
                            MixinApplication.get().getImagePath().createGifTemp()
                        }
                        message.mediaMimeType.equals(MimeType.WEBP.toString(), true) -> {
                            MixinApplication.get().getImagePath().createWebpTemp()
                        }
                        else -> {
                            MixinApplication.get().getImagePath().createImageTemp("REC", ".jpg")
                        }
                    }
                    imageFile.copyFromInputStream(attachmentCipherInputStream)
                    messageDao.updateMediaMessageUrl(Uri.fromFile(imageFile).toString(), message.id)
                    messageDao.updateMediaStatus(MediaStatus.DONE.name, message.id)
                }
            } else if (message.category.endsWith("_DATA")) {
                val attachmentCipherInputStream = if (message.category == MessageCategory.SIGNAL_DATA.name) {
                    AttachmentCipherInputStream(destination, message.mediaKey, Optional.of(message.mediaDigest))
                } else {
                    FileInputStream(destination)
                }
                val extensionName = message.name?.getExtensionName()
                val imageFile = MixinApplication.get().getDocumentPath()
                    .createDocumentTemp(extensionName)
                imageFile.copyFromInputStream(attachmentCipherInputStream)
                messageDao.updateMediaMessageUrl(imageFile.absolutePath, message.id)
                messageDao.updateMediaStatus(MediaStatus.DONE.name, message.id)
            } else if (message.category.endsWith("_VIDEO")) {
                val attachmentCipherInputStream = if (message.category == MessageCategory.SIGNAL_VIDEO.name) {
                    AttachmentCipherInputStream(destination, message.mediaKey, Optional.of(message.mediaDigest))
                } else {
                    FileInputStream(destination)
                }
                val extensionName = message.name?.getExtensionName().let {
                    it ?: "mp4"
                }
                val imageFile = MixinApplication.get().getVideoPath()
                    .createVideoTemp(extensionName)
                imageFile.copyFromInputStream(attachmentCipherInputStream)
                messageDao.updateMediaMessageUrl(Uri.fromFile(imageFile).toString(), message.id)
                messageDao.updateMediaStatus(MediaStatus.DONE.name, message.id)
            } else if (message.category.endsWith("_AUDIO")) {
                val attachmentCipherInputStream = if (message.category == MessageCategory.SIGNAL_AUDIO.name) {
                    AttachmentCipherInputStream(destination, message.mediaKey, Optional.of(message.mediaDigest))
                } else {
                    FileInputStream(destination)
                }
                val imageFile = MixinApplication.get().getAudioPath()
                    .createAudioTemp("ogg")
                imageFile.copyFromInputStream(attachmentCipherInputStream)
                messageDao.updateMediaMessageUrl(Uri.fromFile(imageFile).toString(), message.id)
                messageDao.updateMediaStatus(MediaStatus.DONE.name, message.id)
            }
            return true
        } else {
            messageDao.updateMediaStatus(MediaStatus.CANCELED.name, message.id)
            return false
        }
    }

    private fun createTempFile(): File {
        val file = File.createTempFile("attachment", "tmp", applicationContext.cacheDir)
        file.deleteOnExit()
        return file
    }
}