package one.mixin.android.job

import android.net.Uri
import com.birbit.android.jobqueue.Params
import one.mixin.android.vo.TranscriptMessage
import one.mixin.android.vo.absolutePath
import one.mixin.android.vo.isAttachment
import one.mixin.android.vo.isTranscript
import java.io.File

class TranscriptDeleteJob(private val messageIds: List<String>) : BaseJob(Params(PRIORITY_BACKGROUND).addTags(GROUP).groupBy("transcript_delete").persist()) {

    private val TAG = TranscriptDeleteJob::class.java.simpleName

    companion object {
        const val GROUP = "TranscriptDeleteJob"
        private const val serialVersionUID = 1L
    }

    override fun onRun() {
        messageIds.forEach { messageId ->
            messageDao.deleteMessage(messageId)
            transcriptMessageDao.getTranscript(messageId).forEach { transcriptMessage ->
                if (transcriptMessage.isAttachment()) {
                    transcriptMessageDao.delete(transcriptMessage)
                    transcriptMessage.absolutePath()?.let { url ->
                        deleteAttachment(transcriptMessage.messageId, url)
                    }
                } else if (transcriptMessage.isTranscript()) {
                    deleteTranscript(transcriptMessage)
                }
            }
        }
    }

    private fun deleteAttachment(messageId: String, mediaUrl: String) {
        val count = transcriptMessageDao.countTranscriptByMessageId(messageId)
        if (count <= 1) {
            File(Uri.parse(mediaUrl).path!!).apply {
                if (exists()) {
                    delete()
                }
            }
        }
    }

    private fun deleteTranscript(transcriptMessage: TranscriptMessage) {
        val list = transcriptMessageDao.getTranscript(transcriptMessage.messageId)
        if (list.isEmpty()) {
            return
        } else {
            list.forEach { t ->
                if (t.isTranscript()) {
                    deleteTranscript(t)
                } else {
                    val count = transcriptMessageDao.countTranscriptByMessageId(t.messageId)
                    if (count > 1) {
                        return@forEach
                    } else {
                        if (t.isAttachment()) {
                            t.absolutePath()?.let { url ->
                                deleteAttachment(transcriptMessage.messageId, url)
                            }
                        }
                        transcriptMessageDao.delete(t)
                    }
                }
            }
        }
    }
}
