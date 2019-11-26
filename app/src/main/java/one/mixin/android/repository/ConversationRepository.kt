package one.mixin.android.repository

import android.annotation.SuppressLint
import androidx.lifecycle.LiveData
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import io.reactivex.Observable
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import one.mixin.android.Constants.PAGE_SIZE
import one.mixin.android.api.request.ConversationRequest
import one.mixin.android.api.service.ConversationService
import one.mixin.android.db.ConversationDao
import one.mixin.android.db.JobDao
import one.mixin.android.db.MessageDao
import one.mixin.android.db.MessageProvider
import one.mixin.android.db.MixinDatabase
import one.mixin.android.db.ParticipantDao
import one.mixin.android.db.batchMarkReadAndTake
import one.mixin.android.di.type.DatabaseCategory
import one.mixin.android.di.type.DatabaseCategoryEnum
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.util.SINGLE_DB_THREAD
import one.mixin.android.util.Session
import one.mixin.android.vo.ChatMinimal
import one.mixin.android.vo.Conversation
import one.mixin.android.vo.ConversationCategory
import one.mixin.android.vo.ConversationItem
import one.mixin.android.vo.ConversationStatus
import one.mixin.android.vo.Job
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.MessageMinimal
import one.mixin.android.vo.Participant
import one.mixin.android.vo.SearchMessageItem
import one.mixin.android.vo.UserRelationship

@Singleton
class ConversationRepository
@Inject
internal constructor(
    @DatabaseCategory(DatabaseCategoryEnum.BASE)
    private val appDatabase: MixinDatabase,
    @DatabaseCategory(DatabaseCategoryEnum.READ)
    private val readAppDatabase: MixinDatabase,
    @DatabaseCategory(DatabaseCategoryEnum.BASE)
    private val messageDao: MessageDao,
    @DatabaseCategory(DatabaseCategoryEnum.READ)
    private val readMessageDao: MessageDao,
    @DatabaseCategory(DatabaseCategoryEnum.BASE)
    private val conversationDao: ConversationDao,
    @DatabaseCategory(DatabaseCategoryEnum.READ)
    private val readConversationDao: ConversationDao,
    private val participantDao: ParticipantDao,
    private val jobDao: JobDao,
    private val conversationService: ConversationService
) {

    @SuppressLint("RestrictedApi")
    fun getMessages(conversationId: String) =
        MessageProvider.getMessages(conversationId, readAppDatabase)

    fun conversation(): LiveData<List<ConversationItem>> = readConversationDao.conversationList()

    fun successConversationList(): LiveData<List<ConversationItem>> =
        readConversationDao.successConversationList()

    suspend fun insertConversation(conversation: Conversation, participants: List<Participant>) =
        withContext(SINGLE_DB_THREAD) {
            appDatabase.runInTransaction {
                conversationDao.insert(conversation)
                participantDao.insertList(participants)
            }
        }

    fun syncInsertConversation(conversation: Conversation, participants: List<Participant>) {
        appDatabase.runInTransaction {
            conversationDao.insert(conversation)
            participantDao.insertList(participants)
        }
    }

    fun getConversationById(conversationId: String): LiveData<Conversation> =
        readConversationDao.getConversationById(conversationId)

    fun findConversationById(conversationId: String): Observable<Conversation> =
        Observable.just(conversationId).map {
            readConversationDao.findConversationById(conversationId)
        }

    fun searchConversationById(conversationId: String) =
        readConversationDao.searchConversationById(conversationId)

    fun findMessageById(messageId: String) = messageDao.findMessageById(messageId)

    suspend fun saveDraft(conversationId: String, draft: String) =
        conversationDao.saveDraft(conversationId, draft)

    fun getConversation(conversationId: String) =
        readConversationDao.getConversation(conversationId)

    suspend fun fuzzySearchMessage(query: String, limit: Int): List<SearchMessageItem> =
        readMessageDao.fuzzySearchMessage(query, limit)

    fun fuzzySearchMessageDetail(query: String, conversationId: String) =
        readMessageDao.fuzzySearchMessageByConversationId(query, conversationId)

    suspend fun fuzzySearchChat(query: String): List<ChatMinimal> =
        readConversationDao.fuzzySearchChat(query)

    suspend fun indexUnread(conversationId: String) =
        readConversationDao.indexUnread(conversationId)

    suspend fun indexMediaMessages(
        conversationId: String,
        messageId: String,
        excludeLive: Boolean
    ): Int = if (excludeLive) {
        readMessageDao.indexMediaMessagesExcludeLive(conversationId, messageId)
    } else {
        readMessageDao.indexMediaMessages(conversationId, messageId)
    }

    fun getMediaMessages(
        conversationId: String,
        index: Int,
        excludeLive: Boolean
    ): LiveData<PagedList<MessageItem>> {
        val dataSource = if (excludeLive) {
            messageDao.getMediaMessagesExcludeLive(conversationId)
        } else {
            messageDao.getMediaMessages(conversationId)
        }
        val config = PagedList.Config.Builder()
            .setPrefetchDistance(PAGE_SIZE)
            .setPageSize(PAGE_SIZE)
            .setEnablePlaceholders(true)
            .build()
        return LivePagedListBuilder(dataSource, config)
            .setInitialLoadKey(index)
            .build()
    }

    fun getConversationIdIfExistsSync(recipientId: String) =
        readConversationDao.getConversationIdIfExistsSync(recipientId)

    fun getUnreadMessage(conversationId: String, accountId: String): List<MessageMinimal>? {
        return readMessageDao.getUnreadMessage(conversationId, accountId)
    }

    suspend fun updateCodeUrl(conversationId: String, codeUrl: String) =
        conversationDao.updateCodeUrl(conversationId, codeUrl)

    fun getGroupParticipants(conversationId: String) =
        participantDao.getParticipants(conversationId)

    fun getGroupParticipantsLiveData(conversationId: String) =
        participantDao.getGroupParticipantsLiveData(conversationId)

    fun getGroupBotsLiveData(conversationId: String) =
        participantDao.getGroupBotsLiveData(conversationId)

    suspend fun updateMediaStatus(status: String, messageId: String) =
        messageDao.updateMediaStatusSuspend(status, messageId)

    fun deleteMessage(id: String) = messageDao.deleteMessage(id)

    suspend fun deleteConversationById(conversationId: String) =
        conversationDao.deleteConversationById(conversationId)

    suspend fun updateConversationPinTimeById(conversationId: String, pinTime: String?) =
        withContext(SINGLE_DB_THREAD) {
            conversationDao.updateConversationPinTimeById(conversationId, pinTime)
        }

    suspend fun deleteMessageByConversationId(conversationId: String) = coroutineScope {
        messageDao.deleteMessageByConversationId(conversationId)
    }

    suspend fun getRealParticipants(conversationId: String) =
        participantDao.getRealParticipantsSuspend(conversationId)

    fun getGroupConversationApp(conversationId: String) =
        readAppDatabase.appDao().getGroupConversationApp(conversationId)

    fun getConversationApp(userId: String?) = readAppDatabase.appDao().getConversationApp(userId)

    fun updateAsync(conversationId: String, request: ConversationRequest) =
        conversationService.updateAsync(conversationId, request)

    suspend fun updateAnnouncement(conversationId: String, announcement: String) =
        conversationDao.updateConversationAnnouncement(conversationId, announcement)

    fun getLimitParticipants(conversationId: String, limit: Int) =
        participantDao.getLimitParticipants(conversationId, limit)

    fun findParticipantByIds(conversationId: String, userId: String) =
        participantDao.findParticipantByIds(conversationId, userId)

    fun getParticipantsCount(conversationId: String) =
        participantDao.getParticipantsCount(conversationId)

    fun getStorageUsage(conversationId: String) =
        readConversationDao.getStorageUsage(conversationId)

    fun getConversationStorageUsage() = readConversationDao.getConversationStorageUsage()

    fun getMediaByConversationIdAndCategory(conversationId: String, category: String) =
        readMessageDao
            .getMediaByConversationIdAndCategory(conversationId, category)

    suspend fun findMessageIndex(conversationId: String, messageId: String) =
        readMessageDao.findMessageIndex(conversationId, messageId)

    fun findUnreadMessagesSync(conversationId: String) =
        readMessageDao.findUnreadMessagesSync(conversationId)

    fun batchMarkReadAndTake(conversationId: String, userId: String, createdAt: String) {
        messageDao.batchMarkReadAndTake(conversationId, userId, createdAt)
    }

    fun insertList(it: List<Job>) {
        jobDao.insertList(it)
    }

    fun refreshConversation(conversationId: String) {
        try {
            val call = conversationService.getConversation(conversationId).execute()
            val response = call.body()
            if (response != null && response.isSuccess) {
                response.data?.let { conversationData ->
                    val status =
                        if (conversationData.participants.find { Session.getAccountId() == it.userId } != null) {
                            ConversationStatus.SUCCESS.ordinal
                        } else {
                            ConversationStatus.QUIT.ordinal
                        }
                    var ownerId: String = conversationData.creatorId
                    if (conversationData.category == ConversationCategory.CONTACT.name) {
                        ownerId =
                            conversationData.participants.find { it.userId != Session.getAccountId() }!!.userId
                    }
                    conversationDao.updateConversation(
                        conversationData.conversationId,
                        ownerId,
                        conversationData.category,
                        conversationData.name,
                        conversationData.announcement,
                        conversationData.muteUntil,
                        conversationData.createdAt,
                        status
                    )
                }
            }
        } catch (e: IOException) {
        }
    }

    suspend fun findFirstUnreadMessageId(conversationId: String, userId: String): String? =
        messageDao.findFirstUnreadMessageId(conversationId, userId)

    suspend fun findLastMessage(conversationId: String) = messageDao.findLastMessage(conversationId)

    suspend fun findUnreadMessageByMessageId(
        conversationId: String,
        userId: String,
        messageId: String
    ) =
        messageDao.findUnreadMessageByMessageId(conversationId, userId, messageId)

    suspend fun isSilence(conversationId: String, userId: String): Int =
        messageDao.isSilence(conversationId, userId)

    suspend fun isSilenceInviter(conversationId: String): Boolean {
        return getInviter(conversationId).notNullWithElse(
            { it.relationship != UserRelationship.FRIEND.name },
            false
        )
    }

    suspend fun getInviter(conversationId: String) = messageDao.getInviter(
        conversationId,
        Session.getAccountId()!!
    )

    suspend fun findNextAudioMessage(conversationId: String, createdAt: String, messageId: String) =
        messageDao.findNextAudioMessage(conversationId, createdAt, messageId)

    fun getMediaMessagesExcludeLive(conversationId: String) = messageDao.getMediaMessagesExcludeLive(conversationId)

    fun getAudioMessages(conversationId: String) = messageDao.getAudioMessages(conversationId)

    fun getLinkMessages(conversationId: String) = messageDao.getLinkMessages(conversationId)

    fun getFileMessages(conversationId: String) = messageDao.getFileMessages(conversationId)

    suspend fun getSortMessagesByIds(ids: List<String>) = messageDao.getSortMessagesByIds(ids)
}
