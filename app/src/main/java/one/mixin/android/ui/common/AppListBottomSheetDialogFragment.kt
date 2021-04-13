package one.mixin.android.ui.common

import android.annotation.SuppressLint
import android.app.Dialog
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import one.mixin.android.databinding.FragmentAppListBottomSheetBinding
import one.mixin.android.databinding.ItemAppListBinding
import one.mixin.android.extension.loadImage
import one.mixin.android.extension.withArgs
import one.mixin.android.util.viewBinding
import one.mixin.android.vo.App
import one.mixin.android.widget.BottomSheet

@AndroidEntryPoint
class AppListBottomSheetDialogFragment : MixinBottomSheetDialogFragment() {
    companion object {
        const val TAG = "AppListBottomSheetDialogFragment"
        const val ARGS_APP_LIST = "args_app_list"
        const val ARGS_TITLE = "args_title"

        fun newInstance(
            appList: List<App>,
            title: String
        ) = AppListBottomSheetDialogFragment().withArgs {
            putParcelableArrayList(ARGS_APP_LIST, ArrayList(appList))
            putString(ARGS_TITLE, title)
        }
    }

    private val appList by lazy {
        requireArguments().getParcelableArrayList<App>(ARGS_APP_LIST)
    }
    private val title: String by lazy {
        requireArguments().getString(ARGS_TITLE)!!
    }

    private val adapter = AppListAdapter {
        openApp(it)
    }

    private fun openApp(userId: String) {
        lifecycleScope.launch {
            bottomViewModel.suspendFindUserById(userId)?.let { user ->
                UserBottomSheetDialogFragment.newInstance(user)?.showNow(parentFragmentManager, UserBottomSheetDialogFragment.TAG)
                dismiss()
            }
        }
    }

    private val binding by viewBinding(FragmentAppListBottomSheetBinding::inflate)

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        contentView = binding.root
        (dialog as BottomSheet).setCustomView(contentView)
        binding.apply {
            titleView.rightIv.setOnClickListener { dismiss() }
            titleView.titleTv.text = title
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        }
        adapter.submitList(appList)
    }
}

class AppListAdapter(private val onClickListener: (String) -> Unit) :
    ListAdapter<App, AppHolder>(App.DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        AppHolder(ItemAppListBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: AppHolder, position: Int) {
        getItem(position)?.let { app ->
            holder.bind(app)
            holder.itemView.setOnClickListener {
                onClickListener(app.appId)
            }
        }
    }
}

class AppHolder(val binding: ItemAppListBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(app: App) {
        binding.nameTv.text = app.name
        binding.descTv.text = app.description
        binding.avatar.loadImage(app.iconUrl)
    }
}
