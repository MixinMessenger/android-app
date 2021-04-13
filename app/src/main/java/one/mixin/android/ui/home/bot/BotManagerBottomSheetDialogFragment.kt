package one.mixin.android.ui.home.bot

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.manager.SupportRequestManagerFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tbruyelle.rxpermissions2.RxPermissions
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDispose
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.launch
import one.mixin.android.R
import one.mixin.android.RxBus
import one.mixin.android.databinding.FragmentBotManagerBinding
import one.mixin.android.event.BotCloseEvent
import one.mixin.android.event.BotEvent
import one.mixin.android.extension.booleanFromAttribute
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.dp
import one.mixin.android.extension.openPermissionSetting
import one.mixin.android.extension.putString
import one.mixin.android.session.Session
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.home.MainActivity
import one.mixin.android.ui.setting.WalletPasswordFragment
import one.mixin.android.ui.url.UrlInterpreterActivity
import one.mixin.android.ui.wallet.WalletActivity
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.SystemUIManager
import one.mixin.android.vo.App
import one.mixin.android.vo.BotInterface
import one.mixin.android.widget.MixinBottomSheetDialog
import one.mixin.android.widget.bot.BotDock
import timber.log.Timber

@AndroidEntryPoint
class BotManagerBottomSheetDialogFragment : BottomSheetDialogFragment(), BotDock.OnDockListener {
    private val destroyScope = scope(Lifecycle.Event.ON_DESTROY)
    companion object {
        const val TAG = "BorManagerBottomSheetDialogFragment"
    }

    private lateinit var contentView: View

    private val stopScope = scope(Lifecycle.Event.ON_STOP)

    private val botManagerViewModel by viewModels<BotManagerViewModel>()

    override fun getTheme() = R.style.MixinBottomSheet

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MixinBottomSheetDialog(requireContext(), theme).apply {
            dismissWithAnimation = true
        }
    }

    private var _binding: FragmentBotManagerBinding? = null
    private val binding get() = requireNotNull(_binding)

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        _binding = FragmentBotManagerBinding.inflate(LayoutInflater.from(context), null, false)
        contentView = binding.root
        dialog.setContentView(contentView)
        val params = (contentView.parent as View).layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior as BottomSheetBehavior<*>
        behavior.peekHeight = 440.dp
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setGravity(Gravity.BOTTOM)
        initView()
        loadData()
        RxBus.listen(BotCloseEvent::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .autoDispose(destroyScope)
            .subscribe {
                dismissAllowingStateLoss()
            }
        RxBus.listen(BotEvent::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .autoDispose(destroyScope)
            .subscribe {
                loadData()
            }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            SystemUIManager.lightUI(
                window,
                !requireContext().booleanFromAttribute(R.attr.flag_night)
            )
        }
    }

    override fun onDetach() {
        super.onDetach()
        if (activity is UrlInterpreterActivity) {
            var realFragmentCount = 0
            parentFragmentManager.fragments.forEach { f ->
                if (f !is SupportRequestManagerFragment) {
                    realFragmentCount++
                }
            }
            if (realFragmentCount <= 0) {
                activity?.finish()
            }
        }
    }

    override fun dismissAllowingStateLoss() {
        try {
            super.dismissAllowingStateLoss()
        } catch (e: IllegalStateException) {
            Timber.w(e)
        }
    }

    private fun initView() {
        binding.botClose.setOnClickListener {
            dismiss()
        }
        binding.botDock.setOnDragListener(bottomListAdapter.dragInstance)
        binding.botRv.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.botRv.adapter = bottomListAdapter
        binding.botRv.setOnDragListener(bottomListAdapter.dragInstance)
        binding.botDock.setOnDockListener(this)
    }

    private fun loadData() {
        lifecycleScope.launch {
            val defaultApps = mutableListOf<BotInterface>(InternalWallet, InternalCamera, InternalScan)
            val topApps = mutableListOf<BotInterface>()
            val topIds = mutableListOf<String>()
            defaultSharedPreferences.getString(TOP_BOT, DefaultTopBots)?.let {
                val ids = GsonHelper.customGson.fromJson(it, Array<String>::class.java)
                ids.forEach { id ->
                    topIds.add(id)
                    when (id) {
                        INTERNAL_WALLET_ID -> {
                            topApps.add(InternalWallet)
                            defaultApps.remove(InternalWallet)
                        }
                        INTERNAL_CAMERA_ID -> {
                            topApps.add(InternalCamera)
                            defaultApps.remove(InternalCamera)
                        }
                        INTERNAL_SCAN_ID -> {
                            topApps.add(InternalScan)
                            defaultApps.remove(InternalScan)
                        }
                        else -> {
                            botManagerViewModel.findAppById(id)?.let { app ->
                                topApps.add(app)
                            }
                        }
                    }
                }
            }

            binding.botDock.apps = topApps
            val notTopApps = botManagerViewModel.getNotTopApps(topIds)
            if (notTopApps.isNullOrEmpty()) {
                binding.emptyFl.isVisible = true
                binding.botRv.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            } else {
                binding.emptyFl.isVisible = false
                defaultApps.addAll(notTopApps)
            }
            bottomListAdapter.list = defaultApps
        }
    }

    private val bottomListAdapter by lazy {
        BotManagerAdapter(clickAction)
    }

    override fun onDockChange(apps: List<BotInterface>) {
        saveTopApps(apps)
        RxBus.publish(BotEvent())
    }

    override fun onDockClick(app: BotInterface) {
        clickAction(app)
    }

    private val clickAction: (BotInterface) -> Unit = { app ->
        if (app is App) {
            lifecycleScope.launch {
                botManagerViewModel.findUserByAppId(app.appId)?.let { user ->
                    UserBottomSheetDialogFragment.newInstance(user)
                        ?.show(parentFragmentManager, UserBottomSheetDialogFragment.TAG)
                }
            }
        } else if (app is Bot) {
            when (app.id) {
                INTERNAL_WALLET_ID -> {
                    if (Session.getAccount()?.hasPin == true) {
                        WalletActivity.show(requireActivity())
                    } else {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.root_view, WalletPasswordFragment.newInstance(false))
                            .addToBackStack(null)
                            .commitAllowingStateLoss()
                    }
                    dismissAllowingStateLoss()
                }
                INTERNAL_CAMERA_ID -> {
                    openCamera(false)
                    dismissAllowingStateLoss()
                }
                INTERNAL_SCAN_ID -> {
                    openCamera(true)
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    private fun openCamera(scan: Boolean) {
        RxPermissions(requireActivity())
            .request(Manifest.permission.CAMERA)
            .autoDispose(stopScope)
            .subscribe { granted ->
                if (granted) {
                    (requireActivity() as? MainActivity)?.showCapture(scan)
                } else {
                    context?.openPermissionSetting()
                }
            }
    }

    private fun saveTopApps(apps: List<BotInterface>) {
        apps.map { it.getBotId() }.apply {
            defaultSharedPreferences.putString(TOP_BOT, GsonHelper.customGson.toJson(this))
        }
    }
}
