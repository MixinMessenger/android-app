package one.mixin.android.ui.common

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import kotlinx.android.synthetic.main.bottom_qr.view.*
import one.mixin.android.R
import one.mixin.android.extension.getClipboardManager
import one.mixin.android.extension.isWebUrl
import one.mixin.android.ui.conversation.holder.BaseViewHolder
import one.mixin.android.ui.conversation.link.LinkBottomSheetDialogFragment
import one.mixin.android.ui.conversation.web.WebBottomSheetDialogFragment
import one.mixin.android.ui.group.GroupFragment.Companion.ARGS_CONVERSATION_ID
import one.mixin.android.ui.url.isMixinUrl
import one.mixin.android.widget.BottomSheet
import one.mixin.android.widget.linktext.AutoLinkMode
import org.jetbrains.anko.support.v4.toast

class QrBottomSheetDialogFragment: MixinBottomSheetDialogFragment() {
    companion object {
        const val TAG = "QrBottomSheetDialogFragment"
        const val ARGS_TEXT = "args_text"

        fun newInstance(text: String, conversationId: String? = null) = QrBottomSheetDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARGS_TEXT, text)
                putString(ARGS_CONVERSATION_ID, conversationId)
            }
        }
    }

    private val text: String by lazy { arguments!!.getString(ARGS_TEXT) }
    private val conversationId: String? by lazy { arguments!!.getString(ARGS_CONVERSATION_ID) }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        contentView = View.inflate(context, R.layout.bottom_qr, null)
        (dialog as BottomSheet).setCustomView(contentView)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        contentView.qr_tv.addAutoLinkMode(AutoLinkMode.MODE_URL)
        contentView.qr_tv.setUrlModeColor(BaseViewHolder.LINK_COLOR)
        contentView.qr_tv.setAutoLinkOnClickListener({ _, url ->
            when {
                isMixinUrl(url) -> LinkBottomSheetDialogFragment
                    .newInstance(url).show(fragmentManager, LinkBottomSheetDialogFragment.TAG)
                else -> WebBottomSheetDialogFragment
                    .newInstance(url, conversationId)
                    .show(fragmentManager, WebBottomSheetDialogFragment.TAG)
            }
            dialog?.dismiss()
        })
        contentView.qr_tv.text = text
        contentView.copy.setOnClickListener {
            context?.getClipboardManager()?.primaryClip = ClipData.newPlainText(null, text)
            toast(R.string.copy_success)
            dialog?.dismiss()
        }
        if (text.isWebUrl()) {
            contentView.open_fl.visibility = VISIBLE
            contentView.open.setOnClickListener {
                WebBottomSheetDialogFragment.newInstance(text, conversationId)
                    .show(fragmentManager, WebBottomSheetDialogFragment.TAG)
                dialog.dismiss()
            }
        } else {
            contentView.open_fl.visibility = GONE
        }
    }
}