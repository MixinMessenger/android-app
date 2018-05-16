package one.mixin.android.ui.contacts

import android.Manifest
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tbruyelle.rxpermissions2.RxPermissions
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration
import com.uber.autodispose.kotlin.autoDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import ir.mirrajabi.rxcontacts.Contact
import ir.mirrajabi.rxcontacts.RxContacts
import kotlinx.android.synthetic.main.fragment_contacts.*
import kotlinx.android.synthetic.main.view_title.view.*
import one.mixin.android.R
import one.mixin.android.extension.addFragment
import one.mixin.android.extension.openPermissionSetting
import one.mixin.android.job.MixinJobManager
import one.mixin.android.job.RefreshContactJob
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.common.itemdecoration.SpaceItemDecoration
import one.mixin.android.ui.conversation.ConversationActivity
import one.mixin.android.ui.group.GroupActivity
import one.mixin.android.ui.setting.SettingActivity
import one.mixin.android.vo.User
import java.util.*
import javax.inject.Inject

class ContactsFragment : BaseFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject
    lateinit var jobManager: MixinJobManager

    private val contactsViewModel: ContactViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(ContactViewModel::class.java)
    }

    private val contactAdapter: ContactsAdapter by lazy {
        ContactsAdapter(context!!, Collections.emptyList(), 0)
    }

    companion object {
        val TAG = "ContactsFragment"

        fun newInstance() = ContactsFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.fragment_contacts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contact_recycler_view.adapter = contactAdapter
        contact_recycler_view.setHasFixedSize(true)
        contact_recycler_view.layoutManager = LinearLayoutManager(context)
        contact_recycler_view.addItemDecoration(SpaceItemDecoration(1))
        contact_recycler_view.addItemDecoration(StickyRecyclerHeadersDecoration(contactAdapter))
        val header = LayoutInflater.from(context).inflate(R.layout.view_contact_header, contact_recycler_view, false)
        contactAdapter.setHeader(header)
        if (!hasContactPermission()) {
            val footer = LayoutInflater.from(context)
                .inflate(R.layout.view_contact_list_empty, contact_recycler_view, false)
            contactAdapter.setFooter(footer)
        }
        contactAdapter.setContactListener(mContactListener)
        title_view.left_ib.setOnClickListener { activity?.onBackPressed() }
        title_view.right_animator.setOnClickListener { SettingActivity.show(context!!) }

        if (hasContactPermission()) {
            fetchContacts()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        contactsViewModel.getFriends().observe(this, Observer { users ->
            if (users != null) {
                val mutableList = mutableListOf<User>()
                mutableList.addAll(contactAdapter.users)
                contactAdapter.users.map { item ->
                    for (u in users) {
                        if (u.userId == item.userId) {
                            mutableList.remove(item)
                        }
                    }
                }
                mutableList.addAll(0, users)
                contactAdapter.friendSize = users.size
                contactAdapter.users = mutableList
            } else {
                if (!hasContactPermission()) {
                    contactAdapter.users = Collections.emptyList()
                }
            }
            contactAdapter.notifyDataSetChanged()
        })
        contactsViewModel.findSelf().observe(this, Observer { self ->
            if (self != null) {
                contactAdapter.setSelf(self)
                contactAdapter.notifyDataSetChanged()
            }
        })
    }

    private fun hasContactPermission() =
        context!!.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun fetchContacts() {
        RxContacts.fetch(context!!)
            .toSortedList(Contact::compareTo)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .autoDisposable(scopeProvider)
            .subscribe({ contacts ->
                val mutableList = mutableListOf<User>()
                for (item in contacts) {
                    item.phoneNumbers.mapTo(mutableList) { User("",
                        "", "contact", item.displayName,
                        "", it, false, "", null)
                    }
                }
                mutableList.addAll(0, contactAdapter.users)
                contactAdapter.users = mutableList
                contactAdapter.notifyDataSetChanged()
            }, { _ -> })
    }

    private val mContactListener: ContactsAdapter.ContactListener = object : ContactsAdapter.ContactListener {
        override fun onHeaderRl() {
            activity?.addFragment(this@ContactsFragment, ProfileFragment.newInstance(), ProfileFragment.TAG)
        }

        override fun onNewGroup() {
            GroupActivity.show(context!!)
        }

        override fun onAddContact() {
            activity?.addFragment(this@ContactsFragment, AddPeopleFragment.newInstance(), AddPeopleFragment.TAG)
        }

        override fun onEmptyRl() {
            RxPermissions(activity!!)
                .request(Manifest.permission.READ_CONTACTS)
                .subscribe { granted ->
                    if (granted) {
                        contactAdapter.removeFooter()
                        fetchContacts()
                        jobManager.addJobInBackground(RefreshContactJob())
                    } else {
                        context?.openPermissionSetting()
                    }
                }
        }

        override fun onFriendItem(user: User) {
            context?.let { ctx -> ConversationActivity.show(ctx, null, user) }
        }

        override fun onContactItem(user: User) {
            ContactBottomSheetDialog.newInstance(user).show(fragmentManager, ContactBottomSheetDialog.TAG)
        }
    }
}