package one.mixin.android.crypto.storage

import android.content.Context
import one.mixin.android.crypto.db.SenderKeyDao
import one.mixin.android.crypto.db.SignalDatabase
import one.mixin.android.crypto.vo.SenderKey
import one.mixin.android.util.bugsnag
import org.whispersystems.libsignal.groups.SenderKeyName
import org.whispersystems.libsignal.groups.state.SenderKeyRecord
import org.whispersystems.libsignal.groups.state.SenderKeyStore
import java.io.IOException

class MixinSenderKeyStore(ctx: Context) : SenderKeyStore {

    private val dao: SenderKeyDao = SignalDatabase.getDatabase(ctx).senderKeyDao()

    override fun storeSenderKey(senderKeyName: SenderKeyName, record: SenderKeyRecord) {
        synchronized(LOCK) {
            dao.insert(SenderKey(senderKeyName.groupId, senderKeyName.sender.toString(), record.serialize()))
        }
    }

    override fun loadSenderKey(senderKeyName: SenderKeyName): SenderKeyRecord {
        synchronized(LOCK) {
            val senderKey = dao.getSenderKey(senderKeyName.groupId, senderKeyName.sender.toString())
            try {
                if (senderKey != null) {
                    return SenderKeyRecord(senderKey.record)
                }
            } catch (e: IOException) {
                bugsnag?.notify(e)
                e.printStackTrace()
            }

            return SenderKeyRecord()
        }
    }

    fun removeSenderKey(senderKeyName: SenderKeyName) {
        synchronized(LOCK) {
            dao.delete(senderKeyName.groupId, senderKeyName.sender.toString())
        }
    }

    companion object {
        private val LOCK = Any()
    }
}
