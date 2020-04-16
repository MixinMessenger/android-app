package one.mixin.android.ui.home.bot

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.manager.SupportRequestManagerFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.uber.autodispose.android.lifecycle.scope
import javax.inject.Inject
import kotlinx.android.synthetic.main.fragment_bot_manager.view.*
import kotlinx.android.synthetic.main.fragment_bot_manager.view.bot_dock
import kotlinx.coroutines.launch
import one.mixin.android.R
import one.mixin.android.RxBus
import one.mixin.android.di.Injectable
import one.mixin.android.event.BotEvent
import one.mixin.android.extension.booleanFromAttribute
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.dp
import one.mixin.android.extension.putString
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.url.UrlInterpreterActivity
import one.mixin.android.ui.wallet.WalletActivity
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.SystemUIManager
import one.mixin.android.vo.App
import one.mixin.android.widget.MixinBottomSheetDialog
import one.mixin.android.widget.bot.BotDock
import org.jetbrains.anko.displayMetrics

class BotManagerBottomSheetDialogFragment : BottomSheetDialogFragment(), BotDock.OnDockListener, Injectable {

    companion object {
        const val TAG = "BorManagerBottomSheetDialogFragment"
    }

    private lateinit var contentView: View

    private val stopScope = scope(Lifecycle.Event.ON_STOP)

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val botManagerViewModel: BotManagerViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(BotManagerViewModel::class.java)
    }

    override fun getTheme() = R.style.MixinBottomSheet

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MixinBottomSheetDialog(requireContext(), theme).apply {
            dismissWithAnimation = true
        }
    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        contentView = View.inflate(context, R.layout.fragment_bot_manager, null)
        dialog.setContentView(contentView)
        val params = (contentView.parent as View).layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior as? BottomSheetBehavior<*>
        if (behavior != null) {
            val defaultPeekHeight = getPeekHeight(contentView, behavior)
            behavior.peekHeight = if (defaultPeekHeight == 0) {
                440.dp
            } else defaultPeekHeight
            behavior.addBottomSheetCallback(bottomSheetBehaviorCallback)
            contentView.bot_rv.apply {
                layoutParams.height = requireContext().displayMetrics.heightPixels - 166.dp
            }
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.setGravity(Gravity.BOTTOM)
        }
        initView()
        loadData()
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

    private fun initView() {
        contentView.bot_close.setOnClickListener {
            dismiss()
        }
        contentView.bot_dock.setOnDragListener(bottomListAdapter.dragInstance)
        contentView.bot_rv.layoutManager = GridLayoutManager(requireContext(), 4)
        contentView.bot_rv.adapter = bottomListAdapter
        contentView.bot_rv.setOnDragListener(bottomListAdapter.dragInstance)
        contentView.bot_dock.setOnDockListener(this)
    }

    private fun loadData() {
        lifecycleScope.launch {
            val defaultApps = mutableListOf<BotInterface>(InternalWallet, InternalCamera, InternalScan)
            val topApps = mutableListOf<BotInterface>()
            val topIds = mutableListOf<String>()
            defaultSharedPreferences.getString(TOP_BOT, null)?.let {
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

            contentView.bot_dock.apps = topApps
            defaultApps.addAll(botManagerViewModel.getTopApps(topIds))
            bottomListAdapter.list = defaultApps
        }
    }

    private val bottomListAdapter by lazy {
        BotManagerAdapter(clickAction)
    }

    private fun getPeekHeight(contentView: View, behavior: BottomSheetBehavior<*>): Int = 0

    fun onStateChanged(bottomSheet: View, newState: Int) {}

    fun onSlide(bottomSheet: View, slideOffset: Float) {}

    private val bottomSheetBehaviorCallback = object : BottomSheetBehavior.BottomSheetCallback() {

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            this@BotManagerBottomSheetDialogFragment.onStateChanged(bottomSheet, newState)
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            this@BotManagerBottomSheetDialogFragment.onSlide(bottomSheet, slideOffset)
        }
    }

    override fun onDockChange(apps: List<BotInterface>) {
        saveTopApps(apps)
        loadData()
        RxBus.publish(BotEvent())
    }

    override fun onDockClick(app: BotInterface) {
        clickAction(app)
    }

    private val clickAction: (BotInterface) -> Unit = { app ->
        if (app is App) {
            lifecycleScope.launch {
                botManagerViewModel.findUserByAppId(app.appId)?.let { user ->
                    UserBottomSheetDialogFragment.newInstance(user).show(parentFragmentManager, UserBottomSheetDialogFragment.TAG)
                }
            }
        } else if (app is Bot) {
            when (app.id) {
                INTERNAL_WALLET_ID -> {
                    WalletActivity.show(requireActivity())
                }
                INTERNAL_CAMERA_ID -> {
                    // Todo
                }
                INTERNAL_SCAN_ID -> {
                    // Todo
                }
            }
        }
    }

    private fun saveTopApps(apps: List<BotInterface>) {
        apps.map {
            if (it is App) {
                it.appId
            } else {
                (it as Bot).id
            }
        }.apply {
            defaultSharedPreferences.putString(TOP_BOT, GsonHelper.customGson.toJson(this))
        }
    }
}