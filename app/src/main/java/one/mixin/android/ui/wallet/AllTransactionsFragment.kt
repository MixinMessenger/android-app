package one.mixin.android.ui.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.paging.PagedList
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.Constants
import one.mixin.android.R
import one.mixin.android.databinding.FragmentAllTransactionsBinding
import one.mixin.android.extension.navigate
import one.mixin.android.extension.viewDestroyed
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.wallet.TransactionFragment.Companion.ARGS_SNAPSHOT
import one.mixin.android.ui.wallet.TransactionsFragment.Companion.ARGS_ASSET
import one.mixin.android.ui.wallet.adapter.OnSnapshotListener
import one.mixin.android.ui.wallet.adapter.SnapshotPagedAdapter
import one.mixin.android.vo.SnapshotItem
import one.mixin.android.vo.SnapshotType

@AndroidEntryPoint
class AllTransactionsFragment : BaseTransactionsFragment<PagedList<SnapshotItem>>(), OnSnapshotListener {

    companion object {
        const val TAG = "AllTransactionsFragment"
    }

    private var _binding: FragmentAllTransactionsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val adapter = SnapshotPagedAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentAllTransactionsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter.listener = this
        binding.apply {
            titleView.apply {
                leftIb.setOnClickListener { view.findNavController().navigateUp() }
                rightAnimator.setOnClickListener { showFiltersSheet() }
            }
            transactionsRv.itemAnimator = null
            transactionsRv.adapter = adapter
            transactionsRv.addItemDecoration(StickyRecyclerHeadersDecoration(adapter))
        }
        dataObserver = Observer { pagedList ->
            if (pagedList != null && pagedList.isNotEmpty()) {
                showEmpty(false)
                val opponentIds = pagedList.filter {
                    it?.opponentId != null
                }.map {
                    it.opponentId!!
                }
                walletViewModel.checkAndRefreshUsers(opponentIds)
            } else {
                showEmpty(true)
            }
            adapter.submitList(pagedList)

            if (!refreshedSnapshots) {
                walletViewModel.refreshSnapshots()
                refreshedSnapshots = true
            }
        }
        bindLiveData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun <T> onNormalItemClick(item: T) {
        lifecycleScope.launch {
            val snapshot = item as SnapshotItem
            val a = withContext(Dispatchers.IO) {
                walletViewModel.simpleAssetItem(snapshot.assetId)
            }
            a?.let {
                if (viewDestroyed()) return@launch

                view?.navigate(
                    R.id.action_all_transactions_fragment_to_transaction_fragment,
                    Bundle().apply {
                        putParcelable(ARGS_SNAPSHOT, snapshot)
                        putParcelable(ARGS_ASSET, it)
                    }
                )
            }
        }
    }

    override fun onUserClick(userId: String) {
        lifecycleScope.launch {
            val user = withContext(Dispatchers.IO) {
                walletViewModel.getUser(userId)
            } ?: return@launch
            val f = UserBottomSheetDialogFragment.newInstance(user)
            f?.showUserTransactionAction = {
                view?.navigate(
                    R.id.action_all_transactions_to_user_transactions,
                    Bundle().apply { putString(Constants.ARGS_USER_ID, userId) }
                )
            }
            f?.show(parentFragmentManager, UserBottomSheetDialogFragment.TAG)
        }
    }

    override fun refreshSnapshots() {
        walletViewModel.refreshSnapshots(offset = refreshOffset)
    }

    override fun onApplyClick() {
        initialLoadKey = null
        bindLiveData()
        filtersSheet.dismiss()
    }

    private fun bindLiveData() {
        val orderByAmount = currentOrder == R.id.sort_amount
        when (currentType) {
            R.id.filters_radio_all -> {
                bindLiveData(walletViewModel.allSnapshots(initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_transfer -> {
                bindLiveData(
                    walletViewModel.allSnapshots(
                        SnapshotType.transfer.name,
                        SnapshotType.pending.name,
                        initialLoadKey = initialLoadKey,
                        orderByAmount = orderByAmount
                    )
                )
            }
            R.id.filters_radio_deposit -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.deposit.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_withdrawal -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.withdrawal.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_fee -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.fee.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_rebate -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.rebate.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
            }
            R.id.filters_radio_raw -> {
                bindLiveData(walletViewModel.allSnapshots(SnapshotType.raw.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
            }
        }
    }

    private fun showEmpty(show: Boolean) {
        binding.apply {
            if (show) {
                if (empty.root.visibility == GONE) {
                    empty.root.visibility = VISIBLE
                }
                if (transactionsRv.visibility == VISIBLE) {
                    transactionsRv.visibility = GONE
                }
            } else {
                if (empty.root.visibility == VISIBLE) {
                    empty.root.visibility = GONE
                }
                if (transactionsRv.visibility == GONE) {
                    transactionsRv.visibility = VISIBLE
                }
            }
        }
    }
}
