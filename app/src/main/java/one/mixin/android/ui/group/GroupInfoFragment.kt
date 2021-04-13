package one.mixin.android.ui.group

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagedList
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.Constants.ARGS_CONVERSATION_ID
import one.mixin.android.R
import one.mixin.android.databinding.FragmentGroupInfoBinding
import one.mixin.android.databinding.ViewGroupInfoHeaderBinding
import one.mixin.android.extension.addFragment
import one.mixin.android.extension.alertDialogBuilder
import one.mixin.android.extension.hideKeyboard
import one.mixin.android.extension.indeterminateProgressDialog
import one.mixin.android.job.ConversationJob.Companion.TYPE_ADD
import one.mixin.android.job.ConversationJob.Companion.TYPE_DELETE
import one.mixin.android.job.ConversationJob.Companion.TYPE_DISMISS_ADMIN
import one.mixin.android.job.ConversationJob.Companion.TYPE_MAKE_ADMIN
import one.mixin.android.job.ConversationJob.Companion.TYPE_REMOVE
import one.mixin.android.session.Session
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.conversation.ConversationActivity
import one.mixin.android.ui.group.GroupFragment.Companion.MAX_USER
import one.mixin.android.ui.group.adapter.GroupInfoAdapter
import one.mixin.android.ui.home.MainActivity
import one.mixin.android.util.viewBinding
import one.mixin.android.vo.Conversation
import one.mixin.android.vo.Participant
import one.mixin.android.vo.ParticipantItem
import one.mixin.android.vo.ParticipantRole
import one.mixin.android.vo.User
import one.mixin.android.vo.isGroupConversation
import one.mixin.android.vo.toUser

@AndroidEntryPoint
class GroupInfoFragment : BaseFragment(R.layout.fragment_group_info) {
    companion object {
        const val TAG = "GroupInfoFragment"

        fun newInstance(conversationId: String): GroupInfoFragment {
            val fragment = GroupInfoFragment()
            val b = Bundle().apply {
                putString(ARGS_CONVERSATION_ID, conversationId)
            }
            fragment.arguments = b
            return fragment
        }
    }

    private val groupViewModel by viewModels<GroupViewModel>()

    private val adapter by lazy {
        GroupInfoAdapter(self)
    }

    private val conversationId: String by lazy {
        requireArguments().getString(ARGS_CONVERSATION_ID)!!
    }

    private var observer: Observer<PagedList<ParticipantItem>>? = null
    private var curLiveData: LiveData<PagedList<ParticipantItem>>? = null

    private var conversation: Conversation? = null
    private val self: User = Session.getAccount()!!.toUser()
    private var selfParticipant: Participant? = null
    private var dialog: Dialog? = null
    private lateinit var headerBinding: ViewGroupInfoHeaderBinding

    private val binding by viewBinding(FragmentGroupInfoBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleView.leftIb.setOnClickListener {
            binding.searchEt.hideKeyboard()
            activity?.onBackPressed()
        }
        headerBinding = ViewGroupInfoHeaderBinding.inflate(LayoutInflater.from(context), binding.groupInfoRv, false)
        adapter.headerView = headerBinding.root
        adapter.setShowHeader(true, binding.groupInfoRv)
        binding.groupInfoRv.adapter = adapter
        adapter.setGroupInfoListener(
            object : GroupInfoAdapter.GroupInfoListener {
                override fun onClick(name: View, participant: ParticipantItem) {
                    val choices = mutableListOf<String>()
                    choices.add(getString(R.string.group_pop_menu_message, participant.fullName))
                    choices.add(getString(R.string.group_pop_menu_view, participant.fullName))
                    val role = selfParticipant?.role
                    val userRole = participant.role
                    if (role == ParticipantRole.OWNER.name) {
                        if (userRole == ParticipantRole.ADMIN.name) {
                            choices.add(getString(R.string.group_pop_menu_remove, participant.fullName))
                            choices.add(getString(R.string.group_pop_menu_dismiss_admin))
                        } else {
                            choices.add(getString(R.string.group_pop_menu_remove, participant.fullName))
                            choices.add(getString(R.string.group_pop_menu_make_admin))
                        }
                    } else if (role == ParticipantRole.ADMIN.name) {
                        if (userRole != ParticipantRole.OWNER.name && userRole != ParticipantRole.ADMIN.name) {
                            choices.add(getString(R.string.group_pop_menu_remove, participant.fullName))
                        }
                    }
                    alertDialogBuilder()
                        .setItems(choices.toTypedArray()) { _, which ->
                            when (which) {
                                0 -> {
                                    openChat(participant.toUser())
                                }
                                1 -> {
                                    UserBottomSheetDialogFragment.newInstance(participant.toUser(), conversationId)?.showNow(
                                        parentFragmentManager,
                                        UserBottomSheetDialogFragment.TAG
                                    )
                                }
                                2 -> {
                                    showConfirmDialog(
                                        getString(
                                            R.string.group_info_remove_tip,
                                            participant.fullName,
                                            conversation?.name
                                        ),
                                        TYPE_REMOVE,
                                        user = participant.toUser()
                                    )
                                }
                                3 -> {
                                    handleAdminRole(userRole, participant.toUser())
                                }
                            }
                        }.show()
                }

                override fun onLongClick(name: View, participant: ParticipantItem): Boolean {
                    val popMenu = PopupMenu(activity!!, name)
                    val c = conversation
                    if (c == null || !c.isGroupConversation()) {
                        return false
                    }
                    val role = selfParticipant?.role
                    val userRole = participant.role
                    if (role == ParticipantRole.OWNER.name) {
                        if (userRole == ParticipantRole.ADMIN.name) {
                            popMenu.menuInflater.inflate(R.menu.group_item_owner_dismiss, popMenu.menu)
                        } else {
                            popMenu.menuInflater.inflate(R.menu.group_item_owner, popMenu.menu)
                        }
                        popMenu.menu.findItem(R.id.remove).title = getString(R.string.group_pop_menu_remove, participant.fullName)
                    } else if (role == ParticipantRole.ADMIN.name) {
                        if (userRole == ParticipantRole.OWNER.name || userRole == ParticipantRole.ADMIN.name) {
                            popMenu.menuInflater.inflate(R.menu.group_item_simple, popMenu.menu)
                        } else {
                            popMenu.menuInflater.inflate(R.menu.group_item_admin, popMenu.menu)
                            popMenu.menu.findItem(R.id.remove).title =
                                getString(R.string.group_pop_menu_remove, participant.fullName)
                        }
                    } else {
                        popMenu.menuInflater.inflate(R.menu.group_item_simple, popMenu.menu)
                    }
                    popMenu.menu.findItem(R.id.message).title = getString(R.string.group_pop_menu_message, participant.fullName)
                    popMenu.menu.findItem(R.id.view).title = getString(R.string.group_pop_menu_view, participant.fullName)
                    popMenu.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.message -> {
                                openChat(participant.toUser())
                            }
                            R.id.view -> {
                                UserBottomSheetDialogFragment.newInstance(participant.toUser(), conversationId)?.showNow(parentFragmentManager, UserBottomSheetDialogFragment.TAG)
                            }
                            R.id.remove -> {
                                showConfirmDialog(
                                    getString(
                                        R.string.group_info_remove_tip,
                                        participant.fullName,
                                        conversation?.name
                                    ),
                                    TYPE_REMOVE,
                                    user = participant.toUser()
                                )
                            }
                            R.id.admin -> {
                                handleAdminRole(userRole, participant.toUser())
                            }
                        }
                        return@setOnMenuItemClickListener true
                    }
                    popMenu.show()
                    return true
                }
            }
        )

        filter()

        groupViewModel.getConversationById(conversationId).observe(
            viewLifecycleOwner,
            {
                it?.let {
                    conversation = it
                }
            }
        )

        binding.searchEt.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable) {
                    keyword = s.toString()
                }
            }
        )
    }

    private var keyword: String = ""
        set(value) {
            if (field == value) return

            field = value
            filter()
        }

    private fun filter() = lifecycleScope.launch {
        observer?.let {
            curLiveData?.removeObserver(it)
        }
        curLiveData = if (keyword.isNotBlank()) {
            groupViewModel.fuzzySearchGroupParticipants(conversationId, keyword)
        } else {
            groupViewModel.observeGroupParticipants(conversationId)
        }
        observer = Observer {
            refreshHeader(it.size)
            adapter.submitList(it)
        }
        observer?.let {
            curLiveData?.observe(viewLifecycleOwner, it)
        }
    }

    private fun refreshHeader(participantCount: Int) = lifecycleScope.launch {
        var isAdmin = false
        var inGroup = true
        if (selfParticipant == null) {
            selfParticipant = withContext(Dispatchers.IO) {
                groupViewModel.findParticipantById(conversationId, self.userId)
            }
        }
        if (selfParticipant == null) {
            inGroup = false
        } else {
            val role = selfParticipant!!.role
            isAdmin = role == ParticipantRole.OWNER.name || role == ParticipantRole.ADMIN.name
        }

        headerBinding.apply {
            addRl.setOnClickListener {
                modifyMember(true)
            }
            if (keyword.isBlank() && isAdmin && participantCount < MAX_USER) {
                addRl.visibility = View.VISIBLE
                inviteItem.visibility = View.VISIBLE
            } else {
                addRl.visibility = View.GONE
                inviteItem.visibility = View.GONE
            }
            groupInfoNotIn.isGone = inGroup
            inviteItem.setOnClickListener {
                InviteActivity.show(requireContext(), conversationId)
            }
        }
    }

    private fun handleAdminRole(userRole: String, user: User) = lifecycleScope.launch {
        showPb()
        if (userRole == ParticipantRole.ADMIN.name) {
            groupViewModel.modifyMember(conversationId, listOf(user), TYPE_DISMISS_ADMIN, "")
        } else {
            groupViewModel.modifyMember(conversationId, listOf(user), TYPE_MAKE_ADMIN, "ADMIN")
        }
        dialog?.dismiss()
    }

    private fun openChat(user: User) {
        context?.let { ctx -> ConversationActivity.show(ctx, null, user.userId) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dialog?.dismiss()
    }

    private fun showConfirmDialog(message: String, @Suppress("SameParameterValue") type: Int, user: User? = null) {
        alertDialogBuilder()
            .setMessage(message)
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                showPb()
                when (type) {
                    TYPE_REMOVE -> {
                        handleRemove(user!!)
                    }
                    TYPE_DELETE -> {
                        groupViewModel.deleteMessageByConversationId(conversationId)
                        startActivity(Intent(context, MainActivity::class.java))
                    }
                }
                dialog.dismiss()
            }.show()
    }

    private fun handleRemove(user: User) = lifecycleScope.launch {
        groupViewModel.modifyMember(conversationId, listOf(user), TYPE_REMOVE)
        dialog?.dismiss()
    }

    private fun showPb() {
        if (dialog == null) {
            dialog = indeterminateProgressDialog(message = getString(R.string.pb_dialog_message)).apply {
                setCancelable(false)
            }
        }
        dialog!!.show()
    }

    private fun modifyMember(@Suppress("SameParameterValue") isAdd: Boolean) = lifecycleScope.launch {
        val users = withContext(Dispatchers.IO) { groupViewModel.getGroupParticipants(conversationId) }
        val list = arrayListOf<User>().apply {
            addAll(users)
        }
        activity?.addFragment(
            this@GroupInfoFragment,
            GroupFragment.newInstance(if (isAdd) TYPE_ADD else TYPE_REMOVE, list, conversationId),
            GroupFragment.TAG
        )
    }
}
