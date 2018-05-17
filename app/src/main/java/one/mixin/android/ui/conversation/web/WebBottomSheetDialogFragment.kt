package one.mixin.android.ui.conversation.web

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.android.synthetic.main.fragment_web.view.*
import one.mixin.android.Constants.Mixin_Conversation_ID_HEADER
import one.mixin.android.R
import one.mixin.android.extension.displaySize
import one.mixin.android.extension.isWebUrl
import one.mixin.android.extension.notNullElse
import one.mixin.android.extension.statusBarHeight
import one.mixin.android.extension.withArgs
import one.mixin.android.ui.common.MixinBottomSheetDialogFragment
import one.mixin.android.ui.conversation.link.LinkBottomSheetDialogFragment
import one.mixin.android.ui.url.isMixinUrl
import one.mixin.android.widget.BottomSheet
import one.mixin.android.widget.DragWebView
import java.net.URISyntaxException

class WebBottomSheetDialogFragment : MixinBottomSheetDialogFragment() {

    companion object {
        const val TAG = "WebBottomSheetDialogFragment"

        private const val URL = "url"
        private const val CONVERSATION_ID = "conversation_id"
        private const val NAME = "name"
        fun newInstance(url: String, conversationId: String?, name: String = "Mixin") =
            WebBottomSheetDialogFragment().withArgs {
                putString(URL, url)
                putString(CONVERSATION_ID, conversationId)
                putString(NAME, name)
            }
    }

    private val url: String by lazy {
        arguments!!.getString(URL)
    }
    private val conversationId: String? by lazy {
        arguments!!.getString(CONVERSATION_ID)
    }
    private val name: String by lazy {
        arguments!!.getString(NAME)
    }

    @SuppressLint("RestrictedApi", "SetJavaScriptEnabled")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        contentView = View.inflate(context, R.layout.fragment_web, null)
        (dialog as BottomSheet).setCustomView(contentView)
    }

    private val miniHeight by lazy {
        context!!.displaySize().y * 3 / 4
    }

    private val maxHeight by lazy {
        context!!.displaySize().y - context!!.statusBarHeight()
    }

    private val middleHeight by lazy {
        (miniHeight + maxHeight) / 2
    }

    private var checkEnable = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        contentView.close_iv.setOnClickListener {
            dialog.dismiss()
        }
        contentView.chat_web_view.settings.javaScriptEnabled = true
        contentView.chat_web_view.settings.domStorageEnabled = true

        contentView.chat_web_view.addJavascriptInterface(WebAppInterface(context!!, conversationId), "MixinContext")
        contentView.chat_web_view.webViewClient = WebViewClientImpl(object : WebViewClientImpl.OnPageFinishedListener {
            override fun onPageFinished() {
            }
        }, conversationId, this.requireFragmentManager())

        contentView.chat_web_view.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.equals(url)) {
                    contentView.title_view.text = title
                }
            }
        }

        contentView.chat_web_view.setOnScrollListener(object : DragWebView.OnDragListener {
            override fun onUp() {
                ((dialog as BottomSheet).getCustomView())?.let {
                    val height = it.layoutParams.height
                    if (height < middleHeight) {
                        (dialog as BottomSheet).setCustomViewHeight(miniHeight)
                        changeCheck(false)
                    } else {
                        (dialog as BottomSheet).setCustomViewHeight(maxHeight)
                        checkEnable = false
                        changeCheck(true)
                        checkEnable = true
                    }
                }
            }

            override fun onScroll(disY: Float): Boolean {
                return notNullElse((dialog as BottomSheet).getCustomView(), {
                    val height = it.layoutParams.height - disY.toInt()
                    return if (height in miniHeight..maxHeight) {
                        it.layoutParams.height = height
                        it.requestLayout()
                        true
                    } else {
                        false
                    }
                }, false)
            }
        })

        contentView.zoom_out.setOnCheckedChangeListener { _, isChecked ->
            if (checkEnable) {
                if (!isChecked) {
                    (dialog as BottomSheet).setCustomViewHeight(miniHeight)
                } else {
                    (dialog as BottomSheet).setCustomViewHeight(maxHeight)
                }
            }
        }

        contentView.title_view.text = name
        dialog.setOnShowListener {
            val extraHeaders = HashMap<String, String>()
            conversationId?.let {
                extraHeaders[Mixin_Conversation_ID_HEADER] = it
            }
            contentView.chat_web_view.loadUrl(url, extraHeaders)
        }
        dialog.setOnDismissListener {
            contentView.chat_web_view.stopLoading()
            dismiss()
        }
        (dialog as BottomSheet).setCustomViewHeight(miniHeight)
    }

    class WebViewClientImpl(
        private val onPageFinishedListener: OnPageFinishedListener,
        val conversationId: String?,
        val fragmentManager: FragmentManager
    ) : WebViewClient() {

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            onPageFinishedListener.onPageFinished()
        }

        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (view == null || url == null) {
                return false
            }
            if (isMixinUrl(url)) {
                LinkBottomSheetDialogFragment.newInstance(url).show(fragmentManager, LinkBottomSheetDialogFragment.TAG)
                return true
            }
            val extraHeaders = HashMap<String, String>()
            conversationId?.let {
                extraHeaders[Mixin_Conversation_ID_HEADER] = it
            }
            if (url.isWebUrl()) {
                view.loadUrl(url, extraHeaders)
                return true
            } else {
                try {
                    val context = view.context
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)

                    if (intent != null) {
                        view.stopLoading()

                        val packageManager = context.packageManager
                        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                        if (info != null) {
                            context.startActivity(intent)
                        } else {
                            view.loadUrl(url, extraHeaders)
                            // or call external broswer
                            //                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl));
                            //                    context.startActivity(browserIntent);
                        }
                    }
                } catch (e: URISyntaxException) {
                    view.loadUrl(url, extraHeaders)
                }
            }

            return true
        }

        interface OnPageFinishedListener {
            fun onPageFinished()
        }
    }

    fun changeCheck(checked: Boolean) {
        checkEnable = false
        contentView.zoom_out.isChecked = checked
        checkEnable = true
    }

    class WebAppInterface(val context: Context, val conversationId: String?) {
        @JavascriptInterface
        fun showToast(toast: String) {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun getContext(): String? {
            return if (conversationId != null) {
                Gson().toJson(MixinContext(conversationId))
            } else {
                null
            }
        }
    }

    class MixinContext(
        @SerializedName("conversation_id")
        val conversationId: String?
    )
}