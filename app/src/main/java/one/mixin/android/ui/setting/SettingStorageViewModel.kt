package one.mixin.android.ui.setting

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import one.mixin.android.Constants
import one.mixin.android.Constants.Storage.AUDIO
import one.mixin.android.Constants.Storage.DATA
import one.mixin.android.Constants.Storage.IMAGE
import one.mixin.android.Constants.Storage.VIDEO
import one.mixin.android.MixinApplication
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.getConversationAudioPath
import one.mixin.android.extension.getConversationDocumentPath
import one.mixin.android.extension.getConversationImagePath
import one.mixin.android.extension.getConversationMediaSize
import one.mixin.android.extension.getConversationVideoPath
import one.mixin.android.extension.getStorageUsageByConversationAndType
import one.mixin.android.repository.ConversationRepository
import one.mixin.android.vo.ConversationStorageUsage
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.StorageUsage

class SettingStorageViewModel @ViewModelInject
internal constructor(
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    fun getStorageUsage(conversationId: String): Single<List<StorageUsage>> =
        Single.just(conversationId).map { cid ->
            val result = mutableListOf<StorageUsage>()
            val context = MixinApplication.appContext
            context.getStorageUsageByConversationAndType(cid, IMAGE)?.apply {
                result.add(this)
            }
            context.getStorageUsageByConversationAndType(cid, VIDEO)?.apply {
                result.add(this)
            }
            context.getStorageUsageByConversationAndType(cid, AUDIO)?.apply {
                result.add(this)
            }
            context.getStorageUsageByConversationAndType(cid, DATA)?.apply {
                result.add(this)
            }
            result.toList()
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())

    fun getConversationStorageUsage(): Flowable<List<ConversationStorageUsage>> = conversationRepository.getConversationStorageUsage()
        .map { list ->
            list.asSequence().map { item ->
                val context = MixinApplication.appContext
                item.mediaSize = context.getConversationMediaSize(item.conversationId)
                item
            }.filter { conversationStorageUsage ->
                conversationStorageUsage.mediaSize != 0L && conversationStorageUsage.conversationId.isNotEmpty()
            }.sortedByDescending { conversationStorageUsage ->
                conversationStorageUsage.mediaSize
            }.toList()
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())

    fun clear(conversationId: String, type: String) {
        if (MixinApplication.appContext.defaultSharedPreferences.getBoolean(Constants.Account.PREF_ATTACHMENT, false)) {
            when (type) {
                IMAGE -> {
                    MixinApplication.get().getConversationImagePath(conversationId)?.deleteRecursively()
                    conversationRepository.deleteMediaMessageByConversationAndCategory(
                        conversationId,
                        MessageCategory.SIGNAL_IMAGE.name,
                        MessageCategory.PLAIN_IMAGE.name,
                        MessageCategory.ENCRYPTED_IMAGE.name
                    )
                }
                VIDEO -> {
                    MixinApplication.get().getConversationVideoPath(conversationId)?.deleteRecursively()
                    conversationRepository.deleteMediaMessageByConversationAndCategory(
                        conversationId,
                        MessageCategory.SIGNAL_VIDEO.name,
                        MessageCategory.PLAIN_VIDEO.name,
                        MessageCategory.ENCRYPTED_VIDEO.name
                    )
                }
                AUDIO -> {
                    MixinApplication.get().getConversationAudioPath(conversationId)?.deleteRecursively()
                    conversationRepository.deleteMediaMessageByConversationAndCategory(
                        conversationId,
                        MessageCategory.SIGNAL_AUDIO.name,
                        MessageCategory.PLAIN_AUDIO.name,
                        MessageCategory.ENCRYPTED_AUDIO.name
                    )
                }
                DATA -> {
                    MixinApplication.get().getConversationDocumentPath(conversationId)?.deleteRecursively()
                    conversationRepository.deleteMediaMessageByConversationAndCategory(
                        conversationId,
                        MessageCategory.SIGNAL_DATA.name,
                        MessageCategory.PLAIN_DATA.name,
                        MessageCategory.ENCRYPTED_DATA.name
                    )
                }
            }
        } else {
            when (type) {
                IMAGE -> clear(conversationId, MessageCategory.SIGNAL_IMAGE.name, MessageCategory.PLAIN_IMAGE.name, MessageCategory.ENCRYPTED_IMAGE.name)
                VIDEO -> clear(conversationId, MessageCategory.SIGNAL_VIDEO.name, MessageCategory.PLAIN_VIDEO.name, MessageCategory.ENCRYPTED_VIDEO.name)
                AUDIO -> clear(conversationId, MessageCategory.SIGNAL_AUDIO.name, MessageCategory.PLAIN_AUDIO.name, MessageCategory.ENCRYPTED_AUDIO.name)
                DATA -> clear(conversationId, MessageCategory.SIGNAL_DATA.name, MessageCategory.PLAIN_DATA.name, MessageCategory.ENCRYPTED_DATA.name)
            }
        }
    }

    private fun clear(conversationId: String, signalCategory: String, plainCategory: String, encryptedCategory: String) {
        conversationRepository.getMediaByConversationIdAndCategory(conversationId, signalCategory, plainCategory, encryptedCategory)
            ?.let { list ->
                list.forEach { item ->
                    conversationRepository.deleteMessage(item.messageId, item.mediaUrl)
                }
            }
    }
}
