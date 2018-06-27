package one.mixin.android.job

import com.birbit.android.jobqueue.Params
import one.mixin.android.db.insertWithCreatedAt
import one.mixin.android.vo.Sticker

class RefreshStickerJob(private val stickerId: String) : BaseJob(Params(PRIORITY_SEND_ATTACHMENT_MESSAGE)
    .addTags(RefreshStickerJob.GROUP).persist().requireNetwork()) {

    companion object {
        private const val serialVersionUID = 1L
        const val GROUP = "RefreshStickerJob"
    }

    override fun onRun() {
        val response = accountService.getStickerById(stickerId).execute().body()
        if (response != null && response.isSuccess && response.data != null) {
            val s = response.data as Sticker
            val sticker = stickerDao.getStickerByUnique(s.stickerId)
            if (sticker != null) {
                s.lastUseAt = sticker.lastUseAt
            }
            stickerDao.insertWithCreatedAt(s)
        }
    }
}