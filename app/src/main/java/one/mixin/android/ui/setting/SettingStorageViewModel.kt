package one.mixin.android.ui.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import com.google.protobuf.Mixin
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import one.mixin.android.Constants
import javax.inject.Inject
import one.mixin.android.Constants.Storage.AUDIO
import one.mixin.android.Constants.Storage.DATA
import one.mixin.android.Constants.Storage.IMAGE
import one.mixin.android.Constants.Storage.VIDEO
import one.mixin.android.MixinApplication
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.generateConversationPath
import one.mixin.android.extension.getAudioPath
import one.mixin.android.extension.getConversationAudioPath
import one.mixin.android.extension.getConversationDocumentPath
import one.mixin.android.extension.getConversationImagePath
import one.mixin.android.extension.getConversationMediaSize
import one.mixin.android.extension.getConversationVideoPath
import one.mixin.android.extension.getDocumentPath
import one.mixin.android.extension.getImagePath
import one.mixin.android.extension.getStorageUsageByConversationAndType
import one.mixin.android.extension.getVideoPath
import one.mixin.android.extension.putBoolean
import one.mixin.android.repository.ConversationRepository
import one.mixin.android.vo.ConversationStorageUsage
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.StorageUsage

class SettingStorageViewModel @Inject
internal constructor(
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    fun getStorageUsage(conversationId: String): Single<List<StorageUsage>> =
        Single.just(conversationId).map { cid ->
            val result = mutableListOf<StorageUsage>()
            val context = MixinApplication.appContext
            context.getStorageUsageByConversationAndType(conversationId, IMAGE)?.apply {
                result.add(this)
            }
            context.getStorageUsageByConversationAndType(conversationId, VIDEO)?.apply {
                result.add(this)
            }
            context.getStorageUsageByConversationAndType(conversationId, AUDIO)?.apply {
                result.add(this)
            }
            context.getStorageUsageByConversationAndType(conversationId, DATA)?.apply {
                result.add(this)
            }
            result.toList()
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())

    fun getConversationStorageUsage(): Single<List<ConversationStorageUsage>> = conversationRepository.getConversationStorageUsage()
        .map { list ->
            list.asSequence().map { item ->
                val context = MixinApplication.appContext
                item.mediaSize = context.getConversationMediaSize(item.conversationId)
                item
            }.filter { conversationStorageUsage ->
                conversationStorageUsage.mediaSize != 0L
            }.sortedByDescending { conversationStorageUsage ->
                conversationStorageUsage.mediaSize
            }.toList()
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())

    fun clear(conversationId: String, type: String, context: Context) {
        if (MixinApplication.appContext.defaultSharedPreferences.getBoolean(Constants.Account.PREF_ATTACHMENT, false)) {
            when (type) {
                IMAGE -> {
                    MixinApplication.get().getConversationImagePath(conversationId).deleteRecursively()
                    conversationRepository.deleteMediaMessageByConversationAndCategory(conversationId, MessageCategory.SIGNAL_IMAGE.name, MessageCategory.PLAIN_IMAGE.name)
                }
                VIDEO -> {
                    MixinApplication.get().getConversationVideoPath(conversationId).deleteRecursively()
                    conversationRepository.deleteMediaMessageByConversationAndCategory(conversationId, MessageCategory.SIGNAL_VIDEO.name, MessageCategory.PLAIN_VIDEO.name)
                }
                AUDIO -> {
                    MixinApplication.get().getConversationAudioPath(conversationId).deleteRecursively()
                    conversationRepository.deleteMediaMessageByConversationAndCategory(conversationId, MessageCategory.SIGNAL_AUDIO.name, MessageCategory.PLAIN_AUDIO.name)
                }
                DATA -> {
                    MixinApplication.get().getConversationDocumentPath(conversationId).deleteRecursively()
                    conversationRepository.deleteMediaMessageByConversationAndCategory(conversationId, MessageCategory.SIGNAL_DATA.name, MessageCategory.PLAIN_DATA.name)
                }
            }
        } else {
            when (type) {
                IMAGE -> clear(conversationId, MessageCategory.SIGNAL_IMAGE.name, MessageCategory.PLAIN_IMAGE.name)
                VIDEO -> clear(conversationId, MessageCategory.SIGNAL_VIDEO.name, MessageCategory.PLAIN_VIDEO.name)
                AUDIO -> clear(conversationId, MessageCategory.SIGNAL_AUDIO.name, MessageCategory.PLAIN_AUDIO.name)
                DATA -> clear(conversationId, MessageCategory.SIGNAL_DATA.name, MessageCategory.PLAIN_DATA.name)
            }
        }
    }

    private fun clear(conversationId: String, signalCategory: String, plainCategory: String) {
        conversationRepository.getMediaByConversationIdAndCategory(conversationId, signalCategory, plainCategory)
            ?.let { list ->
                list.forEach { item ->
                    conversationRepository.deleteMessage(item.messageId, item.mediaUrl)
                }
            }
    }
}
