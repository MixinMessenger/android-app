package one.mixin.android.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.BounceInterpolator
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.tbruyelle.rxpermissions2.RxPermissions
import com.uber.autodispose.autoDispose
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.launch
import one.mixin.android.Constants.Account.PREF_NOTIFICATION_ON
import one.mixin.android.Constants.Account.PREF_VERSION_CHECK
import one.mixin.android.Constants.CIRCLE.CIRCLE_ID
import one.mixin.android.Constants.INTERVAL_48_HOURS
import one.mixin.android.Constants.Mute.MUTE_1_HOUR
import one.mixin.android.Constants.Mute.MUTE_1_WEEK
import one.mixin.android.Constants.Mute.MUTE_1_YEAR
import one.mixin.android.Constants.Mute.MUTE_8_HOURS
import one.mixin.android.R
import one.mixin.android.RxBus
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.databinding.FragmentConversationListBinding
import one.mixin.android.databinding.ItemListConversationBinding
import one.mixin.android.databinding.ItemListConversationHeaderBinding
import one.mixin.android.databinding.ViewConversationBottomBinding
import one.mixin.android.event.BotEvent
import one.mixin.android.event.CircleDeleteEvent
import one.mixin.android.extension.alertDialogBuilder
import one.mixin.android.extension.animateHeight
import one.mixin.android.extension.colorFromAttribute
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.dp
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.isPlayStoreInstalled
import one.mixin.android.extension.networkConnected
import one.mixin.android.extension.notEmptyWithElse
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.nowInUtc
import one.mixin.android.extension.openMarket
import one.mixin.android.extension.openNotificationSetting
import one.mixin.android.extension.openPermissionSetting
import one.mixin.android.extension.putInt
import one.mixin.android.extension.putLong
import one.mixin.android.extension.renderMessage
import one.mixin.android.extension.tapVibrate
import one.mixin.android.extension.timeAgo
import one.mixin.android.extension.toast
import one.mixin.android.job.GenerateAvatarJob
import one.mixin.android.job.MixinJobManager
import one.mixin.android.session.Session
import one.mixin.android.ui.common.LinkFragment
import one.mixin.android.ui.common.NavigationController
import one.mixin.android.ui.common.recyclerview.NormalHolder
import one.mixin.android.ui.common.recyclerview.PagedHeaderAdapter
import one.mixin.android.ui.conversation.ConversationActivity
import one.mixin.android.ui.home.bot.BotManagerBottomSheetDialogFragment
import one.mixin.android.ui.home.bot.DefaultTopBots
import one.mixin.android.ui.home.bot.INTERNAL_CAMERA_ID
import one.mixin.android.ui.home.bot.INTERNAL_SCAN_ID
import one.mixin.android.ui.home.bot.INTERNAL_WALLET_ID
import one.mixin.android.ui.home.bot.TOP_BOT
import one.mixin.android.ui.home.bot.getCategoryIcon
import one.mixin.android.ui.web.WebActivity
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.markdown.MarkwonUtil
import one.mixin.android.util.mention.MentionRenderCache
import one.mixin.android.vo.*
import one.mixin.android.websocket.SystemConversationAction
import one.mixin.android.widget.BottomSheet
import one.mixin.android.widget.DraggableRecyclerView
import one.mixin.android.widget.DraggableRecyclerView.Companion.FLING_DOWN
import org.jetbrains.anko.doAsync
import java.io.File
import javax.inject.Inject
import kotlin.math.min

@AndroidEntryPoint
class ConversationListFragment : LinkFragment() {

    private lateinit var navigationController: NavigationController

    @Inject
    lateinit var jobManager: MixinJobManager

    private var _binding: FragmentConversationListBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val messagesViewModel by viewModels<ConversationListViewModel>()

    private val messageAdapter by lazy {
        MessageAdapter().apply {
            registerAdapterDataObserver(messageAdapterDataObserver)
        }
    }

    private val messageAdapterDataObserver =
        object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                super.onItemRangeChanged(positionStart, itemCount)
                if (scrollTop) {
                    scrollTop = false
                    (binding.messageRv.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                        0,
                        0
                    )
                }
            }
        }

    private var distance = 0
    private var shadowVisible = true
    private val touchSlop: Int by lazy {
        ViewConfiguration.get(context).scaledTouchSlop
    }

    private val vibrateDis by lazy { requireContext().dpToPx(110f) }
    private var vibrated = false
    private var expanded = false

    companion object {
        fun newInstance() = ConversationListFragment()

        const val TAG = "ConversationListFragment"

        private const val DRAG_FRICTION = 2
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun getContentView() = binding.root

    private val headerBinding by lazy {
        ItemListConversationHeaderBinding.inflate(layoutInflater, binding.messageRv, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigationController = NavigationController(activity as MainActivity)
        messageAdapter.headerView = headerBinding.root
        binding.messageRv.adapter = messageAdapter
        binding.messageRv.itemAnimator = null
        binding.messageRv.setHasFixedSize(true)
        binding.messageRv.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (distance < -touchSlop && !shadowVisible) {
                        binding.shadowFl.animate().translationY(0f).duration = 200
                        distance = 0
                        shadowVisible = true
                    } else if (distance > touchSlop && shadowVisible) {
                        binding.shadowFl.animate()
                            .translationY(binding.shadowFl.height.toFloat()).duration = 200
                        distance = 0
                        shadowVisible = false
                    }
                    if ((dy > 0 && shadowVisible) || (dy < 0 && !shadowVisible)) {
                        distance += dy
                    }
                }
            }
        )
        binding.messageRv.callback = object : DraggableRecyclerView.Callback {
            override fun onScroll(dis: Float) {
                val topFl = binding.topFl

                if (topFl.isGone) {
                    topFl.isVisible = true
                }
                val targetH = topFl.height + (dis / DRAG_FRICTION).toInt()
                if (targetH <= 0) return

                topFl.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = targetH

                    if (height >= vibrateDis) {
                        if (!vibrated) {
                            requireContext().tapVibrate()
                            vibrated = true
                        }
                        animDownIcon(true)
                    } else {
                        animDownIcon(false)
                    }
                }
                val progress = min(targetH / vibrateDis.toFloat(), 1f)
                (requireActivity() as MainActivity).dragSearch(progress)
            }

            override fun onRelease(fling: Int) {
                val shouldVibrate = false
                if (shouldVibrate && !vibrated) {
                    requireContext().tapVibrate()
                    vibrated = true
                }
                val topFl = _binding?.topFl

                val open = (fling == FLING_DOWN && shouldVibrate) || topFl?.height ?: 0 >= vibrateDis
                if (open) {
                    (requireActivity() as MainActivity).openSearch()
                } else {
                    (requireActivity() as MainActivity).closeSearch()
                }

                topFl?.animateHeight(
                    topFl.height,
                    0,
                    onEndAction = {
                        vibrated = false
                    }
                )
                _binding?.downIv?.apply {
                    scaleX = 1f
                    scaleY = 1f
                }
            }
        }
        binding.shadowView.more.setOnClickListener {
            BotManagerBottomSheetDialogFragment()
                .show(parentFragmentManager, BotManagerBottomSheetDialogFragment.TAG)
        }

        messageAdapter.onItemListener =
            object : PagedHeaderAdapter.OnItemListener<ConversationItem> {
                override fun onNormalLongClick(item: ConversationItem): Boolean {
                    showBottomSheet(item)
                    return true
                }

                override fun onNormalItemClick(item: ConversationItem) {
                    if (item.isGroupConversation() && (
                        item.status == ConversationStatus.START.ordinal ||
                            item.status == ConversationStatus.FAILURE.ordinal
                        )
                    ) {
                        if (!requireContext().networkConnected()) {
                            context?.toast(R.string.error_network)
                            return
                        }
                        doAsync { messagesViewModel.createGroupConversation(item.conversationId) }
                    } else {
                        lifecycleScope.launch {
                            val user = if (item.isContactConversation()) {
                                messagesViewModel.suspendFindUserById(item.ownerId)
                            } else null
                            val messageId =
                                if (item.unseenMessageCount != null && item.unseenMessageCount > 0) {
                                    messagesViewModel.findFirstUnreadMessageId(
                                        item.conversationId,
                                        item.unseenMessageCount - 1
                                    )
                                } else null
                            ConversationActivity.fastShow(
                                requireContext(),
                                conversationId = item.conversationId,
                                recipient = user,
                                initialPositionMessageId = messageId,
                                unreadCount = item.unseenMessageCount ?: 0
                            )
                        }
                    }
                }
            }
        binding.emptyView.startBn.setOnClickListener {
            circleId.notNullWithElse(
                { circleId ->
                    (requireActivity() as MainActivity).openCircleEdit(circleId)
                },
                {
                    navigationController.pushContacts()
                }
            )
        }
        val circleId = defaultSharedPreferences.getString(CIRCLE_ID, null)
        if (circleId == null) {
            selectCircle(null)
        } else {
            this.circleId = circleId
        }
        RxBus.listen(CircleDeleteEvent::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .autoDispose(destroyScope)
            .subscribe {
                if (it.circleId == this.circleId) {
                    (requireActivity() as MainActivity).selectCircle(null, null)
                }
            }
        refreshBot()
        RxBus.listen(BotEvent::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .autoDispose(destroyScope)
            .subscribe {
                refreshBot()
            }
    }

    override fun onDestroyView() {
        if (isAdded) {
            messageAdapter.unregisterAdapterDataObserver(messageAdapterDataObserver)
        }
        super.onDestroyView()
        _binding = null
    }

    private val observer by lazy {
        Observer<PagedList<ConversationItem>> { pagedList ->
            messageAdapter.submitList(pagedList)
            if (pagedList == null || pagedList.isEmpty()) {
                if (circleId == null) {
                    binding.emptyView.infoTv.setText(R.string.empty_info)
                    binding.emptyView.startBn.setText(R.string.empty_start)
                } else {
                    binding.emptyView.infoTv.setText(R.string.circle_empty_info)
                    binding.emptyView.startBn.setText(R.string.circle_empty_start)
                }
                binding.emptyView.root.isVisible = true
            } else {
                binding.emptyView.root.isVisible = false
                pagedList
                    .filter { item: ConversationItem? ->
                        item?.isGroupConversation() == true && (
                            item.iconUrl() == null || !File(
                                item.iconUrl() ?: ""
                            ).exists()
                            )
                    }.forEach {
                        jobManager.addJobInBackground(GenerateAvatarJob(it.conversationId))
                    }
            }
        }
    }

    private var liveData: LiveData<PagedList<ConversationItem>>? = null
    var circleId: String? = null
        set(value) {
            if (field != value) {
                field = value
                selectCircle(circleId)
            }
        }

    private var scrollTop = false
    private fun selectCircle(circleId: String?) {
        liveData?.removeObserver(observer)
        val liveData = messagesViewModel.observeConversations(circleId)
        liveData.observe(viewLifecycleOwner, observer)
        scrollTop = true
        this.liveData = liveData
    }

    private fun animDownIcon(expand: Boolean) {
        val shouldAnim = if (expand) !expanded else expanded
        if (!shouldAnim) return

        binding.downIv.animate().apply {
            interpolator = BounceInterpolator()
        }.scaleX(if (expand) 1.5f else 1f).start()
        binding.downIv.animate().apply {
            interpolator = BounceInterpolator()
        }.scaleY(if (expand) 1.5f else 1f).start()
        expanded = expand
    }

    @SuppressLint("InflateParams")
    fun showBottomSheet(conversationItem: ConversationItem) {
        val conversationId = conversationItem.conversationId
        val isMute = conversationItem.isMute()
        val hasPin = conversationItem.pinTime != null
        val builder = BottomSheet.Builder(requireActivity())
        val viewBinding = ViewConversationBottomBinding.inflate(LayoutInflater.from(ContextThemeWrapper(requireActivity(), R.style.Custom)), null, false)
        builder.setCustomView(viewBinding.root)
        viewBinding.muteTv.setText(
            if (isMute) {
                R.string.un_mute
            } else {
                R.string.mute
            }
        )
        val bottomSheet = builder.create()
        viewBinding.muteTv.setOnClickListener {
            if (isMute) {
                unMute(conversationItem)
            } else {
                showMuteDialog(conversationItem)
            }
            bottomSheet.dismiss()
        }
        viewBinding.deleteTv.setOnClickListener {
            alertDialogBuilder()
                .setMessage(getString(R.string.conversation_delete_tip))
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                    bottomSheet.dismiss()
                }
                .setPositiveButton(R.string.confirm) { _, _ ->
                    val lm = binding.messageRv.layoutManager as LinearLayoutManager
                    val lastCompleteVisibleItem = lm.findLastCompletelyVisibleItemPosition()
                    val firstCompleteVisibleItem = lm.findFirstCompletelyVisibleItemPosition()
                    if (lastCompleteVisibleItem - firstCompleteVisibleItem <= messageAdapter.itemCount &&
                        lm.findFirstVisibleItemPosition() == 0
                    ) {
                        binding.shadowFl.animate().translationY(0f).duration = 200
                    }
                    messagesViewModel.deleteConversation(conversationId)
                    bottomSheet.dismiss()
                }
                .show()
        }
        if (hasPin) {
            viewBinding.pinTv.setText(R.string.conversation_pin_clear)
            viewBinding.pinTv.setOnClickListener {
                messagesViewModel.updateConversationPinTimeById(conversationId, circleId, null)
                bottomSheet.dismiss()
            }
        } else {
            viewBinding.pinTv.setText(R.string.conversation_pin)
            viewBinding.pinTv.setOnClickListener {
                messagesViewModel.updateConversationPinTimeById(
                    conversationId,
                    circleId,
                    nowInUtc()
                )
                bottomSheet.dismiss()
            }
        }

        bottomSheet.show()
    }

    private var displayVersion = false
    override fun onResume() {
        super.onResume()
        lifecycleScope.launchWhenResumed {
            val notificationTime =
                requireContext().defaultSharedPreferences.getLong(PREF_NOTIFICATION_ON, 0)
            if (!displayVersion && System.currentTimeMillis() - notificationTime > INTERVAL_48_HOURS && !NotificationManagerCompat.from(
                    requireContext()
                ).areNotificationsEnabled()
            ) {
                headerBinding.headerTitle.setText(R.string.notification_title)
                headerBinding.headerContent.setText(R.string.notification_content)
                headerBinding.headerSettings.setText(R.string.notification_settings)
                headerBinding.headerClose.setOnClickListener {
                    messageAdapter.setShowHeader(false, binding.messageRv)
                    requireContext().defaultSharedPreferences.putLong(
                        PREF_NOTIFICATION_ON,
                        System.currentTimeMillis()
                    )
                }
                headerBinding.headerSettings.setOnClickListener {
                    requireContext().openNotificationSetting()
                }
                messageAdapter.setShowHeader(true, binding.messageRv)
            } else {
                if (!displayVersion) messageAdapter.setShowHeader(false, binding.messageRv)
                if (!requireContext().isPlayStoreInstalled()) {
                    messagesViewModel.latest()?.let { latest ->
                        if (latest.hasNewVersion() && requireContext().defaultSharedPreferences.getInt(
                                PREF_VERSION_CHECK,
                                0
                            ) < latest.versionCode!!
                        ) {
                            displayVersion = true
                            headerBinding.headerTitle.setText(R.string.version_title)
                            headerBinding.headerContent.setText(R.string.version_content)
                            headerBinding.headerSettings.setText(R.string.version_update)
                            headerBinding.headerClose.setOnClickListener {
                                messageAdapter.setShowHeader(false, binding.messageRv)
                                displayVersion = false
                                requireContext().defaultSharedPreferences.putInt(
                                    PREF_VERSION_CHECK,
                                    latest.versionCode!!
                                )
                            }
                            headerBinding.headerSettings.setOnClickListener {
                                requireContext().openMarket()
                            }
                            messageAdapter.setShowHeader(true, binding.messageRv)
                        } else {
                            displayVersion = false
                            messageAdapter.setShowHeader(false, binding.messageRv)
                        }
                    }
                }
            }
        }
    }

    private fun refreshBot() {
        lifecycleScope.launch {
            binding.shadowView.firstIv.isGone = true
            binding.shadowView.secondIv.isGone = true
            binding.shadowView.thirdIv.isGone = true
            requireContext().defaultSharedPreferences.getString(TOP_BOT, DefaultTopBots)?.let {
                val bots = GsonHelper.customGson.fromJson(it, Array<String>::class.java)
                bots.forEachIndexed { index, id ->
                    if (index > 2) return@launch
                    val view: ImageView =
                        when (index) {
                            0 -> {
                                binding.shadowView.firstIv
                            }
                            1 -> {
                                binding.shadowView.secondIv
                            }
                            else -> {
                                binding.shadowView.thirdIv
                            }
                        }

                    when (id) {
                        INTERNAL_WALLET_ID -> {
                            view.isVisible = true
                            view.setImageResource(R.drawable.ic_bot_category_wallet)
                            view.setOnClickListener {
                                (requireActivity() as MainActivity).openWallet()
                            }
                        }
                        INTERNAL_CAMERA_ID -> {
                            view.isVisible = true
                            view.setImageResource(R.drawable.ic_bot_category_camera)
                            view.setOnClickListener {
                                openCamera(false)
                            }
                        }
                        INTERNAL_SCAN_ID -> {
                            view.isVisible = true
                            view.setImageResource(R.drawable.ic_bot_category_scan)
                            view.setOnClickListener {
                                openCamera(true)
                            }
                        }
                        else -> {
                            messagesViewModel.findAppById(id)?.notNullWithElse(
                                { app ->
                                    view.isVisible = true
                                    view.setImageResource(app.getCategoryIcon())
                                    view.setOnClickListener {
                                        WebActivity.show(requireContext(), app.homeUri, null, app)
                                    }
                                },
                                {
                                    view.isInvisible = true
                                }
                            )
                        }
                    }
                }
                if (bots.size < 3) {
                    val dp88 = 88.dp
                    val dp32 = 32.dp
                    binding.shadowView.children.forEach { v ->
                        v.updateLayoutParams<LinearLayoutCompat.LayoutParams> {
                            width = dp88
                            height = dp88
                        }
                        v.setPadding(dp32)
                    }
                } else {
                    val dp80 = 80.dp
                    val dp28 = 28.dp
                    binding.shadowView.children.forEach { v ->
                        v.updateLayoutParams<LinearLayoutCompat.LayoutParams> {
                            width = dp80
                            height = dp80
                        }
                        v.setPadding(dp28)
                    }
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

    class MessageAdapter : PagedHeaderAdapter<ConversationItem>(ConversationItem.DIFF_CALLBACK) {

        override fun getNormalViewHolder(context: Context, parent: ViewGroup): NormalHolder =
            MessageHolder(
                ItemListConversationBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is MessageHolder) {
                val pos = getPos(position)
                getItem(pos)?.let {
                    holder.bind(onItemListener, it)
                }
            }
        }
    }

    class MessageHolder constructor(val binding: ItemListConversationBinding) :
        NormalHolder(binding.root) {
        var context: Context = itemView.context
        private fun getText(id: Int) = context.getText(id).toString()

        @SuppressLint("SetTextI18n")
        fun bind(
            onItemClickListener: PagedHeaderAdapter.OnItemListener<ConversationItem>?,
            conversationItem: ConversationItem
        ) {
            val id = Session.getAccountId()
            conversationItem.getConversationName().let {
                binding.nameTv.text = it
            }
            binding.groupNameTv.visibility = GONE
            binding.mentionFlag.isVisible =
                conversationItem.mentionCount != null && conversationItem.mentionCount > 0
            when {
                conversationItem.messageStatus == MessageStatus.FAILED.name -> {
                    conversationItem.content?.let {
                        setConversationName(conversationItem)
                        binding.msgTv.setText(R.string.conversation_waiting)
                    }
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_status_fail)
                }
                conversationItem.isText() -> {
                    conversationItem.content?.let {
                        setConversationName(conversationItem)
                        if (conversationItem.mentions != null) {
                            binding.msgTv.renderMessage(
                                it,
                                MentionRenderCache.singleton.getMentionRenderContext(
                                    conversationItem.mentions
                                )
                            )
                        } else {
                            binding.msgTv.text = it
                        }
                    }
                    null
                }
                conversationItem.contentType == MessageCategory.SYSTEM_ACCOUNT_SNAPSHOT.name -> {
                    binding.msgTv.setText(R.string.conversation_status_transfer)
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_transfer)
                }
                conversationItem.isSticker() -> {
                    setConversationName(conversationItem)
                    binding.msgTv.setText(R.string.conversation_status_sticker)
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_stiker)
                }
                conversationItem.isImage() -> {
                    setConversationName(conversationItem)
                    binding.msgTv.setText(R.string.conversation_status_pic)
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_pic)
                }
                conversationItem.isVideo() -> {
                    setConversationName(conversationItem)
                    binding.msgTv.setText(R.string.conversation_status_video)
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_video)
                }
                conversationItem.isLive() -> {
                    setConversationName(conversationItem)
                    binding.msgTv.setText(R.string.conversation_status_live)
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_live)
                }
                conversationItem.isData() -> {
                    setConversationName(conversationItem)
                    binding.msgTv.setText(R.string.conversation_status_file)
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_file)
                }
                conversationItem.isPost() -> {
                    setConversationName(conversationItem)
                    binding.msgTv.text = MarkwonUtil.parseContent(conversationItem.content)
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_file)
                }
                conversationItem.isLocation() -> {
                    setConversationName(conversationItem)
                    binding.msgTv.setText(R.string.conversation_status_location)
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_location)
                }
                conversationItem.isAudio() -> {
                    setConversationName(conversationItem)
                    binding.msgTv.setText(R.string.conversation_status_audio)
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_audio)
                }
                conversationItem.contentType == MessageCategory.APP_BUTTON_GROUP.name -> {
                    binding.groupNameTv.visibility = GONE
                    val buttons =
                        Gson().fromJson(conversationItem.content, Array<AppButtonData>::class.java)
                    var content = ""
                    buttons.map { content += "[" + it.label + "]" }
                    binding.msgTv.text = content
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_touch_app)
                }
                conversationItem.contentType == MessageCategory.APP_CARD.name -> {
                    binding.groupNameTv.visibility = GONE
                    val cardData =
                        Gson().fromJson(conversationItem.content, AppCardData::class.java)
                    binding.msgTv.text = "[${cardData.title}]"
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_touch_app)
                }
                conversationItem.isContact() -> {
                    setConversationName(conversationItem)
                    binding.msgTv.setText(R.string.conversation_status_contact)
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_contact)
                }
                conversationItem.isCallMessage() -> {
                    setConversationName(conversationItem)
                    binding.msgTv.setText(R.string.conversation_status_voice)
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_voice)
                }
                conversationItem.isRecall() -> {
                    setConversationName(conversationItem)
                    if (id == conversationItem.senderId) {
                        binding.msgTv.setText(R.string.chat_recall_me)
                    } else {
                        binding.msgTv.text =
                            itemView.context.getString(R.string.chat_recall_delete)
                    }
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_recall)
                }
                conversationItem.isGroupCall() -> {
                    setConversationName(conversationItem)
                    binding.msgTv.setText(R.string.conversation_status_group_call)
                    AppCompatResources.getDrawable(itemView.context, R.drawable.ic_type_voice)
                }
                conversationItem.contentType == MessageCategory.SYSTEM_CONVERSATION.name -> {
                    when (conversationItem.actionName) {
                        SystemConversationAction.CREATE.name -> {
                            binding.msgTv.text =
                                String.format(
                                    getText(R.string.chat_group_create),
                                    if (id == conversationItem.senderId) {
                                        getText(R.string.chat_you_start)
                                    } else {
                                        conversationItem.name
                                    },
                                    conversationItem.groupName
                                )
                        }
                        SystemConversationAction.ADD.name -> {
                            binding.msgTv.text =
                                String.format(
                                    getText(R.string.chat_group_add),
                                    if (id == conversationItem.senderId) {
                                        getText(R.string.chat_you_start)
                                    } else {
                                        conversationItem.senderFullName
                                    },
                                    if (id == conversationItem.participantUserId) {
                                        getText(R.string.chat_you)
                                    } else {
                                        conversationItem.participantFullName
                                    }
                                )
                        }
                        SystemConversationAction.REMOVE.name -> {
                            binding.msgTv.text =
                                String.format(
                                    getText(R.string.chat_group_remove),
                                    if (id == conversationItem.senderId) {
                                        getText(R.string.chat_you_start)
                                    } else {
                                        conversationItem.senderFullName
                                    },
                                    if (id == conversationItem.participantUserId) {
                                        getText(R.string.chat_you)
                                    } else {
                                        conversationItem.participantFullName
                                    }
                                )
                        }
                        SystemConversationAction.JOIN.name -> {
                            binding.msgTv.text =
                                String.format(
                                    getText(R.string.chat_group_join),
                                    if (id == conversationItem.participantUserId) {
                                        getText(R.string.chat_you_start)
                                    } else {
                                        conversationItem.participantFullName
                                    }
                                )
                        }
                        SystemConversationAction.EXIT.name -> {
                            binding.msgTv.text =
                                String.format(
                                    getText(R.string.chat_group_exit),
                                    if (id == conversationItem.participantUserId) {
                                        getText(R.string.chat_you_start)
                                    } else {
                                        conversationItem.participantFullName
                                    }
                                )
                        }
                        SystemConversationAction.ROLE.name -> {
                            binding.msgTv.text = getText(R.string.group_role)
                        }
                        else -> {
                            binding.msgTv.text = ""
                        }
                    }
                    null
                }
                else -> {
                    binding.msgTv.text = ""
                    null
                }
            }.also {
                it.notNullWithElse(
                    { drawable ->
                        drawable.setBounds(
                            0,
                            0,
                            itemView.context.dpToPx(12f),
                            itemView.context.dpToPx(12f)
                        )
                        binding.msgType.setImageDrawable(drawable)
                        binding.msgType.isVisible = true
                    },
                    {
                        binding.msgType.isVisible = false
                    }
                )
            }

            if (conversationItem.senderId == Session.getAccountId() &&
                conversationItem.contentType != MessageCategory.SYSTEM_CONVERSATION.name &&
                conversationItem.contentType != MessageCategory.SYSTEM_ACCOUNT_SNAPSHOT.name &&
                !conversationItem.isCallMessage() && !conversationItem.isRecall() &&
                !conversationItem.isGroupCall()
            ) {
                when (conversationItem.messageStatus) {
                    MessageStatus.SENDING.name -> AppCompatResources.getDrawable(
                        itemView.context,
                        R.drawable.ic_status_sending
                    )
                    MessageStatus.SENT.name -> AppCompatResources.getDrawable(
                        itemView.context,
                        R.drawable.ic_status_sent_large
                    )
                    MessageStatus.DELIVERED.name -> AppCompatResources.getDrawable(
                        itemView.context,
                        R.drawable.ic_status_delivered
                    )
                    MessageStatus.READ.name -> AppCompatResources.getDrawable(
                        itemView.context,
                        R.drawable.ic_status_read_dark
                    )
                    else -> {
                        AppCompatResources.getDrawable(
                            itemView.context,
                            R.drawable.ic_status_sending
                        )
                    }
                }.also {
                    it?.setBounds(0, 0, itemView.context.dpToPx(14f), itemView.context.dpToPx(14f))
                    binding.msgStatus.setImageDrawable(it)
                    binding.msgStatus.visibility = VISIBLE
                }
            } else {
                binding.msgStatus.visibility = GONE
            }
            conversationItem.createdAt?.let {
                binding.timeTv.timeAgo(it)
            }
            if (conversationItem.pinTime == null) {
                binding.msgPin.visibility = GONE
                if (conversationItem.isGroupConversation() && conversationItem.status == ConversationStatus.START.ordinal) {
                    binding.pb.visibility = VISIBLE
                    binding.unreadTv.visibility = GONE
                } else {
                    binding.pb.visibility = GONE
                    conversationItem.unseenMessageCount.notEmptyWithElse(
                        {
                            binding.unreadTv.text = "$it"; binding.unreadTv.visibility = VISIBLE
                        },
                        { binding.unreadTv.visibility = GONE }
                    )

                    if (conversationItem.isGroupConversation() && conversationItem.status == ConversationStatus.FAILURE.ordinal) {
                        binding.msgTv.text = getText(R.string.group_click_create_tip)
                    }
                }
            } else {
                binding.msgPin.visibility = VISIBLE
                if (conversationItem.isGroupConversation() && conversationItem.status == ConversationStatus.START.ordinal) {
                    binding.pb.visibility = VISIBLE
                    binding.unreadTv.visibility = GONE
                } else {
                    binding.pb.visibility = GONE
                    conversationItem.unseenMessageCount.notEmptyWithElse(
                        {
                            binding.unreadTv.text = "$it"; binding.unreadTv.visibility =
                                VISIBLE
                        },
                        { binding.unreadTv.visibility = GONE }
                    )
                }
            }

            binding.muteIv.visibility = if (conversationItem.isMute()) VISIBLE else GONE
            if (conversationItem.isMute()) {
                binding.unreadTv.setBackgroundResource(R.drawable.bg_unread_mute)
                binding.unreadTv.setTextColor(context.colorFromAttribute(R.attr.badger_text_mute))
            } else {
                binding.unreadTv.setBackgroundResource(R.drawable.bg_unread)
                binding.unreadTv.setTextColor(context.colorFromAttribute(R.attr.badger_text))
            }

            conversationItem.showVerifiedOrBot(binding.verifiedIv, binding.botIv)
            if (conversationItem.isGroupConversation()) {
                binding.avatarIv.setGroup(conversationItem.iconUrl())
            } else {
                binding.avatarIv.setInfo(
                    conversationItem.getConversationName(),
                    conversationItem.iconUrl(),
                    conversationItem.ownerId
                )
            }
            itemView.setOnClickListener { onItemClickListener?.onNormalItemClick(conversationItem) }
            itemView.setOnLongClickListener {
                onItemClickListener.notNullWithElse(
                    { it.onNormalLongClick(conversationItem) },
                    false
                )
            }
        }

        @SuppressLint("SetTextI18n")
        private fun setConversationName(conversationItem: ConversationItem) {
            if (conversationItem.isGroupConversation() && conversationItem.senderId != Session.getAccountId()) {
                binding.groupNameTv.text = "${conversationItem.senderFullName}: "
                binding.groupNameTv.visibility = VISIBLE
            } else {
                binding.groupNameTv.visibility = GONE
            }
        }
    }

    private fun showMuteDialog(conversationItem: ConversationItem) {
        val choices = arrayOf(
            getString(R.string.contact_mute_1hour),
            getString(R.string.contact_mute_8hours),
            getString(R.string.contact_mute_1week),
            getString(R.string.contact_mute_1year)
        )
        var duration = MUTE_8_HOURS
        var whichItem = 0
        alertDialogBuilder()
            .setTitle(getString(R.string.contact_mute_title))
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                if (conversationItem.isGroupConversation()) {
                    lifecycleScope.launch {
                        handleMixinResponse(
                            invokeNetwork = {
                                messagesViewModel.mute(
                                    duration.toLong(),
                                    conversationId = conversationItem.conversationId
                                )
                            },
                            successBlock = { response ->
                                messagesViewModel.updateGroupMuteUntil(
                                    conversationItem.conversationId,
                                    response.data!!.muteUntil
                                )
                                context?.toast(getString(R.string.contact_mute_title) + " ${conversationItem.groupName} " + choices[whichItem])
                            }
                        )
                    }
                } else {
                    val account = Session.getAccount()
                    account?.let {
                        lifecycleScope.launch {
                            handleMixinResponse(
                                invokeNetwork = {
                                    messagesViewModel.mute(
                                        duration.toLong(),
                                        senderId = it.userId,
                                        recipientId = conversationItem.ownerId
                                    )
                                },
                                successBlock = { response ->
                                    messagesViewModel.updateMuteUntil(
                                        conversationItem.ownerId,
                                        response.data!!.muteUntil
                                    )
                                    context?.toast(getString(R.string.contact_mute_title) + "  ${conversationItem.name}  " + choices[whichItem])
                                }
                            )
                        }
                    }
                }

                dialog.dismiss()
            }
            .setSingleChoiceItems(choices, 0) { _, which ->
                whichItem = which
                when (which) {
                    0 -> duration = MUTE_1_HOUR
                    1 -> duration = MUTE_8_HOURS
                    2 -> duration = MUTE_1_WEEK
                    3 -> duration = MUTE_1_YEAR
                }
            }
            .show()
    }

    private fun unMute(conversationItem: ConversationItem) {
        if (conversationItem.isGroupConversation()) {
            lifecycleScope.launch {
                handleMixinResponse(
                    invokeNetwork = {
                        messagesViewModel.mute(0, conversationId = conversationItem.conversationId)
                    },
                    successBlock = { response ->
                        messagesViewModel.updateGroupMuteUntil(
                            conversationItem.conversationId,
                            response.data!!.muteUntil
                        )
                        context?.toast(getString(R.string.un_mute) + " ${conversationItem.groupName}")
                    }
                )
            }
        } else {
            Session.getAccount()?.let {
                lifecycleScope.launch {
                    handleMixinResponse(
                        invokeNetwork = {
                            messagesViewModel.mute(
                                0,
                                senderId = it.userId,
                                recipientId = conversationItem.ownerId
                            )
                        },
                        successBlock = { response ->
                            messagesViewModel.updateMuteUntil(
                                conversationItem.ownerId,
                                response.data!!.muteUntil
                            )
                            context?.toast(getString(R.string.un_mute) + " ${conversationItem.name}")
                        }
                    )
                }
            }
        }
    }
}
