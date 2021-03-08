package one.mixin.android.ui.conversation

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.RequiresPermission
import androidx.core.net.toUri
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.Constants
import one.mixin.android.Constants.MARK_LIMIT
import one.mixin.android.Constants.PAGE_SIZE
import one.mixin.android.MixinApplication
import one.mixin.android.api.request.RelationshipRequest
import one.mixin.android.api.request.StickerAddRequest
import one.mixin.android.extension.deserialize
import one.mixin.android.extension.fileExists
import one.mixin.android.extension.getFilePath
import one.mixin.android.extension.isUUID
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.nowInUtc
import one.mixin.android.extension.putString
import one.mixin.android.job.AttachmentDownloadJob
import one.mixin.android.job.ConvertVideoJob
import one.mixin.android.job.MixinJobManager
import one.mixin.android.job.RefreshStickerAlbumJob
import one.mixin.android.job.RefreshUserJob
import one.mixin.android.job.RemoveStickersJob
import one.mixin.android.job.SendAttachmentMessageJob
import one.mixin.android.job.SendGiphyJob
import one.mixin.android.job.UpdateRelationshipJob
import one.mixin.android.repository.AccountRepository
import one.mixin.android.repository.AssetRepository
import one.mixin.android.repository.ConversationRepository
import one.mixin.android.repository.UserRepository
import one.mixin.android.session.Session
import one.mixin.android.ui.common.message.SendMessageHelper
import one.mixin.android.util.Attachment
import one.mixin.android.util.ControlledRunner
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.KeyLivePagedListBuilder
import one.mixin.android.util.SINGLE_DB_THREAD
import one.mixin.android.vo.AppCap
import one.mixin.android.vo.AppItem
import one.mixin.android.vo.AssetItem
import one.mixin.android.vo.ConversationCategory
import one.mixin.android.vo.ConversationStatus
import one.mixin.android.vo.ForwardCategory
import one.mixin.android.vo.ForwardMessage
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.Message
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.MessageMinimal
import one.mixin.android.vo.MessageStatus
import one.mixin.android.vo.Participant
import one.mixin.android.vo.ShareCategory
import one.mixin.android.vo.ShareImageData
import one.mixin.android.vo.Sticker
import one.mixin.android.vo.User
import one.mixin.android.vo.createAckJob
import one.mixin.android.vo.createConversation
import one.mixin.android.vo.generateConversationId
import one.mixin.android.vo.giphy.Gif
import one.mixin.android.vo.giphy.Image
import one.mixin.android.vo.isGroupConversation
import one.mixin.android.vo.isImage
import one.mixin.android.vo.isVideo
import one.mixin.android.webrtc.SelectItem
import one.mixin.android.websocket.ACKNOWLEDGE_MESSAGE_RECEIPTS
import one.mixin.android.websocket.AudioMessagePayload
import one.mixin.android.websocket.BlazeAckMessage
import one.mixin.android.websocket.CREATE_MESSAGE
import one.mixin.android.websocket.ContactMessagePayload
import one.mixin.android.websocket.DataMessagePayload
import one.mixin.android.websocket.LiveMessagePayload
import one.mixin.android.websocket.LocationPayload
import one.mixin.android.websocket.StickerMessagePayload
import one.mixin.android.websocket.VideoMessagePayload
import one.mixin.android.websocket.toLocationData
import org.jetbrains.anko.doAsync
import java.io.File
import java.util.UUID

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class ConversationViewModel
@ViewModelInject
internal constructor(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val jobManager: MixinJobManager,
    private val assetRepository: AssetRepository,
    private val accountRepository: AccountRepository,
    private val messenger: SendMessageHelper
) : ViewModel() {

    var keyLivePagedListBuilder: KeyLivePagedListBuilder<Int, MessageItem>? = null

    fun getMessages(id: String, firstKeyToLoad: Int = 0, countable: Boolean): LiveData<PagedList<MessageItem>> {
        val pagedListConfig = PagedList.Config.Builder()
            .setPrefetchDistance(PAGE_SIZE * 2)
            .setPageSize(PAGE_SIZE)
            .setEnablePlaceholders(true)
            .build()
        if (!countable) {
            return LivePagedListBuilder(
                conversationRepository.getMessages(id, firstKeyToLoad, countable),
                pagedListConfig
            ).setInitialLoadKey(firstKeyToLoad)
                .build()
        }
        if (keyLivePagedListBuilder == null) {
            keyLivePagedListBuilder = KeyLivePagedListBuilder(
                conversationRepository.getMessages(id, firstKeyToLoad, countable),
                pagedListConfig
            ).setFirstKeyToLoad(firstKeyToLoad)
        }
        return keyLivePagedListBuilder!!.build()
    }

    suspend fun indexUnread(conversationId: String) =
        conversationRepository.indexUnread(conversationId) ?: 0

    suspend fun findFirstUnreadMessageId(conversationId: String, offset: Int): String? =
        conversationRepository.findFirstUnreadMessageId(conversationId, offset)

    fun searchConversationById(id: String) =
        conversationRepository.searchConversationById(id)
            .observeOn(AndroidSchedulers.mainThread()).subscribeOn(Schedulers.io())

    fun getConversationById(id: String) = conversationRepository.getConversationById(id)

    fun saveDraft(conversationId: String, text: String) = viewModelScope.launch {
        conversationRepository.saveDraft(conversationId, text)
    }

    fun findUserById(conversationId: String): LiveData<User> =
        userRepository.findUserById(conversationId)

    fun sendTextMessage(conversationId: String, sender: User, content: String, isEncrypted: Boolean) {
        messenger.sendTextMessage(viewModelScope, conversationId, sender, content, isEncrypted)
    }

    fun sendPostMessage(conversationId: String, sender: User, content: String, isEncrypted: Boolean) {
        messenger.sendPostMessage(conversationId, sender, content, isEncrypted)
    }

    fun sendAppCardMessage(conversationId: String, sender: User, content: String) {
        messenger.sendAppCardMessage(conversationId, sender, content)
    }

    fun sendReplyTextMessage(
        conversationId: String,
        sender: User,
        content: String,
        replyMessage: MessageItem,
        isEncrypted: Boolean
    ) {
        messenger.sendReplyTextMessage(conversationId, sender, content, replyMessage, isEncrypted)
    }

    fun sendAttachmentMessage(conversationId: String, sender: User, attachment: Attachment, isEncrypted: Boolean, replyMessage: MessageItem? = null) {
        messenger.sendAttachmentMessage(conversationId, sender, attachment, isEncrypted, replyMessage)
    }

    fun sendAudioMessage(
        conversationId: String,
        messageId: String,
        sender: User,
        file: File,
        duration: Long,
        waveForm: ByteArray,
        isEncrypted: Boolean,
        replyMessage: MessageItem? = null
    ) {
        messenger.sendAudioMessage(conversationId, messageId, sender, file, duration, waveForm, isEncrypted, replyMessage)
    }

    fun sendAudioMessage(
        conversationId: String,
        sender: User,
        audioMessagePayload: AudioMessagePayload,
        isEncrypted: Boolean,
        replyMessage: MessageItem? = null
    ) {
        val messageId = audioMessagePayload.messageId
        val file = File(audioMessagePayload.url)
        val duration = audioMessagePayload.duration
        val waveForm = audioMessagePayload.waveForm
        messenger.sendAudioMessage(conversationId, messageId, sender, file, duration, waveForm, isEncrypted, replyMessage)
    }

    fun sendStickerMessage(
        conversationId: String,
        sender: User,
        transferStickerData: StickerMessagePayload,
        isEncrypted: Boolean
    ) {
        messenger.sendStickerMessage(conversationId, sender, transferStickerData, isEncrypted)
    }

    fun sendContactMessage(conversationId: String, sender: User, shareUserId: String, isEncrypted: Boolean, replyMessage: MessageItem? = null) {
        viewModelScope.launch {
            val user = userRepository.suspendFindUserById(shareUserId)
            messenger.sendContactMessage(conversationId, sender, shareUserId, user?.fullName, isEncrypted, replyMessage)
        }
    }

    fun sendVideoMessage(
        conversationId: String,
        senderId: String,
        uri: Uri,
        isEncrypted: Boolean,
        messageId: String? = null,
        createdAt: String? = null,
        replyMessage: MessageItem? = null
    ) {
        messenger.sendVideoMessage(conversationId, senderId, uri, isEncrypted, messageId, createdAt, replyMessage)
    }

    fun sendVideoMessage(
        conversationId: String,
        senderId: String,
        videoMessagePayload: VideoMessagePayload,
        isEncrypted: Boolean,
        replyMessage: MessageItem? = null
    ) {
        val uri = videoMessagePayload.url.toUri()
        val messageId = videoMessagePayload.messageId
        val createdAt = videoMessagePayload.createdAt
        messenger.sendVideoMessage(conversationId, senderId, uri, isEncrypted, messageId, createdAt, replyMessage)
    }

    fun sendRecallMessage(conversationId: String, sender: User, list: List<MessageItem>) {
        messenger.sendRecallMessage(conversationId, sender, list)
    }

    fun sendLiveMessage(
        conversationId: String,
        sender: User,
        transferLiveData: LiveMessagePayload,
        isEncrypted: Boolean
    ) {
        messenger.sendLiveMessage(conversationId, sender, transferLiveData, isEncrypted)
    }

    fun sendGiphyMessage(
        conversationId: String,
        senderId: String,
        image: Image,
        isEncrypted: Boolean,
        previewUrl: String
    ) {
        messenger.sendGiphyMessage(conversationId, senderId, image, isEncrypted, previewUrl)
    }

    fun sendLocationMessage(conversationId: String, senderId: String, location: LocationPayload, isEncrypted: Boolean) {
        messenger.sendLocationMessage(conversationId, senderId, location, isEncrypted)
    }

    fun sendImageMessage(
        conversationId: String,
        sender: User,
        uri: Uri,
        isEncrypted: Boolean,
        mime: String? = null,
        replyMessage: MessageItem? = null
    ): Int {
        return messenger.sendImageMessage(conversationId, sender, uri, isEncrypted, mime, replyMessage)
    }

    fun updateRelationship(request: RelationshipRequest) {
        jobManager.addJobInBackground(UpdateRelationshipJob(request))
    }

    fun observeParticipantsCount(conversationId: String) =
        conversationRepository.observeParticipantsCount(conversationId)

    fun findParticipantById(conversationId: String, userId: String): Participant? =
        conversationRepository.findParticipantById(conversationId, userId)

    fun initConversation(conversationId: String, recipient: User, sender: User) {
        val createdAt = nowInUtc()
        val conversation = createConversation(
            conversationId,
            ConversationCategory.CONTACT.name,
            recipient.userId,
            ConversationStatus.START.ordinal
        )
        val participants = arrayListOf(
            Participant(conversationId, sender.userId, "", createdAt),
            Participant(conversationId, recipient.userId, "", createdAt)
        )
        conversationRepository.syncInsertConversation(conversation, participants)
    }

    fun getUserById(userId: String) =
        Observable.just(userId).subscribeOn(Schedulers.io())
            .map { userRepository.getUserById(it) }.observeOn(AndroidSchedulers.mainThread())!!

    fun cancel(id: String) = viewModelScope.launch(Dispatchers.IO) {
        jobManager.cancelJobByMixinJobId(id) {
            viewModelScope.launch {
                conversationRepository.updateMediaStatusSuspend(MediaStatus.CANCELED.name, id)
            }
        }
    }

    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun retryUpload(id: String, onError: () -> Unit) {
        doAsync {
            conversationRepository.findMessageById(id)?.let {
                if (it.isVideo() && it.mediaSize != null && it.mediaSize == 0L) {
                    try {
                        conversationRepository.updateMediaStatus(MediaStatus.PENDING.name, it.id)
                        jobManager.addJobInBackground(
                            ConvertVideoJob(
                                it.conversationId,
                                it.userId,
                                Uri.parse(it.mediaUrl),
                                it.category.startsWith("PLAIN"),
                                it.id,
                                it.createdAt
                            )
                        )
                    } catch (e: NullPointerException) {
                        onError.invoke()
                    }
                } else if (it.isImage() && it.mediaSize != null && it.mediaSize == 0L) { // un-downloaded GIPHY
                    val category =
                        if (it.category.startsWith("PLAIN")) MessageCategory.PLAIN_IMAGE.name else MessageCategory.SIGNAL_IMAGE.name
                    try {
                        jobManager.addJobInBackground(
                            SendGiphyJob(
                                it.conversationId, it.userId, it.mediaUrl!!, it.mediaWidth!!, it.mediaHeight!!,
                                it.mediaSize, category, it.id, it.thumbImage ?: "", it.createdAt
                            )
                        )
                    } catch (e: NullPointerException) {
                        onError.invoke()
                    }
                } else {
                    conversationRepository.updateMediaStatus(MediaStatus.PENDING.name, it.id)
                    jobManager.addJobInBackground(SendAttachmentMessageJob(it))
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun retryDownload(id: String) {
        doAsync {
            conversationRepository.findMessageById(id)?.let {
                jobManager.addJobInBackground(AttachmentDownloadJob(it))
            }
        }
    }

    fun markMessageRead(conversationId: String, accountId: String) {
        GlobalScope.launch(SINGLE_DB_THREAD) {
            notificationManager.cancel(conversationId.hashCode())
            while (true) {
                val list = conversationRepository.getUnreadMessage(conversationId, accountId, MARK_LIMIT)
                if (list.isEmpty()) return@launch
                conversationRepository.batchMarkReadAndTake(
                    conversationId,
                    accountId,
                    list.last().rowId
                )

                list.map {
                    createAckJob(
                        ACKNOWLEDGE_MESSAGE_RECEIPTS,
                        BlazeAckMessage(it.id, MessageStatus.READ.name)
                    )
                }.let {
                    conversationRepository.insertList(it)
                }
                createReadSessionMessage(list, conversationId)
                if (list.size < MARK_LIMIT) {
                    return@launch
                }
            }
        }
    }

    suspend fun getFriends(): List<User> = userRepository.getFriends()

    suspend fun findFriendsNotBot() = userRepository.findFriendsNotBot()

    suspend fun successConversationList() = conversationRepository.successConversationList()

    fun findContactUsers() = userRepository.findContactUsers()

    private val notificationManager: NotificationManager by lazy {
        MixinApplication.appContext.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun deleteMessages(list: List<MessageItem>) {
        viewModelScope.launch(SINGLE_DB_THREAD) {
            list.forEach { item ->
                conversationRepository.deleteMessage(
                    item.messageId,
                    item.mediaUrl,
                    item.mediaStatus == MediaStatus.DONE.name
                )
                jobManager.cancelJobByMixinJobId(item.messageId)
                notificationManager.cancel(item.userId.hashCode())
            }
        }
    }

    fun getSystemAlbums() = accountRepository.getSystemAlbums()

    suspend fun getPersonalAlbums() = accountRepository.getPersonalAlbums()

    fun observeStickers(id: String) = accountRepository.observeStickers(id)

    fun observePersonalStickers() = accountRepository.observePersonalStickers()

    fun recentStickers() = accountRepository.recentUsedStickers()

    fun updateStickerUsedAt(stickerId: String) {
        viewModelScope.launch {
            accountRepository.updateUsedAt(stickerId, System.currentTimeMillis().toString())
        }
    }

    fun getBottomApps(conversationId: String, guestId: String?): LiveData<List<AppItem>>? {
        return if (guestId == null) {
            Transformations.map(
                conversationRepository.getGroupAppsByConversationId(conversationId)
            ) { list ->
                list.filter {
                    it.capabilities?.contains(AppCap.GROUP.name) == true
                }
            }
        } else {
            val accountId = Session.getAccountId() ?: return null
            conversationRepository.getFavoriteAppsByUserId(guestId, accountId)
        }
    }

    suspend fun findAppById(id: String) = userRepository.findAppById(id)

    fun assetItemsWithBalance(): LiveData<List<AssetItem>> =
        assetRepository.assetItemsWithBalance()

    fun addStickerAsync(stickerAddRequest: StickerAddRequest) =
        accountRepository.addStickerAsync(stickerAddRequest)

    fun addStickerLocal(sticker: Sticker, albumId: String) =
        accountRepository.addStickerLocal(sticker, albumId)

    fun removeStickers(ids: List<String>) {
        jobManager.addJobInBackground(RemoveStickersJob(ids))
    }

    fun refreshStickerAlbums() {
        jobManager.addJobInBackground(RefreshStickerAlbumJob())
    }

    suspend fun findMessageIndex(conversationId: String, messageId: String) =
        conversationRepository.findMessageIndex(conversationId, messageId)

    private fun createReadSessionMessage(list: List<MessageMinimal>, conversationId: String) {
        Session.getExtensionSessionId()?.let {
            list.map {
                createAckJob(
                    CREATE_MESSAGE,
                    BlazeAckMessage(it.id, MessageStatus.READ.name),
                    conversationId
                )
            }.let {
                conversationRepository.insertList(it)
            }
        }
    }

    fun trendingGifs(limit: Int, offset: Int): Observable<List<Gif>> =
        accountRepository.trendingGifs(limit, offset).map { it.data }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

    fun searchGifs(query: String, limit: Int, offset: Int): Observable<List<Gif>> =
        accountRepository.searchGifs(query, limit, offset).map { it.data }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

    fun observeAddress(addressId: String) = assetRepository.observeAddress(addressId)

    fun updateRecentUsedBots(
        defaultSharedPreferences: SharedPreferences,
        userId: String
    ) = viewModelScope.launch(Dispatchers.IO) {
        val botsString =
            defaultSharedPreferences.getString(Constants.Account.PREF_RECENT_USED_BOTS, null)
        if (botsString != null) {
            var botsList = botsString.split("=")
            if (botsList.size == 1 && !botsList[0].isUUID()) {
                getPreviousVersionBotsList(defaultSharedPreferences)?.let {
                    botsList = it
                }
            }
            if (botsList.isNullOrEmpty()) {
                defaultSharedPreferences.putString(Constants.Account.PREF_RECENT_USED_BOTS, userId)
                return@launch
            }

            val arr = botsList.filter { it != userId }
                .toMutableList()
                .also {
                    if (it.size >= Constants.RECENT_USED_BOTS_MAX_COUNT) {
                        it.dropLast(1)
                    }
                    it.add(0, userId)
                }
            defaultSharedPreferences.putString(
                Constants.Account.PREF_RECENT_USED_BOTS,
                arr.joinToString("=")
            )
        } else {
            defaultSharedPreferences.putString(Constants.Account.PREF_RECENT_USED_BOTS, userId)
        }
    }

    private fun getPreviousVersionBotsList(defaultSharedPreferences: SharedPreferences): List<String>? {
        defaultSharedPreferences.getString(
            Constants.Account.PREF_RECENT_USED_BOTS,
            null
        )?.let { botsString ->
            return botsString.deserialize<Array<String>>()?.toList()
        } ?: return null
    }

    suspend fun findLastMessage(conversationId: String) =
        conversationRepository.findLastMessage(conversationId)

    suspend fun findUnreadMessageByMessageId(
        conversationId: String,
        userId: String,
        messageId: String
    ) = conversationRepository.findUnreadMessageByMessageId(conversationId, userId, messageId)

    suspend fun isSilence(conversationId: String, userId: String) =
        conversationRepository.isSilence(conversationId, userId) == 0

    fun refreshUser(userId: String, forceRefresh: Boolean) {
        jobManager.addJobInBackground(RefreshUserJob(listOf(userId), forceRefresh = forceRefresh))
    }

    fun downloadAttachment(message: Message) {
        jobManager.addJobInBackground(AttachmentDownloadJob(message))
    }

    suspend fun suspendFindUserById(userId: String) = withContext(Dispatchers.IO) {
        userRepository.suspendFindUserById(userId)
    }

    suspend fun getSortMessagesByIds(messages: Set<MessageItem>): ArrayList<ForwardMessage> {
        return withContext(Dispatchers.IO) {
            val list = ArrayList<ForwardMessage>()
            val sortMessages = conversationRepository.getSortMessagesByIds(messages.map { it.messageId })
            for (m in sortMessages) {
                val forwardMessage: ForwardMessage? = when {
                    m.category.endsWith("_TEXT") ->
                        m.content.notNullWithElse<String, ForwardMessage?>(
                            { c ->
                                ForwardMessage(ShareCategory.Text, c)
                            },
                            { null }
                        )
                    m.category.endsWith("_IMAGE") ->
                        m.mediaUrl.notNullWithElse<String, ForwardMessage?>(
                            { url ->
                                ForwardMessage(
                                    ShareCategory.Image,
                                    GsonHelper.customGson.toJson(ShareImageData(url))
                                )
                            },
                            { null }
                        )
                    m.category.endsWith("_DATA") -> {
                        if (m.mediaUrl == null || !m.mediaUrl.fileExists()) {
                            continue
                        }
                        m.name ?: continue
                        m.mediaMimeType ?: continue
                        m.mediaSize ?: continue
                        val dataMessagePayload = DataMessagePayload(
                            m.mediaUrl,
                            m.name,
                            m.mediaMimeType,
                            m.mediaSize
                        )
                        ForwardMessage(ForwardCategory.Data, GsonHelper.customGson.toJson(dataMessagePayload))
                    }
                    m.category.endsWith("_VIDEO") -> {
                        if (m.mediaUrl == null || !m.mediaUrl.fileExists()) {
                            continue
                        }
                        val videoData = VideoMessagePayload(
                            m.mediaUrl,
                            UUID.randomUUID().toString(),
                            nowInUtc()
                        )
                        ForwardMessage(ForwardCategory.Video, GsonHelper.customGson.toJson(videoData))
                    }
                    m.category.endsWith("_CONTACT") -> {
                        val shareUserId = m.sharedUserId ?: continue
                        val contactData = ContactMessagePayload(shareUserId)
                        ForwardMessage(ShareCategory.Contact, GsonHelper.customGson.toJson(contactData))
                    }
                    m.category.endsWith("_STICKER") -> {
                        val stickerData = StickerMessagePayload(
                            name = m.name,
                            stickerId = m.stickerId
                        )
                        ForwardMessage(ForwardCategory.Sticker, GsonHelper.customGson.toJson(stickerData))
                    }
                    m.category.endsWith("_AUDIO") -> {
                        val url = m.mediaUrl?.getFilePath() ?: continue
                        if (!File(url).exists()) continue

                        val duration = m.mediaDuration?.toLongOrNull() ?: continue
                        val waveForm = m.mediaWaveform ?: continue

                        val audioData = AudioMessagePayload(
                            UUID.randomUUID().toString(),
                            url,
                            duration,
                            waveForm
                        )
                        ForwardMessage(ForwardCategory.Audio, GsonHelper.customGson.toJson(audioData))
                    }
                    m.category.endsWith("_LIVE") -> {
                        if (m.mediaWidth == null ||
                            m.mediaWidth == 0 ||
                            m.mediaHeight == null ||
                            m.mediaHeight == 0 ||
                            m.mediaUrl.isNullOrBlank()
                        ) {
                            continue
                        }
                        val liveData = LiveMessagePayload(
                            m.mediaWidth,
                            m.mediaHeight,
                            m.thumbUrl ?: "",
                            m.mediaUrl
                        )
                        ForwardMessage(ShareCategory.Live, GsonHelper.customGson.toJson(liveData))
                    }
                    m.category.endsWith("_POST") ->
                        m.content.notNullWithElse<String, ForwardMessage?>(
                            { c ->
                                ForwardMessage(ShareCategory.Post, c)
                            },
                            { null }
                        )
                    m.category.endsWith("_LOCATION") ->
                        m.content.notNullWithElse<String, ForwardMessage?>(
                            { c ->
                                ForwardMessage(ForwardCategory.Location, GsonHelper.customGson.toJson(toLocationData(c)))
                            },
                            { null }
                        )
                    m.category == MessageCategory.APP_CARD.name ->
                        m.content.notNullWithElse<String, ForwardMessage?>(
                            { c ->
                                ForwardMessage(ShareCategory.AppCard, c)
                            },
                            { null }
                        )
                    else -> null
                }
                forwardMessage?.let { fm -> list.add(fm) }
            }
            return@withContext list
        }
    }

    suspend fun getAnnouncementByConversationId(conversationId: String) = conversationRepository.getAnnouncementByConversationId(conversationId)

    private val searchControlledRunner = ControlledRunner<List<User>>()

    suspend fun fuzzySearchUser(conversationId: String, keyword: String?): List<User> {
        return withContext(Dispatchers.IO) {
            searchControlledRunner.cancelPreviousThenRun {
                if (keyword.isNullOrEmpty()) {
                    userRepository.suspendGetGroupParticipants(conversationId)
                } else {
                    userRepository.fuzzySearchGroupUser(conversationId, keyword)
                }
            }
        }
    }

    suspend fun findUserByIdentityNumberSuspend(identityNumber: String) = userRepository.findUserByIdentityNumberSuspend(identityNumber)

    fun getUnreadMentionMessageByConversationId(conversationId: String) = conversationRepository.getUnreadMentionMessageByConversationId(conversationId)

    suspend fun markMentionRead(messageId: String, conversationId: String) {
        conversationRepository.markMentionRead(messageId, conversationId)
    }

    suspend fun conversationZeroClear(conversationId: String) =
        conversationRepository.conversationZeroClear(conversationId)

    suspend fun findLatestTrace(opponentId: String?, destination: String?, tag: String?, amount: String, assetId: String) =
        assetRepository.findLatestTrace(opponentId, destination, tag, amount, assetId)

    suspend fun checkData(selectItem: SelectItem, callback: suspend (String, Boolean) -> Unit) {
        withContext(Dispatchers.IO) {
            if (selectItem.conversationId != null) {
                val conversation = conversationRepository.getConversation(selectItem.conversationId)
                if (conversation != null) {
                    if (conversation.isGroupConversation()) {
                        withContext(Dispatchers.Main) {
                            callback(conversation.conversationId, false)
                        }
                    } else {
                        userRepository.findContactByConversationId(selectItem.conversationId)?.let { user ->
                            withContext(Dispatchers.Main) {
                                callback(conversation.conversationId, user.isBot())
                            }
                        }
                    }
                }
            } else if (selectItem.userId != null) {
                userRepository.getUserById(selectItem.userId)?.let { user ->
                    val conversation = conversationRepository.findContactConversationByOwnerId(user.userId)
                    if (conversation == null) {
                        val createdAt = nowInUtc()
                        val conversationId = generateConversationId(Session.getAccountId()!!, user.userId)
                        val participants = arrayListOf(
                            Participant(conversationId, Session.getAccountId()!!, "", createdAt),
                            Participant(conversationId, user.userId, "", createdAt)
                        )
                        conversationRepository.syncInsertConversation(
                            createConversation(
                                conversationId,
                                ConversationCategory.CONTACT.name,
                                user.userId,
                                ConversationStatus.START.ordinal
                            ),
                            participants
                        )
                        withContext(Dispatchers.Main) {
                            callback(conversationId, user.isBot())
                        }
                    }
                }
            }
        }
    }
}
