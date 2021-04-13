package one.mixin.android.extension

import android.content.Context
import android.net.Uri
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import one.mixin.android.Constants
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.crypto.Base64
import one.mixin.android.db.MixinDatabase
import one.mixin.android.ui.common.QrScanBottomSheetDialogFragment
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.common.share.ShareMessageBottomSheetDialogFragment
import one.mixin.android.ui.conversation.link.LinkBottomSheetDialogFragment
import one.mixin.android.ui.device.ConfirmBottomFragment
import one.mixin.android.ui.forward.ForwardActivity
import one.mixin.android.ui.qr.donateSupported
import one.mixin.android.ui.web.WebActivity
import one.mixin.android.vo.App
import one.mixin.android.vo.AppCardData
import one.mixin.android.vo.ForwardAction
import one.mixin.android.vo.ForwardMessage
import one.mixin.android.vo.ShareCategory
import one.mixin.android.vo.getShareCategory
import one.mixin.android.vo.notMessengerUser
import timber.log.Timber

fun String.openAsUrlOrWeb(
    context: Context,
    conversationId: String?,
    supportFragmentManager: FragmentManager,
    scope: CoroutineScope,
    app: App? = null,
    appCard: AppCardData? = null
) = openAsUrl(context, supportFragmentManager, scope, currentConversation = conversationId, app = app) {
    WebActivity.show(context, this, conversationId, app, appCard)
}

fun String.openAsUrlOrQrScan(
    context: Context,
    supportFragmentManager: FragmentManager,
    scope: CoroutineScope
) = openAsUrl(context, supportFragmentManager, scope) {
    QrScanBottomSheetDialogFragment.newInstance(this)
        .showNow(supportFragmentManager, QrScanBottomSheetDialogFragment.TAG)
}

fun String.isMixinUrl(): Boolean {
    return if (startsWith(Constants.Scheme.HTTPS_PAY, true) ||
        startsWith(Constants.Scheme.PAY, true) ||
        startsWith(Constants.Scheme.USERS, true) ||
        startsWith(Constants.Scheme.HTTPS_USERS, true) ||
        startsWith(Constants.Scheme.DEVICE, true) ||
        startsWith(Constants.Scheme.SEND, true) ||
        startsWith(Constants.Scheme.ADDRESS, true) ||
        startsWith(Constants.Scheme.WITHDRAWAL, true) ||
        startsWith(Constants.Scheme.APPS, true) ||
        startsWith(Constants.Scheme.SNAPSHOTS, true)
    ) {
        true
    } else {
        val segments = Uri.parse(this).pathSegments
        if (startsWith(Constants.Scheme.HTTPS_CODES, true) ||
            startsWith(Constants.Scheme.HTTPS_USERS, true) ||
            startsWith(Constants.Scheme.HTTPS_APPS, true)
        ) {
            segments.size >= 2 && segments[1].isUUID()
        } else if (startsWith(Constants.Scheme.CODES, true) ||
            startsWith(Constants.Scheme.USERS, true) ||
            startsWith(Constants.Scheme.APPS, true)
        ) {
            segments.size >= 1 && segments[0].isUUID()
        } else if (startsWith(Constants.Scheme.TRANSFER, true)) {
            segments.size >= 1 && segments[0].isUUID()
        } else if (startsWith(Constants.Scheme.HTTPS_TRANSFER, true)) {
            segments.size >= 2 && segments[1].isUUID()
        } else if (startsWith(Constants.Scheme.HTTPS_ADDRESS, true)) {
            true
        } else startsWith(Constants.Scheme.HTTPS_WITHDRAWAL, true)
    }
}

fun String.openAsUrl(
    context: Context,
    supportFragmentManager: FragmentManager,
    scope: CoroutineScope,
    currentConversation: String? = null,
    app: App? = null,
    host: String? = null,
    extraAction: () -> Unit,
) {
    if (startsWith(Constants.Scheme.SEND, true)) {
        val uri = Uri.parse(this)
        uri.handleSchemeSend(
            context, supportFragmentManager, currentConversation, app, host,
            onError = { err ->
                Timber.e(IllegalStateException(err))
            }
        )
    } else if (startsWith(Constants.Scheme.DEVICE, true)) {
        ConfirmBottomFragment.show(MixinApplication.appContext, supportFragmentManager, this)
    } else if (isUserScheme() || isAppScheme()) {
        checkUserOrApp(context, supportFragmentManager, scope)
    } else {
        if (isMixinUrl() || isDonateUrl()) {
            LinkBottomSheetDialogFragment.newInstance(this)
                .showNow(supportFragmentManager, LinkBottomSheetDialogFragment.TAG)
        } else {
            extraAction()
        }
    }
}

fun Uri.checkUserOrApp(
    context: Context,
    supportFragmentManager: FragmentManager,
    scope: CoroutineScope
) = this.toString().checkUserOrApp(context, supportFragmentManager, scope)

fun String.checkUserOrApp(
    context: Context,
    supportFragmentManager: FragmentManager,
    scope: CoroutineScope
) {
    val isAppScheme = isAppScheme()
    val ctx = MixinApplication.appContext
    val uri = Uri.parse(this)
    val segments = uri.pathSegments
    val userId = if (segments.size >= 2) {
        segments[1]
    } else {
        segments[0]
    }
    if (!userId.isUUID()) {
        ctx.toast(getUserOrAppNotFoundTip(isAppScheme))
        return
    }

    val db = MixinDatabase.getDatabase(ctx)
    val userDao = db.userDao()
    val appDao = db.appDao()
    scope.launch {
        val user = userDao.suspendFindUserById(userId)
        if (user == null) {
            val bottomSheet = LinkBottomSheetDialogFragment.newInstance(uri.toString())
            bottomSheet.showNow(supportFragmentManager, LinkBottomSheetDialogFragment.TAG)
        } else {
            val isOpenApp = isAppScheme && uri.getQueryParameter("action") == "open"
            if (user.notMessengerUser()) {
                return@launch
            } else if (isOpenApp && user.appId != null) {
                val app = appDao.findAppById(user.appId!!)
                if (app != null) {
                    val url = try {
                        app.homeUri.appendQueryParamsFromOtherUri(uri)
                    } catch (e: Exception) {
                        app.homeUri
                    }
                    WebActivity.show(context, url, null, app)
                    return@launch
                }
            }
            UserBottomSheetDialogFragment.newInstance(user)?.showNow(supportFragmentManager, UserBottomSheetDialogFragment.TAG)
        }
    }
}

fun String.isDonateUrl() = donateSupported.any { startsWith(it) }

private fun String.isUserScheme() = startsWith(Constants.Scheme.USERS, true) ||
    startsWith(Constants.Scheme.HTTPS_USERS, true)

private fun String.isAppScheme() = startsWith(Constants.Scheme.APPS, true) ||
    startsWith(Constants.Scheme.HTTPS_APPS, true)

private fun getUserOrAppNotFoundTip(isApp: Boolean) =
    if (isApp) R.string.error_app_not_found else R.string.error_user_not_found

fun Uri.handleSchemeSend(
    context: Context,
    supportFragmentManager: FragmentManager,
    currentConversation: String? = null,
    app: App? = null,
    host: String? = null,
    showNow: Boolean = true,
    afterShareText: (() -> Unit)? = null,
    afterShareData: (() -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
) {
    val text = this.getQueryParameter("text")
    text.notNullWithElse(
        {
            ForwardActivity.show(
                context,
                arrayListOf(ForwardMessage(ShareCategory.Text, it)),
                ForwardAction.App.Resultless()
            )
            afterShareText?.invoke()
        },
        {
            val category = this.getQueryParameter("category")
            val conversationId = this.getQueryParameter("conversation").let {
                if (it == currentConversation) {
                    it
                } else {
                    null
                }
            }
            val data = this.getQueryParameter("data")
            val shareCategory = category?.getShareCategory()
            if (shareCategory != null && data != null) {
                try {
                    afterShareData?.invoke()
                    val fragment = ShareMessageBottomSheetDialogFragment.newInstance(
                        ForwardMessage(shareCategory, String(Base64.decode(data))),
                        conversationId, app, host
                    )
                    if (showNow) {
                        fragment.showNow(supportFragmentManager, ShareMessageBottomSheetDialogFragment.TAG)
                    } else {
                        fragment.show(supportFragmentManager, ShareMessageBottomSheetDialogFragment.TAG)
                    }
                } catch (e: Exception) {
                    onError?.invoke("Error data:${e.message}")
                }
            } else {
                onError?.invoke("Error data")
            }
        }
    )
}
