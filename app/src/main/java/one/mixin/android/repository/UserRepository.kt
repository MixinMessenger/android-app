package one.mixin.android.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.mixin.android.api.MixinResponse
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.request.CircleConversationRequest
import one.mixin.android.api.service.CircleService
import one.mixin.android.api.service.UserService
import one.mixin.android.db.AppDao
import one.mixin.android.db.CircleConversationDao
import one.mixin.android.db.CircleDao
import one.mixin.android.db.ConversationDao
import one.mixin.android.db.TranscriptMessageDao
import one.mixin.android.db.UserDao
import one.mixin.android.db.insertUpdate
import one.mixin.android.db.insertUpdateList
import one.mixin.android.db.insertUpdateSuspend
import one.mixin.android.db.runInTransaction
import one.mixin.android.db.updateRelationship
import one.mixin.android.extension.oneWeekAgo
import one.mixin.android.session.Session
import one.mixin.android.vo.App
import one.mixin.android.vo.Circle
import one.mixin.android.vo.CircleConversation
import one.mixin.android.vo.CircleName
import one.mixin.android.vo.CircleOrder
import one.mixin.android.vo.ConversationCircleManagerItem
import one.mixin.android.vo.User
import one.mixin.android.vo.UserRelationship
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository
@Inject
constructor(
    private val userDao: UserDao,
    private val appDao: AppDao,
    private val circleDao: CircleDao,
    private val userService: UserService,
    private val conversationDao: ConversationDao,
    private val circleService: CircleService,
    private val circleConversationDao: CircleConversationDao,
    private val transcriptMessageDao: TranscriptMessageDao
) {

    fun findFriends(): LiveData<List<User>> = userDao.findFriends()

    fun findContacts(): LiveData<List<User>> = userDao.findContacts()

    suspend fun getFriends(): List<User> = userDao.getFriends()

    suspend fun fuzzySearchUser(query: String): List<User> = userDao.fuzzySearchUser(query, query, Session.getAccountId() ?: "")

    suspend fun searchSuspend(query: String): MixinResponse<User> = userService.searchSuspend(query)

    suspend fun fuzzySearchGroupUser(conversationId: String, query: String): List<User> =
        userDao.fuzzySearchGroupUser(conversationId, query, query, Session.getAccountId() ?: "")

    suspend fun fuzzySearchBotGroupUser(conversationId: String, query: String): List<User> =
        userDao.fuzzySearchBotGroupUser(conversationId, query, query, Session.getAccountId() ?: "", oneWeekAgo())

    suspend fun suspendGetGroupParticipants(conversationId: String): List<User> =
        userDao.suspendGetGroupParticipants(conversationId, Session.getAccountId() ?: "")

    fun findUserById(query: String): LiveData<User> = userDao.findUserById(query)

    suspend fun suspendFindUserById(query: String) = userDao.suspendFindUserById(query)

    fun getUserById(id: String): User? = userDao.findUser(id)

    suspend fun findUserExist(userIds: List<String>): List<String> = userDao.findUserExist(userIds)

    fun getUser(id: String) = userService.getUserById(id)

    suspend fun refreshUser(id: String): User? {
        val user = userDao.suspendFindUserById(id)
        if (user != null) return user

        return handleMixinResponse(
            invokeNetwork = {
                userService.getUserByIdSuspend(id)
            },
            switchContext = Dispatchers.IO,
            successBlock = {
                it.data?.let { u ->
                    upsert(u)
                    return@handleMixinResponse u
                }
            }
        )
    }

    suspend fun getAppAndCheckUser(id: String, updatedAt: String?): App? {
        val app = findAppById(id)
        if (app?.updatedAt != null && app.updatedAt == updatedAt) {
            return app
        }

        return handleMixinResponse(
            invokeNetwork = {
                userService.getUserByIdSuspend(id)
            },
            successBlock = {
                it.data?.let { u ->
                    withContext(Dispatchers.IO) {
                        upsert(u)
                    }
                    return@handleMixinResponse u.app
                }
            }
        )
    }

    fun findUserByConversationId(conversationId: String): LiveData<User> =
        userDao.findUserByConversationId(conversationId)

    fun findContactByConversationId(conversationId: String): User? =
        userDao.findContactByConversationId(conversationId)

    suspend fun suspendFindContactByConversationId(conversationId: String): User? =
        userDao.suspendFindContactByConversationId(conversationId)

    fun findSelf(): LiveData<User?> = userDao.findSelf(Session.getAccountId() ?: "").asLiveData()

    suspend fun upsert(user: User) = withContext(Dispatchers.IO) {
        userDao.insertUpdate(user, appDao)
    }

    suspend fun upsertList(users: List<User>) = withContext(Dispatchers.IO) {
        userDao.insertUpdateList(users, appDao)
    }

    suspend fun insertApp(app: App) {
        appDao.insertSuspend(app)
    }

    suspend fun upsertBlock(user: User) = withContext(Dispatchers.IO) {
        userDao.updateRelationship(user, UserRelationship.BLOCKING.name)
    }

    fun updatePhone(id: String, phone: String) = userDao.updatePhone(id, phone)

    suspend fun findAppById(id: String) = appDao.findAppById(id)

    suspend fun searchAppByHost(query: String) = appDao.searchAppByHost("%$query%")

    fun findContactUsers() = userDao.findContactUsers()

    suspend fun findFriendsNotBot() = userDao.findFriendsNotBot()

    fun findAppsByIds(appIds: List<String>) = appDao.findAppsByIds(appIds)

    suspend fun getApps() = appDao.getApps()

    suspend fun findMultiUsersByIds(ids: Set<String>) = userDao.findMultiUsersByIds(ids)

    suspend fun findMultiCallUsersByIds(conversationId: String, ids: Set<String>) =
        userDao.findMultiCallUsersByIds(conversationId, ids)

    suspend fun findSelfCallUser(conversationId: String, userId: String) =
        userDao.findSelfCallUser(conversationId, userId)

    suspend fun fetchUser(ids: List<String>) = userService.fetchUsers(ids)

    suspend fun findUserByIdentityNumberSuspend(identityNumber: String) = userDao.suspendFindUserByIdentityNumber(identityNumber)

    fun insertUser(user: User) = userDao.insertUpdate(user, appDao)

    suspend fun findUserIdByAppNumber(conversationId: String, appNumber: String) = userDao.findUserIdByAppNumber(conversationId, appNumber)

    suspend fun createCircle(name: String) = circleService.createCircle(CircleName(name))

    fun observeAllCircleItem() = circleDao.observeAllCircleItem()

    suspend fun insertCircle(circle: Circle) = circleDao.insertUpdateSuspend(circle)

    suspend fun circleRename(circleId: String, name: String) = circleService.updateCircle(circleId, CircleName(name))

    suspend fun deleteCircle(circleId: String) = circleService.deleteCircle(circleId)

    suspend fun deleteCircleById(circleId: String) = circleDao.deleteCircleByIdSuspend(circleId)

    suspend fun findConversationItemByCircleId(circleId: String) =
        circleDao.findConversationItemByCircleId(circleId)

    suspend fun updateCircleConversations(id: String, circleConversationRequests: List<CircleConversationRequest>) =
        circleService.updateCircleConversations(id, circleConversationRequests)

    suspend fun sortCircleConversations(list: List<CircleOrder>?) = withContext(Dispatchers.IO) {
        runInTransaction {
            list?.forEach {
                circleDao.updateOrderAt(CircleOrder(it.circleId, it.orderedAt))
            }
        }
    }

    suspend fun deleteCircleConversation(conversationId: String, circleId: String) =
        circleConversationDao.deleteByIdsSuspend(conversationId, circleId)

    suspend fun deleteByCircleId(circleId: String) =
        circleConversationDao.deleteByCircleIdSuspend(circleId)

    suspend fun insertCircleConversation(circleConversation: CircleConversation) =
        circleConversationDao.insertSuspend(circleConversation)

    suspend fun findCircleConversationByCircleId(circleId: String) =
        circleConversationDao.findCircleConversationByCircleId(circleId)

    suspend fun getIncludeCircleItem(conversationId: String): List<ConversationCircleManagerItem> =
        circleDao.getIncludeCircleItem(conversationId)

    suspend fun getOtherCircleItem(conversationId: String): List<ConversationCircleManagerItem> =
        circleDao.getOtherCircleItem(conversationId)

    fun hasUnreadMessage(circleId: String): LiveData<Boolean> {
        return conversationDao.hasUnreadMessage(circleId).map {
            it != null && it > 0
        }
    }

    suspend fun findCirclesNameByConversationId(conversationId: String) =
        circleDao.findCirclesNameByConversationId(conversationId)

    suspend fun findCircleItemByCircleIdSuspend(circleId: String) =
        circleDao.findCircleItemByCircleIdSuspend(circleId)

    suspend fun getCircleConversationCount(conversationId: String) =
        circleConversationDao.getCircleConversationCount(conversationId)

    suspend fun getNotTopApps(appIds: List<String>): List<App> = appDao.getNotTopApps(appIds)

    suspend fun findUserByAppId(appId: String): User? = userDao.findUserByAppId(appId)

    fun updateMuteUntil(id: String, muteUntil: String) = userDao.updateMuteUntil(id, muteUntil)
}
