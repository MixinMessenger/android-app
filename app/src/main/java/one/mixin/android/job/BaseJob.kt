package one.mixin.android.job

import android.util.ArrayMap
import com.birbit.android.jobqueue.CancelReason
import com.birbit.android.jobqueue.Job
import com.birbit.android.jobqueue.Params
import com.birbit.android.jobqueue.RetryConstraint
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.MetaData
import com.microsoft.appcenter.crashes.Crashes
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import one.mixin.android.api.ClientErrorException
import one.mixin.android.api.ExpiredTokenException
import one.mixin.android.api.LocalJobException
import one.mixin.android.api.NetworkException
import one.mixin.android.api.ServerErrorException
import one.mixin.android.api.WebSocketException
import one.mixin.android.api.service.AccountService
import one.mixin.android.api.service.AddressService
import one.mixin.android.api.service.AssetService
import one.mixin.android.api.service.CircleService
import one.mixin.android.api.service.ContactService
import one.mixin.android.api.service.ConversationService
import one.mixin.android.api.service.MessageService
import one.mixin.android.api.service.SignalKeyService
import one.mixin.android.api.service.UserService
import one.mixin.android.crypto.EncryptedProtocol
import one.mixin.android.crypto.SignalProtocol
import one.mixin.android.db.AddressDao
import one.mixin.android.db.AppDao
import one.mixin.android.db.AssetDao
import one.mixin.android.db.CircleConversationDao
import one.mixin.android.db.CircleDao
import one.mixin.android.db.ConversationDao
import one.mixin.android.db.FavoriteAppDao
import one.mixin.android.db.HyperlinkDao
import one.mixin.android.db.JobDao
import one.mixin.android.db.MessageDao
import one.mixin.android.db.MessageHistoryDao
import one.mixin.android.db.MessageMentionDao
import one.mixin.android.db.MessagesFts4Dao
import one.mixin.android.db.MixinDatabase
import one.mixin.android.db.OffsetDao
import one.mixin.android.db.ParticipantDao
import one.mixin.android.db.ParticipantSessionDao
import one.mixin.android.db.SnapshotDao
import one.mixin.android.db.StickerAlbumDao
import one.mixin.android.db.StickerDao
import one.mixin.android.db.StickerRelationshipDao
import one.mixin.android.db.TopAssetDao
import one.mixin.android.db.UserDao
import one.mixin.android.repository.AssetRepository
import one.mixin.android.repository.ConversationRepository
import one.mixin.android.repository.UserRepository
import one.mixin.android.vo.LinkState
import one.mixin.android.websocket.ChatWebSocket
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject

abstract class BaseJob(params: Params) : Job(params) {

    @InstallIn(SingletonComponent::class)
    @EntryPoint
    interface JobEntryPoint {
        fun inject(baseJob: BaseJob)
    }

    @Inject
    @Transient
    lateinit var jobManager: MixinJobManager
    @Inject
    @Transient
    lateinit var conversationApi: ConversationService
    @Inject
    @Transient
    lateinit var userService: UserService
    @Inject
    @Transient
    lateinit var contactService: ContactService
    @Inject
    @Transient
    lateinit var signalKeyService: SignalKeyService
    @Inject
    @Transient
    lateinit var messageService: MessageService
    @Inject
    @Transient
    lateinit var assetService: AssetService
    @Inject
    @Transient
    lateinit var accountService: AccountService
    @Inject
    @Transient
    lateinit var addressService: AddressService
    @Inject
    @Transient
    lateinit var circleService: CircleService
    @Inject
    @Transient
    lateinit var messageDao: MessageDao
    @Inject
    @Transient
    lateinit var messageHistoryDao: MessageHistoryDao
    @Inject
    @Transient
    lateinit var userDao: UserDao
    @Inject
    @Transient
    lateinit var conversationDao: ConversationDao
    @Inject
    @Transient
    lateinit var participantDao: ParticipantDao
    @Inject
    @Transient
    lateinit var participantSessionDao: ParticipantSessionDao
    @Inject
    @Transient
    lateinit var offsetDao: OffsetDao
    @Inject
    @Transient
    lateinit var assetDao: AssetDao
    @Inject
    @Transient
    lateinit var snapshotDao: SnapshotDao
    @Inject
    @Transient
    lateinit var chatWebSocket: ChatWebSocket
    @Inject
    @Transient
    lateinit var conversationRepo: ConversationRepository
    @Inject
    @Transient
    lateinit var userRepo: UserRepository
    @Inject
    @Transient
    lateinit var assetRepo: AssetRepository
    @Inject
    @Transient
    lateinit var stickerDao: StickerDao
    @Inject
    @Transient
    lateinit var hyperlinkDao: HyperlinkDao
    @Inject
    @Transient
    lateinit var stickerAlbumDao: StickerAlbumDao
    @Inject
    @Transient
    lateinit var stickerRelationshipDao: StickerRelationshipDao
    @Inject
    @Transient
    lateinit var addressDao: AddressDao
    @Inject
    @Transient
    lateinit var topAssetDao: TopAssetDao
    @Inject
    @Transient
    lateinit var jobDao: JobDao
    @Inject
    @Transient
    lateinit var favoriteAppDao: FavoriteAppDao
    @Inject
    @Transient
    lateinit var messageMentionDao: MessageMentionDao
    @Inject
    @Transient
    lateinit var messageFts4Dao: MessagesFts4Dao
    @Inject
    @Transient
    lateinit var appDao: AppDao
    @Inject
    @Transient
    lateinit var circleDao: CircleDao
    @Inject
    @Transient
    lateinit var circleConversationDao: CircleConversationDao
    @Inject
    @Transient
    lateinit var signalProtocol: SignalProtocol
    @Inject
    @Transient
    lateinit var encryptedProtocol: EncryptedProtocol
    @Transient
    @Inject
    lateinit var appDatabase: MixinDatabase
    @Transient
    @Inject
    lateinit var linkState: LinkState

    open fun shouldRetry(throwable: Throwable): Boolean {
        if (throwable is SocketTimeoutException) {
            return true
        }
        if (throwable is IOException) {
            return true
        }
        if (throwable is InterruptedException) {
            return true
        }
        return (throwable as? ServerErrorException)?.shouldRetry()
            ?: (throwable as? ExpiredTokenException)?.shouldRetry()
            ?: (
                (throwable as? ClientErrorException)?.shouldRetry()
                    ?: (
                        (throwable as? NetworkException)?.shouldRetry()
                            ?: (
                                (throwable as? WebSocketException)?.shouldRetry()
                                    ?: ((throwable as? LocalJobException)?.shouldRetry() ?: false)
                                )
                        )
                )
    }

    public override fun shouldReRunOnThrowable(throwable: Throwable, runCount: Int, maxRunCount: Int): RetryConstraint {
        if (runCount >= 10) {
            Bugsnag.notify(throwable) { report ->
                report.error.metaData = MetaData().apply {
                    addToTab("Job", "shouldReRunOnThrowable", "Retry max count:$runCount")
                }
            }
            Crashes.trackError(
                throwable,
                ArrayMap<String, String>().apply {
                    put("Job_shouldReRunOnThrowable", "Retry max count:$runCount")
                },
                null
            )
        }
        return if (shouldRetry(throwable)) {
            RetryConstraint.RETRY
        } else {
            RetryConstraint.CANCEL
        }
    }

    override fun onAdded() {
    }

    override fun onCancel(cancelReason: Int, throwable: Throwable?) {
        if (cancelReason == CancelReason.REACHED_RETRY_LIMIT) {
            throwable?.let {
                Bugsnag.notify(it) { report ->
                    report.error.metaData = MetaData().apply {
                        addToTab("Job", "CancelReason", "REACHED_RETRY_LIMIT")
                    }
                }
            }
        }
    }

    override fun getRetryLimit(): Int {
        return Integer.MAX_VALUE
    }

    @Suppress("unused")
    companion object {
        private const val serialVersionUID = 1L

        const val PRIORITY_UI_HIGH = 20
        const val PRIORITY_SEND_MESSAGE = 18
        const val PRIORITY_SEND_ATTACHMENT_MESSAGE = 17
        const val PRIORITY_SEND_SESSION_MESSAGE = 16
        const val PRIORITY_RECEIVE_MESSAGE = 15
        const val PRIORITY_BACKGROUND = 10
        const val PRIORITY_DELIVERED_ACK_MESSAGE = 7
        const val PRIORITY_ACK_MESSAGE = 5
        const val PRIORITY_LOWER = 3
        const val PRIORITY_LOWEST = 0
    }
}
