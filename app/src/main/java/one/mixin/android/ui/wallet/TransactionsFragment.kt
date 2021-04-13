package one.mixin.android.ui.wallet

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagedList
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.Constants
import one.mixin.android.R
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.databinding.FragmentTransactionsBinding
import one.mixin.android.databinding.ViewBadgeCircleImageBinding
import one.mixin.android.databinding.ViewTransactionsFragmentHeaderBinding
import one.mixin.android.databinding.ViewWalletTransactionsBottomBinding
import one.mixin.android.databinding.ViewWalletTransactionsSendBottomBinding
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.loadImage
import one.mixin.android.extension.mainThreadDelayed
import one.mixin.android.extension.navigate
import one.mixin.android.extension.numberFormat
import one.mixin.android.extension.numberFormat2
import one.mixin.android.extension.putString
import one.mixin.android.extension.screenHeight
import one.mixin.android.extension.toast
import one.mixin.android.extension.viewDestroyed
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.conversation.TransferFragment
import one.mixin.android.ui.wallet.adapter.OnSnapshotListener
import one.mixin.android.ui.wallet.adapter.TransactionsAdapter
import one.mixin.android.util.ErrorHandler
import one.mixin.android.vo.AssetItem
import one.mixin.android.vo.Fiats
import one.mixin.android.vo.SnapshotItem
import one.mixin.android.vo.SnapshotType
import one.mixin.android.vo.differentProcess
import one.mixin.android.vo.toAssetItem
import one.mixin.android.widget.BottomSheet

@AndroidEntryPoint
class TransactionsFragment : BaseTransactionsFragment<PagedList<SnapshotItem>>(), OnSnapshotListener {

    companion object {
        const val TAG = "TransactionsFragment"
        const val ARGS_ASSET = "args_asset"
    }

    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var _headBinding: ViewTransactionsFragmentHeaderBinding? = null
    private val headBinding get() = requireNotNull(_headBinding)
    private var _bottomBinding: ViewWalletTransactionsBottomBinding? = null
    private val bottomBinding get() = requireNotNull(_bottomBinding)
    private var _bottomSendBinding: ViewWalletTransactionsSendBottomBinding? = null
    private val bottomSendBinding get() = requireNotNull(_bottomSendBinding)

    private val adapter = TransactionsAdapter()
    lateinit var asset: AssetItem

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTransactionsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        asset = requireArguments().getParcelable(ARGS_ASSET)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleView.apply {
            titleTv.text = asset.name
            leftIb.setOnClickListener { activity?.onBackPressed() }
            rightAnimator.setOnClickListener {
                showBottom()
            }
        }

        _headBinding = ViewTransactionsFragmentHeaderBinding.bind(layoutInflater.inflate(R.layout.view_transactions_fragment_header, binding.transactionsRv, false))
        headBinding.apply {
            groupInfoMemberTitleSort.setOnClickListener {
                showFiltersSheet()
            }
            topRl.setOnClickListener {
                AssetKeyBottomSheetDialogFragment.newInstance(asset)
                    .showNow(parentFragmentManager, AssetKeyBottomSheetDialogFragment.TAG)
            }
            updateHeader(asset)
            sendTv.setOnClickListener {
                showSendBottom()
            }
            receiveTv.setOnClickListener {
                asset.differentProcess(
                    {
                        view.navigate(
                            R.id.action_transactions_to_deposit_public_key,
                            Bundle().apply { putParcelable(ARGS_ASSET, asset) }
                        )
                    },
                    {
                        view.navigate(
                            R.id.action_transactions_to_deposit_account,
                            Bundle().apply { putParcelable(ARGS_ASSET, asset) }
                        )
                    },
                    {
                        toast(getString(R.string.error_bad_data, ErrorHandler.BAD_DATA))
                    }
                )
            }

            adapter.listener = this@TransactionsFragment
            adapter.headerView = headBinding.root
            adapter.setShowHeader(true, binding.transactionsRv)
            binding.transactionsRv.itemAnimator = null
            binding.transactionsRv.addItemDecoration(StickyRecyclerHeadersDecoration(adapter))
            binding.transactionsRv.adapter = adapter
            root.post {
                if (viewDestroyed()) return@post

                bottomRl.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = requireContext().screenHeight() - binding.titleView.height - topLl.height - groupInfoMemberTitleLayout.height
                }
            }
        }

        dataObserver = Observer { pagedList ->
            if (pagedList != null && pagedList.isNotEmpty()) {
                updateHeaderBottomLayout(false)
                val opponentIds = pagedList.filter {
                    it?.opponentId != null
                }.map {
                    it.opponentId!!
                }
                walletViewModel.checkAndRefreshUsers(opponentIds)
            } else {
                updateHeaderBottomLayout(true)
            }
            adapter.submitList(pagedList)

            if (!refreshedSnapshots) {
                walletViewModel.refreshSnapshots(asset.assetId)
                refreshedSnapshots = true
            }
        }
        bindLiveData()
        walletViewModel.assetItem(asset.assetId).observe(
            viewLifecycleOwner,
            { assetItem ->
                assetItem?.let {
                    asset = it
                    updateHeader(it)
                }
            }
        )

        refreshPendingDeposits(asset)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _headBinding = null
        _bottomBinding = null
        _bottomSendBinding = null
    }

    @SuppressLint("SetTextI18n")
    private fun updateHeader(asset: AssetItem) {
        headBinding.apply {
            balance.text = try {
                if (asset.balance.toFloat() == 0f) {
                    "0.00"
                } else {
                    asset.balance.numberFormat()
                }
            } catch (ignored: NumberFormatException) {
                asset.balance.numberFormat()
            }
            symbolTv.text = asset.symbol
            balanceAs.text = try {
                if (asset.fiat().toFloat() == 0f) {
                    "≈ ${Fiats.getSymbol()}0.00"
                } else {
                    "≈ ${Fiats.getSymbol()}${asset.fiat().numberFormat2()}"
                }
            } catch (ignored: NumberFormatException) {
                "≈ ${Fiats.getSymbol()}${asset.fiat().numberFormat2()}"
            }
            ViewBadgeCircleImageBinding.bind(avatar).apply {
                bg.loadImage(asset.iconUrl, R.drawable.ic_avatar_place_holder)
                badge.loadImage(asset.chainIconUrl, R.drawable.ic_avatar_place_holder)
            }
        }
    }

    private fun refreshPendingDeposits(asset: AssetItem) {
        lifecycleScope.launch {
            if (asset.destination.isNotEmpty()) {
                walletViewModel.refreshAsset(asset.assetId)
                walletViewModel.refreshPendingDeposits(asset)
            } else {
                headBinding.apply {
                    receiveTv.visibility = GONE
                    receiveProgress.visibility = VISIBLE
                    handleMixinResponse(
                        invokeNetwork = {
                            walletViewModel.getAsset(asset.assetId)
                        },
                        successBlock = { response ->
                            receiveTv.visibility = VISIBLE
                            receiveProgress.visibility = GONE
                            response.data?.let { asset ->
                                walletViewModel.upsetAsset(asset)
                                asset.toAssetItem().let { assetItem ->
                                    this@TransactionsFragment.asset = assetItem
                                    refreshPendingDeposits(assetItem)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showBottom() {
        val builder = BottomSheet.Builder(requireActivity())
        _bottomBinding = ViewWalletTransactionsBottomBinding.bind(View.inflate(ContextThemeWrapper(requireActivity(), R.style.Custom), R.layout.view_wallet_transactions_bottom, null))
        builder.setCustomView(bottomBinding.root)
        val bottomSheet = builder.create()
        bottomBinding.apply {
            hide.setText(if (asset.hidden == true) R.string.wallet_transactions_show else R.string.wallet_transactions_hide)
            hide.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    walletViewModel.updateAssetHidden(asset.assetId, asset.hidden != true)
                }
                bottomSheet.dismiss()
                activity?.mainThreadDelayed({ activity?.onBackPressed() }, 200)
            }
            cancel.setOnClickListener { bottomSheet.dismiss() }
        }

        bottomSheet.show()
    }

    private fun showSendBottom() {
        val builder = BottomSheet.Builder(requireActivity())
        _bottomSendBinding = ViewWalletTransactionsSendBottomBinding.bind(View.inflate(ContextThemeWrapper(requireActivity(), R.style.Custom), R.layout.view_wallet_transactions_send_bottom, null))
        builder.setCustomView(bottomSendBinding.root)
        val bottomSheet = builder.create()
        bottomSendBinding.apply {
            contact.setOnClickListener {
                bottomSheet.dismiss()
                defaultSharedPreferences.putString(TransferFragment.ASSET_PREFERENCE, asset.assetId)
                this@TransactionsFragment.view?.navigate(R.id.action_transactions_to_single_friend_select)
            }
            address.setOnClickListener {
                bottomSheet.dismiss()
                this@TransactionsFragment.view?.navigate(
                    R.id.action_transactions_to_address_management,
                    Bundle().apply {
                        putParcelable(ARGS_ASSET, asset)
                    }
                )
            }
            sendCancel.setOnClickListener { bottomSheet.dismiss() }
        }

        bottomSheet.show()
    }

    override fun <T> onNormalItemClick(item: T) {
        view?.navigate(
            R.id.action_transactions_fragment_to_transaction_fragment,
            Bundle().apply {
                putParcelable(TransactionFragment.ARGS_SNAPSHOT, item as SnapshotItem)
                putParcelable(ARGS_ASSET, asset)
            }
        )
    }

    override fun onUserClick(userId: String) {
        lifecycleScope.launch {
            val user = withContext(Dispatchers.IO) {
                walletViewModel.getUser(userId)
            } ?: return@launch

            val f = UserBottomSheetDialogFragment.newInstance(user)
            f?.showUserTransactionAction = {
                view?.navigate(
                    R.id.action_transactions_to_user_transactions,
                    Bundle().apply { putString(Constants.ARGS_USER_ID, userId) }
                )
            }
            f?.show(parentFragmentManager, UserBottomSheetDialogFragment.TAG)
        }
    }

    override fun refreshSnapshots() {
        walletViewModel.refreshSnapshots(asset.assetId, offset = refreshOffset)
    }

    override fun onApplyClick() {
        bindLiveData()
        filtersSheet.dismiss()
    }

    private fun bindLiveData() {
        val orderByAmount = currentOrder == R.id.sort_amount
        headBinding.apply {
            when (currentType) {
                R.id.filters_radio_all -> {
                    bindLiveData(walletViewModel.snapshotsFromDb(asset.assetId, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount))
                    groupInfoMemberTitle.setText(R.string.wallet_transactions_title)
                    walletTransactionsEmpty.setText(R.string.wallet_transactions_empty)
                }
                R.id.filters_radio_transfer -> {
                    bindLiveData(
                        walletViewModel.snapshotsFromDb(
                            asset.assetId,
                            SnapshotType.transfer.name,
                            SnapshotType.pending.name,
                            initialLoadKey = initialLoadKey,
                            orderByAmount = orderByAmount
                        )
                    )
                    groupInfoMemberTitle.setText(R.string.filters_transfer)
                    walletTransactionsEmpty.setText(R.string.wallet_transactions_empty)
                }
                R.id.filters_radio_deposit -> {
                    bindLiveData(
                        walletViewModel.snapshotsFromDb(
                            asset.assetId,
                            SnapshotType.deposit.name,
                            initialLoadKey = initialLoadKey,
                            orderByAmount = orderByAmount
                        )
                    )
                    groupInfoMemberTitle.setText(R.string.filters_deposit)
                    walletTransactionsEmpty.setText(R.string.wallet_deposits_empty)
                }
                R.id.filters_radio_withdrawal -> {
                    bindLiveData(
                        walletViewModel.snapshotsFromDb(
                            asset.assetId,
                            SnapshotType.withdrawal.name,
                            initialLoadKey = initialLoadKey,
                            orderByAmount = orderByAmount
                        )
                    )
                    groupInfoMemberTitle.setText(R.string.filters_withdrawal)
                    walletTransactionsEmpty.setText(R.string.wallet_withdrawals_empty)
                }
                R.id.filters_radio_fee -> {
                    bindLiveData(
                        walletViewModel.snapshotsFromDb(asset.assetId, SnapshotType.fee.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount)
                    )
                    groupInfoMemberTitle.setText(R.string.filters_fee)
                    walletTransactionsEmpty.setText(R.string.wallet_fees_empty)
                }
                R.id.filters_radio_rebate -> {
                    bindLiveData(
                        walletViewModel.snapshotsFromDb(asset.assetId, SnapshotType.rebate.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount)
                    )
                    groupInfoMemberTitle.setText(R.string.filters_rebate)
                    walletTransactionsEmpty.setText(R.string.wallet_rebates_empty)
                }
                R.id.filters_radio_raw -> {
                    bindLiveData(
                        walletViewModel.snapshotsFromDb(asset.assetId, SnapshotType.raw.name, initialLoadKey = initialLoadKey, orderByAmount = orderByAmount)
                    )
                    groupInfoMemberTitle.setText(R.string.filters_raw)
                    walletTransactionsEmpty.setText(R.string.wallet_raw_empty)
                }
            }
        }
    }

    private fun updateHeaderBottomLayout(show: Boolean) {
        headBinding.bottomRl.isVisible = show
    }
}
