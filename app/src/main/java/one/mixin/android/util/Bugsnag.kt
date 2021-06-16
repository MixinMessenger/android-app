package one.mixin.android.util

import com.bugsnag.android.Bugsnag

val bugsnag by lazy {
    try {
        Bugsnag.getClient()
    } catch (e: Exception) {
        null
    }
}