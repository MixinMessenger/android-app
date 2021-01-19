package one.mixin.android.ui.setting

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import one.mixin.android.Constants.Download.AUTO_DOWNLOAD_DOCUMENT
import one.mixin.android.Constants.Download.AUTO_DOWNLOAD_MOBILE
import one.mixin.android.Constants.Download.AUTO_DOWNLOAD_PHOTO
import one.mixin.android.Constants.Download.AUTO_DOWNLOAD_ROAMING
import one.mixin.android.Constants.Download.AUTO_DOWNLOAD_VIDEO
import one.mixin.android.Constants.Download.AUTO_DOWNLOAD_WIFI
import one.mixin.android.R
import one.mixin.android.databinding.FragmentStorageDataBinding
import one.mixin.android.databinding.ViewStotageDataBinding
import one.mixin.android.extension.addFragment
import one.mixin.android.extension.alertDialogBuilder
import one.mixin.android.extension.autoDownloadDocument
import one.mixin.android.extension.autoDownloadPhoto
import one.mixin.android.extension.autoDownloadVideo
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.getAutoDownloadMobileValue
import one.mixin.android.extension.getAutoDownloadRoamingValue
import one.mixin.android.extension.getAutoDownloadWifiValue
import one.mixin.android.extension.putInt
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.util.viewBinding
import org.jetbrains.anko.layoutInflater

@AndroidEntryPoint
class SettingDataStorageFragment : BaseFragment(R.layout.fragment_storage_data) {
    companion object {
        const val TAG = "SettingDataStorageFragment"

        fun newInstance(): SettingDataStorageFragment {
            return SettingDataStorageFragment()
        }
    }

    private val binding by viewBinding(FragmentStorageDataBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            titleView.leftIb.setOnClickListener { activity?.onBackPressed() }
            storageRl.setOnClickListener {
                requireActivity().addFragment(
                    this@SettingDataStorageFragment,
                    SettingStorageFragment.newInstance(),
                    SettingStorageFragment.TAG
                )
            }
            storageMobile.setOnClickListener {
                lifecycleScope.launch {
                    showMenu(AUTO_DOWNLOAD_MOBILE, requireContext().getAutoDownloadMobileValue(), R.string.setting_data_mobile)
                }
            }
            storageWifi.setOnClickListener {
                lifecycleScope.launch {
                    showMenu(
                        AUTO_DOWNLOAD_WIFI,
                        requireContext().getAutoDownloadWifiValue(),
                        R.string
                            .setting_data_wifi
                    )
                }
            }
            storageRoaming.setOnClickListener {
                lifecycleScope.launch {
                    showMenu(
                        AUTO_DOWNLOAD_ROAMING,
                        requireContext().getAutoDownloadRoamingValue(),
                        R.string
                            .setting_data_roaming
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() = lifecycleScope.launch {
        binding.apply {
            storageMobileInfo.text = getInfo(requireContext().getAutoDownloadMobileValue())
            storageWifiInfo.text = getInfo(requireContext().getAutoDownloadWifiValue())
            storageRoamingInfo.text = getInfo(requireContext().getAutoDownloadRoamingValue())
        }
    }

    private suspend fun getInfo(value: Int): String {
        val list = mutableListOf<String>()
        if (autoDownloadPhoto(value)) list.add(getString(R.string.setting_data_photo))
        if (autoDownloadVideo(value)) list.add(getString(R.string.setting_data_video))
        if (autoDownloadDocument(value)) list.add(getString(R.string.setting_data_documents))
        val divide = getString(R.string.divide)
        if (list.isEmpty()) return getString(R.string.setting_data_noting)
        val str = StringBuffer()
        list.forEachIndexed { index, s ->
            if (index != 0) {
                str.append(divide)
            }
            str.append(s)
        }
        return str.toString()
    }

    private var menuDialog: AlertDialog? = null
    private suspend fun showMenu(key: String, value: Int, @StringRes titleId: Int) {
        menuDialog?.dismiss()
        val menuBinding = ViewStotageDataBinding.inflate(requireContext().layoutInflater, null, false).apply {
            this.checkPhoto.apply {
                setName(R.string.setting_data_photo)
                isChecked = autoDownloadPhoto(value)
            }
            this.checkVideo.apply {
                setName(R.string.setting_data_video)
                isChecked = autoDownloadVideo(value)
            }
            this.checkDocument.apply {
                setName(R.string.setting_data_documents)
                isChecked = autoDownloadDocument(value)
            }
        }
        menuDialog = alertDialogBuilder()
            .setTitle(titleId)
            .setView(menuBinding.root)
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                var localValue = 0
                if (menuBinding.checkPhoto.isChecked) {
                    localValue += (AUTO_DOWNLOAD_PHOTO)
                }
                if (menuBinding.checkVideo.isChecked) {
                    localValue += (AUTO_DOWNLOAD_VIDEO)
                }
                if (menuBinding.checkDocument.isChecked) {
                    localValue += (AUTO_DOWNLOAD_DOCUMENT)
                }
                defaultSharedPreferences.putInt(key, localValue)
                refresh()
                dialog.dismiss()
            }.create().apply {
                this.window?.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
            }
        menuDialog?.show()
    }
}
