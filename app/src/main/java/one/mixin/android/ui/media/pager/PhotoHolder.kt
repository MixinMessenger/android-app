package one.mixin.android.ui.media.pager

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import one.mixin.android.R
import one.mixin.android.extension.loadGif
import one.mixin.android.extension.loadImage
import one.mixin.android.job.MixinJobManager.Companion.getAttachmentProcess
import one.mixin.android.session.Session
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.absolutePath
import one.mixin.android.widget.CircleProgress
import one.mixin.android.widget.PhotoView.PhotoView
import one.mixin.android.widget.gallery.MimeType

class PhotoHolder(itemView: View) : MediaPagerHolder(itemView) {
    fun bind(
        messageItem: MessageItem,
        needPostTransition: Boolean,
        mediaPagerAdapterListener: MediaPagerAdapterListener
    ) {
        val imageView = (itemView as ViewGroup).getChildAt(0) as PhotoView
        val photoViewAttacher = imageView.attacher
        val circleProgress = itemView.findViewById<CircleProgress>(R.id.circle_progress)
        if (messageItem.mediaMimeType.equals(MimeType.GIF.toString(), true)) {
            imageView.loadGif(
                messageItem.absolutePath(),
                object : RequestListener<GifDrawable?> {
                    override fun onResourceReady(
                        resource: GifDrawable?,
                        model: Any?,
                        target: Target<GifDrawable?>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        photoViewAttacher.isZoomable = true
                        if (needPostTransition) {
                            ViewCompat.setTransitionName(imageView, "transition")
                            mediaPagerAdapterListener.onReadyPostTransition(imageView)
                        }
                        return false
                    }

                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<GifDrawable?>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }
                },
                base64Holder = messageItem.thumbImage,
                overrideWidth = Target.SIZE_ORIGINAL,
                overrideHeight = Target.SIZE_ORIGINAL
            )
        } else {
            imageView.loadImage(
                messageItem.absolutePath(),
                messageItem.thumbImage,
                object : RequestListener<Drawable?> {
                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable?>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        photoViewAttacher.isZoomable = true
                        if (needPostTransition) {
                            ViewCompat.setTransitionName(imageView, "transition")
                            mediaPagerAdapterListener.onReadyPostTransition(imageView)
                        }
                        return false
                    }

                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable?>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }
                },
                overrideWidth = Target.SIZE_ORIGINAL,
                overrideHeight = Target.SIZE_ORIGINAL
            )
        }
        if (messageItem.mediaStatus == MediaStatus.DONE.name || messageItem.mediaStatus == MediaStatus.READ.name) {
            circleProgress.isVisible = false
            circleProgress.setBindId(messageItem.messageId)
        } else {
            circleProgress.isVisible = true
            circleProgress.setBindId(messageItem.messageId)
            if (messageItem.mediaStatus == MediaStatus.PENDING.name) {
                circleProgress.enableLoading(getAttachmentProcess(messageItem.messageId))
            } else if (messageItem.mediaStatus == MediaStatus.CANCELED.name) {
                if (Session.getAccountId() == messageItem.userId) {
                    circleProgress.enableUpload()
                } else {
                    circleProgress.enableDownload()
                }
            } else {
                // TODO expired
            }
            circleProgress.setOnClickListener {
                mediaPagerAdapterListener.onCircleProgressClick(messageItem)
            }
        }
        imageView.setOnClickListener {
            mediaPagerAdapterListener.onClick(messageItem)
        }
        imageView.setOnLongClickListener {
            mediaPagerAdapterListener.onLongClick(messageItem, itemView)
            return@setOnLongClickListener true
        }
        if (needPostTransition) {
            ViewCompat.setTransitionName(imageView, "transition")
            mediaPagerAdapterListener.onReadyPostTransition(imageView)
        }
    }
}
