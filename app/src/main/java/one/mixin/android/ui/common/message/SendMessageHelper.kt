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
import one.mixin.android.repository.UserRepository
import one.mixin.android.util.Attachment
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.image.Compressor
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.MessageStatus
import one.mixin.android.vo.QuoteMessageItem
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

    fun sendTextMessage(scope: CoroutineScope, conversationId: String, sender: User, content: String, isEncrypted: Boolean) {
        val category =
            if (isEncrypted) MessageCategory.ENCRYPTED_TEXT.name else MessageCategory.SIGNAL_TEXT.name
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
                    message.category = MessageCategory.ENCRYPTED_TEXT.name
                }
            }
            jobManager.addJobInBackground(SendMessageJob(message, recipientId = recipientId))
        }
    }

    fun sendReplyTextMessage(
        conversationId: String,
        sender: User,
        content: String,
        replyMessage: MessageItem,
        isEncrypted: Boolean
    ) {
        val category =
            if (isEncrypted) MessageCategory.ENCRYPTED_TEXT.name else MessageCategory.SIGNAL_TEXT.name
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
        jobManager.addJobInBackground(SendMessageJob(message))
    }

    fun sendPostMessage(conversationId: String, sender: User, content: String, isEncrypted: Boolean) {
        val category =
            if (isEncrypted) MessageCategory.ENCRYPTED_POST.name else MessageCategory.SIGNAL_POST.name
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

    fun sendAttachmentMessage(conversationId: String, sender: User, attachment: Attachment, isEncrypted: Boolean, replyMessage: MessageItem? = null) {
        val category = if (isEncrypted) MessageCategory.ENCRYPTED_DATA.name else MessageCategory.SIGNAL_DATA.name
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
        isEncrypted: Boolean,
        replyMessage: MessageItem? = null
    ) {
        val category = if (isEncrypted) MessageCategory.ENCRYPTED_AUDIO.name else MessageCategory.SIGNAL_AUDIO.name
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
        isEncrypted: Boolean
    ) {
        val category =
            if (isEncrypted) MessageCategory.ENCRYPTED_STICKER.name else MessageCategory.SIGNAL_STICKER.name
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
        isEncrypted: Boolean,
        replyMessage: MessageItem? = null
    ) {
        val category = if (isEncrypted) MessageCategory.ENCRYPTED_CONTACT.name else MessageCategory.SIGNAL_CONTACT.name
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
        isEncrypted: Boolean,
        messageId: String? = null,
        createdAt: String? = null,
        replyMessage: MessageItem? = null
    ) {
        val mid = messageId ?: UUID.randomUUID().toString()
        jobManager.addJobInBackground(ConvertVideoJob(conversationId, senderId, uri, isEncrypted, mid, createdAt, replyMessage))
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
        isEncrypted: Boolean
    ) {
        val category =
            if (isEncrypted) MessageCategory.ENCRYPTED_LIVE.name else MessageCategory.SIGNAL_LIVE.name
        val encoded =
            GsonHelper.customGson.toJson(transferLiveData).base64Encode()
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
        isEncrypted: Boolean,
        previewUrl: String
    ) {
        val category =
            if (isEncrypted) MessageCategory.ENCRYPTED_IMAGE.name else MessageCategory.SIGNAL_IMAGE.name
        jobManager.addJobInBackground(
            SendGiphyJob(
                conversationId, senderId, image.url, image.width, image.height, image.size.toLong(),
                category, UUID.randomUUID().toString(), previewUrl, nowInUtc()
            )
        )
    }

    fun sendLocationMessage(conversationId: String, senderId: String, location: LocationPayload, isEncrypted: Boolean) {
        val category = if (isEncrypted) MessageCategory.ENCRYPTED_LOCATION.name else MessageCategory.SIGNAL_LOCATION.name
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
        isEncrypted: Boolean,
        mime: String? = null,
        replyMessage: MessageItem? = null
    ): Int {
        val category =
            if (isEncrypted) MessageCategory.ENCRYPTED_IMAGE.name else MessageCategory.SIGNAL_IMAGE.name
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
