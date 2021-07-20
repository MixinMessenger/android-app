package one.mixin.android.ui.landing

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.Constants.Account.PREF_FTS4_UPGRADE
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.databinding.FragmentUpgradeBinding
import one.mixin.android.db.runInTransaction
import one.mixin.android.extension.withArgs
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.home.MainActivity
import one.mixin.android.util.MessageFts4Helper
import one.mixin.android.util.PropertyHelper
import one.mixin.android.util.viewBinding

@AndroidEntryPoint
class UpgradeFragment : BaseFragment(R.layout.fragment_upgrade) {

    companion object {
        const val TAG: String = "UpgradeFragment"

        const val ARGS_TYPE = "args_type"
        const val TYPE_DB = 0
        const val TYPE_FTS = 1

        fun newInstance(type: Int) = UpgradeFragment().withArgs {
            putInt(ARGS_TYPE, type)
        }
    }

    private val viewModel by viewModels<MobileViewModel>()
    private val binding by viewBinding(FragmentUpgradeBinding::bind)

    private val type: Int by lazy { requireArguments().getInt(ARGS_TYPE) }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MixinApplication.get().onlining.set(true)
        if (type == TYPE_FTS) {
            lifecycleScope.launch {
                val done = MessageFts4Helper.syncMessageFts4(preProcess = true) { progress ->
                    binding.pb.progress = progress
                    binding.progressTv.text = "$progress%"
                }
                if (!done) {
                    viewModel.startSyncFts4Job()
                }
                PropertyHelper.updateKeyValue(requireContext(), PREF_FTS4_UPGRADE, true.toString())
                MainActivity.show(requireContext())
                activity?.finish()
            }
        } else {
            lifecycleScope.launch {
                binding.pb.isIndeterminate = true
                withContext(Dispatchers.IO) {
                    PropertyHelper.checkMigrated(requireContext())
                    runInTransaction { }
                }
                MainActivity.show(requireContext())
                activity?.finish()
            }
        }
    }
}
