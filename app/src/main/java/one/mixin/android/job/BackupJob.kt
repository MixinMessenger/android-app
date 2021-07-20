package one.mixin.android.job

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils.DAY_IN_MILLIS
import android.text.format.DateUtils.WEEK_IN_MILLIS
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.birbit.android.jobqueue.Params
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import one.mixin.android.Constants.Account.PREF_BACKUP
import one.mixin.android.Constants.BackUp.BACKUP_LAST_TIME
import one.mixin.android.Constants.BackUp.BACKUP_PERIOD
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.extension.getCacheMediaPath
import one.mixin.android.extension.getMediaPath
import one.mixin.android.extension.moveChileFileToDir
import one.mixin.android.extension.toast
import one.mixin.android.util.PropertyHelper
import one.mixin.android.util.backup.BackupLiveData
import one.mixin.android.util.backup.BackupNotification
import one.mixin.android.util.backup.Result
import java.io.File

class BackupJob(private val force: Boolean = false) : BaseJob(
    Params(
        if (force) {
            PRIORITY_UI_HIGH
        } else {
            PRIORITY_BACKGROUND
        }
    ).addTags(GROUP).persist()
) {

    companion object {
        private const val serialVersionUID = 1L
        const val GROUP = "BackupJob"
        val backupLiveData = BackupLiveData()
    }

    override fun onRun() = runBlocking {
        val context = MixinApplication.appContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return@runBlocking
        }
        if (force) {
            backup(context)
        } else if (propertyDao.findValueByKey(PREF_BACKUP)?.toBoolean() == true) {
            val option = PropertyHelper.findValueByKey(context, BACKUP_PERIOD)?.toIntOrNull() ?: 0
            if (option in 1..3) {
                val currentTime = System.currentTimeMillis()
                val lastTime = PropertyHelper.findValueByKey(context, BACKUP_LAST_TIME)?.toLongOrNull() ?: currentTime
                val timeDiff = currentTime - lastTime
                if (timeDiff >= when (option) {
                    1 -> DAY_IN_MILLIS
                    2 -> WEEK_IN_MILLIS
                    3 -> DAY_IN_MILLIS * 30
                    else -> Long.MAX_VALUE
                }
                ) {
                    backup(context)
                }
            }
        }
    }

    private fun cleanMedia() {
        val mediaPath = MixinApplication.appContext.getMediaPath()?.absolutePath ?: return
        val mediaCachePath = MixinApplication.appContext.getCacheMediaPath()
        if (!mediaCachePath.exists()) {
            return
        }
        mediaCachePath.listFiles()?.forEach { mediaCacheChild ->
            if (mediaCacheChild.isDirectory) {
                val local = File("$mediaPath${File.separator}${mediaCacheChild.name}${File.separator}")
                mediaCacheChild.moveChileFileToDir(local) { newFile, oldFile ->
                    messageDao.updateMediaUrl(newFile.toUri().toString(), oldFile.toUri().toString())
                }
            }
        }
    }

    @WorkerThread
    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private fun backup(context: Context) = runBlocking {
        try {
            backupLiveData.start()
            BackupNotification.show()
            cleanMedia()
            one.mixin.android.util.backup.backup(context) { result ->
                backupLiveData.setResult(false, result)
                BackupNotification.cancel()
                if (result == Result.SUCCESS) {
                    this.launch {
                        PropertyHelper.updateKeyValue(context, BACKUP_LAST_TIME, System.currentTimeMillis().toString())
                    }
                    context.toast(R.string.backup_success_tip)
                }
            }
        } catch (e: Exception) {
            backupLiveData.setResult(false, null)
            BackupNotification.cancel()
            throw e
        }
    }
}
