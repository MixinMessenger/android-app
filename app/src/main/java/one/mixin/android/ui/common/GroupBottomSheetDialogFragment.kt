package one.mixin.android.ui.common

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.uber.autodispose.autoDispose
import java.io.File
import kotlinx.android.synthetic.main.fragment_group_bottom_sheet.view.*
import kotlinx.android.synthetic.main.view_round_title.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.Constants.ARGS_CONVERSATION_ID
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.api.response.ConversationResponse
import one.mixin.android.extension.addFragment
import one.mixin.android.extension.colorFromAttribute
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.localTime
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.screenHeight
import one.mixin.android.extension.showConfirmDialog
import one.mixin.android.extension.toast
import one.mixin.android.ui.common.info.MenuStyle
import one.mixin.android.ui.common.info.MixinScrollableBottomSheetDialogFragment
import one.mixin.android.ui.common.info.createMenuLayout
import one.mixin.android.ui.common.info.menu
import one.mixin.android.ui.common.info.menuGroup
import one.mixin.android.ui.common.info.menuList
import one.mixin.android.ui.conversation.ConversationActivity
import one.mixin.android.ui.conversation.holder.BaseViewHolder
import one.mixin.android.ui.conversation.link.LinkBottomSheetDialogFragment.Companion.CODE
import one.mixin.android.ui.group.GroupActivity
import one.mixin.android.ui.group.GroupActivity.Companion.ARGS_EXPAND
import one.mixin.android.ui.group.GroupEditFragment
import one.mixin.android.ui.media.SharedMediaActivity
import one.mixin.android.ui.search.SearchMessageFragment
import one.mixin.android.ui.url.openUrlWithExtraWeb
import one.mixin.android.util.ErrorHandler
import one.mixin.android.util.Session
import one.mixin.android.vo.Conversation
import one.mixin.android.vo.ConversationStatus
import one.mixin.android.vo.Participant
import one.mixin.android.vo.ParticipantRole
import one.mixin.android.vo.SearchMessageItem
import one.mixin.android.widget.linktext.AutoLinkMode
import org.jetbrains.anko.dimen
import org.jetbrains.anko.margin
import org.threeten.bp.Instant

class GroupBottomSheetDialogFragment : MixinScrollableBottomSheetDialogFragment() {

    companion object {
        const val TAG = "ProfileBottomSheetDialogFragment"

        fun newInstance(conversationId: String, code: String? = null, expand: Boolean = false) =
            GroupBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARGS_CONVERSATION_ID, conversationId)
                    putString(CODE, code)
                    putBoolean(ARGS_EXPAND, expand)
                }
            }
    }

    var callback: Callback? = null

    private val conversationId: String by lazy {
        arguments!!.getString(ARGS_CONVERSATION_ID)!!
    }
    private val code: String? by lazy { arguments!!.getString(CODE) }
    private lateinit var conversation: Conversation
    private var me: Participant? = null

    private var menuListLayout: ViewGroup? = null

    override fun getLayoutId() = R.layout.fragment_group_bottom_sheet

    @SuppressLint("SetTextI18n")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        contentView.title.right_iv.setOnClickListener { dismiss() }
        contentView.join_tv.setOnClickListener {
            if (code == null) return@setOnClickListener

            bottomViewModel.join(code!!).autoDispose(stopScope).subscribe({
                if (it.isSuccess) {
                    dismiss()
                    val conversationResponse = it.data as ConversationResponse
                    val accountId = Session.getAccountId()
                    conversationResponse.participants.forEach { request ->
                        if (request.userId == accountId) {
                            bottomViewModel.refreshConversation(conversationId)
                            return@forEach
                        }
                    }
                    ConversationActivity.show(requireContext(), conversationId)
                } else {
                    ErrorHandler.handleMixinError(it.errorCode, it.errorDescription)
                }
            }, {
                ErrorHandler.handleError(it)
            })
        }
        contentView.member_fl.setOnClickListener {
            GroupActivity.show(requireContext(), GroupActivity.INFO, conversationId)
            dismiss()
        }
        contentView.send_fl.setOnClickListener {
            if (conversationId != MixinApplication.conversationId) {
                ConversationActivity.show(requireContext(), conversationId)
            }
            dismiss()
        }
        contentView.detail_tv.movementMethod = LinkMovementMethod()
        contentView.detail_tv.addAutoLinkMode(AutoLinkMode.MODE_URL)
        contentView.detail_tv.setUrlModeColor(BaseViewHolder.LINK_COLOR)
        contentView.detail_tv.setAutoLinkOnClickListener { _, url ->
            openUrlWithExtraWeb(url, conversationId, parentFragmentManager, lifecycleScope, {
                bottomViewModel.suspendFindUserById(it)
            }, {
                bottomViewModel.findAppById(it)
            })
            dismiss()
        }

        bottomViewModel.getConversationById(conversationId).observe(this, Observer { c ->
            if (c == null) return@Observer

            val changeMenu = menuListLayout == null ||
                c.muteUntil != conversation.muteUntil
            conversation = c
            val icon = c.iconUrl
            contentView.avatar.setGroup(icon)
            if (icon == null || !File(icon).exists()) {
                bottomViewModel.startGenerateAvatar(c.conversationId)
            }
            contentView.name.text = c.name
            if (c.announcement.isNullOrBlank()) {
                contentView.detail_tv.isVisible = false
            } else {
                contentView.detail_tv.isVisible = true
                contentView.detail_tv.text = c.announcement
            }
            initParticipant(changeMenu, c)
        })

        contentView.post {
            contentView.detail_tv.maxHeight = requireContext().screenHeight() / 3
        }

        bottomViewModel.refreshConversation(conversationId)
    }

    @SuppressLint("SetTextI18n")
    private fun initParticipant(
        changeMenu: Boolean,
        conversation: Conversation
    ) = lifecycleScope.launch {
        if (!isAdded) return@launch

        var participantCount = 0
        var localMe: Participant? = null
        withContext(Dispatchers.IO) {
            localMe = bottomViewModel.findParticipantByIds(conversationId, Session.getAccountId()!!)
            participantCount = bottomViewModel.getParticipantsCount(conversationId)
        }
        if (!isAdded) return@launch

        contentView.count_tv.text = getString(R.string.group_participants_count, participantCount)
        if (changeMenu || me != localMe) {
            initMenu(localMe)
        }
        me = localMe
        if (me != null) {
            contentView.ops_ll.isVisible = true
            contentView.join_tv.isVisible = false
            contentView.scroll_view.isEnabled = true
        } else {
            val withoutCode = conversation.status == ConversationStatus.QUIT.ordinal && code == null
            contentView.scroll_view.isEnabled = withoutCode
            contentView.ops_ll.isVisible = withoutCode
            contentView.join_tv.isVisible = code != null
        }

        contentView.doOnPreDraw {
            behavior?.peekHeight = contentView.title.height + contentView.scroll_content.height -
                (menuListLayout?.height ?: 0) - if (menuListLayout != null) requireContext().dpToPx(70f) else requireContext().dpToPx(40f)
        }
    }

    private fun initMenu(me: Participant?) {
        val list = menuList {
            menuGroup {
                menu {
                    title = getString(R.string.contact_other_shared_media)
                    action = {
                        SharedMediaActivity.show(requireContext(), conversationId)
                        dismiss()
                    }
                }
                menu {
                    title = getString(R.string.contact_other_search_conversation)
                    action = {
                        startSearchConversation()
                        dismiss()
                    }
                }
            }
        }
        if (me != null) {
            if (me.role == ParticipantRole.OWNER.name || me.role == ParticipantRole.ADMIN.name) {
                val announcementString = if (TextUtils.isEmpty(conversation.announcement)) {
                    getString(R.string.group_info_add)
                } else {
                    getString(R.string.group_info_edit)
                }
                list.groups.add(menuGroup {
                    menu {
                        title = announcementString
                        action = {
                            activity?.addFragment(
                                this@GroupBottomSheetDialogFragment, GroupEditFragment.newInstance(
                                    conversationId, conversation.announcement
                                ), GroupEditFragment.TAG
                            )
                            dismiss()
                        }
                    }
                    menu {
                        title = getString(R.string.group_edit_name)
                        action = { showDialog(conversation.name) }
                    }
                })
            }
            val muteMenu = if (conversation.muteUntil.notNullWithElse({
                    Instant.now().isBefore(Instant.parse(it))
                }, false)) {
                menu {
                    title = getString(R.string.un_mute)
                    subtitle = getString(R.string.mute_until, conversation.muteUntil?.localTime())
                    action = { unMute() }
                }
            } else {
                menu {
                    title = getString(R.string.mute)
                    action = { mute() }
                }
            }
            list.groups.add(menuGroup {
                menu(muteMenu)
            })
        }
        val deleteMenu = if (me != null) {
            menu {
                title = getString(R.string.group_info_exit_group)
                style = MenuStyle.Danger
                action = {
                    requireContext().showConfirmDialog(getString(R.string.group_info_exit_group)) {
                        bottomViewModel.exitGroup(conversationId)
                        dismiss()
                    }
                }
            }
        } else {
            menu {
                title = getString(R.string.group_info_delete_group)
                style = MenuStyle.Danger
                action = {
                    requireContext().showConfirmDialog(getString(R.string.group_info_delete_group)) {
                        bottomViewModel.deleteGroup(conversationId)
                        callback?.onDelete()
                    }
                }
            }
        }
        list.groups.add(menuGroup {
            menu {
                title = getString(R.string.group_info_clear_chat)
                style = MenuStyle.Danger
                action = {
                    requireContext().showConfirmDialog(getString(R.string.group_info_clear_chat)) {
                        bottomViewModel.deleteMessageByConversationId(conversationId)
                        dismiss()
                    }
                }
            }
            menu(deleteMenu)
        })

        menuListLayout?.removeAllViews()
        contentView.scroll_content.removeView(menuListLayout)
        list.createMenuLayout(requireContext())
            .let { layout ->
                menuListLayout = layout
                contentView.scroll_content.addView(layout)
                layout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = requireContext().dpToPx(30f)
                }
                contentView.more_fl.setOnClickListener {
                    if (behavior?.state == BottomSheetBehavior.STATE_COLLAPSED) {
                        behavior?.state = BottomSheetBehavior.STATE_EXPANDED
                        contentView.more_iv.rotationX = 180f
                    } else {
                        behavior?.state = BottomSheetBehavior.STATE_COLLAPSED
                        contentView.scroll_view.smoothScrollTo(0, 0)
                        contentView.more_iv.rotationX = 0f
                    }
                }
            }
    }

    private fun startSearchConversation() = lifecycleScope.launch(Dispatchers.IO) {
        bottomViewModel.getConversation(conversationId)?.let {
            val searchMessageItem = SearchMessageItem(
                it.conversationId, it.category, it.name,
                0, "", null, null, it.iconUrl
            )
            activity?.addFragment(
                this@GroupBottomSheetDialogFragment,
                SearchMessageFragment.newInstance(searchMessageItem, ""), SearchMessageFragment.TAG
            )
        }
    }

    private fun mute() {
        showMuteDialog()
    }

    private fun unMute() {
        val account = Session.getAccount()
        account?.let {
            bottomViewModel.mute(conversationId, 0)
            context?.toast(getString(R.string.un_mute) + " ${conversation.name}")
        }
    }

    private fun showMuteDialog() {
        val choices = arrayOf(
            getString(R.string.contact_mute_8hours),
            getString(R.string.contact_mute_1week), getString(R.string.contact_mute_1year)
        )
        var duration = UserBottomSheetDialogFragment.MUTE_8_HOURS
        var whichItem = 0
        AlertDialog.Builder(requireContext(), R.style.MixinAlertDialogTheme)
            .setTitle(getString(R.string.contact_mute_title))
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                val account = Session.getAccount()
                account?.let {
                    bottomViewModel.mute(conversationId, duration.toLong())
                    context?.toast(getString(R.string.contact_mute_title) + " ${conversation.name} " + choices[whichItem])
                }
                dialog.dismiss()
            }
            .setSingleChoiceItems(choices, 0) { _, which ->
                whichItem = which
                when (which) {
                    0 -> duration = UserBottomSheetDialogFragment.MUTE_8_HOURS
                    1 -> duration = UserBottomSheetDialogFragment.MUTE_1_WEEK
                    2 -> duration = UserBottomSheetDialogFragment.MUTE_1_YEAR
                }
            }
            .show()
    }

    @SuppressLint("RestrictedApi")
    private fun showDialog(name: String?) {
        if (context == null) {
            return
        }
        val editText = EditText(requireContext())
        editText.setTextColor(requireContext().colorFromAttribute(R.attr.text_primary))
        editText.setHintTextColor(requireContext().colorFromAttribute(R.attr.text_assist))
        editText.hint = getString(R.string.profile_modify_name_hint)
        editText.setText(name)
        if (name != null) {
            editText.setSelection(name.length)
        }
        val frameLayout = FrameLayout(requireContext())
        frameLayout.addView(editText)
        val params = editText.layoutParams as FrameLayout.LayoutParams
        params.margin = requireContext().dimen(R.dimen.activity_horizontal_margin)
        editText.layoutParams = params
        val nameDialog = AlertDialog.Builder(requireContext(), R.style.MixinAlertDialogTheme)
            .setTitle(R.string.profile_modify_name)
            .setView(frameLayout)
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                bottomViewModel.updateGroup(conversationId, editText.text.toString(), null)
                dialog.dismiss()
            }
            .show()
        nameDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                nameDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled =
                    !(s.isNullOrBlank() || s.toString() == name.toString())
            }
        })

        nameDialog.window?.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
        nameDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    override fun onStateChanged(bottomSheet: View, newState: Int) {
        when (newState) {
            BottomSheetBehavior.STATE_HIDDEN -> dismiss()
            BottomSheetBehavior.STATE_COLLAPSED -> contentView.more_iv.rotationX = 0f
            BottomSheetBehavior.STATE_EXPANDED -> contentView.more_iv.rotationX = 180f
        }
    }

    interface Callback {
        fun onDelete()
    }
}
