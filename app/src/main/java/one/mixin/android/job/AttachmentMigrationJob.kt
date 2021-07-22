package one.mixin.android.job

import androidx.core.net.toUri
import com.birbit.android.jobqueue.Params
import one.mixin.android.Constants
import one.mixin.android.Constants.Account.PREF_ATTACHMENT_LAST
import one.mixin.android.Constants.Account.PREF_ATTACHMENT_OFFSET
import one.mixin.android.MixinApplication
import one.mixin.android.extension.createAudioTemp
import one.mixin.android.extension.createDocumentTemp
import one.mixin.android.extension.createEmptyTemp
import one.mixin.android.extension.createGifTemp
import one.mixin.android.extension.createImageTemp
import one.mixin.android.extension.createVideoTemp
import one.mixin.android.extension.createWebpTemp
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.getAudioPath
import one.mixin.android.extension.getDocumentPath
import one.mixin.android.extension.getExtensionName
import one.mixin.android.extension.getFilePath
import one.mixin.android.extension.getImagePath
import one.mixin.android.extension.getOldMediaPath
import one.mixin.android.extension.getVideoPath
import one.mixin.android.extension.hasWritePermission
import one.mixin.android.extension.isImageSupport
import one.mixin.android.extension.putBoolean
import one.mixin.android.extension.putLong
import one.mixin.android.vo.MessageCategory
import one.mixin.android.widget.gallery.MimeType
import timber.log.Timber
import java.io.File

class AttachmentMigrationJob : BaseJob(Params(PRIORITY_LOWER).groupBy(GROUP_ID).persist()) {
    companion object {
        private const val GROUP_ID = "attachment_migration"
        private const val EACH = 10
    }

    override fun onRun() {
        val startTime = System.currentTimeMillis()
        val preferences = MixinApplication.appContext.defaultSharedPreferences
        var migrationLast = preferences.getLong(PREF_ATTACHMENT_LAST, -1)
        val offset = preferences.getLong(PREF_ATTACHMENT_OFFSET, 0)
        if (migrationLast == -1L) {
            migrationLast = messageDao.getLastMessageRowid()
            preferences.putLong(PREF_ATTACHMENT_LAST, migrationLast)
        }
        if (!hasWritePermission()) return
        val list = messageDao.findAttachmentMigration(migrationLast, EACH, offset)
        list.forEach { attachment ->
            val path = attachment.mediaUrl?.toUri()?.getFilePath() ?: return@forEach
            val fromFile = File(path)
            if (!fromFile.exists()) return@forEach
            val toFile = when (attachment.category) {
                MessageCategory.PLAIN_IMAGE.name, MessageCategory.SIGNAL_IMAGE.name -> {
                    when {
                        attachment.mediaMimeType?.isImageSupport() == false -> {
                            MixinApplication.get().getImagePath().createEmptyTemp(attachment.conversationId, attachment.messageId)
                        }
                        attachment.mediaMimeType.equals(MimeType.PNG.toString(), true) -> {
                            MixinApplication.get().getImagePath().createImageTemp(attachment.conversationId, attachment.messageId, ".png")
                        }
                        attachment.mediaMimeType.equals(MimeType.GIF.toString(), true) -> {
                            MixinApplication.get().getImagePath().createGifTemp(attachment.conversationId, attachment.messageId)
                        }
                        attachment.mediaMimeType.equals(MimeType.WEBP.toString(), true) -> {
                            MixinApplication.get().getImagePath().createWebpTemp(attachment.conversationId, attachment.messageId)
                        }
                        else -> {
                            MixinApplication.get().getImagePath().createImageTemp(attachment.conversationId, attachment.messageId, ".jpg")
                        }
                    }
                }
                MessageCategory.PLAIN_DATA.name, MessageCategory.SIGNAL_DATA.name -> {
                    val extensionName = attachment.name?.getExtensionName()
                    MixinApplication.get().getDocumentPath()
                        .createDocumentTemp(attachment.conversationId, attachment.messageId, extensionName)
                }
                MessageCategory.PLAIN_VIDEO.name, MessageCategory.SIGNAL_VIDEO.name -> {
                    val extensionName = attachment.name?.getExtensionName().let {
                        it ?: "mp4"
                    }
                    MixinApplication.get().getVideoPath()
                        .createVideoTemp(attachment.conversationId, attachment.messageId, extensionName)
                }
                MessageCategory.PLAIN_AUDIO.name, MessageCategory.SIGNAL_AUDIO.name -> {
                    MixinApplication.get().getAudioPath()
                        .createAudioTemp(attachment.conversationId, attachment.messageId, "ogg")
                }
                else -> null
            }
            toFile ?: return@forEach
            fromFile.renameTo(toFile)
            messageDao.updateMediaMessageUrl(toFile.name, attachment.messageId)
        }
        preferences.putLong(PREF_ATTACHMENT_OFFSET, offset + list.size)
        Timber.d("Attachment migration handle ${offset + list.size} file cost: ${System.currentTimeMillis() - startTime} ms")
        if (list.size < EACH) {
            MixinApplication.appContext.getOldMediaPath()?.deleteRecursively()
            preferences.putBoolean(Constants.Account.PREF_ATTACHMENT, true)
        } else {
            jobManager.addJobInBackground(AttachmentMigrationJob())
        }
    }
}
