package one.mixin.android.ui.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import one.mixin.android.R
import one.mixin.android.databinding.FragmentHiddenAssetsBinding
import one.mixin.android.databinding.ViewTitleBinding
import one.mixin.android.extension.navigate
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.common.recyclerview.HeaderAdapter
import one.mixin.android.ui.wallet.adapter.AssetItemCallback
import one.mixin.android.ui.wallet.adapter.WalletAssetAdapter
import one.mixin.android.vo.AssetItem

@AndroidEntryPoint
class HiddenAssetsFragment : BaseFragment(), HeaderAdapter.OnItemListener {

    companion object {
        val TAG = HiddenAssetsFragment::class.java.simpleName
        fun newInstance() = HiddenAssetsFragment()

        const val POS_ASSET = 0
        const val POS_EMPTY = 1
    }

    private var _binding: FragmentHiddenAssetsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var _titleBinding: ViewTitleBinding? = null
    private val titleBinding get() = requireNotNull(_titleBinding)

    private val walletViewModel by viewModels<WalletViewModel>()

    private var assets: List<AssetItem> = listOf()
    private val assetsAdapter by lazy { WalletAssetAdapter(true) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentHiddenAssetsBinding.inflate(layoutInflater, container, false)
        _titleBinding = ViewTitleBinding.bind(binding.titleView)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleBinding.leftIb.setOnClickListener { activity?.onBackPressed() }
        assetsAdapter.onItemListener = this
        binding.apply {
            ItemTouchHelper(
                AssetItemCallback(
                    object : AssetItemCallback.ItemCallbackListener {
                        override fun onSwiped(viewHolder: RecyclerView.ViewHolder) {
                            val hiddenPos = viewHolder.absoluteAdapterPosition
                            val asset = assetsAdapter.data!![assetsAdapter.getPosition(hiddenPos)]
                            val deleteItem = assetsAdapter.removeItem(hiddenPos)!!
                            lifecycleScope.launch {
                                walletViewModel.updateAssetHidden(asset.assetId, false)
                                val anchorView = assetsRv

                                Snackbar.make(anchorView, getString(R.string.wallet_already_shown, asset.symbol), Snackbar.LENGTH_LONG)
                                    .setAction(R.string.undo_capital) {
                                        assetsAdapter.restoreItem(deleteItem, hiddenPos)
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            walletViewModel.updateAssetHidden(asset.assetId, true)
                                        }
                                    }.setActionTextColor(ContextCompat.getColor(requireContext(), R.color.wallet_blue)).apply {
                                        this.view.setBackgroundResource(R.color.call_btn_icon_checked)
                                        (this.view.findViewById(R.id.snackbar_text) as TextView)
                                            .setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                                    }.show()
                            }
                        }
                    }
                )
            ).apply { attachToRecyclerView(assetsRv) }
            assetsRv.adapter = assetsAdapter

            walletViewModel.hiddenAssets().observe(
                viewLifecycleOwner,
                {
                    if (it != null && it.isNotEmpty()) {
                        assetsVa.displayedChild = POS_ASSET
                        assets = it
                        assetsAdapter.setAssetList(it)
                    } else {
                        assetsVa.displayedChild = POS_EMPTY
                    }
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _titleBinding = null
    }

    override fun <T> onNormalItemClick(item: T) {
        item as AssetItem
        view?.navigate(
            R.id.action_hidden_assets_to_transactions,
            Bundle().apply { putParcelable(TransactionsFragment.ARGS_ASSET, item) }
        )
    }
}
