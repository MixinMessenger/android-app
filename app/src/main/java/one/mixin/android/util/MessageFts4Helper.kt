package one.mixin.android.util

import android.os.SystemClock
import androidx.annotation.WorkerThread
import androidx.room.ColumnInfo
import one.mixin.android.Constants.Account.PREF_SYNC_FTS4_OFFSET
import one.mixin.android.MixinApplication
import one.mixin.android.db.MixinDatabase
import one.mixin.android.extension.joinWhiteSpace
import one.mixin.android.vo.Message
import one.mixin.android.vo.MessageFts4
import one.mixin.android.vo.isContact
import one.mixin.android.vo.isFtsMessage
import org.threeten.bp.Instant
import org.threeten.bp.temporal.ChronoUnit
import timber.log.Timber

data class QueryMessage(
    @ColumnInfo(name = "message_id")
    val messageId: String,
    val content: String?,
    val name: String?
)

object MessageFts4Helper {

    private const val SYNC_FTS4_LIMIT = 100
    private const val PRE_PROCESS_COUNT = 20000
    private const val PER_JOB_HANDLE_COUNT = 1000

    suspend fun syncMessageFts4(
        preProcess: Boolean,
        waitMillis: Long? = null,
        onProgressChanged: ((Int) -> Unit)? = null
    ): Boolean {
        val ctx = MixinApplication.appContext
        val messageDao = MixinDatabase.getDatabase(ctx).messageDao()
        val messageFts4Dao = MixinDatabase.getDatabase(ctx).messageFts4Dao()

        var done = false
        var offset = PropertyHelper.findValueByKey(ctx, PREF_SYNC_FTS4_OFFSET)?.toIntOrNull() ?: 0
        var handleCount = 0
        var start: Long
        val totalStart = System.currentTimeMillis()
        val sixMonthsAgo = Instant.now().minus(6 * 30, ChronoUnit.DAYS).toEpochMilli()
        Timber.d("syncMessageFts4 preProcess: $preProcess， start offset: $offset")
        while (true) {
            start = System.currentTimeMillis()
            val queryMessageList = messageDao.batchQueryMessages(SYNC_FTS4_LIMIT, offset, sixMonthsAgo)
            val messageFts4List = arrayListOf<MessageFts4>()
            queryMessageList.forEach { item ->
                val name = item.name.joinWhiteSpace()
                val content = item.content.joinWhiteSpace()
                messageFts4List.add(MessageFts4(item.messageId, name + content))
            }
            messageFts4Dao.insertListSuspend(messageFts4List)
            offset += queryMessageList.size
            handleCount += queryMessageList.size
            PropertyHelper.updateKeyValue(ctx, PREF_SYNC_FTS4_OFFSET, offset.toString())
            onProgressChanged?.invoke((offset.toFloat() / PRE_PROCESS_COUNT * 100).toInt())
            Timber.d("syncMessageFts4 handle 100 messages cost ${System.currentTimeMillis() - start}, offset: $offset")

            if (queryMessageList.size < SYNC_FTS4_LIMIT) {
                done = true
                break
            }
            if (preProcess && offset >= PRE_PROCESS_COUNT) {
                break
            }
            if (!preProcess && handleCount >= PER_JOB_HANDLE_COUNT) {
                break
            }
            if (waitMillis != null) {
                SystemClock.sleep(waitMillis)
            }
        }
        Timber.d("syncMessageFts4 handle $offset messages cost: ${System.currentTimeMillis() - totalStart}")
        return done
    }

    @WorkerThread
    fun insertOrReplaceMessageFts4(message: Message, extraContent: String? = null) {
        if (!message.isFtsMessage()) {
            if (message.isContact() && !extraContent.isNullOrBlank()) {
                insertContact(message.id, extraContent)
            }
            return
        }

        val messageFts4Dao = MixinDatabase.getDatabase(MixinApplication.appContext).messageFts4Dao()
        val name = message.name.joinWhiteSpace()
        val content = message.content.joinWhiteSpace()
        messageFts4Dao.insert(MessageFts4(message.id, name + content))
    }

    @WorkerThread
    fun insertMessageFts4(messageId: String, content: String) {
        val messageFts4Dao = MixinDatabase.getDatabase(MixinApplication.appContext).messageFts4Dao()
        messageFts4Dao.insert(MessageFts4(messageId, content))
    }

    @WorkerThread
    private fun insertContact(messageId: String, text: String) {
        val messageFts4Dao = MixinDatabase.getDatabase(MixinApplication.appContext).messageFts4Dao()
        val content = text.joinWhiteSpace()
        messageFts4Dao.insert(MessageFts4(messageId, content))
    }
}
