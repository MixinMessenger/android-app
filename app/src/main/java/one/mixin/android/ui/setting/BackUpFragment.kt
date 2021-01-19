package one.mixin.android.ui.setting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tbruyelle.rxpermissions2.RxPermissions
import com.uber.autodispose.autoDispose
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.Constants.BackUp.BACKUP_PERIOD
import one.mixin.android.R
import one.mixin.android.databinding.FragmentBackupBinding
import one.mixin.android.extension.alertDialogBuilder
import one.mixin.android.extension.fileSize
import one.mixin.android.extension.getRelativeTimeSpan
import one.mixin.android.extension.openPermissionSetting
import one.mixin.android.extension.toast
import one.mixin.android.extension.viewDestroyed
import one.mixin.android.job.BackupJob
import one.mixin.android.job.MixinJobManager
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.util.PropertyHelper
import one.mixin.android.util.backup.Result
import one.mixin.android.util.backup.delete
import one.mixin.android.util.backup.findBackup
import one.mixin.android.util.viewBinding
import javax.inject.Inject

@AndroidEntryPoint
class BackUpFragment : BaseFragment(R.layout.fragment_backup) {
    companion object {
        const val TAG = "BackUpFragment"
        fun newInstance() = BackUpFragment()
    }

    @Inject
    lateinit var jobManager: MixinJobManager

    private val binding by viewBinding(FragmentBackupBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            backupInfo.text = getString(R.string.backup_external_storage, "")
            backupAuto.setOnClickListener {
                showBackupDialog()
            }
            lifecycleScope.launch {
                binding.backupAutoTv.text = options[loadBackupPeriod()]
            }
            titleView.leftIb.setOnClickListener { activity?.onBackPressed() }
            backupBn.setOnClickListener {
                RxPermissions(requireActivity())
                    .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .autoDispose(stopScope)
                    .subscribe(
                        { granted ->
                            if (granted) {
                                jobManager.addJobInBackground(BackupJob(true))
                            } else {
                                context?.openPermissionSetting()
                            }
                        },
                        {
                            context?.openPermissionSetting()
                        }
                    )
            }
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                findBackUp()
            }
            deleteBn.setOnClickListener {
                deleteBn.visibility = GONE
                lifecycleScope.launch {
                    if (delete(requireContext())) {
                        findBackUp()
                    } else {
                        withContext(Dispatchers.Main) {
                            deleteBn.visibility = VISIBLE
                        }
                    }
                }
            }
        }
        BackupJob.backupLiveData.observe(
            viewLifecycleOwner,
            {
                if (it) {
                    binding.backupBn.visibility = INVISIBLE
                    binding.progressGroup.visibility = VISIBLE
                } else {
                    binding.backupBn.visibility = VISIBLE
                    binding.progressGroup.visibility = GONE
                    when (BackupJob.backupLiveData.result) {
                        Result.SUCCESS -> findBackUp()
                        Result.NO_AVAILABLE_MEMORY ->
                            alertDialogBuilder()
                                .setMessage(R.string.backup_no_available_memory)
                                .setNegativeButton(R.string.group_ok) { dialog, _ -> dialog.dismiss() }
                                .show()
                        Result.FAILURE -> toast(R.string.backup_failure_tip)
                        else -> throw IllegalStateException("Unknown")
                    }
                }
            }
        )
    }

    private val options by lazy {
        requireContext().resources.getStringArray(R.array.backup_dialog_list)
    }

    private suspend fun loadBackupPeriod(): Int =
        PropertyHelper.findValueByKey(requireContext(), BACKUP_PERIOD)?.toIntOrNull() ?: 0

    private fun showBackupDialog() = lifecycleScope.launch {
        val builder = alertDialogBuilder()
        builder.setTitle(R.string.backup_dialog_title)

        val checkedItem = loadBackupPeriod()
        builder.setSingleChoiceItems(options, checkedItem) { dialog, which ->
            binding.backupAutoTv.text = options[which]
            lifecycleScope.launch {
                PropertyHelper.updateKeyValue(requireContext(), BACKUP_PERIOD, which.toString())
            }
            dialog.dismiss()
        }
        builder.setNegativeButton(android.R.string.cancel) { _, _ ->
        }
        val dialog = builder.create()
        dialog.show()
    }

    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private fun findBackUp() = lifecycleScope.launch(Dispatchers.IO) {
        val file = findBackup(requireContext(), coroutineContext)
        withContext(Dispatchers.Main) {
            if (viewDestroyed()) return@withContext
            binding.apply {
                if (file == null) {
                    backupInfo.text = getString(R.string.backup_external_storage, getString(R.string.backup_never))
                    backupSize.visibility = GONE
                    backupPath.visibility = GONE
                    deleteBn.visibility = GONE
                } else {
                    val time = file.lastModified().run {
                        this.getRelativeTimeSpan()
                    }
                    backupInfo.text = getString(R.string.backup_external_storage, time)
                    backupPath.text = getString(R.string.restore_path, file.parentFile?.parentFile?.absolutePath)
                    backupSize.text = getString(R.string.restore_size, file.length().fileSize())
                    backupSize.visibility = VISIBLE
                    backupPath.visibility = VISIBLE
                    deleteBn.visibility = VISIBLE
                }
            }
        }
    }
}
