package one.mixin.android.ui.group

import android.app.Dialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_group.*
import kotlinx.android.synthetic.main.view_title.view.*
import one.mixin.android.R
import one.mixin.android.RxBus
import one.mixin.android.event.ConversationEvent
import one.mixin.android.extension.addFragment
import one.mixin.android.extension.hideKeyboard
import one.mixin.android.job.ConversationJob.Companion.TYPE_ADD
import one.mixin.android.job.ConversationJob.Companion.TYPE_CREATE
import one.mixin.android.job.ConversationJob.Companion.TYPE_REMOVE
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.common.itemdecoration.SpaceItemDecoration
import one.mixin.android.ui.group.adapter.GroupFriendAdapter
import one.mixin.android.vo.User
import org.jetbrains.anko.support.v4.indeterminateProgressDialog
import org.jetbrains.anko.textColor
import javax.inject.Inject

class GroupFragment : BaseFragment() {

    companion object {
        const val TAG = "GroupFragment"

        const val ARGS_FROM = "args_from"
        const val ARGS_ALREADY_USERS = "args_already_users"
        const val ARGS_CONVERSATION_ID = "args_conversation_id"

        const val MAX_USER = 256

        fun newInstance(
            from: Int? = 0,
            alreadyUsers: ArrayList<User>? = null,
            conversationId: String? = null
        ): GroupFragment {
            val f = GroupFragment()
            val b = Bundle()
            from?.let {
                b.putInt(ARGS_FROM, it)
            }
            alreadyUsers?.let {
                b.putParcelableArrayList(ARGS_ALREADY_USERS, it)
            }
            conversationId?.let {
                b.putString(ARGS_CONVERSATION_ID, conversationId)
            }
            f.arguments = b
            return f
        }
    }

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private val groupViewModel: GroupViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(GroupViewModel::class.java)
    }

    private val from: Int by lazy {
        arguments!!.getInt(ARGS_FROM)
    }

    private val alreadyUsers: ArrayList<User>? by lazy {
        arguments!!.getParcelableArrayList<User>(ARGS_ALREADY_USERS)
    }

    private val conversationId: String? by lazy {
        arguments!!.getString(ARGS_CONVERSATION_ID)
    }

    private val groupFriendAdapter: GroupFriendAdapter by lazy {
        GroupFriendAdapter().apply { isAdd = from == TYPE_ADD }
    }

    private var users: List<User>? = null
    private var checkedUsers: MutableList<User> = mutableListOf()
    private var disposable: Disposable? = null
    private var dialog: Dialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.fragment_group, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        title_view.left_ib.setOnClickListener {
            activity?.onBackPressed()
        }
        if (from == TYPE_ADD || from == TYPE_REMOVE) {
            title_view.right_tv.text = getString(R.string.done)
            updateTitle(alreadyUsers?.size ?: 0)
        } else if (from == TYPE_CREATE) {
            updateTitle(0)
        }
        title_view.right_animator.setOnClickListener {
            search_et.hideKeyboard()
            if (from == TYPE_ADD || from == TYPE_REMOVE) {
                groupViewModel.modifyGroupMembers(conversationId!!, checkedUsers, from)
                if (dialog == null) {
                    val title = if (from == TYPE_ADD) R.string.group_adding else R.string.group_removing
                    dialog = indeterminateProgressDialog(message = R.string.pb_dialog_message, title = title).apply {
                        setCancelable(false)
                    }
                }
                dialog!!.show()
            } else {
                activity?.addFragment(this@GroupFragment,
                    NewGroupFragment.newInstance(ArrayList(checkedUsers)), NewGroupFragment.TAG)
            }
        }
        title_view.right_animator.isEnabled = false
        groupFriendAdapter.setGroupFriendListener(mGroupFriendListener)
        alreadyUsers?.let {
            val alreadyUserIds = mutableListOf<String>()
            it.mapTo(alreadyUserIds) { it.userId }
            groupFriendAdapter.alreadyUserIds = alreadyUserIds
        }
        group_rv.adapter = groupFriendAdapter
        group_rv.addItemDecoration(SpaceItemDecoration())
        group_rv.addItemDecoration(StickyRecyclerHeadersDecoration(groupFriendAdapter))

        if (from == TYPE_ADD || from == TYPE_CREATE) {
            groupViewModel.getFriends().observe(this, Observer {
                users = it
                groupFriendAdapter.setData(it, true)
            })
        } else {
            users = alreadyUsers
            groupFriendAdapter.setData(alreadyUsers, true)
        }
        search_et.addTextChangedListener(mWatcher)

        search_et.isFocusableInTouchMode = false
        search_et.isFocusable = false
        search_et.post {
            search_et?.let {
                it.isFocusableInTouchMode = true
                it.isFocusable = true
            }
        }

        if (disposable == null) {
            disposable = RxBus.listen(ConversationEvent::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (it.type == TYPE_ADD || it.type == TYPE_REMOVE) {
                        dialog?.dismiss()
                        if (it.isSuccess) {
                            activity?.supportFragmentManager?.popBackStackImmediate()
                        }
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable?.dispose()
        disposable = null
        dialog?.dismiss()
    }

    private fun updateTitle(size: Int) {
        title_view.setSubTitle(when (from) {
            TYPE_REMOVE -> getString(R.string.group_info_remove_member)
            else -> getString(R.string.group_add)
        }, "$size/$MAX_USER")
    }

    private val mGroupFriendListener = object : GroupFriendAdapter.GroupFriendListener {
        override fun onItemClick(user: User, checked: Boolean) {
            if (checked) {
                checkedUsers.add(user)
            } else {
                checkedUsers.remove(user)
            }
            val existCount = if (alreadyUsers == null) 0 else alreadyUsers!!.size
            updateTitle(if (from == TYPE_ADD || from == TYPE_CREATE)
                checkedUsers.size + existCount else existCount - checkedUsers.size)

            if (checkedUsers.isEmpty()) {
                title_view.right_tv.textColor = resources.getColor(R.color.text_gray, null)
                title_view.right_animator.isEnabled = false
            } else {
                title_view.right_tv.textColor = resources.getColor(R.color.colorBlue, null)
                title_view.right_animator.isEnabled = true
            }
        }
    }

    private val mWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        }

        override fun afterTextChanged(s: Editable?) {
            groupFriendAdapter.setData(users?.filter { it.fullName!!.contains(s.toString(), true) },
                s.isNullOrEmpty())
        }
    }
}