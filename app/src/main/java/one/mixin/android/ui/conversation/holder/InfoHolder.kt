package one.mixin.android.ui.conversation.holder

import android.content.Context
import android.graphics.Color
import android.view.View
import kotlinx.android.synthetic.main.item_chat_info.view.*
import one.mixin.android.R
import one.mixin.android.ui.conversation.adapter.ConversationAdapter
import one.mixin.android.vo.MessageItem
import one.mixin.android.websocket.SystemConversationAction

class InfoHolder constructor(containerView: View) : BaseViewHolder(containerView) {
    override fun chatLayout(isMe: Boolean, isLast: Boolean) {
    }

    var context: Context = itemView.context
    private fun getText(id: Int) = context.getText(id).toString()

    fun bind(
        messageItem: MessageItem,
        hasSelect: Boolean,
        isSelect: Boolean,
        onItemListener: ConversationAdapter.OnItemListener,
        group: String?) {
        val id = meId

        if (hasSelect && isSelect) {
            itemView.setBackgroundColor(Color.parseColor("#660D94FC"))
        } else {
            itemView.setBackgroundColor(Color.TRANSPARENT)
        }
        itemView.setOnLongClickListener {
            if (!hasSelect) {
                onItemListener.onLongClick(messageItem, adapterPosition)
            } else {
                onItemListener.onSelect(!isSelect, messageItem, adapterPosition)
                true
            }
        }
        itemView.setOnClickListener {
            if (hasSelect) {
                onItemListener.onSelect(!isSelect, messageItem, adapterPosition)
            }
        }

        when (messageItem.actionName) {
            SystemConversationAction.CREATE.name -> {
                group?.let {
                    itemView.chat_info.text =
                        String.format(getText(R.string.chat_group_create),
                            if (id == messageItem.userId) {
                                getText(R.string.chat_you_start)
                            } else {
                                messageItem.userFullName
                            }, group)
                }
            }
            SystemConversationAction.ADD.name -> {
                itemView.chat_info.text =
                    String.format(getText(R.string.chat_group_add),
                        if (id == messageItem.userId) {
                            getText(R.string.chat_you_start)
                        } else {
                            messageItem.userFullName
                        },
                        if (id == messageItem.participantUserId) {
                            getText(R.string.chat_you)
                        } else {
                            messageItem.participantFullName
                        })
            }
            SystemConversationAction.REMOVE.name -> {
                itemView.chat_info.text =
                    String.format(getText(R.string.chat_group_remove),
                        if (id == messageItem.userId) {
                            getText(R.string.chat_you_start)
                        } else {
                            messageItem.userFullName
                        },
                        if (id == messageItem.participantUserId) {
                            getText(R.string.chat_you)
                        } else {
                            messageItem.participantFullName
                        })
            }
            SystemConversationAction.JOIN.name -> {
                itemView.chat_info.text =
                    String.format(getText(R.string.chat_group_join),
                        if (id == messageItem.participantUserId) {
                            getText(R.string.chat_you_start)
                        } else {
                            messageItem.participantFullName
                        })
            }
            SystemConversationAction.EXIT.name -> {
                itemView.chat_info.text =
                    String.format(getText(R.string.chat_group_exit),
                        if (id == messageItem.participantUserId) {
                            getText(R.string.chat_you_start)
                        } else {
                            messageItem.participantFullName
                        })
            }
            SystemConversationAction.ROLE.name -> {
                itemView.chat_info.text = getText(R.string.group_role)
            }
            else -> {
                itemView.chat_info.text = getText(R.string.unknown)
            }
        }
    }
}