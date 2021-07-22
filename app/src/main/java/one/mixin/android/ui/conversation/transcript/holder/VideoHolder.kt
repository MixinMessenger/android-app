package one.mixin.android.ui.conversation.transcript.holder

import android.view.Gravity
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import one.mixin.android.R
import one.mixin.android.databinding.ItemChatVideoBinding
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.fileSize
import one.mixin.android.extension.formatMillis
import one.mixin.android.extension.loadImageMark
import one.mixin.android.extension.loadVideoMark
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.round
import one.mixin.android.extension.timeAgoClock
import one.mixin.android.job.MixinJobManager
import one.mixin.android.session.Session
import one.mixin.android.ui.conversation.transcript.TranscriptAdapter
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.MessageStatus
import one.mixin.android.vo.TranscriptMessageItem
import one.mixin.android.vo.absolutePath
import one.mixin.android.vo.isLive
import org.jetbrains.anko.dip

class VideoHolder constructor(val binding: ItemChatVideoBinding) : MediaHolder(binding.root) {

    init {
        val radius = itemView.context.dpToPx(4f).toFloat()
        binding.chatImage.round(radius)
        binding.chatTime.round(radius)
        binding.progress.round(radius)
    }

    private val dp4 by lazy {
        itemView.context.dpToPx(4f)
    }

    fun bind(
        messageItem: TranscriptMessageItem,
        isLast: Boolean,
        isFirst: Boolean,
        onItemListener: TranscriptAdapter.OnItemListener
    ) {
        super.bind(messageItem)
        val isMe = messageItem.userId == Session.getAccountId()
        if (isFirst && !isMe) {
            binding.chatName.visibility = VISIBLE
            binding.chatName.text = messageItem.userFullName
            if (messageItem.appId != null) {
                binding.chatName.setCompoundDrawables(null, null, botIcon, null)
                binding.chatName.compoundDrawablePadding = itemView.dip(3)
            } else {
                binding.chatName.setCompoundDrawables(null, null, null, null)
            }
            binding.chatName.setOnClickListener { onItemListener.onUserClick(messageItem.userId) }
            binding.chatName.setTextColor(getColorById(messageItem.userId))
        } else {
            binding.chatName.visibility = GONE
        }

        if (messageItem.isLive()) {
            binding.chatWarning.visibility = GONE
            binding.durationTv.visibility = GONE
            binding.progress.visibility = GONE
            binding.play.isVisible = true
            binding.liveTv.visibility = VISIBLE
            binding.progress.setBindId("${messageItem.transcriptId}${messageItem.messageId}")
            binding.progress.setOnClickListener {}
            binding.progress.setOnLongClickListener { false }
            binding.chatImage.setOnClickListener {
                onItemListener.onImageClick(messageItem, binding.chatImage)
            }
        } else {
            binding.liveTv.visibility = GONE
            when (messageItem.mediaStatus) {
                MediaStatus.DONE.name -> {
                    binding.durationTv.bindId(null)
                    messageItem.mediaDuration.notNullWithElse(
                        {
                            binding.durationTv.visibility = VISIBLE
                            binding.durationTv.text = it.toLongOrNull()?.formatMillis() ?: ""
                        },
                        {
                            binding.durationTv.visibility = GONE
                        }
                    )
                }
                MediaStatus.PENDING.name -> {
                    messageItem.mediaSize.notNullWithElse(
                        {
                            binding.durationTv.visibility = VISIBLE
                            if (it == 0L) {
                                binding.durationTv.bindId(messageItem.messageId)
                            } else {
                                binding.durationTv.text = it.fileSize()
                                binding.durationTv.bindId(null)
                            }
                        },
                        {
                            binding.durationTv.bindId(null)
                            binding.durationTv.visibility = GONE
                        }
                    )
                }
                else -> {
                    messageItem.mediaSize.notNullWithElse(
                        {
                            if (it == 0L) {
                                binding.durationTv.visibility = GONE
                            } else {
                                binding.durationTv.visibility = VISIBLE
                                binding.durationTv.text = it.fileSize()
                            }
                        },
                        {
                            binding.durationTv.visibility = GONE
                        }
                    )
                    binding.durationTv.bindId(null)
                }
            }
            messageItem.mediaStatus?.let {
                when (it) {
                    MediaStatus.EXPIRED.name -> {
                        binding.chatWarning.visibility = VISIBLE
                        binding.progress.visibility = GONE
                        binding.play.visibility = GONE
                        binding.chatImage.setOnClickListener {
                        }
                    }
                    MediaStatus.PENDING.name -> {
                        binding.chatWarning.visibility = GONE
                        binding.progress.visibility = VISIBLE
                        binding.play.visibility = GONE
                        binding.progress.enableLoading(MixinJobManager.getAttachmentProcess(messageItem.messageId))
                        binding.progress.setBindOnly("${messageItem.transcriptId}${messageItem.messageId}")
                        binding.progress.setOnClickListener {
                            onItemListener.onCancel(messageItem.transcriptId, messageItem.messageId)
                        }
                        binding.chatImage.setOnClickListener { }
                        binding.chatImage.setOnLongClickListener { false }
                    }
                    MediaStatus.DONE.name -> {
                        binding.chatWarning.visibility = GONE
                        binding.progress.visibility = GONE
                        binding.play.visibility = VISIBLE
                        binding.progress.setBindId("${messageItem.transcriptId}${messageItem.messageId}")
                        binding.progress.setOnClickListener {}
                        binding.progress.setOnLongClickListener { false }
                        binding.chatImage.setOnClickListener {
                            onItemListener.onImageClick(messageItem, binding.chatImage)
                        }
                    }
                    MediaStatus.CANCELED.name -> {
                        binding.chatWarning.visibility = GONE
                        binding.progress.visibility = VISIBLE
                        binding.play.visibility = GONE
                        if (isMe) {
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
                        binding.chatImage.setOnLongClickListener { false }
                    }
                }
            }
        }
        binding.chatTime.timeAgoClock(messageItem.createdAt)

        setStatusIcon(
            isMe,
            MessageStatus.DELIVERED.name,
            isSecret = false,
            isRepresentative = false,
            isWhite = true
        ) { statusIcon, secretIcon, representativeIcon ->
            statusIcon?.setBounds(0, 0, dp12, dp12)
            secretIcon?.setBounds(0, 0, dp8, dp8)
            representativeIcon?.setBounds(0, 0, dp8, dp8)
            TextViewCompat.setCompoundDrawablesRelative(binding.chatTime, secretIcon ?: representativeIcon, null, statusIcon, null)
        }

        dataWidth = messageItem.mediaWidth
        dataHeight = messageItem.mediaHeight
        dataUrl = if (messageItem.isLive()) {
            messageItem.thumbUrl
        } else {
            messageItem.absolutePath()
        }
        type = messageItem.type
        dataThumbImage = messageItem.thumbImage
        chatLayout(isMe, isLast)
    }

    private var dataUrl: String? = null
    private var type: String? = null
    private var dataThumbImage: String? = null
    private var dataWidth: Int? = null
    private var dataHeight: Int? = null

    override fun chatLayout(isMe: Boolean, isLast: Boolean, isBlink: Boolean) {
        super.chatLayout(isMe, isLast, isBlink)
        if (isMe) {
            (binding.chatLayout.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.END
            (binding.chatImageLayout.layoutParams as ConstraintLayout.LayoutParams).horizontalBias = 1f
            (binding.durationTv.layoutParams as ViewGroup.MarginLayoutParams).marginStart = dp4
            (binding.liveTv.layoutParams as ViewGroup.MarginLayoutParams).marginStart = dp4
            (binding.chatTime.layoutParams as ViewGroup.MarginLayoutParams).marginEnd = dp10
        } else {
            (binding.chatLayout.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.START
            (binding.chatImageLayout.layoutParams as ConstraintLayout.LayoutParams).horizontalBias = 0f
            (binding.durationTv.layoutParams as ViewGroup.MarginLayoutParams).marginStart = dp10
            (binding.liveTv.layoutParams as ViewGroup.MarginLayoutParams).marginStart = dp10
            (binding.chatTime.layoutParams as ViewGroup.MarginLayoutParams).marginEnd = dp3
        }

        var width = mediaWidth - dp6
        when {
            isLast -> {
                width = mediaWidth
                (binding.chatImage.layoutParams as ViewGroup.MarginLayoutParams).marginEnd = 0
                (binding.chatImage.layoutParams as ViewGroup.MarginLayoutParams).marginStart = 0
            }
            isMe -> {
                (binding.chatImage.layoutParams as ViewGroup.MarginLayoutParams).marginEnd = dp6
                (binding.chatImage.layoutParams as ViewGroup.MarginLayoutParams).marginStart = 0
            }
            else -> {
                (binding.chatImage.layoutParams as ViewGroup.MarginLayoutParams).marginEnd = 0
                (binding.chatImage.layoutParams as ViewGroup.MarginLayoutParams).marginStart = dp6
            }
        }
        if (dataWidth == null || dataHeight == null ||
            dataWidth!! <= 0 || dataHeight!! <= 0
        ) {
            binding.chatImage.layoutParams.width = width
            binding.chatImage.layoutParams.height = width
        } else {
            binding.chatImage.layoutParams.width = width
            binding.chatImage.layoutParams.height = width * dataHeight!! / dataWidth!!
        }

        val mark = when {
            isMe && isLast -> R.drawable.chat_mark_image_me
            isMe -> R.drawable.chat_mark_image
            !isMe && isLast -> R.drawable.chat_mark_image_other
            else -> R.drawable.chat_mark_image
        }

        binding.chatImage.setShape(mark)
        if (type == MessageCategory.PLAIN_LIVE.name || type == MessageCategory.SIGNAL_LIVE.name) {
            binding.chatImage.loadImageMark(dataUrl, R.drawable.image_holder, mark)
        } else {
            binding.chatImage.loadVideoMark(dataUrl, dataThumbImage, mark)
        }
    }
}
