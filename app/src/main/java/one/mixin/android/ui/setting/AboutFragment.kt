package one.mixin.android.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_about.*
import kotlinx.android.synthetic.main.view_title.view.*
import one.mixin.android.R
import one.mixin.android.extension.openUrl
import one.mixin.android.ui.common.BaseFragment

class AboutFragment : BaseFragment() {
    companion object {
        const val TAG = "AboutFragment"

        fun newInstance() = AboutFragment()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        layoutInflater.inflate(R.layout.fragment_about, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val versionName = context!!.packageManager.getPackageInfo(context!!.packageName, 0).versionName
        title_view.setSubTitle(getString(R.string.app_name), getString(R.string.about_version, versionName))
        title_view.left_ib.setOnClickListener { activity?.onBackPressed() }
        twitter.setOnClickListener { context?.openUrl("https://twitter.com/MixinMessenger") }
        facebook.setOnClickListener { context?.openUrl("https://fb.com/MixinMessenger") }
        terms.setOnClickListener { context?.openUrl(getString(R.string.landing_terms_url)) }
        privacy.setOnClickListener { context?.openUrl(getString(R.string.landing_privacy_policy_url)) }
    }
}
