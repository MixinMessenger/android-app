package one.mixin.android.ui.web

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebSettings.FORCE_DARK_AUTO
import android.webkit.WebSettings.FORCE_DARK_ON
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewClient.ERROR_CONNECT
import android.webkit.WebViewClient.ERROR_HOST_LOOKUP
import android.webkit.WebViewClient.ERROR_IO
import android.webkit.WebViewClient.ERROR_TIMEOUT
import android.widget.Toast
import androidx.activity.result.ActivityResultRegistry
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.ShareCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.tbruyelle.rxpermissions2.RxPermissions
import com.uber.autodispose.autoDispose
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.BuildConfig
import one.mixin.android.Constants
import one.mixin.android.Constants.Mixin_Conversation_ID_HEADER
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.databinding.FragmentWebBinding
import one.mixin.android.databinding.ViewWebBottomMenuBinding
import one.mixin.android.extension.REQUEST_CAMERA
import one.mixin.android.extension.checkInlinePermissions
import one.mixin.android.extension.colorFromAttribute
import one.mixin.android.extension.copyFromInputStream
import one.mixin.android.extension.createImageTemp
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.getClipboardManager
import one.mixin.android.extension.getOtherPath
import one.mixin.android.extension.getPublicPicturePath
import one.mixin.android.extension.isDarkColor
import one.mixin.android.extension.isMixinUrl
import one.mixin.android.extension.isNightMode
import one.mixin.android.extension.isWebUrl
import one.mixin.android.extension.loadImage
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.openAsUrl
import one.mixin.android.extension.openAsUrlOrQrScan
import one.mixin.android.extension.openCamera
import one.mixin.android.extension.openPermissionSetting
import one.mixin.android.extension.openUrl
import one.mixin.android.extension.showPipPermissionNotification
import one.mixin.android.extension.supportsQ
import one.mixin.android.extension.toast
import one.mixin.android.extension.viewDestroyed
import one.mixin.android.session.Session
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.common.BottomSheetViewModel
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.common.info.createMenuLayout
import one.mixin.android.ui.common.info.menu
import one.mixin.android.ui.common.info.menuList
import one.mixin.android.ui.conversation.ConversationActivity
import one.mixin.android.ui.conversation.web.PermissionBottomSheetDialogFragment
import one.mixin.android.ui.forward.ForwardActivity
import one.mixin.android.ui.player.MusicActivity
import one.mixin.android.ui.player.MusicViewModel
import one.mixin.android.ui.player.internal.MUSIC_PLAYLIST
import one.mixin.android.ui.player.internal.MusicServiceConnection
import one.mixin.android.ui.player.provideMusicViewModel
import one.mixin.android.ui.qr.QRCodeProcessor
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.language.Lingver
import one.mixin.android.vo.App
import one.mixin.android.vo.AppCap
import one.mixin.android.vo.AppCardData
import one.mixin.android.vo.ForwardAction
import one.mixin.android.vo.ForwardMessage
import one.mixin.android.vo.ShareCategory
import one.mixin.android.vo.matchResourcePattern
import one.mixin.android.widget.BottomSheet
import one.mixin.android.widget.FailLoadView
import one.mixin.android.widget.MixinWebView
import one.mixin.android.widget.SuspiciousLinkView
import one.mixin.android.widget.WebControlView
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.net.URISyntaxException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class WebFragment : BaseFragment() {

    companion object {
        const val TAG = "WebBottomSheetDialogFragment"

        private const val FILE_CHOOSER = 0x01

        private const val CONTEXT_MENU_ID_SCAN_IMAGE = 0x11
        private const val CONTEXT_MENU_ID_SAVE_IMAGE = 0x12

        const val URL = "url"
        const val CONVERSATION_ID = "conversation_id"
        const val ARGS_APP = "args_app"
        const val ARGS_APP_CARD = "args_app_card"
        const val ARGS_INDEX = "args_index"

        const val themeColorScript =
            """
            (function() {
                var metas = document.getElementsByTagName('meta');
                for (var i = 0; i < metas.length; i++) {
                    if (metas[i].getAttribute('name') === 'theme-color' && metas[i].hasAttribute('content')) {
                        return metas[i].getAttribute('content');
                    }
                }
                return '';
            }) ();
            """

        fun newInstance(
            bundle: Bundle
        ) = WebFragment().apply {
            arguments = bundle
        }
    }

    @Inject
    lateinit var musicServiceConnection: MusicServiceConnection

    private val musicViewModel by viewModels<MusicViewModel> {
        provideMusicViewModel(musicServiceConnection, MUSIC_PLAYLIST)
    }

    private val bottomViewModel by viewModels<BottomSheetViewModel>()

    private val url: String by lazy {
        requireArguments().getString(URL)!!
    }
    private val conversationId: String? by lazy {
        requireArguments().getString(CONVERSATION_ID)
    }
    private var app: App? = null
    private val appCard: AppCardData? by lazy {
        requireArguments().getParcelable(ARGS_APP_CARD)
    }
    private var index: Int = -1
    private var currentUrl: String? = null
    private var isFinished: Boolean = false

    private val processor = QRCodeProcessor()

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        webView.hitTestResult.let {
            when (it.type) {
                WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    menu.add(0, CONTEXT_MENU_ID_SCAN_IMAGE, 0, R.string.contact_sq_scan_title)
                    menu.getItem(0).setOnMenuItemClickListener { menu ->
                        onContextItemSelected(menu)
                        return@setOnMenuItemClickListener true
                    }
                    menu.add(0, CONTEXT_MENU_ID_SAVE_IMAGE, 1, R.string.contact_save_image)
                    menu.getItem(1).setOnMenuItemClickListener { menu ->
                        onContextItemSelected(menu)
                        return@setOnMenuItemClickListener true
                    }
                }
                else -> Timber.d("App does not yet handle target type: ${it.type}")
            }
        }
    }

    override fun onBackPressed(): Boolean {
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        webView.hitTestResult.let {
            val url = it.extra
            if (item.itemId == CONTEXT_MENU_ID_SCAN_IMAGE) {
                lifecycleScope.launch {
                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            Glide.with(requireContext())
                                .asBitmap()
                                .load(url)
                                .submit()
                                .get(10, TimeUnit.SECONDS)
                        }
                        if (isDetached) return@launch

                        processor.detect(
                            lifecycleScope,
                            bitmap,
                            onSuccess = { result ->
                                result.openAsUrlOrQrScan(
                                    requireActivity(),
                                    parentFragmentManager,
                                    lifecycleScope
                                )
                            },
                            onFailure = {
                                if (isAdded) toast(R.string.can_not_recognize)
                            }
                        )
                    } catch (e: Exception) {
                        if (isAdded) toast(R.string.can_not_recognize)
                    }
                }
                return true
            } else if (item.itemId == CONTEXT_MENU_ID_SAVE_IMAGE) {
                saveImageFromUrl(url)
            }
        }
        return super.onContextItemSelected(item)
    }

    var uploadMessage: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        index = requireArguments().getInt(ARGS_INDEX, -1)
    }

    private var _binding: FragmentWebBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    private lateinit var contentView: ViewGroup
    private lateinit var webView: MixinWebView
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        contentView = binding.container
        webView = if (index >= 0 && index < clips.size) {
            clips[index].let { clip ->
                binding.titleTv.text = clip.name
                clip.icon?.let { icon ->
                    this.icon = icon
                    binding.iconIv.isVisible = true
                    binding.iconIv.setImageBitmap(icon)
                }
                isFinished = clip.isFinished
                clip.webView ?: MixinWebView(requireContext())
            }
        } else {
            MixinWebView(requireContext())
        }
        if (webView.parent != null) {
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
        binding.webLl.addView(webView, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        binding.webControl.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = requireContext().dpToPx(6f)
        }
        registerForContextMenu(webView)

        WebView.setWebContentsDebuggingEnabled(
            defaultSharedPreferences.getBoolean(
                Constants.Debug.WEB_DEBUG,
                false
            )
        )

        app = requireArguments().getParcelable(ARGS_APP)

        initView()

        appCard.notNullWithElse(
            {
                checkAppCard(it)
            },
            {
                loadWebView()
            }
        )
        if (requireContext().checkInlinePermissions()) {
            showClip(requireActivity())
        }
    }

    private fun checkAppCard(appCard: AppCardData) = lifecycleScope.launch {
        if (viewDestroyed()) return@launch

        if (appCard.appId != null) {
            app = bottomViewModel.getAppAndCheckUser(appCard.appId, appCard.updatedAt)
            if (app.matchResourcePattern(url)) {
                controlSuspiciousView(false)
                loadWebView()
            } else {
                controlSuspiciousView(true)
            }
        } else {
            loadWebView()
        }
    }

    private fun controlSuspiciousView(show: Boolean) {
        _binding?.apply {
            suspiciousLinkView.isVisible = show
            if (show) {
                pb.isVisible = false
            }
        }
    }

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalSystemUiVisibility = 0

    @SuppressLint("SetJavaScriptEnabled")
    private fun initView() {
        binding.suspiciousLinkView.listener = object : SuspiciousLinkView.SuspiciousListener {
            override fun onBackClick() {
                requireActivity().finish()
            }

            override fun onContinueClick() {
                loadWebView()
                controlSuspiciousView(false)
            }
        }
        app.notNullWithElse(
            {
                binding.failLoadView.contactTv.visibility = VISIBLE
            },
            {
                binding.failLoadView.contactTv.visibility = INVISIBLE
            }
        )
        binding.failLoadView.listener = object : FailLoadView.FailLoadListener {
            override fun onReloadClick() {
                refresh()
            }

            override fun onContactClick() {
                app?.let { app ->
                    lifecycleScope.launch {
                        val user = bottomViewModel.refreshUser(app.creatorId)
                        if (user != null) {
                            ConversationActivity.show(requireContext(), recipientId = app.creatorId)
                            this@WebFragment.requireActivity().finish()
                        }
                    }
                }
            }
        }

        binding.webControl.callback = object : WebControlView.Callback {
            override fun onMoreClick() {
                showBottomSheet()
            }

            override fun onCloseClick() {
                requireActivity().finish()
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        supportsQ {
            webView.settings.forceDark = if (requireContext().isNightMode()) {
                FORCE_DARK_ON
            } else {
                FORCE_DARK_AUTO
            }
        }
        webView.settings.mixedContentMode =
            WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.userAgentString =
            webView.settings.userAgentString + " Mixin/" + BuildConfig.VERSION_NAME

        webView.webViewClient =
            WebViewClientImpl(
                object : WebViewClientImpl.OnPageFinishedListener {
                    override fun onPageFinished() {
                        reloadTheme()
                    }
                },
                conversationId,
                requireContext(),
                this.parentFragmentManager,
                requireActivity().activityResultRegistry,
                lifecycleScope,
                { url ->
                    currentUrl = url
                    isFinished = true
                },
                { errorCode, description, failingUrl ->
                    currentUrl = failingUrl
                    if (errorCode == ERROR_HOST_LOOKUP ||
                        errorCode == ERROR_CONNECT ||
                        errorCode == ERROR_IO ||
                        errorCode == ERROR_TIMEOUT
                    ) {
                        _binding?.apply {
                            failLoadView.webFailDescription.text =
                                getString(R.string.web_cannot_reached_desc, failingUrl)
                            failLoadView.isVisible = true
                        }
                    }
                }
            )

        webView.webChromeClient = object : WebChromeClient() {

            override fun onShowCustomView(
                view: View,
                requestedOrientation: Int,
                callback: CustomViewCallback?
            ) {
                onShowCustomView(view, callback)
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback?) {
                if (customView != null || !isAdded) {
                    customViewCallback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                originalSystemUiVisibility = requireActivity().window.decorView.systemUiVisibility

                binding.customViewContainer.addView(view)
                binding.customViewContainer.isVisible = true
                binding.webLl.isVisible = false
                binding.webControl.isVisible = false

                requireActivity().window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE
                requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            @SuppressLint("SourceLockedOrientationActivity")
            override fun onHideCustomView() {
                if (customView == null)
                    return
                _binding?.apply {
                    customViewContainer.isVisible = false
                    webLl.isVisible = true
                    webControl.isVisible = true
                    customViewContainer.removeView(customView)
                }

                customView = null

                requireActivity().window.decorView.systemUiVisibility = originalSystemUiVisibility
                requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!isBot()) {
                    _binding?.titleTv?.text = title
                }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                if (!isBot()) {
                    icon?.let {
                        _binding?.apply {
                            iconIv.isVisible = true
                            iconIv.setImageBitmap(it)
                        }
                        this@WebFragment.icon = it
                    }
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let {
                    for (code in request.resources) {
                        if (code != PermissionRequest.RESOURCE_VIDEO_CAPTURE && code != PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                            request.deny()
                            return@let
                        }
                    }
                    request.grant(request.resources)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (viewDestroyed()) return false
                uploadMessage?.onReceiveValue(null)
                uploadMessage = filePathCallback
                val intent: Intent? = fileChooserParams?.createIntent()
                if (fileChooserParams?.isCaptureEnabled == true) {
                    if (intent?.type == "video/*") {
                        PermissionBottomSheetDialogFragment.requestVideo(
                            binding.titleTv.text.toString(),
                            app?.name,
                            app?.iconUrl
                        )
                            .setCancelAction {
                                uploadMessage?.onReceiveValue(null)
                                uploadMessage = null
                            }
                            .setGrantedAction {
                                RxPermissions(requireActivity())
                                    .request(Manifest.permission.CAMERA)
                                    .autoDispose(stopScope)
                                    .subscribe(
                                        { granted ->
                                            if (granted) {
                                                startActivityForResult(
                                                    Intent(MediaStore.ACTION_VIDEO_CAPTURE),
                                                    FILE_CHOOSER
                                                )
                                            } else {
                                                context?.openPermissionSetting()
                                            }
                                        },
                                        {
                                        }
                                    )
                            }.show(parentFragmentManager, PermissionBottomSheetDialogFragment.TAG)
                        return true
                    } else if (intent?.type == "image/*") {
                        PermissionBottomSheetDialogFragment.requestCamera(
                            binding.titleTv.text.toString(),
                            app?.name,
                            app?.iconUrl
                        )
                            .setCancelAction {
                                uploadMessage?.onReceiveValue(null)
                                uploadMessage = null
                            }.setGrantedAction {
                                RxPermissions(requireActivity())
                                    .request(Manifest.permission.CAMERA)
                                    .autoDispose(stopScope)
                                    .subscribe(
                                        { granted ->
                                            if (granted) {
                                                openCamera(getImageUri())
                                            } else {
                                                context?.openPermissionSetting()
                                            }
                                        },
                                        {
                                        }
                                    )
                            }.show(parentFragmentManager, PermissionBottomSheetDialogFragment.TAG)
                        return true
                    }
                }
                try {
                    startActivityForResult(intent, FILE_CHOOSER)
                } catch (e: ActivityNotFoundException) {
                    uploadMessage = null
                    toast(R.string.error_file_chooser)
                    return false
                }
                return true
            }
        }
    }

    @Override
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CAMERA) {
            imageUri?.let {
                uploadMessage?.onReceiveValue(arrayOf(it))
                imageUri = null
            }
            uploadMessage = null
        } else if (requestCode == FILE_CHOOSER && resultCode == Activity.RESULT_OK) {
            uploadMessage?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(
                    resultCode,
                    data
                )
            )
            uploadMessage = null
        } else {
            uploadMessage?.onReceiveValue(null)
            uploadMessage = null
        }
    }

    private fun loadWebView() {
        _binding?.let { binding ->
            binding.pb.isVisible = false
            var immersive = false
            app?.capabilities?.let {
                if (it.contains(AppCap.IMMERSIVE.name)) {
                    immersive = true
                }
            }
            app?.name?.let { binding.titleTv.text = it }
            app?.iconUrl?.let {
                binding.iconIv.isVisible = true
                binding.iconIv.loadImage(it)
                binding.titleTv.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginStart = requireContext().dpToPx(10f)
                }
            }
            binding.titleLl.isGone = immersive

            webView.addJavascriptInterface(
                WebAppInterface(
                    requireContext(),
                    conversationId,
                    immersive,
                    reloadThemeAction = { reloadTheme() },
                    playlistAction = { showPlaylist(it) },
                ),
                "MixinContext"
            )

            val extraHeaders = HashMap<String, String>()
            conversationId?.let {
                extraHeaders[Mixin_Conversation_ID_HEADER] = it
            }
            if (isFinished) {
                if (index >= 0) {
                    refreshByLuminance(requireContext().isNightMode(), clips[index].titleColor)
                }
                return
            } else if (webView.url != null) {
                webView.reload()
                return
            }
            webView.loadUrl(url, extraHeaders)
        }
    }

    private fun showPlaylist(playlist: Array<String>) {
        if (viewDestroyed()) return

        if (!checkFloatingPermission()) {
            return
        }
        lifecycleScope.launch {
            musicViewModel.showPlaylist(playlist) {
                if (viewDestroyed()) return@showPlaylist
                if (checkFloatingPermission()) {
                    one.mixin.android.ui.player.collapse(requireActivity(), MUSIC_PLAYLIST)
                } else {
                    requireActivity().showPipPermissionNotification(MusicActivity::class.java, getString(R.string.web_floating_permission))
                }
            }
        }
    }

    private fun reloadTheme() {
        if (viewDestroyed()) return

        lifecycleScope.launch {
            webView.evaluateJavascript(themeColorScript) {
                setStatusBarColor(it)
            }
        }
    }

    private var imageUri: Uri? = null
    private fun getImageUri(): Uri {
        if (imageUri == null) {
            imageUri = Uri.fromFile(requireContext().getOtherPath().createImageTemp())
        }
        return imageUri!!
    }

    private fun isBot() = app != null
    private var hold = false
    private var icon: Bitmap? = null

    private fun generateWebClip(): WebClip {
        val currentUrl = webView.url ?: url
        val v = webView
        val screenshot = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.RGB_565)
        val c = Canvas(screenshot)
        c.translate((-v.scrollX).toFloat(), (-v.scrollY).toFloat())
        v.draw(c)
        return WebClip(
            currentUrl,
            app,
            titleColor,
            app?.name ?: binding.titleTv.text.toString(),
            screenshot,
            icon,
            conversationId,
            webView,
            isFinished
        )
    }

    var titleColor: Int = Color.WHITE

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onDestroyView() {
        webView.stopLoading()
        when {
            hold -> {
                holdClip(requireActivity(), generateWebClip())
            }
            index < 0 -> {
                webView.destroy()
                webView.webViewClient = object : WebViewClient() {}
                webView.webChromeClient = null
            }
            else -> {
                updateClip(index, generateWebClip())
            }
        }
        unregisterForContextMenu(webView)
        binding.webLl.removeView(webView)
        processor.close()
        if (requireActivity().requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            requireActivity().window.decorView.systemUiVisibility = originalSystemUiVisibility
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        super.onDestroyView()
        _binding = null
    }

    private fun showBottomSheet() {
        if (viewDestroyed()) return

        val builder = BottomSheet.Builder(requireActivity())

        val view = View.inflate(
            ContextThemeWrapper(requireActivity(), R.style.Custom),
            R.layout.view_web_bottom_menu,
            null
        )
        val viewBinding = ViewWebBottomMenuBinding.bind(view)
        if (isBot()) {
            app?.let {
                viewBinding.avatar.loadImage(it.iconUrl)
                viewBinding.nameTv.text = it.name
                viewBinding.descTv.text = it.appNumber
            }
            viewBinding.avatar.isVisible = true
        } else {
            viewBinding.nameTv.text = binding.titleTv.text
            viewBinding.descTv.text = webView.url
            viewBinding.avatar.isVisible = false
        }
        builder.setCustomView(view)
        val bottomSheet = builder.create()
        viewBinding.closeIv.setOnClickListener { bottomSheet.dismiss() }

        val shareMenu = menu {
            title = getString(if (isBot()) R.string.about else R.string.share)
            icon = if (isBot()) R.drawable.ic_setting_about else R.drawable.ic_web_share
            action = {
                if (isBot()) {
                    openBot()
                } else {
                    activity?.let {
                        ShareCompat.IntentBuilder
                            .from(it)
                            .setType("text/plain")
                            .setChooserTitle(webView.title)
                            .setText(webView.url)
                            .startChooser()
                        bottomSheet.dismiss()
                    }
                }
                bottomSheet.dismiss()
            }
        }
        val forwardMenu = menu {
            title = getString(R.string.forward)
            icon = R.drawable.ic_web_forward
            action = {
                val currentUrl = webView.url ?: url
                if (isBot()) {
                    app?.appId?.let { id ->
                        lifecycleScope.launch {
                            val app = bottomViewModel.getAppAndCheckUser(id, app?.updatedAt)
                            if (app.matchResourcePattern(currentUrl)) {
                                var webTitle = webView.title
                                if (webTitle.isNullOrBlank()) {
                                    webTitle = app.name
                                }
                                val appCardData = AppCardData(
                                    app.appId,
                                    app.iconUrl,
                                    webTitle,
                                    app.name,
                                    currentUrl,
                                    app.updatedAt
                                )
                                ForwardActivity.show(
                                    requireContext(),
                                    arrayListOf(
                                        ForwardMessage(
                                            ShareCategory.AppCard,
                                            GsonHelper.customGson.toJson(appCardData)
                                        )
                                    ),
                                    ForwardAction.App.Resultless()
                                )
                            } else {
                                ForwardActivity.show(requireContext(), currentUrl)
                            }
                        }
                    }
                } else {
                    ForwardActivity.show(requireContext(), currentUrl)
                }
                bottomSheet.dismiss()
            }
        }
        val refreshMenu = menu {
            title = getString(R.string.action_refresh)
            icon = R.drawable.ic_web_refresh
            action = {
                refresh()
                bottomSheet.dismiss()
            }
        }
        val openMenu = menu {
            title = getString(R.string.action_open)
            icon = R.drawable.ic_web_browser
            action = {
                (webView.url ?: currentUrl)?.let {
                    context?.openUrl(it)
                }
                bottomSheet.dismiss()
            }
        }
        val copyMenu = menu {
            title = getString(R.string.copy_link)
            icon = R.drawable.ic_content_copy
            action = {
                requireContext().getClipboardManager()
                    .setPrimaryClip(ClipData.newPlainText(null, url))
                requireContext().toast(R.string.copy_success)
                bottomSheet.dismiss()
            }
        }
        val isHold = isHold()
        val floatingMenu = menu {
            title = if (isHold) {
                getString(R.string.cancel_floating)
            } else {
                getString(R.string.open_floating)
            }
            icon = if (isHold) {
                R.drawable.ic_web_floating_cancel
            } else {
                R.drawable.ic_web_floating
            }
            action = {
                if (isHold) {
                    releaseClip(index)
                    index = -1
                    bottomSheet.dismiss()
                } else {
                    if (clips.size >= 6) {
                        requireActivity().toast(R.string.web_full)
                        bottomSheet.dismiss()
                    } else if (checkFloatingPermission()) {
                        hold = true
                        bottomSheet.fakeDismiss(false) {
                            requireActivity().finish()
                        }
                    }
                }
            }
        }

        val list = if (isBot()) {
            menuList {
                menuGroup {
                    menu(forwardMenu)
                }
                menuGroup {
                    menu(shareMenu)
                    menu(floatingMenu)
                    menu(refreshMenu)
                }
            }
        } else {
            menuList {
                menuGroup {
                    menu(forwardMenu)
                }
                menuGroup {
                    menu(shareMenu)
                    menu(floatingMenu)
                    menu(copyMenu)
                    menu(refreshMenu)
                    menu(openMenu)
                }
            }
        }
        list.createMenuLayout(requireContext()).let { layout ->
            viewBinding.root.addView(layout)
            layout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = requireContext().dpToPx(30f)
            }
        }
        bottomSheet.show()
    }

    private var permissionAlert: AlertDialog? = null

    private fun checkFloatingPermission() =
        requireContext().checkInlinePermissions {
            if (permissionAlert != null && permissionAlert!!.isShowing) return@checkInlinePermissions

            permissionAlert = AlertDialog.Builder(requireContext())
                .setTitle(R.string.app_name)
                .setMessage(R.string.web_floating_permission)
                .setPositiveButton(R.string.live_setting) { dialog, _ ->
                    try {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${requireContext().packageName}")
                            )
                        )
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                    dialog.dismiss()
                }.show()
        }

    private fun isHold(): Boolean {
        return index >= 0
    }

    private fun refresh() {
        webView.clearCache(true)
        webView.reload()
        _binding?.failLoadView?.isVisible = false
    }

    private fun openBot() = lifecycleScope.launch {
        if (viewDestroyed()) return@launch

        if (app?.appId != null) {
            val u = bottomViewModel.suspendFindUserById(app?.appId!!)
            if (u != null) {
                UserBottomSheetDialogFragment.newInstance(u, conversationId)?.showNow(parentFragmentManager, UserBottomSheetDialogFragment.TAG)
            }
        }
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !requireActivity().isInMultiWindowMode) {
            webView.onPause()
            webView.pauseTimers()
        }
        super.onPause()
    }

    override fun onResume() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !requireActivity().isInMultiWindowMode) {
            webView.onResume()
            webView.resumeTimers()
        }
        super.onResume()
    }

    private fun saveImageFromUrl(url: String?) {
        if (viewDestroyed()) return
        RxPermissions(requireActivity())
            .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .autoDispose(stopScope)
            .subscribe { granted ->
                if (granted) {
                    doAsync {
                        try {
                            val outFile = requireContext().getPublicPicturePath()
                                .createImageTemp(noMedia = false)
                            val encodingPrefix = "base64,"
                            val prefixIndex = url?.indexOf(encodingPrefix)
                            if (url != null && prefixIndex != null && prefixIndex != -1) {
                                val dataStartIndex = prefixIndex + encodingPrefix.length
                                val imageData =
                                    Base64.decode(url.substring(dataStartIndex), Base64.DEFAULT)
                                outFile.copyFromInputStream(ByteArrayInputStream(imageData))
                            } else {
                                val file = Glide.with(MixinApplication.appContext)
                                    .asFile()
                                    .load(url)
                                    .submit()
                                    .get(10, TimeUnit.SECONDS)
                                outFile.copyFromInputStream(FileInputStream(file))
                            }
                            requireContext().sendBroadcast(
                                Intent(
                                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                    Uri.fromFile(outFile)
                                )
                            )
                            uiThread {
                                if (isAdded) toast(
                                    getString(
                                        R.string.save_to,
                                        outFile.absolutePath
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            uiThread { if (isAdded) toast(R.string.save_failure) }
                        }
                    }
                } else {
                    context?.openPermissionSetting()
                }
            }
    }

    private fun setStatusBarColor(content: String) {
        try {
            val color = content.replace("\"", "")
            val c = Color.parseColor(color)
            val dark = isDarkColor(c)
            refreshByLuminance(dark, c)
        } catch (e: Exception) {
            context?.let {
                refreshByLuminance(it.isNightMode(), it.colorFromAttribute(R.attr.icon_white))
            }
        }
    }

    private fun refreshByLuminance(
        dark: Boolean,
        color: Int
    ) {
        if (viewDestroyed()) return

        requireActivity().window.statusBarColor = color
        requireActivity().window.decorView.let {
            if (dark) {
                binding.titleTv.setTextColor(Color.WHITE)
                it.systemUiVisibility =
                    it.systemUiVisibility and SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                binding.titleTv.setTextColor(Color.BLACK)
                it.systemUiVisibility = it.systemUiVisibility or SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
        titleColor = color
        binding.titleLl.setBackgroundColor(color)
        binding.webControl.mode = dark
    }

    @Suppress("DEPRECATION")
    class WebViewClientImpl(
        private val onPageFinishedListener: OnPageFinishedListener,
        val conversationId: String?,
        private val context: Context,
        private val fragmentManager: FragmentManager,
        private val registry: ActivityResultRegistry,
        private val scope: CoroutineScope,
        private val onFinished: (url: String?) -> Unit,
        private val onReceivedError: (request: Int?, description: String?, failingUrl: String?) -> Unit
    ) : WebViewClient() {

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            onPageFinishedListener.onPageFinished()
            onFinished(url)
        }

        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            onReceivedError(errorCode, description, failingUrl)
        }

        override fun onPageCommitVisible(view: WebView?, url: String?) {
            super.onPageCommitVisible(view, url)
            onPageFinishedListener.onPageFinished()
        }

        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (view == null || url == null) {
                return super.shouldOverrideUrlLoading(view, url)
            }
            if (url.isMixinUrl()) {
                val host = view.url?.run { Uri.parse(this).host }
                url.openAsUrl(
                    context,
                    fragmentManager,
                    scope,
                    host = host,
                    currentConversation = conversationId
                ) {}
                return true
            }
            val extraHeaders = HashMap<String, String>()
            conversationId?.let {
                extraHeaders[Mixin_Conversation_ID_HEADER] = it
            }
            if (url.isWebUrl()) {
                view.loadUrl(url, extraHeaders)
                return true
            } else {
                try {
                    val context = view.context
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)

                    if (intent != null) {
                        val packageManager = context.packageManager
                        val info = packageManager.resolveActivity(
                            intent,
                            PackageManager.MATCH_DEFAULT_ONLY
                        )
                        if (info != null) {
                            view.stopLoading()
                            context.startActivity(intent)
                        }
                    }
                } catch (e: URISyntaxException) {
                    view.loadUrl(url, extraHeaders)
                } catch (e: ActivityNotFoundException) {
                    view.loadUrl(url, extraHeaders)
                }
            }
            return true
        }

        interface OnPageFinishedListener {
            fun onPageFinished()
        }
    }

    class WebAppInterface(
        val context: Context,
        val conversationId: String?,
        val immersive: Boolean,
        val reloadThemeAction: () -> Unit,
        val playlistAction: (Array<String>) -> Unit,
    ) {
        @JavascriptInterface
        fun showToast(toast: String) {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun getContext(): String? = Gson().toJson(
            MixinContext(
                conversationId,
                immersive,
                appearance = if (context.isNightMode()) {
                    "dark"
                } else {
                    "light"
                }
            )
        )

        @JavascriptInterface
        fun reloadTheme() {
            reloadThemeAction.invoke()
        }

        @JavascriptInterface
        fun playlist(list: Array<String>) {
            playlistAction.invoke(list)
        }
    }

    class MixinContext(
        @SerializedName("conversation_id")
        val conversationId: String?,
        @SerializedName("immersive")
        val immersive: Boolean,
        @SerializedName("app_version")
        val appVersion: String = BuildConfig.VERSION_NAME,
        @SerializedName("appearance")
        val appearance: String,
        @SerializedName("platform")
        val platform: String = "Android",
        @SerializedName("currency")
        val currency: String = Session.getFiatCurrency(),
        @SerializedName("locale")
        val locale: String = "${Lingver.getInstance().getLocale().language}-${
        Lingver.getInstance().getLocale().country
        }"
    )
}
