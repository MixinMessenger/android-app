package one.mixin.android.job

import com.birbit.android.jobqueue.Params
import kotlinx.coroutines.runBlocking
import one.mixin.android.Constants
import one.mixin.android.Constants.Account.PREF_BACKUP
import one.mixin.android.MixinApplication
import one.mixin.android.extension.getBackupPath
import one.mixin.android.extension.hasWritePermission
import one.mixin.android.util.PropertyHelper
import one.mixin.android.util.backup.findOldBackupSync
import timber.log.Timber
import java.io.File

class BackupMigrationJob : BaseJob(Params(PRIORITY_LOWER).groupBy(GROUP_ID).persist()) {
    companion object {
        private const val GROUP_ID = "backup_migration"
    }

    override fun onRun() = runBlocking {
        val startTime = System.currentTimeMillis()
        if (!hasWritePermission()) return@runBlocking

        if (propertyDao.findValueByKey(PREF_BACKUP)?.toBoolean() != true) {
            val oldBackup = findOldBackupSync(MixinApplication.appContext)
            if (oldBackup != null && oldBackup.exists()) {
                MixinApplication.get().getBackupPath(true)?.let { backupDir ->
                    oldBackup.renameTo(File("$backupDir${File.separator}${Constants.DataBase.DB_NAME}"))
                }
            }
            PropertyHelper.updateKeyValue(MixinApplication.appContext, PREF_BACKUP, true.toString())
        }
        Timber.d("Backup migration cost: ${System.currentTimeMillis() - startTime} ms")
    }
}
