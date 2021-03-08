package one.mixin.android.job

import android.os.SystemClock
import com.google.gson.JsonElement
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import one.mixin.android.Constants.SLEEP_MILLIS
import one.mixin.android.MixinApplication
import one.mixin.android.api.service.CircleService
import one.mixin.android.api.service.ConversationService
import one.mixin.android.api.service.UserService
import one.mixin.android.crypto.EncryptedProtocol
import one.mixin.android.crypto.SignalProtocol
import one.mixin.android.crypto.db.RatchetSenderKeyDao
import one.mixin.android.db.AppDao
import one.mixin.android.db.AssetDao
import one.mixin.android.db.CircleConversationDao
import one.mixin.android.db.CircleDao
import one.mixin.android.db.ConversationDao
import one.mixin.android.db.HyperlinkDao
import one.mixin.android.db.JobDao
import one.mixin.android.db.MessageDao
import one.mixin.android.db.MessageHistoryDao
import one.mixin.android.db.MessageMentionDao
import one.mixin.android.db.MixinDatabase
import one.mixin.android.db.ParticipantDao
import one.mixin.android.db.ParticipantSessionDao
import one.mixin.android.db.ResendSessionMessageDao
import one.mixin.android.db.SnapshotDao
import one.mixin.android.db.StickerDao
import one.mixin.android.db.TraceDao
import one.mixin.android.db.UserDao
import one.mixin.android.db.insertUpdate
import one.mixin.android.session.Session
import one.mixin.android.util.ErrorHandler
import one.mixin.android.vo.ConversationCategory
import one.mixin.android.vo.ConversationStatus
import one.mixin.android.vo.Participant
import one.mixin.android.vo.ParticipantSession
import one.mixin.android.vo.SYSTEM_USER
import one.mixin.android.vo.User
import one.mixin.android.vo.createConversation
import one.mixin.android.websocket.BlazeMessage
import one.mixin.android.websocket.BlazeMessageData
import one.mixin.android.websocket.ChatWebSocket
import java.io.IOException
import javax.inject.Inject

open class Injector {
    @Inject
    lateinit var jobManager: MixinJobManager
    @Inject
    lateinit var messageDao: MessageDao
    @Inject
    lateinit var messageHistoryDao: MessageHistoryDao
    @Inject
    lateinit var userDao: UserDao
    @Inject
    lateinit var appDao: AppDao
    @Inject
    lateinit var jobDao: JobDao
    @Inject
    lateinit var conversationDao: ConversationDao
    @Inject
    lateinit var participantDao: ParticipantDao
    @Inject
    lateinit var participantSessionDao: ParticipantSessionDao
    @Inject
    lateinit var snapshotDao: SnapshotDao
    @Inject
    lateinit var assetDao: AssetDao
    @Inject
    lateinit var circleDao: CircleDao
    @Inject
    lateinit var circleConversationDao: CircleConversationDao
    @Inject
    lateinit var traceDao: TraceDao
    @Inject
    lateinit var circleService: CircleService
    @Inject
    lateinit var chatWebSocket: ChatWebSocket
    @Inject
    lateinit var stickerDao: StickerDao
    @Inject
    lateinit var messageMentionDao: MessageMentionDao
    @Inject
    lateinit var signalProtocol: SignalProtocol
    @Inject
    lateinit var encryptedProtocol: EncryptedProtocol
    @Inject
    lateinit var ratchetSenderKeyDao: RatchetSenderKeyDao
    @Inject
    lateinit var resendMessageDao: ResendSessionMessageDao
    @Inject
    lateinit var hyperlinkDao: HyperlinkDao
    @Inject
    lateinit var userApi: UserService
    @Inject
    lateinit var conversationService: ConversationService
    @Inject
    lateinit var database: MixinDatabase

    @InstallIn(SingletonComponent::class)
    @EntryPoint
    interface InjectorEntryPoint {
        fun inject(injector: Injector)
    }

    init {
        val entryPoint = EntryPointAccessors.fromApplication(MixinApplication.get().applicationContext, InjectorEntryPoint::class.java)
        entryPoint.inject(this)
    }

    protected tailrec fun signalKeysChannel(blazeMessage: BlazeMessage): JsonElement? {
        val bm = chatWebSocket.sendMessage(blazeMessage)
        if (bm == null) {
            SystemClock.sleep(SLEEP_MILLIS)
            return signalKeysChannel(blazeMessage)
        } else if (bm.error != null) {
            return if (bm.error.code == ErrorHandler.FORBIDDEN) {
                null
            } else {
                SystemClock.sleep(SLEEP_MILLIS)
                return signalKeysChannel(blazeMessage)
            }
        }
        return bm.data
    }

    protected fun syncUser(userId: String, conversationId: String? = null): User? {
        var user = userDao.findUser(userId)
        if (user == null) {
            try {
                val call = userApi.getUserById(userId).execute()
                val response = call.body()
                if (response != null && response.isSuccess && response.data != null) {
                    response.data?.let { u ->
                        userDao.insertUpdate(u, appDao)
                        user = u
                    }
                }
            } catch (e: IOException) {
            }
        }
        if (user == null) {
            jobManager.addJobInBackground(RefreshUserJob(arrayListOf(userId), conversationId = conversationId))
        }
        return user
    }

    protected fun syncConversation(data: BlazeMessageData) {
        if (data.conversationId == SYSTEM_USER || data.conversationId == Session.getAccountId()) {
            return
        }
        var conversation = conversationDao.getConversation(data.conversationId)
        if (conversation == null) {
            conversation = createConversation(data.conversationId, null, data.userId, ConversationStatus.START.ordinal)
            conversationDao.insert(conversation)
            refreshConversation(data.conversationId)
        }
        if (conversation.status == ConversationStatus.START.ordinal) {
            jobManager.addJobInBackground(RefreshConversationJob(data.conversationId))
        }
    }

    protected fun isExistMessage(messageId: String): Boolean {
        val id = messageDao.findMessageIdById(messageId)
        val messageHistory = messageHistoryDao.findMessageHistoryById(messageId)
        return id != null || messageHistory != null
    }

    private fun refreshConversation(conversationId: String) {
        try {
            val call = conversationService.getConversation(conversationId).execute()
            val response = call.body()
            if (response != null && response.isSuccess) {
                response.data?.let { conversationData ->
                    val status = if (conversationData.participants.find { Session.getAccountId() == it.userId } != null) {
                        ConversationStatus.SUCCESS.ordinal
                    } else {
                        ConversationStatus.QUIT.ordinal
                    }
                    var ownerId: String = conversationData.creatorId
                    if (conversationData.category == ConversationCategory.CONTACT.name) {
                        ownerId = conversationData.participants.find { it.userId != Session.getAccountId() }!!.userId
                    } else if (conversationData.category == ConversationCategory.GROUP.name) {
                        syncUser(conversationData.creatorId)
                    }

                    val remote = conversationData.participants.map {
                        Participant(conversationId, it.userId, it.role, it.createdAt!!)
                    }
                    participantDao.replaceAll(conversationId, remote)
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

                    conversationData.circles?.let { circles ->
                        circles.forEach {
                            val circle = circleDao.findCircleById(it.circleId)
                            if (circle == null) {
                                val circleResponse = circleService.getCircle(it.circleId).execute().body()
                                if (circleResponse?.isSuccess == true) {
                                    circleResponse.data?.let { item ->
                                        circleDao.insert(item)
                                    }
                                }
                            }
                            circleConversationDao.insertUpdate(it)
                        }
                    }

                    val sessionParticipants = conversationData.participantSessions?.map {
                        ParticipantSession(conversationId, it.userId, it.sessionId)
                    }
                    sessionParticipants?.let {
                        participantSessionDao.replaceAll(conversationId, it)
                    }
                }
            }
        } catch (e: IOException) {
        }
    }
}
