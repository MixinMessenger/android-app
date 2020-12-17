package one.mixin.android.vo

import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.room.Entity
import org.threeten.bp.Instant

@Entity
data class ConversationItem(
    val conversationId: String,
    val avatarUrl: String?,
    val groupIconUrl: String?,
    val category: String?,
    val groupName: String?,
    val name: String,
    val ownerId: String,
    val ownerIdentityNumber: String,
    val status: Int,
    val lastReadMessageId: String?,
    val unseenMessageCount: Int?,
    val content: String?,
    val contentType: String?,
    val mediaUrl: String?,
    val createdAt: String?,
    val pinTime: String?,
    val senderId: String?,
    val senderFullName: String?,
    val messageStatus: String?,
    val actionName: String?,
    val participantFullName: String?,
    val participantUserId: String?,
    val ownerMuteUntil: String?,
    val ownerVerified: Boolean?,
    val muteUntil: String?,
    val snapshotType: String?,
    val appId: String?,
    val mentions: String?,
    val mentionCount: Int?
) : ICategory {
    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ConversationItem>() {
            override fun areItemsTheSame(oldItem: ConversationItem, newItem: ConversationItem) =
                oldItem.conversationId == newItem.conversationId

            override fun areContentsTheSame(oldItem: ConversationItem, newItem: ConversationItem) =
                oldItem == newItem
        }
    }

    override val type: String
        get() = contentType ?: MessageCategory.PLAIN_TEXT.name

    fun isGroup() = category == ConversationCategory.GROUP.name

    fun isContact() = category == ConversationCategory.CONTACT.name

    fun getConversationName(): String {
        return when {
            isContact() -> name
            isGroup() -> groupName!!
            else -> ""
        }
    }

    fun iconUrl(): String? {
        return when {
            isContact() -> avatarUrl
            isGroup() -> groupIconUrl
            else -> null
        }
    }

    fun isMute(): Boolean {
        if (isContact() && ownerMuteUntil != null) {
            return Instant.now().isBefore(Instant.parse(ownerMuteUntil))
        }
        if (isGroup() && muteUntil != null) {
            return Instant.now().isBefore(Instant.parse(muteUntil))
        }
        return false
    }

    fun isBot(): Boolean {
        return category == ConversationCategory.CONTACT.name && appId != null
    }
}

fun ConversationItem.showVerifiedOrBot(verifiedView: View, botView: View) {
    when {
        ownerVerified == true -> {
            verifiedView.isVisible = true
            botView.isVisible = false
        }
        isBot() -> {
            verifiedView.isVisible = false
            botView.isVisible = true
        }
        else -> {
            verifiedView.isVisible = false
            botView.isVisible = false
        }
    }
}
