package one.mixin.android.ui.media

import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import one.mixin.android.R
import one.mixin.android.databinding.ItemMediaBinding
import one.mixin.android.extension.formatMillis
import one.mixin.android.extension.loadBase64ImageCenterCrop
import one.mixin.android.extension.loadGif
import one.mixin.android.extension.loadImageCenterCrop
import one.mixin.android.ui.common.recyclerview.NormalHolder
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.absolutePath
import one.mixin.android.vo.isImage
import one.mixin.android.vo.isVideo
import one.mixin.android.widget.gallery.MimeType

class MediaAdapter(private val onClickListener: (imageView: View, messageItem: MessageItem) -> Unit) :
    SharedMediaHeaderAdapter<MediaHolder>() {
    var size: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MediaHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_media,
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: MediaHolder, position: Int) {
        getItem(position)?.let {
            holder.bind(it, size, onClickListener)
        }
    }

    override fun getHeaderTextMargin() = 5f
}

class MediaHolder(itemView: View) : NormalHolder(itemView) {
    fun bind(
        item: MessageItem,
        size: Int,
        onClickListener: (imageView: View, messageItem: MessageItem) -> Unit
    ) {
        val binding = ItemMediaBinding.bind(itemView)
        val params = itemView.layoutParams
        params.width = size
        params.height = size
        itemView.layoutParams = params
        val imageView = binding.thumbnailIv
        imageView.updateLayoutParams<ViewGroup.LayoutParams> {
            width = size
            height = size
        }
        if (item.mediaUrl == null && item.thumbImage != null) {
            val imageData = Base64.decode(item.thumbImage, Base64.DEFAULT)
            imageView.loadBase64ImageCenterCrop(imageData)
        } else {
            if (item.isImage()) {
                val isGif = item.mediaMimeType.equals(MimeType.GIF.toString(), true)
                if (isGif) {
                    imageView.loadGif(
                        item.mediaUrl.toString(),
                        centerCrop = true,
                        holder = R.drawable.ic_giphy_place_holder
                    )
                    binding.gifTv.isVisible = true
                } else {
                    imageView.loadImageCenterCrop(item.absolutePath(), R.drawable.image_holder)
                    binding.gifTv.isVisible = false
                }
                binding.videoIv.isVisible = false
                binding.durationTv.isVisible = false
            } else {
                binding.gifTv.isVisible = false
                if (item.isVideo()) {
                    binding.videoIv.isVisible = true
                    binding.durationTv.isVisible = true
                    binding.durationTv.text = item.mediaDuration?.toLongOrNull()?.formatMillis() ?: ""
                } else {
                    binding.videoIv.isVisible = false
                    binding.durationTv.isVisible = false
                }
                imageView.loadImageCenterCrop(item.absolutePath(), R.drawable.image_holder)
            }
        }
        itemView.setOnClickListener {
            onClickListener(binding.thumbnailIv, item)
        }
    }
}
