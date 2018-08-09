package one.mixin.android.ui.common

import android.support.v4.app.Fragment
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider
import one.mixin.android.di.Injectable

open class BaseFragment : Fragment(), Injectable {
    protected val scopeProvider: AndroidLifecycleScopeProvider by lazy { AndroidLifecycleScopeProvider.from(this) }
    open fun onBackPressed() = false
}