package one.mixin.android.vo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import one.mixin.android.extension.nowInUtc
import java.util.UUID

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = arrayOf("pin_time", "last_message_created_at")),
        Index(value = arrayOf("category", "status", "pin_time", "created_at"))
    ],
)
open class Conversation(
    @PrimaryKey
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: String?,
    @ColumnInfo(name = "category")
    val category: String?,
    @ColumnInfo(name = "name")
    val name: String?,
    @ColumnInfo(name = "icon_url")
    val iconUrl: String?,
    @ColumnInfo(name = "announcement")
    val announcement: String?,
    @ColumnInfo(name = "code_url")
    val codeUrl: String?,
    @ColumnInfo(name = "pay_type")
    val payType: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "pin_time")
    val pinTime: String?,
    @ColumnInfo(name = "last_message_id")
    val lastMessageId: String?,
    @ColumnInfo(name = "last_read_message_id")
    val lastReadMessageId: String?,
    @ColumnInfo(name = "unseen_message_count")
    val unseenMessageCount: Int?,
    @ColumnInfo(name = "status")
    val status: Int,
    @ColumnInfo(name = "draft")
    val draft: String? = null,
    @ColumnInfo(name = "mute_until")
    val muteUntil: String? = null,
    @ColumnInfo(name = "last_message_created_at")
    val lastMessageCreatedAt: String? = null,
) : IConversationCategory {
    override val conversationCategory: String
        get() = category ?: ConversationCategory.CONTACT.name
}

enum class ConversationCategory { CONTACT, GROUP }
enum class ConversationStatus { START, FAILURE, SUCCESS, QUIT }

fun createConversation(conversationId: String, category: String?, recipientId: String, status: Int) =
    ConversationBuilder(conversationId, nowInUtc(), status)
        .setCategory(category)
        .setOwnerId(recipientId)
        .setAnnouncement("")
        .setCodeUrl("")
        .setPayType("")
        .setUnseenMessageCount(0)
        .build()

fun generateConversationId(senderId: String, recipientId: String): String {
    val mix = minOf(senderId, recipientId) + maxOf(senderId, recipientId)
    return UUID.nameUUIDFromBytes(mix.toByteArray()).toString()
}
