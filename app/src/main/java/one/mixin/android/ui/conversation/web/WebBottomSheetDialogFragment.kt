package one.mixin.android.ui.conversation.web

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.ShareCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.tbruyelle.rxpermissions2.RxPermissions
import com.uber.autodispose.autoDisposable
import kotlinx.android.synthetic.main.fragment_web.view.*
import kotlinx.android.synthetic.main.view_web_bottom.view.*
import one.mixin.android.Constants.Mixin_Conversation_ID_HEADER
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.extension.REQUEST_CAMERA
import one.mixin.android.extension.copyFromInputStream
import one.mixin.android.extension.createImageTemp
import one.mixin.android.extension.getImagePath
import one.mixin.android.extension.getPublicPictyresPath
import one.mixin.android.extension.hideKeyboard
import one.mixin.android.extension.isWebUrl
import one.mixin.android.extension.openCamera
import one.mixin.android.extension.openPermissionSetting
import one.mixin.android.extension.openUrl
import one.mixin.android.extension.toast
import one.mixin.android.extension.withArgs
import one.mixin.android.ui.common.MixinBottomSheetDialogFragment
import one.mixin.android.ui.common.QrScanBottomSheetDialogFragment
import one.mixin.android.ui.forward.ForwardActivity
import one.mixin.android.ui.url.isMixinUrl
import one.mixin.android.ui.url.openUrl
import one.mixin.android.util.KeyBoardAssist
import one.mixin.android.widget.BottomSheet
import one.mixin.android.widget.getMaxCustomViewHeight
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.net.URISyntaxException
import java.util.concurrent.TimeUnit

class WebBottomSheetDialogFragment : MixinBottomSheetDialogFragment() {

    companion object {
        const val TAG = "WebBottomSheetDialogFragment"

        const val POS_LOADING = 0
        const val POS_WEB = 1

        private const val FILE_CHOOSER = 0x01

        private const val CONTEXT_MENU_ID_SCAN_IMAGE = 0x11
        private const val CONTEXT_MENU_ID_SAVE_IMAGE = 0x12

        private const val URL = "url"
        private const val CONVERSATION_ID = "conversation_id"
        private const val NAME = "name"
        const val APP_NAME = "app_name"
        const val APP_AVATAR = "app_avatar"
        fun newInstance(url: String, conversationId: String?, name: String? = null, appName: String? = null, appAvatar: String? = null) =
            WebBottomSheetDialogFragment().withArgs {
                putString(URL, url)
                putString(CONVERSATION_ID, conversationId)
                putString(NAME, name)
                putString(APP_NAME, appName)
                putString(APP_AVATAR, appAvatar)
            }
    }

    private val url: String by lazy {
        arguments!!.getString(URL)
    }
    private val conversationId: String? by lazy {
        arguments!!.getString(CONVERSATION_ID)
    }
    private val name: String? by lazy {
        arguments!!.getString(NAME)
    }
    private val appName: String? by lazy {
        arguments!!.getString(APP_NAME)
    }
    private val appAvatar: String? by lazy {
        arguments!!.getString(APP_AVATAR)
    }

    @SuppressLint("RestrictedApi", "SetJavaScriptEnabled")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        contentView = View.inflate(context, R.layout.fragment_web, null)
        contentView.chat_web_view.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK && contentView.chat_web_view.canGoBack()) {
                contentView.chat_web_view.goBack()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }
        registerForContextMenu(contentView.chat_web_view)
        (dialog as BottomSheet).setCustomView(contentView)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        contentView.chat_web_view.hitTestResult?.let {
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

    override fun onContextItemSelected(item: MenuItem): Boolean {
        contentView.chat_web_view.hitTestResult?.let {
            val url = it.extra
            if (item.itemId == CONTEXT_MENU_ID_SCAN_IMAGE) {
                doAsync {
                    try {
                        val bitmap = Glide.with(requireContext())
                            .asBitmap()
                            .load(url)
                            .submit()
                            .get(10, TimeUnit.SECONDS)
                        uiThread {
                            if (isDetached) {
                                return@uiThread
                            }
                            val image = FirebaseVisionImage.fromBitmap(bitmap)
                            val detector = FirebaseVision.getInstance().visionBarcodeDetector
                            detector.detectInImage(image)
                                .addOnSuccessListener { barcodes ->
                                    val result = barcodes.firstOrNull()?.rawValue
                                    if (result != null) {
                                        openUrl(result, requireFragmentManager()) {
                                            QrScanBottomSheetDialogFragment.newInstance(result)
                                                .showNow(requireFragmentManager(), QrScanBottomSheetDialogFragment.TAG)
                                        }
                                    } else {
                                        if (isAdded) toast(R.string.can_not_recognize)
                                    }
                                }
                                .addOnFailureListener {
                                    if (isAdded) toast(R.string.can_not_recognize)
                                }
                        }
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
    @SuppressLint("SetJavaScriptEnabled")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        KeyBoardAssist.assistContent(contentView as ViewGroup)
        contentView.close_iv.setOnClickListener {
            dialog.dismiss()
        }
        Glide.with(this).load(R.drawable.ic_web_loading).into(contentView.loading_iv)

        contentView.chat_web_view.settings.javaScriptEnabled = true
        contentView.chat_web_view.settings.domStorageEnabled = true
        contentView.chat_web_view.settings.useWideViewPort = true
        contentView.chat_web_view.settings.loadWithOverviewMode = true
        contentView.chat_web_view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        contentView.chat_web_view.addJavascriptInterface(WebAppInterface(context!!, conversationId), "MixinContext")
        contentView.chat_web_view.webViewClient = WebViewClientImpl(object : WebViewClientImpl.OnPageFinishedListener {
            override fun onPageFinished() {
                contentView.va.displayedChild = POS_WEB
                contentView.title_view.visibility = View.VISIBLE
            }
        }, conversationId, this.requireFragmentManager())

        contentView.chat_web_view.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.equals(url)) {
                    contentView.title_view.text = title
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

            override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                uploadMessage?.onReceiveValue(null)
                uploadMessage = filePathCallback
                val intent: Intent? = fileChooserParams?.createIntent()
                if (fileChooserParams?.isCaptureEnabled == true) {
                    if (intent?.type == "video/*") {
                        PermissionBottomSheetDialogFragment.requestVideo(contentView.title_view.text.toString(), appName, appAvatar)
                            .setCancelAction {
                                uploadMessage?.onReceiveValue(null)
                                uploadMessage = null
                            }
                            .setGrantedAction {
                                RxPermissions(requireActivity())
                                    .request(Manifest.permission.CAMERA)
                                    .autoDisposable(stopScope)
                                    .subscribe({ granted ->
                                        if (granted) {
                                            startActivityForResult(Intent(MediaStore.ACTION_VIDEO_CAPTURE), FILE_CHOOSER)
                                        } else {
                                            context?.openPermissionSetting()
                                        }
                                    }, {
                                    })
                            }.show(fragmentManager, PermissionBottomSheetDialogFragment.TAG)
                        return true
                    } else if (intent?.type == "image/*") {
                        PermissionBottomSheetDialogFragment.requestCamera(contentView.title_view.text.toString(), appName, appAvatar)
                            .setCancelAction {
                                uploadMessage?.onReceiveValue(null)
                                uploadMessage = null
                            }.setGrantedAction {
                                RxPermissions(requireActivity())
                                    .request(Manifest.permission.CAMERA)
                                    .autoDisposable(stopScope)
                                    .subscribe({ granted ->
                                        if (granted) {
                                            openCamera(getImageUri())
                                        } else {
                                            context?.openPermissionSetting()
                                        }
                                    }, {
                                    })
                            }.show(fragmentManager, PermissionBottomSheetDialogFragment.TAG)
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

        contentView.more_iv.setOnClickListener {
            showBottomSheet()
        }

        name?.let {
            contentView.title_view.text = it
            contentView.va.displayedChild = POS_WEB
            contentView.title_view.visibility = View.VISIBLE
        }

        dialog.setOnShowListener {
            val extraHeaders = HashMap<String, String>()
            conversationId?.let {
                extraHeaders[Mixin_Conversation_ID_HEADER] = it
            }
            contentView.chat_web_view.loadUrl(url, extraHeaders)
        }
        dialog.setOnDismissListener {
            contentView.hideKeyboard()
            contentView.chat_web_view.stopLoading()
            contentView.chat_web_view.webViewClient = null
            contentView.chat_web_view.webChromeClient = null
            dismiss()
        }

        // workaround with realSize() not get the correct value in some device.
        contentView.postDelayed(setCustomViewHeightRunnable, 100)
    }

    private val setCustomViewHeightRunnable = Runnable {
        dialog?.let {
            (it as BottomSheet).setCustomViewHeight(it.getMaxCustomViewHeight())
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
            uploadMessage?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            uploadMessage = null
        } else {
            uploadMessage?.onReceiveValue(null)
            uploadMessage = null
        }
    }

    private var imageUri: Uri? = null
    private fun getImageUri(): Uri {
        if (imageUri == null) {
            imageUri = Uri.fromFile(requireContext().getImagePath().createImageTemp())
        }
        return imageUri!!
    }

    override fun onStop() {
        super.onStop()
        stopAudio()
    }

    override fun dismiss() {
        stopAudio()
        super.dismiss()
    }

    override fun onDestroyView() {
        contentView.chat_web_view.stopLoading()
        contentView.chat_web_view.webViewClient = null
        contentView.chat_web_view.webChromeClient = null
        unregisterForContextMenu(contentView.chat_web_view)
        contentView.removeCallbacks(setCustomViewHeightRunnable)
        super.onDestroyView()
    }

    private fun stopAudio() {
        context?.getSystemService<AudioManager>()?.requestAudioFocus({},
            AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }

    private fun showBottomSheet() {
        val builder = BottomSheet.Builder(requireActivity())
        val view = View.inflate(ContextThemeWrapper(requireActivity(), R.style.Custom), R.layout.view_web_bottom, null)
        builder.setCustomView(view)
        val bottomSheet = builder.create()
        view.forward.setOnClickListener {
            ForwardActivity.show(requireContext(), contentView.chat_web_view.url)
            bottomSheet.dismiss()
        }
        view.share.setOnClickListener {
            ShareCompat.IntentBuilder
                .from(activity)
                .setType("text/plain")
                .setChooserTitle(name)
                .setText(url)
                .startChooser()
            bottomSheet.dismiss()
        }
        view.refresh.setOnClickListener {
            contentView.chat_web_view.clearCache(true)
            contentView.chat_web_view.reload()
            contentView.va.displayedChild = POS_LOADING
            bottomSheet.dismiss()
        }
        view.open.setOnClickListener {
            context?.openUrl(contentView.chat_web_view.url)
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !requireActivity().isInMultiWindowMode) {
            contentView.chat_web_view.onPause()
            contentView.chat_web_view.pauseTimers()
        }
        super.onPause()
    }

    override fun onResume() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !requireActivity().isInMultiWindowMode) {
            contentView.chat_web_view.onResume()
            contentView.chat_web_view.resumeTimers()
        }
        super.onResume()
    }

    private fun saveImageFromUrl(url: String?) {
        if (!isAdded) return
        RxPermissions(requireActivity())
            .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .autoDisposable(stopScope)
            .subscribe { granted ->
                if (granted) {
                    doAsync {
                        try {
                            val outFile = requireContext().getPublicPictyresPath().createImageTemp(noMedia = false)
                            val encodingPrefix = "base64,"
                            val prefixIndex = url?.indexOf(encodingPrefix)
                            if (url != null && prefixIndex != null && prefixIndex != -1) {
                                val dataStartIndex = prefixIndex + encodingPrefix.length
                                val imageData = Base64.decode(url.substring(dataStartIndex), Base64.DEFAULT)
                                outFile.copyFromInputStream(ByteArrayInputStream(imageData))
                            } else {
                                val file = Glide.with(MixinApplication.appContext)
                                    .asFile()
                                    .load(url)
                                    .submit()
                                    .get(10, TimeUnit.SECONDS)
                                outFile.copyFromInputStream(FileInputStream(file))
                            }
                            requireContext().sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outFile)))
                            uiThread { toast(R.string.save_success) }
                        } catch (e: Exception) {
                            uiThread { toast(R.string.save_failure) }
                        }
                    }
                } else {
                    context?.openPermissionSetting()
                }
            }
    }

    class WebViewClientImpl(
        private val onPageFinishedListener: OnPageFinishedListener,
        val conversationId: String?,
        private val fragmentManager: FragmentManager
    ) : WebViewClient() {

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            onPageFinishedListener.onPageFinished()
        }

        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (view == null || url == null) {
                return false
            }
            if (isMixinUrl(url)) {
                openUrl(url, fragmentManager, {})
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
                        view.stopLoading()

                        val packageManager = context.packageManager
                        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                        if (info != null) {
                            context.startActivity(intent)
                        } else {
                            view.loadUrl(url, extraHeaders)
                            // or call external broswer
                            //                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl));
                            //                    context.startActivity(browserIntent);
                        }
                    }
                } catch (e: URISyntaxException) {
                    view.loadUrl(url, extraHeaders)
                }
            }

            return true
        }

        interface OnPageFinishedListener {
            fun onPageFinished()
        }
    }

    class WebAppInterface(val context: Context, val conversationId: String?) {
        @JavascriptInterface
        fun showToast(toast: String) {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun getContext(): String? {
            return if (conversationId != null) {
                Gson().toJson(MixinContext(conversationId))
            } else {
                null
            }
        }
    }

    class MixinContext(
        @SerializedName("conversation_id")
        val conversationId: String?
    )
}
