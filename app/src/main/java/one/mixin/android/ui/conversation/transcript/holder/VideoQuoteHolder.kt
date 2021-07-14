package one.mixin.android.ui.conversation.transcript.holder

import android.view.Gravity
import android.view.View
import androidx.core.widget.TextViewCompat
import one.mixin.android.R
import one.mixin.android.databinding.ItemChatVideoQuoteBinding
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.fileSize
import one.mixin.android.extension.formatMillis
import one.mixin.android.extension.loadVideo
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.realSize
import one.mixin.android.extension.round
import one.mixin.android.extension.timeAgoClock
import one.mixin.android.job.MixinJobManager
import one.mixin.android.session.Session
import one.mixin.android.ui.conversation.transcript.TranscriptAdapter
import one.mixin.android.util.GsonHelper
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.MessageStatus
import one.mixin.android.vo.SnakeQuoteMessageItem
import one.mixin.android.vo.TranscriptMessageItem
import one.mixin.android.vo.absolutePath
import org.jetbrains.anko.dip

class VideoQuoteHolder constructor(val binding: ItemChatVideoQuoteBinding) : BaseViewHolder(binding.root) {
    private val dp16 = itemView.context.dpToPx(16f)
    private val minWidth by lazy {
        (itemView.context.realSize().x * 0.5).toInt()
    }

    init {
        val radius = itemView.context.dpToPx(4f).toFloat()
        binding.chatImageLayout.round(radius)
        binding.chatTime.round(radius)
    }

    override fun chatLayout(isMe: Boolean, isLast: Boolean, isBlink: Boolean) {
        super.chatLayout(isMe, isLast, isBlink)
        if (isMe) {
            binding.chatMsgLayout.gravity = Gravity.END
            if (isLast) {
                setItemBackgroundResource(
                    binding.chatLayout,
                    R.drawable.chat_bubble_reply_me_last,
                    R.drawable.chat_bubble_reply_me_last_night
                )
            } else {
                setItemBackgroundResource(
                    binding.chatLayout,
                    R.drawable.chat_bubble_reply_me,
                    R.drawable.chat_bubble_reply_me_night
                )
            }
        } else {
            binding.chatMsgLayout.gravity = Gravity.START

            if (isLast) {
                setItemBackgroundResource(
                    binding.chatLayout,
                    R.drawable.chat_bubble_reply_other_last,
                    R.drawable.chat_bubble_reply_other_last_night
                )
            } else {
                setItemBackgroundResource(
                    binding.chatLayout,
                    R.drawable.chat_bubble_reply_other,
                    R.drawable.chat_bubble_reply_other_night
                )
            }
        }
    }

    private var onItemListener: TranscriptAdapter.OnItemListener? = null

    fun bind(
        messageItem: TranscriptMessageItem,
        isLast: Boolean,
        isFirst: Boolean = false,
        onItemListener: TranscriptAdapter.OnItemListener
    ) {
        super.bind(messageItem)
        this.onItemListener = onItemListener
        val isMe = messageItem.userId == Session.getAccountId()
        binding.chatQuoteLayout.setRatio(messageItem.mediaWidth!!.toFloat() / messageItem.mediaHeight!!.toFloat())

        binding.chatTime.timeAgoClock(messageItem.createdAt)
        when (messageItem.mediaStatus) {
            MediaStatus.DONE.name -> {

                binding.durationTv.bindId(null)
                messageItem.mediaDuration.notNullWithElse(
                    {
                        binding.durationTv.visibility = View.VISIBLE
                        binding.durationTv.text = it.toLongOrNull()?.formatMillis() ?: ""
                    },
                    {
                        binding.durationTv.visibility = View.GONE
                    }
                )
            }
            MediaStatus.PENDING.name -> {
                messageItem.mediaSize.notNullWithElse(
                    {
                        binding.durationTv.visibility = View.VISIBLE
                        if (it == 0L) {
                            binding.durationTv.bindId(messageItem.messageId)
                        } else {
                            binding.durationTv.text = it.fileSize()
                            binding.durationTv.bindId(null)
                        }
                    },
                    {
                        binding.durationTv.bindId(null)
                        binding.durationTv.visibility = View.GONE
                    }
                )
            }
            else -> {
                messageItem.mediaSize.notNullWithElse(
                    {
                        if (it == 0L) {
                            binding.durationTv.visibility = View.GONE
                        } else {
                            binding.durationTv.visibility = View.VISIBLE
                            binding.durationTv.text = it.fileSize()
                        }
                    },
                    {
                        binding.durationTv.visibility = View.GONE
                    }
                )
                binding.durationTv.bindId(null)
            }
        }
        messageItem.mediaStatus?.let {
            when (it) {
                MediaStatus.EXPIRED.name -> {
                    binding.chatWarning.visibility = View.VISIBLE
                    binding.progress.visibility = View.GONE
                    binding.play.visibility = View.GONE
                }
                MediaStatus.PENDING.name -> {
                    binding.chatWarning.visibility = View.GONE
                    binding.progress.visibility = View.VISIBLE
                    binding.play.visibility = View.GONE
                    binding.progress.enableLoading(MixinJobManager.getAttachmentProcess(messageItem.messageId))
                    binding.progress.setBindOnly("${messageItem.transcriptId}${messageItem.messageId}")
                    binding.progress.setOnClickListener {
                        onItemListener.onCancel(messageItem.transcriptId, messageItem.messageId)
                    }
                    binding.chatImage.setOnClickListener { }
                }
                MediaStatus.DONE.name -> {
                    binding.chatWarning.visibility = View.GONE
                    binding.progress.visibility = View.GONE
                    binding.play.visibility = View.VISIBLE
                    binding.progress.setBindId("${messageItem.transcriptId}${messageItem.messageId}")
                    binding.progress.setOnClickListener {}
                    binding.chatImage.setOnClickListener {
                        onItemListener.onImageClick(messageItem, binding.chatImage)
                    }
                }
                MediaStatus.CANCELED.name -> {
                    binding.play.visibility = View.GONE
                    binding.chatWarning.visibility = View.GONE
                    binding.progress.visibility = View.VISIBLE
                    if (isMe && messageItem.mediaUrl != null) {
                        binding.progress.enableUpload()
                    } else {
                        binding.progress.enableDownload()
                    }
                    binding.progress.setBindId("${messageItem.transcriptId}${messageItem.messageId}")
                    binding.progress.setProgress(-1)
                    binding.progress.setOnClickListener {
                        if (messageItem.mediaUrl.isNullOrEmpty()) {
                            onItemListener.onRetryDownload(messageItem.transcriptId, messageItem.messageId)
                        } else {
                            onItemListener.onRetryUpload(messageItem.transcriptId, messageItem.messageId)
                        }
                    }
                    binding.chatImage.setOnClickListener {}
                }
            }
        }

        binding.chatImage.loadVideo(
            messageItem.absolutePath(),
            messageItem.thumbImage,
            minWidth,
            minWidth * messageItem.mediaHeight / messageItem.mediaWidth
        )

        if (isFirst && !isMe) {
            binding.chatName.visibility = View.VISIBLE
            binding.chatName.text = messageItem.userFullName
            if (messageItem.appId != null) {
                binding.chatName.setCompoundDrawables(null, null, botIcon, null)
                binding.chatName.compoundDrawablePadding = itemView.dip(3)
            } else {
                binding.chatName.setCompoundDrawables(null, null, null, null)
            }
            binding.chatName.setTextColor(getColorById(messageItem.userId))
            binding.chatName.setOnClickListener { onItemListener.onUserClick(messageItem.userId) }
        } else {
            binding.chatName.visibility = View.GONE
        }

        if (messageItem.appId != null) {
            binding.chatName.setCompoundDrawables(null, null, botIcon, null)
            binding.chatName.compoundDrawablePadding = itemView.dip(3)
        } else {
            binding.chatName.setCompoundDrawables(null, null, null, null)
        }
        setStatusIcon(
            isMe, MessageStatus.DELIVERED.name,
            isSecret = false,
            isRepresentative = false,
            isWhite = true
        ) { statusIcon, secretIcon, representativeIcon ->
            statusIcon?.setBounds(0, 0, dp12, dp12)
            secretIcon?.setBounds(0, 0, dp8, dp8)
            representativeIcon?.setBounds(0, 0, dp8, dp8)
            TextViewCompat.setCompoundDrawablesRelative(binding.chatTime, secretIcon ?: representativeIcon, null, statusIcon, null)
        }

        val quoteMessage = GsonHelper.customGson.fromJson(messageItem.quoteContent, SnakeQuoteMessageItem::class.java)
        binding.chatQuote.bind(quoteMessage)
        binding.chatQuote.setOnClickListener {
            onItemListener.onQuoteMessageClick(messageItem.messageId, messageItem.quoteId)
        }
        chatLayout(isMe, isLast)
    }
}
