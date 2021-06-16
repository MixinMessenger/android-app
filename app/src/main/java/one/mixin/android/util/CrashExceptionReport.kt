package one.mixin.android.util

import androidx.collection.ArrayMap
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.microsoft.appcenter.crashes.Crashes
import org.jetbrains.anko.getStackTraceString

fun reportException(e: Throwable) {
    bugsnag?.notify(e)
    FirebaseCrashlytics.getInstance().recordException(e)
    Crashes.trackError(e)
}

fun reportException(msg: String, e: Throwable) {
    bugsnag?.notify(e)
    FirebaseCrashlytics.getInstance().log(msg + e.getStackTraceString())
    Crashes.trackError(
        e,
        ArrayMap<String, String>().apply {
            put("log", msg)
        },
        null
    )
}

fun reportExoPlayerException(prefix: String, error: ExoPlaybackException) {
    val cause = error.cause
    if (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 404) {
        return
    }
    val msg = "$prefix onPlayerError type: ${error.type}, cause: $cause"
    reportException(msg, error)
}
