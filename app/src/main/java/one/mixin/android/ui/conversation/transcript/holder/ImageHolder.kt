package one.mixin.android.ui.conversation.transcript.holder

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import one.mixin.android.R
import one.mixin.android.databinding.ItemChatImageBinding
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.loadGifMark
import one.mixin.android.extension.loadImageMark
import one.mixin.android.extension.loadLongImageMark
import one.mixin.android.extension.round
import one.mixin.android.extension.timeAgoClock
import one.mixin.android.job.MixinJobManager.Companion.getAttachmentProcess
import one.mixin.android.session.Session
import one.mixin.android.ui.conversation.transcript.TranscriptAdapter
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.MessageStatus
import one.mixin.android.vo.TranscriptMessageItem
import one.mixin.android.vo.absolutePath
import one.mixin.android.widget.gallery.MimeType
import org.jetbrains.anko.dip
import kotlin.math.min

class ImageHolder constructor(val binding: ItemChatImageBinding) : MediaHolder(binding.root) {

    init {
        val radius = itemView.context.dpToPx(4f).toFloat()
        binding.chatImage.round(radius)
        binding.chatTime.round(radius)
        binding.progress.round(radius)
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
            binding.chatName.visibility = View.VISIBLE
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
            binding.chatName.visibility = View.GONE
        }

        binding.chatTime.timeAgoClock(messageItem.createdAt)
        messageItem.mediaStatus?.let {
            when (it) {
                MediaStatus.EXPIRED.name -> {
                    binding.chatWarning.visibility = View.VISIBLE
                    binding.progress.visibility = View.GONE
                }
                MediaStatus.PENDING.name -> {
                    binding.chatWarning.visibility = View.GONE
                    binding.progress.visibility = View.VISIBLE
                    binding.progress.enableLoading(getAttachmentProcess(messageItem.messageId))
                    binding.progress.setBindOnly("${messageItem.transcriptId}${messageItem.messageId}")
                    binding.progress.setOnClickListener {
                        onItemListener.onCancel(messageItem.transcriptId, messageItem.messageId)
                    }
                    binding.chatImage.setOnClickListener { }
                    binding.chatImage.setOnLongClickListener { false }
                }
                MediaStatus.DONE.name -> {
                    binding.chatWarning.visibility = View.GONE
                    binding.progress.visibility = View.GONE
                    binding.progress.setBindId("${messageItem.transcriptId}${messageItem.messageId}")
                    binding.progress.setOnClickListener {}
                    binding.progress.setOnLongClickListener { false }
                    binding.chatImage.setOnClickListener {
                        onItemListener.onImageClick(messageItem, binding.chatImage)
                    }
                }
                MediaStatus.CANCELED.name -> {
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
                    binding.chatImage.setOnLongClickListener { false }
                }
            }
        }
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
            TextViewCompat.setCompoundDrawablesRelative(
                binding.chatTime,
                secretIcon ?: representativeIcon,
                null,
                statusIcon,
                null
            )
        }

        dataWidth = messageItem.mediaWidth
        dataHeight = messageItem.mediaHeight
        dataUrl = messageItem.absolutePath()
        dataThumbImage = messageItem.thumbImage
        dataSize = messageItem.mediaSize
        isGif = messageItem.mediaMimeType.equals(MimeType.GIF.toString(), true)
        chatLayout(isMe, isLast)
    }

    private var isGif = false
    private var dataUrl: String? = null
    private var dataThumbImage: String? = null
    private var dataWidth: Int? = null
    private var dataHeight: Int? = null
    private var dataSize: Long? = null

    override fun chatLayout(isMe: Boolean, isLast: Boolean, isBlink: Boolean) {
        super.chatLayout(isMe, isLast, isBlink)
        if (isMe) {
            (binding.chatLayout.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.END
            (binding.chatImageLayout.layoutParams as ConstraintLayout.LayoutParams).horizontalBias =
                1f
            (binding.chatTime.layoutParams as ViewGroup.MarginLayoutParams).marginEnd = dp10
        } else {
            (binding.chatLayout.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.START
            (binding.chatImageLayout.layoutParams as ConstraintLayout.LayoutParams).horizontalBias =
                0f
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
            binding.chatImage.layoutParams.height =
                min(width * dataHeight!! / dataWidth!!, mediaHeight)
        }
        val mark = when {
            isMe && isLast -> R.drawable.chat_mark_image_me
            isMe -> R.drawable.chat_mark_image
            !isMe && isLast -> R.drawable.chat_mark_image_other
            else -> R.drawable.chat_mark_image
        }

        binding.chatImage.setShape(mark)
        binding.largeImageIv.isVisible = binding.chatImage.layoutParams.height == mediaHeight
        if (isBlink) {
            when {
                isGif -> handleGif(mark)
                binding.chatImage.layoutParams.height == mediaHeight -> binding.chatImage.loadLongImageMark(
                    dataUrl,
                    mark
                )
                else -> binding.chatImage.loadImageMark(dataUrl, mark)
            }
        } else {
            when {
                isGif -> handleGif(mark)
                binding.chatImage.layoutParams.height == mediaHeight -> binding.chatImage.loadLongImageMark(
                    dataUrl,
                    dataThumbImage,
                    mark
                )
                else -> binding.chatImage.loadImageMark(dataUrl, dataThumbImage, mark)
            }
        }
    }

    private fun handleGif(mark: Int) {
        if (dataSize == null || dataSize == 0L) { // un-downloaded giphy
            binding.chatImage.loadGifMark(dataThumbImage, mark, false)
        } else {
            binding.chatImage.loadGifMark(dataUrl, dataThumbImage, mark)
        }
    }
}
