package one.mixin.android.ui.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration
import kotlinx.android.synthetic.main.fragment_all_transactions.*
import kotlinx.android.synthetic.main.view_title.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import one.mixin.android.R
import one.mixin.android.job.RefreshSnapshotsJob
import one.mixin.android.job.RefreshUserJob
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.wallet.TransactionFragment.Companion.ARGS_SNAPSHOT
import one.mixin.android.ui.wallet.TransactionsFragment.Companion.ARGS_ASSET
import one.mixin.android.ui.wallet.adapter.OnSnapshotListener
import one.mixin.android.ui.wallet.adapter.SnapshotPagedAdapter
import one.mixin.android.vo.SnapshotItem
import one.mixin.android.vo.SnapshotType

class AllTransactionsFragment : BaseTransactionsFragment<PagedList<SnapshotItem>>(), OnSnapshotListener {

    companion object {
        const val TAG = "AllTransactionsFragment"

        fun newInstance() = AllTransactionsFragment()
    }

    private val adapter = SnapshotPagedAdapter()
    private var initialLoadKey: Int? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        layoutInflater.inflate(R.layout.fragment_all_transactions, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        title_view.left_ib.setOnClickListener { view?.findNavController()?.navigateUp() }
        title_view.right_animator.setOnClickListener { showFiltersSheet() }
        adapter.listener = this
        transaction_rv.itemAnimator = null
        transaction_rv.adapter = adapter
        transaction_rv.addItemDecoration(StickyRecyclerHeadersDecoration(adapter))
        dataObserver = Observer { pagedList ->
            if (pagedList != null && pagedList.isNotEmpty()) {
                showEmpty(false)
                adapter.submitList(pagedList)
                GlobalScope.launch(Dispatchers.IO) {
                    for (s in pagedList) {
                        s?.opponentId?.let {
                            val u = walletViewModel.getUserById(it)
                            if (u == null) {
                                jobManager.addJobInBackground(RefreshUserJob(arrayListOf(it)))
                            }
                        }
                    }
                }
            } else {
                showEmpty(true)
            }
        }
        bindLiveData(walletViewModel.allSnapshots(initialLoadKey = initialLoadKey, orderByAmount = currentOrder == R.id.sort_amount))
        jobManager.addJobInBackground(RefreshSnapshotsJob())
    }

    override fun onStop() {
        super.onStop()
        initialLoadKey = (transaction_rv.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()
    }

    override fun <T> onNormalItemClick(item: T) {
        GlobalScope.launch(Dispatchers.IO) {
            val snapshot = item as SnapshotItem
            val a = walletViewModel.simpleAssetItem(snapshot.assetId)
            a?.let {
                if (!isAdded) return@launch

                view?.findNavController()?.navigate(R.id.action_all_transactions_fragment_to_transaction_fragment,
                    Bundle().apply {
                        putParcelable(ARGS_SNAPSHOT, snapshot)
                        putParcelable(ARGS_ASSET, it)
                    })
            }
        }
    }

    override fun onUserClick(userId: String) {
        GlobalScope.launch(Dispatchers.IO) {
            walletViewModel.getUser(userId)?.let {
                UserBottomSheetDialogFragment.newInstance(it).show(requireFragmentManager(), UserBottomSheetDialogFragment.TAG)
            }
        }
    }

    override fun onApplyClick() {
        val orderByAmount = currentOrder == R.id.sort_amount
        when (currentType) {
            R.id.filters_radio_all -> {
                bindLiveData(walletViewModel.allSnapshots(orderByAmount = orderByAmount))
            }
            R.id.filters_radio_transfer -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.transfer.name, SnapshotType.pending.name, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_deposit -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.deposit.name, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_withdrawal -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.withdrawal.name, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_fee -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.fee.name, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_rebate -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.rebate.name, orderByAmount = orderByAmount))
            }
        }
        filtersSheet.dismiss()
    }

    private fun showEmpty(show: Boolean) {
        if (show) {
            if (empty_rl.visibility == GONE) {
                empty_rl.visibility = VISIBLE
            }
            if (transaction_rv.visibility == VISIBLE) {
                transaction_rv.visibility = GONE
            }
        } else {
            if (empty_rl.visibility == VISIBLE) {
                empty_rl.visibility = GONE
            }
            if (transaction_rv.visibility == GONE) {
                transaction_rv.visibility = VISIBLE
            }
        }
    }
}