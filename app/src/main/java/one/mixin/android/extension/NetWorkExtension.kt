@file:Suppress("DEPRECATION")

package one.mixin.android.extension

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import one.mixin.android.Constants
import one.mixin.android.Constants.Download.MOBILE_DEFAULT
import one.mixin.android.Constants.Download.ROAMING_DEFAULT
import one.mixin.android.Constants.Download.WIFI_DEFAULT
import one.mixin.android.util.PropertyHelper
import timber.log.Timber

val autoDownloadPhoto: suspend (value: Int) -> Boolean = {
    it.or(0x110) == 0x111
}
val autoDownloadVideo: suspend (value: Int) -> Boolean = {
    it.or(0x101) == 0x111
}
val autoDownloadDocument: suspend (value: Int) -> Boolean = {
    it.or(0x011) == 0x111
}

fun Context.isConnectedToWiFi(): Boolean {
    try {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        if (netInfo != null && netInfo.state == NetworkInfo.State.CONNECTED) {
            return true
        }
    } catch (e: Exception) {
        Timber.e(e)
    }

    return false
}

fun Context.isRoaming(): Boolean {
    try {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = connectivityManager.activeNetworkInfo
        if (netInfo != null) {
            return netInfo.isRoaming
        }
    } catch (e: Exception) {
        Timber.e(e)
    }

    return false
}

suspend fun Context.differentNetWorkAction(wifiAction: suspend () -> Unit, mobileAction: suspend () -> Unit, roaming: suspend () -> Unit) {
    when {
        isConnectedToWiFi() -> {
            wifiAction()
        }
        isRoaming() -> {
            roaming()
        }
        else -> {
            mobileAction()
        }
    }
}

suspend fun Context.autoDownload(support: suspend (value: Int) -> Boolean, action: () -> Unit) {
    if (hasWritePermission()) {
        differentNetWorkAction(
            {
                if (support(getAutoDownloadWifiValue())) {
                    action()
                }
            },
            {
                if (support(getAutoDownloadMobileValue())) {
                    action()
                }
            },
            {
                if (support(getAutoDownloadRoamingValue())) {
                    action()
                }
            }
        )
    }
}

suspend fun Context.getAutoDownloadWifiValue() = PropertyHelper.findValueByKey(this, Constants.Download.AUTO_DOWNLOAD_WIFI)?.toIntOrNull() ?: WIFI_DEFAULT
suspend fun Context.getAutoDownloadMobileValue() = PropertyHelper.findValueByKey(this, Constants.Download.AUTO_DOWNLOAD_MOBILE)?.toIntOrNull() ?: MOBILE_DEFAULT
suspend fun Context.getAutoDownloadRoamingValue() = PropertyHelper.findValueByKey(this, Constants.Download.AUTO_DOWNLOAD_ROAMING)?.toIntOrNull() ?: ROAMING_DEFAULT
