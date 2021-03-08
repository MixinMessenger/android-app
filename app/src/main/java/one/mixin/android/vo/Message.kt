package one.mixin.android.vo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(
    tableName = "messages",
    indices = [
        Index(value = arrayOf("conversation_id", "created_at")),
        Index(value = arrayOf("conversation_id", "user_id", "status", "created_at")),
        Index(value = arrayOf("conversation_id", "status", "user_id"))
    ],
    foreignKeys = [
        (
            ForeignKey(
                entity = Conversation::class,
                onDelete = CASCADE,
                parentColumns = arrayOf("conversation_id"),
                childColumns = arrayOf("conversation_id")
            )
            )
    ]
)
class Message(
    @PrimaryKey
    @SerializedName("id")
    @ColumnInfo(name = "id")
    var id: String,

    @SerializedName("conversation_id")
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    @SerializedName("user_id")
    @ColumnInfo(name = "user_id")
    var userId: String,

    @SerializedName("category")
    @ColumnInfo(name = "category")
    var category: String,

    @SerializedName("content")
    @ColumnInfo(name = "content")
    var content: String?,

    @SerializedName("media_url")
    @ColumnInfo(name = "media_url")
    val mediaUrl: String?,

    @SerializedName("media_mime_type")
    @ColumnInfo(name = "media_mime_type")
    val mediaMimeType: String?,

    @SerializedName("media_size")
    @ColumnInfo(name = "media_size")
    val mediaSize: Long?,

    @SerializedName("media_duration")
    @ColumnInfo(name = "media_duration")
    val mediaDuration: String?,

    @SerializedName("media_width")
    @ColumnInfo(name = "media_width")
    val mediaWidth: Int?,

    @SerializedName("media_height")
    @ColumnInfo(name = "media_height")
    val mediaHeight: Int?,

    @SerializedName("media_hash")
    @ColumnInfo(name = "media_hash")
    val mediaHash: String?,

    @SerializedName("thumb_image")
    @ColumnInfo(name = "thumb_image")
    val thumbImage: String?,

    @SerializedName("thumb_url")
    @ColumnInfo(name = "thumb_url")
    val thumbUrl: String?,

    @ColumnInfo(name = "media_key", typeAffinity = ColumnInfo.BLOB)
    val mediaKey: ByteArray? = null,

    @ColumnInfo(name = "media_digest", typeAffinity = ColumnInfo.BLOB)
    val mediaDigest: ByteArray? = null,

    @ColumnInfo(name = "media_status")
    val mediaStatus: String? = null,

    @SerializedName("status")
    @ColumnInfo(name = "status")
    val status: String,

    @SerializedName("created_at")
    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @SerializedName("action")
    @ColumnInfo(name = "action")
    val action: String? = null,

    @SerializedName("participant_id")
    @ColumnInfo(name = "participant_id")
    val participantId: String? = null,

    @SerializedName("snapshot_id")
    @ColumnInfo(name = "snapshot_id")
    val snapshotId: String? = null,

    @SerializedName("hyperlink")
    @ColumnInfo(name = "hyperlink")
    val hyperlink: String? = null,

    @SerializedName("name")
    @ColumnInfo(name = "name")
    val name: String? = null,

    @Deprecated(
        "Deprecated at database version 15",
        ReplaceWith("@{link sticker_id}", "one.mixin.android.vo.Message.sticker_id"),
        DeprecationLevel.ERROR
    )
    @SerializedName("album_id")
    @ColumnInfo(name = "album_id")
    val albumId: String? = null,

    @SerializedName("sticker_id")
    @ColumnInfo(name = "sticker_id")
    val stickerId: String? = null,

    @SerializedName("shared_user_id")
    @ColumnInfo(name = "shared_user_id")
    val sharedUserId: String? = null,

    @ColumnInfo(name = "media_waveform", typeAffinity = ColumnInfo.BLOB)
    val mediaWaveform: ByteArray? = null,

    @Deprecated(
        "Replace with mediaMimeType",
        ReplaceWith("@{link mediaMimeType}", "one.mixin.android.vo.Message.mediaMimeType"),
        DeprecationLevel.ERROR
    )
    @SerializedName("media_mine_type")
    @ColumnInfo(name = "media_mine_type")
    val mediaMineType: String? = null,

    @SerializedName("quote_message_id")
    @ColumnInfo(name = "quote_message_id")
    val quoteMessageId: String? = null,

    @SerializedName("quote_content")
    @ColumnInfo(name = "quote_content")
    val quoteContent: String? = null,

    @SerializedName("caption")
    @ColumnInfo(name = "caption")
    var caption: String? = null,
) : Serializable, ICategory {
    companion object {
        private const val serialVersionUID: Long = 1L
    }

    override val type: String
        get() = category
}

fun Message.isRepresentativeMessage(conversation: ConversationItem): Boolean {
    return conversation.category == ConversationCategory.CONTACT.name && conversation.ownerId != userId
}

enum class MessageCategory {
    SIGNAL_KEY,
    SIGNAL_TEXT,
    SIGNAL_IMAGE,
    SIGNAL_VIDEO,
    SIGNAL_STICKER,
    SIGNAL_DATA,
    SIGNAL_CONTACT,
    SIGNAL_AUDIO,
    SIGNAL_LIVE,
    SIGNAL_POST,
    SIGNAL_LOCATION,
    PLAIN_TEXT,
    PLAIN_IMAGE,
    PLAIN_VIDEO,
    PLAIN_DATA,
    PLAIN_STICKER,
    PLAIN_CONTACT,
    PLAIN_AUDIO,
    PLAIN_LIVE,
    PLAIN_POST,
    PLAIN_JSON,
    PLAIN_LOCATION,
    MESSAGE_RECALL,
    STRANGER,
    SECRET,
    SYSTEM_CONVERSATION,
    SYSTEM_USER,
    SYSTEM_CIRCLE,
    SYSTEM_SESSION,
    SYSTEM_ACCOUNT_SNAPSHOT,
    APP_BUTTON_GROUP,
    APP_CARD,
    WEBRTC_AUDIO_OFFER,
    WEBRTC_AUDIO_ANSWER,
    WEBRTC_ICE_CANDIDATE,
    WEBRTC_AUDIO_CANCEL,
    WEBRTC_AUDIO_DECLINE,
    WEBRTC_AUDIO_END,
    WEBRTC_AUDIO_BUSY,
    WEBRTC_AUDIO_FAILED,
    KRAKEN_INVITE,
    KRAKEN_PUBLISH,
    KRAKEN_SUBSCRIBE,
    KRAKEN_ANSWER,
    KRAKEN_TRICKLE,
    KRAKEN_END,
    KRAKEN_CANCEL,
    KRAKEN_DECLINE,
    KRAKEN_LIST,
    KRAKEN_RESTART,
    ENCRYPTED_TEXT,
    ENCRYPTED_IMAGE,
    ENCRYPTED_VIDEO,
    ENCRYPTED_STICKER,
    ENCRYPTED_DATA,
    ENCRYPTED_CONTACT,
    ENCRYPTED_AUDIO,
    ENCRYPTED_LIVE,
    ENCRYPTED_POST,
    ENCRYPTED_LOCATION,
}

fun String.isIllegalMessageCategory(): Boolean {
    return !enumValues<MessageCategory>().any { it.name == this }
}

enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED, UNKNOWN }

enum class MediaStatus { PENDING, DONE, CANCELED, EXPIRED, READ }

fun mediaDownloaded(name: String?) = name == MediaStatus.DONE.name || name == MediaStatus.READ.name

fun createMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    category: String,
    content: String,
    createdAt: String,
    status: String,
    action: String? = null,
    participantId: String? = null,
    snapshotId: String? = null,
    quoteMessageId: String? = null
) = MessageBuilder(messageId, conversationId, userId, category, status, createdAt)
    .setContent(content)
    .setAction(action)
    .setParticipantId(participantId)
    .setSnapshotId(snapshotId)
    .setQuoteMessageId(quoteMessageId)
    .build()

fun createPostMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    category: String,
    content: String,
    thumbImage: String,
    createdAt: String,
    status: String
) = MessageBuilder(messageId, conversationId, userId, category, status, createdAt)
    .setContent(content)
    .setThumbImage(thumbImage)
    .build()

fun createAppCardMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    content: String,
    createdAt: String,
    status: String
) = MessageBuilder(messageId, conversationId, userId, MessageCategory.APP_CARD.name, status, createdAt)
    .setContent(content)
    .build()

fun createAppButtonGroupMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    content: String,
    createdAt: String,
    status: String
) = MessageBuilder(messageId, conversationId, userId, MessageCategory.APP_BUTTON_GROUP.name, status, createdAt)
    .setContent(content)
    .build()

fun createCallMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    category: String,
    content: String?,
    createdAt: String,
    status: String,
    quoteMessageId: String? = null,
    mediaDuration: String? = null
): Message {
    val builder = MessageBuilder(messageId, conversationId, userId, category, status, createdAt)
        .setContent(content)
        .setQuoteMessageId(quoteMessageId)
    if (mediaDuration != null) {
        builder.setMediaDuration(mediaDuration)
    }
    return builder.build()
}

fun createReplyTextMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    category: String,
    content: String,
    createdAt: String,
    status: String,
    quoteMessageId: String?,
    quoteContent: String? = null,
    action: String? = null,
    participantId: String? = null,
    snapshotId: String? = null
) = MessageBuilder(messageId, conversationId, userId, category, status, createdAt)
    .setContent(content)
    .setAction(action)
    .setParticipantId(participantId)
    .setSnapshotId(snapshotId)
    .setQuoteMessageId(quoteMessageId)
    .setQuoteContent(quoteContent)
    .build()

fun createAttachmentMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    category: String,
    content: String?,
    name: String?,
    mediaUrl: String?,
    mediaMimeType: String,
    mediaSize: Long,
    createdAt: String,
    key: ByteArray?,
    digest: ByteArray?,
    mediaStatus: MediaStatus,
    status: String,
    quoteMessageId: String? = null,
    quoteContent: String? = null
) = MessageBuilder(messageId, conversationId, userId, category, status, createdAt)
    .setContent(content)
    .setName(name)
    .setMediaUrl(mediaUrl)
    .setMediaMimeType(mediaMimeType)
    .setMediaSize(mediaSize)
    .setMediaKey(key)
    .setMediaDigest(digest)
    .setMediaStatus(mediaStatus.name)
    .setQuoteMessageId(quoteMessageId)
    .setQuoteContent(quoteContent)
    .build()

fun createVideoMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    category: String,
    content: String?,
    name: String?,
    mediaUrl: String?,
    duration: Long?,
    mediaWidth: Int? = null,
    mediaHeight: Int? = null,
    thumbImage: String? = null,
    mediaMimeType: String,
    mediaSize: Long,
    createdAt: String,
    key: ByteArray?,
    digest: ByteArray?,
    mediaStatus: MediaStatus,
    status: String,
    quoteMessageId: String? = null,
    quoteContent: String? = null
) = MessageBuilder(messageId, conversationId, userId, category, status, createdAt)
    .setContent(content)
    .setName(name)
    .setMediaUrl(mediaUrl)
    .setMediaDuration(duration.toString())
    .setMediaWidth(mediaWidth)
    .setMediaHeight(mediaHeight)
    .setThumbImage(thumbImage)
    .setMediaMimeType(mediaMimeType)
    .setMediaSize(mediaSize)
    .setMediaKey(key)
    .setMediaDigest(digest)
    .setMediaStatus(mediaStatus.name)
    .setQuoteMessageId(quoteMessageId)
    .setQuoteContent(quoteContent)
    .build()

fun createMediaMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    category: String,
    content: String?,
    mediaUrl: String?,
    mediaMimeType: String?,
    mediaSize: Long,
    mediaWidth: Int?,
    mediaHeight: Int?,
    thumbImage: String?,
    key: ByteArray?,
    digest: ByteArray?,
    createdAt: String,
    mediaStatus: MediaStatus,
    status: String,
    quoteMessageId: String? = null,
    quoteContent: String? = null,
) = MessageBuilder(messageId, conversationId, userId, category, status, createdAt)
    .setContent(content)
    .setMediaUrl(mediaUrl)
    .setMediaMimeType(mediaMimeType)
    .setMediaSize(mediaSize)
    .setMediaWidth(mediaWidth)
    .setMediaHeight(mediaHeight)
    .setThumbImage(thumbImage)
    .setMediaKey(key)
    .setMediaDigest(digest)
    .setMediaStatus(mediaStatus.name)
    .setQuoteMessageId(quoteMessageId)
    .setQuoteContent(quoteContent)
    .build()

fun createStickerMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    category: String,
    content: String?,
    albumId: String?,
    stickerId: String,
    stickerName: String?,
    status: String,
    createdAt: String
) = MessageBuilder(messageId, conversationId, userId, category, status, createdAt)
    .setContent(content)
    .setStickerId(stickerId)
    .setAlbumId(albumId)
    .setName(stickerName)
    .build()

fun createLiveMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    category: String,
    content: String?,
    width: Int,
    height: Int,
    url: String,
    thumbUrl: String,
    status: String,
    createdAt: String
) = MessageBuilder(messageId, conversationId, userId, category, status, createdAt)
    .setContent(content)
    .setMediaWidth(width)
    .setMediaHeight(height)
    .setMediaUrl(url)
    .setThumbUrl(thumbUrl)
    .build()

fun createLocationMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    category: String,
    content: String?,
    status: String,
    createdAt: String
) = MessageBuilder(messageId, conversationId, userId, category, status, createdAt)
    .setContent(content)
    .build()

fun createContactMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    category: String,
    content: String,
    sharedUserId: String,
    status: String,
    createdAt: String,
    name: String? = null,
    quoteMessageId: String? = null,
    quoteContent: String? = null
) = MessageBuilder(messageId, conversationId, userId, category, status, createdAt)
    .setContent(content)
    .setName(name)
    .setSharedUserId(sharedUserId)
    .setQuoteMessageId(quoteMessageId)
    .setQuoteContent(quoteContent)
    .build()

fun createRecallMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    category: String,
    content: String,
    status: String,
    createdAt: String
) = MessageBuilder(messageId, conversationId, userId, category, status, createdAt)
    .setContent(content)
    .build()

fun createAudioMessage(
    messageId: String,
    conversationId: String,
    userId: String,
    content: String?,
    category: String,
    mediaSize: Long,
    mediaUrl: String?,
    mediaDuration: String,
    createdAt: String,
    mediaWaveform: ByteArray?,
    key: ByteArray?,
    digest: ByteArray?,
    mediaStatus: MediaStatus,
    status: String,
    quoteMessageId: String? = null,
    quoteContent: String? = null
) = MessageBuilder(messageId, conversationId, userId, category, status, createdAt)
    .setMediaUrl(mediaUrl)
    .setContent(content)
    .setMediaWaveform(mediaWaveform)
    .setMediaKey(key)
    .setMediaSize(mediaSize)
    .setMediaDuration(mediaDuration)
    .setMediaMimeType("audio/ogg")
    .setMediaDigest(digest)
    .setMediaStatus(mediaStatus.name)
    .setQuoteMessageId(quoteMessageId)
    .setQuoteContent(quoteContent)
    .build()
