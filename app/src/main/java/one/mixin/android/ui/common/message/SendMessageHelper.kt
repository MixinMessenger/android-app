package one.mixin.android.ui.common.message

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import one.mixin.android.MixinApplication
import one.mixin.android.extension.base64Encode
import one.mixin.android.extension.bitmap2String
import one.mixin.android.extension.blurThumbnail
import one.mixin.android.extension.copyFromInputStream
import one.mixin.android.extension.createGifTemp
import one.mixin.android.extension.createImageTemp
import one.mixin.android.extension.getBotNumber
import one.mixin.android.extension.getFilePath
import one.mixin.android.extension.getImagePath
import one.mixin.android.extension.getImageSize
import one.mixin.android.extension.getMimeType
import one.mixin.android.extension.isImageSupport
import one.mixin.android.extension.nowInUtc
import one.mixin.android.extension.postOptimize
import one.mixin.android.job.ConvertDataJob
import one.mixin.android.job.ConvertVideoJob
import one.mixin.android.job.MixinJobManager
import one.mixin.android.job.SendAttachmentMessageJob
import one.mixin.android.job.SendGiphyJob
import one.mixin.android.job.SendMessageJob
import one.mixin.android.job.SendTranscriptJob
import one.mixin.android.repository.UserRepository
import one.mixin.android.util.Attachment
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.image.Compressor
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.MessageStatus
import one.mixin.android.vo.QuoteMessageItem
import one.mixin.android.vo.TranscriptMessage
import one.mixin.android.vo.TranscriptMinimal
import one.mixin.android.vo.User
import one.mixin.android.vo.createAppCardMessage
import one.mixin.android.vo.createAttachmentMessage
import one.mixin.android.vo.createAudioMessage
import one.mixin.android.vo.createContactMessage
import one.mixin.android.vo.createLiveMessage
import one.mixin.android.vo.createLocationMessage
import one.mixin.android.vo.createMediaMessage
import one.mixin.android.vo.createMessage
import one.mixin.android.vo.createPostMessage
import one.mixin.android.vo.createRecallMessage
import one.mixin.android.vo.createReplyTextMessage
import one.mixin.android.vo.createStickerMessage
import one.mixin.android.vo.giphy.Image
import one.mixin.android.vo.isAppCard
import one.mixin.android.vo.isAudio
import one.mixin.android.vo.isContact
import one.mixin.android.vo.isData
import one.mixin.android.vo.isImage
import one.mixin.android.vo.isLocation
import one.mixin.android.vo.isPost
import one.mixin.android.vo.isSticker
import one.mixin.android.vo.isText
import one.mixin.android.vo.isVideo
import one.mixin.android.vo.toQuoteMessageItem
import one.mixin.android.websocket.ContactMessagePayload
import one.mixin.android.websocket.LiveMessagePayload
import one.mixin.android.websocket.LocationPayload
import one.mixin.android.websocket.RecallMessagePayload
import one.mixin.android.websocket.StickerMessagePayload
import one.mixin.android.widget.gallery.MimeType
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import javax.inject.Inject

class SendMessageHelper @Inject internal constructor(private val jobManager: MixinJobManager, private val userRepository: UserRepository) {

    fun sendTextMessage(scope: CoroutineScope, conversationId: String, sender: User, content: String, isPlain: Boolean, isSilentMessage: Boolean? = null) {
        val category =
            if (isPlain) MessageCategory.PLAIN_TEXT.name else MessageCategory.SIGNAL_TEXT.name
        val message = createMessage(
            UUID.randomUUID().toString(),
            conversationId,
            sender.userId,
            category,
            content.trim(),
            nowInUtc(),
            MessageStatus.SENDING.name
        )
        scope.launch {
            val botNumber = message.content?.getBotNumber()
            var recipientId: String? = null
            if (botNumber != null && botNumber.isNotBlank()) {
                recipientId = userRepository.findUserIdByAppNumber(message.conversationId, botNumber)
                recipientId?.let {
                    message.category = MessageCategory.PLAIN_TEXT.name
                }
            }
            jobManager.addJobInBackground(SendMessageJob(message, recipientId = recipientId, isSilent = isSilentMessage))
        }
    }

    fun sendTranscriptMessage(messageId: String, conversationId: String, sender: User, transcriptMessages: List<TranscriptMessage>, isPlain: Boolean) {
        val category = if (isPlain) MessageCategory.PLAIN_TRANSCRIPT.name else MessageCategory.SIGNAL_TRANSCRIPT.name
        transcriptMessages.onEach { t ->
            t.type = when {
                t.isText() -> if (isPlain)MessageCategory.PLAIN_TEXT.name else MessageCategory.SIGNAL_TEXT.name
                t.isAudio() -> if (isPlain)MessageCategory.PLAIN_AUDIO.name else MessageCategory.SIGNAL_AUDIO.name
                t.isContact() -> if (isPlain)MessageCategory.PLAIN_CONTACT.name else MessageCategory.SIGNAL_CONTACT.name
                t.isData() -> if (isPlain)MessageCategory.PLAIN_DATA.name else MessageCategory.SIGNAL_DATA.name
                t.isImage() -> if (isPlain)MessageCategory.PLAIN_IMAGE.name else MessageCategory.SIGNAL_IMAGE.name
                t.isLocation() -> if (isPlain)MessageCategory.PLAIN_LOCATION.name else MessageCategory.SIGNAL_LOCATION.name
                t.isPost() -> if (isPlain)MessageCategory.PLAIN_POST.name else MessageCategory.SIGNAL_POST.name
                t.isSticker() -> if (isPlain)MessageCategory.PLAIN_STICKER.name else MessageCategory.SIGNAL_STICKER.name
                t.isVideo() -> if (isPlain)MessageCategory.PLAIN_VIDEO.name else MessageCategory.SIGNAL_VIDEO.name
                t.isAppCard() -> MessageCategory.APP_CARD.name
                else -> throw IllegalArgumentException("Unknown type")
            }
        }
        val message = createMessage(
            messageId,
            conversationId,
            sender.userId,
            category,
            GsonHelper.customGson.toJson(
                transcriptMessages.sortedBy { t -> t.createdAt }.map {
                    TranscriptMinimal(it.userFullName ?: "", it.type, it.content)
                }
            ),
            nowInUtc(),
            MessageStatus.SENDING.name
        )
        jobManager.addJobInBackground(SendTranscriptJob(message, transcriptMessages))
    }

    fun sendReplyTextMessage(
        conversationId: String,
        sender: User,
        content: String,
        replyMessage: MessageItem,
        isPlain: Boolean,
        isSilentMessage: Boolean? = null
    ) {
        val category =
            if (isPlain) MessageCategory.PLAIN_TEXT.name else MessageCategory.SIGNAL_TEXT.name
        val message = createReplyTextMessage(
            UUID.randomUUID().toString(),
            conversationId,
            sender.userId,
            category,
            content.trim(),
            nowInUtc(),
            MessageStatus.SENDING.name,
            replyMessage.messageId,
            Gson().toJson(QuoteMessageItem(replyMessage))
        )
        jobManager.addJobInBackground(SendMessageJob(message, isSilent = isSilentMessage))
    }

    fun sendPostMessage(conversationId: String, sender: User, content: String, isPlain: Boolean) {
        val category =
            if (isPlain) MessageCategory.PLAIN_POST.name else MessageCategory.SIGNAL_POST.name
        val message = createPostMessage(
            UUID.randomUUID().toString(),
            conversationId,
            sender.userId,
            category,
            content.trim(),
            content.postOptimize(),
            nowInUtc(),
            MessageStatus.SENDING.name
        )
        jobManager.addJobInBackground(SendMessageJob(message))
    }

    fun sendAppCardMessage(conversationId: String, sender: User, content: String) {
        val message = createAppCardMessage(
            UUID.randomUUID().toString(),
            conversationId,
            sender.userId,
            content,
            nowInUtc(),
            MessageStatus.SENDING.name
        )
        jobManager.addJobInBackground(SendMessageJob(message))
    }

    fun sendAttachmentMessage(conversationId: String, sender: User, attachment: Attachment, isPlain: Boolean, replyMessage: MessageItem? = null) {
        val category = if (isPlain) MessageCategory.PLAIN_DATA.name else MessageCategory.SIGNAL_DATA.name
        val message = createAttachmentMessage(
            UUID.randomUUID().toString(), conversationId, sender.userId, category,
            null, attachment.filename, attachment.uri.toString(),
            attachment.mimeType, attachment.fileSize, nowInUtc(), null,
            null, MediaStatus.PENDING, MessageStatus.SENDING.name, replyMessage?.messageId, replyMessage?.toQuoteMessageItem()
        )
        jobManager.addJobInBackground(ConvertDataJob(message))
    }

    fun sendAudioMessage(
        conversationId: String,
        messageId: String,
        sender: User,
        file: File,
        duration: Long,
        waveForm: ByteArray,
        isPlain: Boolean,
        replyMessage: MessageItem? = null,
    ) {
        val category = if (isPlain) MessageCategory.PLAIN_AUDIO.name else MessageCategory.SIGNAL_AUDIO.name
        val message = createAudioMessage(
            messageId, conversationId, sender.userId, null, category,
            file.length(), Uri.fromFile(file).toString(), duration.toString(), nowInUtc(), waveForm, null, null,
            MediaStatus.PENDING, MessageStatus.SENDING.name, replyMessage?.messageId, replyMessage?.toQuoteMessageItem()
        )
        jobManager.addJobInBackground(SendAttachmentMessageJob(message))
    }

    fun sendStickerMessage(
        conversationId: String,
        sender: User,
        transferStickerData: StickerMessagePayload,
        isPlain: Boolean
    ) {
        val category =
            if (isPlain) MessageCategory.PLAIN_STICKER.name else MessageCategory.SIGNAL_STICKER.name
        val encoded = GsonHelper.customGson.toJson(transferStickerData).base64Encode()
        transferStickerData.stickerId?.let {
            val message = createStickerMessage(
                UUID.randomUUID().toString(),
                conversationId,
                sender.userId,
                category,
                encoded,
                transferStickerData.albumId,
                it,
                transferStickerData.name,
                MessageStatus.SENDING.name,
                nowInUtc()
            )
            jobManager.addJobInBackground(SendMessageJob(message))
        }
    }

    fun sendContactMessage(
        conversationId: String,
        sender: User,
        shareUserId: String,
        shareUserFullName: String? = null,
        isPlain: Boolean,
        replyMessage: MessageItem? = null
    ) {
        val category = if (isPlain) MessageCategory.PLAIN_CONTACT.name else MessageCategory.SIGNAL_CONTACT.name
        val transferContactData = ContactMessagePayload(shareUserId)
        val encoded = GsonHelper.customGson.toJson(transferContactData).base64Encode()
        val message = createContactMessage(
            UUID.randomUUID().toString(), conversationId, sender.userId, category, encoded, shareUserId,
            MessageStatus.SENDING.name, nowInUtc(), shareUserFullName, replyMessage?.messageId, replyMessage?.toQuoteMessageItem()
        )
        jobManager.addJobInBackground(SendMessageJob(message))
    }

    fun sendVideoMessage(
        conversationId: String,
        senderId: String,
        uri: Uri,
        isPlain: Boolean,
        messageId: String? = null,
        createdAt: String? = null,
        replyMessage: MessageItem? = null,
    ) {
        val mid = messageId ?: UUID.randomUUID().toString()
        jobManager.addJobInBackground(ConvertVideoJob(conversationId, senderId, uri, isPlain, mid, createdAt, replyMessage))
    }

    fun sendRecallMessage(conversationId: String, sender: User, list: List<MessageItem>) {
        list.forEach { messageItem ->
            val transferRecallData = RecallMessagePayload(messageItem.messageId)
            val encoded = GsonHelper.customGson.toJson(transferRecallData).base64Encode()
            val message = createRecallMessage(
                UUID.randomUUID().toString(),
                conversationId,
                sender.userId,
                MessageCategory.MESSAGE_RECALL.name,
                encoded,
                MessageStatus.SENDING.name,
                nowInUtc()
            )
            jobManager.addJobInBackground(
                SendMessageJob(
                    message,
                    recallMessageId = messageItem.messageId
                )
            )
        }
    }

    fun sendLiveMessage(
        conversationId: String,
        sender: User,
        transferLiveData: LiveMessagePayload,
        isPlain: Boolean
    ) {
        val category =
            if (isPlain) MessageCategory.PLAIN_LIVE.name else MessageCategory.SIGNAL_LIVE.name
        val encoded =
            GsonHelper.customGson.toJson(transferLiveData)
        val message = createLiveMessage(
            UUID.randomUUID().toString(),
            conversationId,
            sender.userId,
            category,
            encoded,
            transferLiveData.width,
            transferLiveData.height,
            transferLiveData.url,
            transferLiveData.thumbUrl,
            MessageStatus.SENDING.name,
            nowInUtc()
        )
        jobManager.addJobInBackground(SendMessageJob(message))
    }

    fun sendGiphyMessage(
        conversationId: String,
        senderId: String,
        image: Image,
        isPlain: Boolean,
        previewUrl: String
    ) {
        val category =
            if (isPlain) MessageCategory.PLAIN_IMAGE.name else MessageCategory.SIGNAL_IMAGE.name
        jobManager.addJobInBackground(
            SendGiphyJob(
                conversationId, senderId, image.url, image.width, image.height, image.size.toLong(),
                category, UUID.randomUUID().toString(), previewUrl, nowInUtc()
            )
        )
    }

    fun sendLocationMessage(conversationId: String, senderId: String, location: LocationPayload, isPlain: Boolean) {
        val category = if (isPlain) MessageCategory.PLAIN_LOCATION.name else MessageCategory.SIGNAL_LOCATION.name
        jobManager.addJobInBackground(
            SendMessageJob(
                createLocationMessage(
                    UUID.randomUUID().toString(),
                    conversationId,
                    senderId,
                    category,
                    GsonHelper.customGson.toJson(location),
                    MessageStatus.SENT.name,
                    nowInUtc()
                )
            )
        )
    }

    /**
     * Send image message
     *
     * @return 0  send successful
     * @return -1 target image has some bad data
     * @return -2 target image format not supported
     */
    fun sendImageMessage(
        conversationId: String,
        sender: User,
        uri: Uri,
        isPlain: Boolean,
        mime: String? = null,
        replyMessage: MessageItem? = null,
    ): Int {
        val category =
            if (isPlain) MessageCategory.PLAIN_IMAGE.name else MessageCategory.SIGNAL_IMAGE.name
        var mimeType = mime
        if (mimeType == null) {
            mimeType = getMimeType(uri, true)
            if (mimeType?.isImageSupport() != true) {
                return -2
            }
        }
        val messageId = UUID.randomUUID().toString()
        if (mimeType == MimeType.GIF.toString()) {
            val gifFile = MixinApplication.get().getImagePath().createGifTemp(conversationId, messageId)
            val path = uri.getFilePath(MixinApplication.get()) ?: return -1
            gifFile.copyFromInputStream(FileInputStream(path))
            val size = getImageSize(gifFile)
            val thumbnail = gifFile.blurThumbnail(size)?.bitmap2String(mimeType)

            val message = createMediaMessage(
                messageId,
                conversationId,
                sender.userId,
                category,
                null,
                Uri.fromFile(gifFile).toString(),
                mimeType,
                gifFile.length(),
                size.width,
                size.height,
                thumbnail,
                null,
                null,
                nowInUtc(),
                MediaStatus.PENDING,
                MessageStatus.SENDING.name,
                replyMessage?.messageId,
                replyMessage?.toQuoteMessageItem()
            )
            jobManager.addJobInBackground(SendAttachmentMessageJob(message))
            return 0
        }

        val temp = MixinApplication.get().getImagePath().createImageTemp(conversationId, messageId, type = ".jpg")
        val path = uri.getFilePath(MixinApplication.get()) ?: return -1
        val imageFile: File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && mimeType == MimeType.HEIC.toString()) {
            val source = ImageDecoder.createSource(File(path))
            val bitmap = ImageDecoder.decodeBitmap(source)
            temp.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            }
            temp
        } else {
            Compressor()
                .setCompressFormat(Bitmap.CompressFormat.JPEG)
                .compressToFile(
                    File(path),
                    temp.absolutePath
                )
        }
        val imageUrl = Uri.fromFile(temp).toString()
        val length = imageFile.length()
        if (length <= 0) {
            return -1
        }
        val size = getImageSize(imageFile)
        val thumbnail = imageFile.blurThumbnail(size)?.bitmap2String(mimeType)
        val message = createMediaMessage(
            messageId,
            conversationId,
            sender.userId,
            category,
            null,
            imageUrl,
            MimeType.JPEG.toString(),
            length,
            size.width,
            size.height,
            thumbnail,
            null,
            null,
            nowInUtc(),
            MediaStatus.PENDING,
            MessageStatus.SENDING.name,
            replyMessage?.messageId,
            replyMessage?.toQuoteMessageItem()
        )
        jobManager.addJobInBackground(SendAttachmentMessageJob(message))
        return 0
    }
}
