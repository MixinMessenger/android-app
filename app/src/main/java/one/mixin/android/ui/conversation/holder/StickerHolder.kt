package one.mixin.android.ui.conversation.holder

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.support.v4.widget.TextViewCompat
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.item_chat_sticker.view.*
import one.mixin.android.R
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.loadSticker
import one.mixin.android.extension.round
import one.mixin.android.extension.timeAgoClock
import one.mixin.android.ui.conversation.adapter.ConversationAdapter
import one.mixin.android.vo.MessageItem
import org.jetbrains.anko.dip
import org.jetbrains.anko.textColorResource

class StickerHolder constructor(containerView: View) : BaseViewHolder(containerView) {

    init {
        val radius = itemView.context.dpToPx(4f).toFloat()
        itemView.chat_time.textColorResource = R.color.color_chat_date
        itemView.chat_sticker.round(radius)
    }

    private val dp160 by lazy {
        itemView.context.dpToPx(160f)
    }

    private val dp48 by lazy {
        itemView.context.dpToPx(48f)
    }

    fun bind(
        messageItem: MessageItem,
        isFirst: Boolean,
        hasSelect: Boolean,
        isSelect: Boolean,
        onItemListener: ConversationAdapter.OnItemListener
    ) {
        val isMe = meId == messageItem.userId
        chatLayout(isMe, false)
        if (hasSelect && isSelect) {
            itemView.setBackgroundColor(SELECT_COLOR)
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
        if (messageItem.assetWidth == null || messageItem.assetHeight == null) {
            itemView.chat_sticker.layoutParams.width = dp160
            itemView.chat_time.layoutParams.width = dp160
            itemView.chat_sticker.layoutParams.height = dp160
            itemView.chat_sticker.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
        } else if (messageItem.assetWidth * 2 < dp48 || messageItem.assetHeight * 2 < dp48) {
            if (messageItem.assetWidth < messageItem.assetHeight) {
                if (dp48 * messageItem.assetHeight / messageItem.assetWidth > dp160) {
                    itemView.chat_sticker.layoutParams.width = dp160 * messageItem.assetWidth / messageItem.assetHeight
                    itemView.chat_sticker.layoutParams.height = dp160
                } else {
                    itemView.chat_sticker.layoutParams.width = dp48
                    itemView.chat_sticker.layoutParams.height = dp48 * messageItem.assetHeight / messageItem.assetWidth
                }
            } else {
                if (dp48 * messageItem.assetWidth / messageItem.assetHeight > dp160) {
                    itemView.chat_sticker.layoutParams.height = dp160 * messageItem.assetHeight / messageItem.assetWidth
                    itemView.chat_sticker.layoutParams.width = dp160
                } else {
                    itemView.chat_sticker.layoutParams.height = dp48
                    itemView.chat_sticker.layoutParams.width = dp48 * messageItem.assetWidth / messageItem.assetHeight
                }
            }
            itemView.chat_sticker.loadSticker(messageItem.assetUrl, messageItem.assetType)
        } else if (messageItem.assetWidth * 2 > dp160 || messageItem.assetHeight * 2 > dp160) {
            if (messageItem.assetWidth > messageItem.assetHeight) {
                itemView.chat_sticker.layoutParams.width = dp160
                itemView.chat_sticker.layoutParams.height = dp160 * messageItem.assetHeight / messageItem.assetWidth
            } else {
                itemView.chat_sticker.layoutParams.height = dp160
                itemView.chat_sticker.layoutParams.width = dp160 * messageItem.assetWidth / messageItem.assetHeight
            }
            itemView.chat_sticker.loadSticker(messageItem.assetUrl, messageItem.assetType)
        } else {
            itemView.chat_sticker.layoutParams.width = messageItem.assetWidth * 2
            itemView.chat_sticker.layoutParams.height = messageItem.assetHeight * 2
            itemView.chat_sticker.loadSticker(messageItem.assetUrl, messageItem.assetType)
        }

        itemView.chat_time.timeAgoClock(messageItem.createdAt)
        if (isFirst && !isMe) {
            itemView.chat_name.visibility = View.VISIBLE
            itemView.chat_name.text = messageItem.userFullName
            if (messageItem.appId != null) {
                itemView.chat_name.setCompoundDrawables(null, null, botIcon, null)
                itemView.chat_name.compoundDrawablePadding = itemView.dip(3)
            } else {
                itemView.chat_name.setCompoundDrawables(null, null, null, null)
            }
            itemView.chat_name.setTextColor(colors[messageItem.userIdentityNumber.toLong().rem(colors.size).toInt()])
            itemView.chat_name.setOnClickListener { onItemListener.onUserClick(messageItem.userId) }
        } else {
            itemView.chat_name.visibility = View.GONE
        }
        setStatusIcon(isMe, messageItem.status, {
            TextViewCompat.setCompoundDrawablesRelative(itemView.chat_time, null, null, it, null)
        }, {
            TextViewCompat.setCompoundDrawablesRelative(itemView.chat_time, null, null, null, null)
        })
    }

    override fun chatLayout(isMe: Boolean, isLast: Boolean, isBlink: Boolean) {
        super.chatLayout(isMe, isLast, isBlink)
        if (isMe) {
            (itemView.chat_layout.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.END
            itemView.requestLayout()
        } else {
            (itemView.chat_layout.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.START
            itemView.requestLayout()
        }
    }
}