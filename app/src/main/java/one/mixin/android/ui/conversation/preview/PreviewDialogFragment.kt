package one.mixin.android.ui.conversation.preview

import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v4.app.MixinDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.fragment_preview.view.*
import kotlinx.android.synthetic.main.fragment_preview_video.view.*
import one.mixin.android.R
import one.mixin.android.extension.displaySize
import one.mixin.android.extension.getFilePath
import one.mixin.android.extension.getMimeType
import one.mixin.android.extension.loadImage
import one.mixin.android.extension.toast
import one.mixin.android.util.video.MixinPlayer
import one.mixin.android.widget.VideoTimelineView
import org.jetbrains.anko.bundleOf
import java.util.concurrent.TimeUnit

class PreviewDialogFragment : MixinDialogFragment(), VideoTimelineView.VideoTimelineViewDelegate {

    companion object {
        const val IS_VIDEO: String = "IS_VIDEO"
        fun newInstance(isVideo: Boolean = false): PreviewDialogFragment {
            val previewDialogFragment = PreviewDialogFragment()
            previewDialogFragment.arguments = bundleOf(
                IS_VIDEO to isVideo
            )
            return previewDialogFragment
        }
    }

    private val isVideo by lazy {
        arguments!!.getBoolean(IS_VIDEO)
    }

    private var currentState = false

    private val mixinPlayer: MixinPlayer by lazy {
        MixinPlayer().apply {
            setOnVideoPlayerListener(videoListener)
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentState) {
            mixinPlayer.start()
        }
    }

    override fun onPause() {
        super.onPause()
        currentState = mixinPlayer.isPlaying()
        mixinPlayer.pause()
    }

    fun release() {
        if (isVideo) {
            mixinPlayer.release()
        }
    }

    private var mediaDialogView: View? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mediaDialogView = inflater.inflate(if (isVideo) {
            R.layout.fragment_preview_video
        } else {
            R.layout.fragment_preview
        }, null, false)
        if (isVideo) {
            mediaDialogView!!.dialog_play.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    mixinPlayer.pause()
                } else {
                    mixinPlayer.start()
                }
            }
            mediaDialogView!!.time.setDelegate(this)
            mediaDialogView!!.dialog_cancel.setOnClickListener {
                dismiss()
            }
        } else {
            mediaDialogView!!.dialog_close_iv.setOnClickListener {
                dismiss()
            }
        }

        return mediaDialogView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        dialog.window.requestFeature(Window.FEATURE_NO_TITLE)
        super.onActivityCreated(savedInstanceState)
        dialog.window.setBackgroundDrawable(ColorDrawable(0x00000000))
        dialog.window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog.window.setWindowAnimations(R.style.BottomSheet_Animation)
        dialog.setOnShowListener {
            if (isVideo) {
                val mimeType = getMimeType(uri!!)
                if (mimeType == null || !mimeType.startsWith("video", true)) {
                    context?.toast(R.string.error_format)
                    dismiss()
                }
                mixinPlayer.loadVideo(uri.toString())
                mixinPlayer.setVideoTextureView(mediaDialogView!!.dialog_video_texture)
                mediaDialogView!!.time.setVideoPath(uri!!.getFilePath(context!!))
                Observable.interval(0, 100, TimeUnit.MILLISECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe {
                    if (mixinPlayer.duration() != 0 && mixinPlayer.isPlaying()) {
                        mediaDialogView!!.time.progress = mixinPlayer.getCurrentPos().toFloat() / mixinPlayer.duration()
                    }
                }
                mediaDialogView!!.dialog_ok.setOnClickListener {
                    action!!(uri!!)
                    dismiss()
                }
            } else {
                mediaDialogView!!.dialog_send_ib.setOnClickListener { action!!(uri!!); dismiss() }
                mediaDialogView!!.dialog_iv.loadImage(uri)
            }
        }
    }

    private var uri: Uri? = null
    private var action: ((Uri) -> Unit)? = null
    fun show(fragmentManager: FragmentManager, uri: Uri, action: (Uri) -> Unit) {
        super.showNow(fragmentManager, if (isVideo) {
            "PreviewVideoDialogFragment"
        } else {
            "PreviewDialogFragment"
        })
        this.uri = uri
        this.action = action
    }

    private val videoListener = object : MixinPlayer.VideoPlayerListenerWrapper() {
        override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
            val ratio = width / height.toFloat()
            val lp = mediaDialogView!!.dialog_video_texture.layoutParams
            val screenWidth = context!!.displaySize().x
            val screenHeight = context!!.displaySize().y
            if (screenWidth / ratio > screenHeight) {
                lp.height = screenHeight
                lp.width = (screenHeight * ratio).toInt()
            } else {
                lp.width = screenWidth
                lp.height = (screenWidth / ratio).toInt()
            }
            mediaDialogView!!.dialog_video_texture.layoutParams = lp
        }
    }

    override fun didStopDragging() {
        if (currentState) {
            mixinPlayer.start()
        }
    }

    override fun didStartDragging() {
        currentState = mixinPlayer.isPlaying()
        mixinPlayer.pause()
    }

    override fun onPlayProgressChanged(progress: Float) {
        mixinPlayer.seekTo((progress * mixinPlayer.duration()).toInt())
    }
}
