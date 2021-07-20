package one.mixin.android.util

import android.content.Context
import one.mixin.android.Constants.Account.PREF_ATTACHMENT
import one.mixin.android.Constants.Account.PREF_ATTACHMENT_LAST
import one.mixin.android.Constants.Account.PREF_ATTACHMENT_OFFSET
import one.mixin.android.Constants.Account.PREF_BACKUP
import one.mixin.android.Constants.Account.PREF_DUPLICATE_TRANSFER
import one.mixin.android.Constants.Account.PREF_FTS4_UPGRADE
import one.mixin.android.Constants.Account.PREF_STRANGER_TRANSFER
import one.mixin.android.Constants.Account.PREF_SYNC_FTS4_OFFSET
import one.mixin.android.Constants.BackUp.BACKUP_LAST_TIME
import one.mixin.android.Constants.BackUp.BACKUP_PERIOD
import one.mixin.android.Constants.Download.AUTO_DOWNLOAD_MOBILE
import one.mixin.android.Constants.Download.AUTO_DOWNLOAD_ROAMING
import one.mixin.android.Constants.Download.AUTO_DOWNLOAD_WIFI
import one.mixin.android.Constants.Download.MOBILE_DEFAULT
import one.mixin.android.Constants.Download.ROAMING_DEFAULT
import one.mixin.android.Constants.Download.WIFI_DEFAULT
import one.mixin.android.db.MixinDatabase
import one.mixin.android.db.PropertyDao
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.nowInUtc
import one.mixin.android.vo.Property

object PropertyHelper {

    private const val PREF_PROPERTY_MIGRATED = "pref_property_migrated"

    suspend fun checkFts4Upgrade(context: Context, action: () -> Unit) {
        checkWithKey(context, PREF_FTS4_UPGRADE, true.toString(), action)
    }

    suspend fun checkAttachmentMigrated(context: Context, action: () -> Unit) {
        checkWithKey(context, PREF_ATTACHMENT, true.toString(), action)
    }

    suspend fun checkBackupMigrated(context: Context, action: () -> Unit) {
        checkWithKey(context, PREF_BACKUP, true.toString(), action)
    }

    suspend fun checkMigrated(context: Context): PropertyDao {
        val propertyDao = MixinDatabase.getDatabase(context).propertyDao()
        if (!hasMigrated(propertyDao)) {
            migrateProperties(context, propertyDao)
        }
        return propertyDao
    }

    suspend fun updateKeyValue(context: Context, key: String, value: String) {
        val propertyDao = MixinDatabase.getDatabase(context).propertyDao()
        propertyDao.insertSuspend(Property(key, value, nowInUtc()))
    }

    suspend fun findValueByKey(context: Context, key: String): String? {
        val propertyDao = MixinDatabase.getDatabase(context).propertyDao()
        return propertyDao.findValueByKey(key)
    }

    private suspend fun checkWithKey(context: Context, key: String, expectValue: String, action: () -> Unit) {
        val propertyDao = checkMigrated(context)

        val value = propertyDao.findValueByKey(key)
        if (value != expectValue) {
            action.invoke()
        }
    }

    private suspend fun migrateProperties(context: Context, propertyDao: PropertyDao) {
        val pref = context.defaultSharedPreferences
        val updatedAt = nowInUtc()
        val fts4Upgrade = pref.getBoolean(PREF_FTS4_UPGRADE, false)
        propertyDao.insertSuspend(Property(PREF_FTS4_UPGRADE, fts4Upgrade.toString(), updatedAt))
        val syncFtsOffset = pref.getInt(PREF_SYNC_FTS4_OFFSET, 0)
        propertyDao.insertSuspend(Property(PREF_SYNC_FTS4_OFFSET, syncFtsOffset.toString(), updatedAt))

        val attachment = pref.getBoolean(PREF_ATTACHMENT, false)
        propertyDao.insertSuspend(Property(PREF_ATTACHMENT, attachment.toString(), updatedAt))
        val attachmentLast = pref.getLong(PREF_ATTACHMENT_LAST, -1)
        propertyDao.insertSuspend(Property(PREF_ATTACHMENT_LAST, attachmentLast.toString(), updatedAt))
        val attachmentOffset = pref.getLong(PREF_ATTACHMENT_OFFSET, 0)
        propertyDao.insertSuspend(Property(PREF_ATTACHMENT_OFFSET, attachmentOffset.toString(), updatedAt))

        val backup = pref.getBoolean(PREF_BACKUP, false)
        propertyDao.insertSuspend(Property(PREF_BACKUP, backup.toString(), updatedAt))
        val period = pref.getInt(BACKUP_PERIOD, 0)
        propertyDao.insertSuspend(Property(BACKUP_PERIOD, period.toString(), updatedAt))
        val lastTime = pref.getLong(BACKUP_LAST_TIME, System.currentTimeMillis())
        propertyDao.insertSuspend(Property(BACKUP_LAST_TIME, lastTime.toString(), updatedAt))

        val duplicateTransfer = pref.getBoolean(PREF_DUPLICATE_TRANSFER, true)
        propertyDao.insertSuspend(Property(PREF_DUPLICATE_TRANSFER, duplicateTransfer.toString(), updatedAt))
        val strangerTransfer = pref.getBoolean(PREF_STRANGER_TRANSFER, true)
        propertyDao.insertSuspend(Property(PREF_STRANGER_TRANSFER, strangerTransfer.toString(), updatedAt))

        val autoWifi = pref.getInt(AUTO_DOWNLOAD_WIFI, WIFI_DEFAULT)
        propertyDao.insertSuspend(Property(AUTO_DOWNLOAD_WIFI, autoWifi.toString(), updatedAt))
        val autoMobile = pref.getInt(AUTO_DOWNLOAD_MOBILE, MOBILE_DEFAULT)
        propertyDao.insertSuspend(Property(AUTO_DOWNLOAD_MOBILE, autoMobile.toString(), updatedAt))
        val autoRoaming = pref.getInt(AUTO_DOWNLOAD_ROAMING, ROAMING_DEFAULT)
        propertyDao.insertSuspend(Property(AUTO_DOWNLOAD_ROAMING, autoRoaming.toString(), updatedAt))

        propertyDao.insertSuspend(Property(PREF_PROPERTY_MIGRATED, true.toString(), updatedAt))
    }

    private suspend fun hasMigrated(propertyDao: PropertyDao) =
        propertyDao.findValueByKey(PREF_PROPERTY_MIGRATED)?.toBoolean() == true
}
