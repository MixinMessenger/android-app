package one.mixin.android.widget

import android.content.Context
import android.util.ArraySet
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.widget.RelativeLayout
import kotlinx.android.synthetic.main.view_pseudo_notification.view.*
import one.mixin.android.R
import one.mixin.android.ui.url.isMixinUrl
import org.jetbrains.anko.backgroundResource
import org.jetbrains.anko.dip

class PseudoNotificationView: RelativeLayout {

    var currContent: String? = null

    private val contentSet = ArraySet<String>()
    private var visible = false

    lateinit var callback: Callback

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        LayoutInflater.from(context).inflate(R.layout.view_pseudo_notification, this, true)
        val d = resources.getDrawable(R.drawable.ic_qr_code_preview, null)
        val size = context.dip(12)
        d.setBounds(0, 0, size, size)
        title_tv.setCompoundDrawables(d, null, null, null)
        backgroundResource = R.drawable.bg_round_gray
    }

    fun addContent(s: String) {
        if (contentSet.contains(s)) {
            return
        }
        contentSet.add(s)
        currContent = s
        content_tv.text = if (isMixinUrl(s)) {
            context.getString(R.string.detect_qr_tip)
        } else {
            s
        }
        if (!visible) {
            animate().apply {
                translationY(0f)
                interpolator = DecelerateInterpolator()
            }.start()
            visible = true
        }
    }

    private fun hide() {
        animate().apply {
            translationY(-context.dip(300).toFloat())
            interpolator = DecelerateInterpolator()
        }.start()
        visible = false
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
            hide()
            return super.onFling(e1, e2, velocityX, velocityY)
        }

        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
            currContent?.let { callback.onClick(it) }
            hide()
            return super.onSingleTapConfirmed(e)
        }
    })

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    interface Callback {
        fun onClick(content: String)
    }
}