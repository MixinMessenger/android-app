package one.mixin.android.websocket

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class BlazeMessageData(
    @SerializedName("conversation_id")
    val conversationId: String,
    @SerializedName("user_id")
    var userId: String,
    @SerializedName("message_id")
    var messageId: String,
    @SerializedName("category")
    val category: String,
    @SerializedName("data")
    val data: String,
    @SerializedName("status")
    val status: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("source")
    val source: String,
    @SerializedName("representative_id")
    val representativeId: String?,
    @SerializedName("quote_message_id")
    val quoteMessageId: String?,
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("silent")
    val silent: Boolean? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 5L
    }
}
