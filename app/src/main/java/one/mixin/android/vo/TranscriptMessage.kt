package one.mixin.android.vo

import android.content.Context
import android.os.Parcelable
import androidx.core.net.toUri
import androidx.room.ColumnInfo
import androidx.room.Entity
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize
import one.mixin.android.MixinApplication
import one.mixin.android.extension.getMediaPath
import one.mixin.android.extension.getTranscriptDirPath
import one.mixin.android.util.JsonSkip
import java.io.File
import java.io.Serializable

@Parcelize
@Entity(tableName = "transcript_messages", primaryKeys = ["transcript_id", "message_id"])
class TranscriptMessage(
    @SerializedName("transcript_id")
    @ColumnInfo(name = "transcript_id")
    var transcriptId: String,
    @SerializedName("message_id")
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @SerializedName("user_id")
    @ColumnInfo(name = "user_id")
    val userId: String?,
    @SerializedName("user_full_name")
    @ColumnInfo(name = "user_full_name")
    val userFullName: String?,
    @SerializedName("category")
    @ColumnInfo(name = "category")
    override var type: String,
    @SerializedName("created_at")
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @SerializedName("content")
    @ColumnInfo(name = "content")
    val content: String?,
    @ColumnInfo(name = "media_url")
    @JsonSkip
    var mediaUrl: String? = null,
    @SerializedName("media_name")
    @ColumnInfo(name = "media_name")
    val mediaName: String? = null,
    @SerializedName("media_size")
    @ColumnInfo(name = "media_size")
    val mediaSize: Long? = null,
    @SerializedName("media_width")
    @ColumnInfo(name = "media_width")
    val mediaWidth: Int? = null,
    @SerializedName("media_height")
    @ColumnInfo(name = "media_height")
    val mediaHeight: Int? = null,
    @SerializedName("media_mime_type")
    @ColumnInfo(name = "media_mime_type")
    val mediaMimeType: String? = null,
    @SerializedName("media_duration")
    @ColumnInfo(name = "media_duration")
    val mediaDuration: Long? = null,
    @JsonSkip
    @ColumnInfo(name = "media_status")
    var mediaStatus: String? = null,
    @SerializedName("media_waveform")
    @ColumnInfo(name = "media_waveform")
    val mediaWaveform: ByteArray? = null,
    @SerializedName("thumb_image")
    @ColumnInfo(name = "thumb_image")
    val thumbImage: String? = null,
    @SerializedName("thumb_url")
    @ColumnInfo(name = "thumb_url")
    val thumbUrl: String? = null,
    @SerializedName("media_key")
    @ColumnInfo(name = "media_key")
    val mediaKey: ByteArray? = null,
    @SerializedName("media_digest")
    @ColumnInfo(name = "media_digest")
    val mediaDigest: ByteArray? = null,
    @SerializedName("media_created_at")
    @ColumnInfo(name = "media_created_at")
    val mediaCreatedAt: String? = null,
    @SerializedName("sticker_id")
    @ColumnInfo(name = "sticker_id")
    val stickerId: String? = null,
    @SerializedName("shared_user_id")
    @ColumnInfo(name = "shared_user_id")
    val sharedUserId: String? = null,
    @SerializedName("mentions")
    @ColumnInfo(name = "mentions")
    val mentions: String? = null,
    @SerializedName("quote_id")
    @ColumnInfo(name = "quote_id")
    val quoteId: String? = null,
    @SerializedName("quote_content")
    @ColumnInfo(name = "quote_content")
    var quoteContent: String? = null,
    @SerializedName("caption")
    @ColumnInfo(name = "caption")
    val caption: String? = null
) : ICategory, Serializable, Parcelable

fun TranscriptMessage.isValidAttachment(): Boolean =
    (isPlain() && mediaKey == null && mediaDigest == null) || ((isSignal() || isEncrypted()) && mediaKey != null && mediaDigest != null)

fun TranscriptMessage.copy(tid: String): TranscriptMessage {
    return TranscriptMessage(
        tid,
        messageId,
        userId,
        userFullName,
        type,
        createdAt,
        content,
        mediaUrl,
        mediaName,
        mediaSize,
        mediaWidth,
        mediaHeight,
        mediaMimeType,
        mediaDuration,
        mediaStatus,
        mediaWaveform,
        thumbImage,
        thumbUrl,
        mediaKey,
        mediaDigest,
        mediaCreatedAt,
        stickerId,
        sharedUserId,
        mentions,
        quoteId,
        quoteContent,
        caption
    )
}

fun TranscriptMessage.absolutePath(context: Context = MixinApplication.appContext): String? {
    val mediaPath = context.getMediaPath()?.absolutePath ?: return null
    val url = mediaUrl
    return when {
        url == null -> null
        url.startsWith(mediaPath) -> url
        else -> File(context.getTranscriptDirPath(), url).toUri().toString()
    }
}
