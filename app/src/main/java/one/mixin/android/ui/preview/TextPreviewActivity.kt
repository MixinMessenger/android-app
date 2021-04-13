package one.mixin.android.ui.preview

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import one.mixin.android.R
import one.mixin.android.databinding.ActivityPreviewTextBinding
import one.mixin.android.extension.openAsUrlOrWeb
import one.mixin.android.extension.renderMessage
import one.mixin.android.extension.toast
import one.mixin.android.ui.common.BlazeBaseActivity
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.conversation.holder.BaseViewHolder
import one.mixin.android.ui.forward.ForwardActivity
import one.mixin.android.util.mention.MentionRenderCache
import one.mixin.android.vo.MessageItem
import one.mixin.android.widget.linktext.AutoLinkMode

@AndroidEntryPoint
class TextPreviewActivity : BlazeBaseActivity() {

    private lateinit var binding: ActivityPreviewTextBinding

    private val messageItem: MessageItem by lazy {
        intent.getParcelableExtra(ARGS_MESSAGE)!!
    }

    private val viewModel by viewModels<TextPreviewViewModel>()

    private var actionMode: ActionMode? = null
    private var dismissWhenClickText = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(android.R.anim.fade_in, 0)
        binding = ActivityPreviewTextBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.text.requestFocus()
        binding.text.movementMethod = LinkMovementMethod()
        binding.text.addAutoLinkMode(AutoLinkMode.MODE_URL, AutoLinkMode.MODE_MENTION)
        binding.text.setUrlModeColor(BaseViewHolder.LINK_COLOR)
        binding.text.setMentionModeColor(BaseViewHolder.LINK_COLOR)
        binding.text.setSelectedStateColor(BaseViewHolder.SELECT_COLOR)
        binding.text.setAutoLinkOnClickListener { autoLinkMode, matchedText ->
            when (autoLinkMode) {
                AutoLinkMode.MODE_URL -> {
                    dismissWhenClickText = false
                    matchedText.openAsUrlOrWeb(this, messageItem.conversationId, supportFragmentManager, lifecycleScope)
                }
                AutoLinkMode.MODE_MENTION -> {
                    dismissWhenClickText = false
                    lifecycleScope.launch {
                        viewModel.findUserByIdentityNumberSuspend(matchedText.substring(1))?.let { user ->
                            UserBottomSheetDialogFragment.newInstance(user, messageItem.conversationId)
                                ?.showNow(supportFragmentManager, UserBottomSheetDialogFragment.TAG)
                        }
                    }
                }
                else -> {
                }
            }
        }
        val mention = messageItem.mentions
        if (mention?.isNotBlank() == true) {
            val mentionRenderContext = MentionRenderCache.singleton.getMentionRenderContext(mention)
            binding.text.renderMessage(messageItem.content, null, mentionRenderContext)
        } else {
            binding.text.renderMessage(messageItem.content, null)
        }
        binding.text.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val menuInflater: MenuInflater = mode.menuInflater
                menuInflater.inflate(R.menu.text_preview_selection_action_menu, menu)
                actionMode = mode
                dismissWhenClickText = false
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                binding.text.text?.let { editable ->
                    when (item.itemId) {
                        android.R.id.copy -> {
                            toast(R.string.copy_success)
                        }
                        R.id.forward -> {
                            ForwardActivity.show(this@TextPreviewActivity, editable.toString())
                        }
                        else -> ""
                    }
                }
                dismissWhenClickText = true
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                actionMode = null
            }
        }
        binding.text.listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                if (actionMode == null && dismissWhenClickText) {
                    finish()
                }
                dismissWhenClickText = true
                return true
            }
        }
        binding.root.setOnClickListener {
            if (actionMode == null) {
                finish()
            } else {
                actionMode?.finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
    }

    companion object {
        const val ARGS_MESSAGE = "args_message"

        fun show(context: Context, messageItem: MessageItem) {
            Intent(context, TextPreviewActivity::class.java).apply {
                putExtra(ARGS_MESSAGE, messageItem)
                context.startActivity(this)
            }
        }
    }
}
