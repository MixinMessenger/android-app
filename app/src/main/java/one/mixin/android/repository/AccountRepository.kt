package one.mixin.android.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.mixin.android.Constants.PIN_ERROR_MAX
import one.mixin.android.api.MixinResponse
import one.mixin.android.api.request.AccountRequest
import one.mixin.android.api.request.AccountUpdateRequest
import one.mixin.android.api.request.AuthorizeRequest
import one.mixin.android.api.request.DeauthorRequest
import one.mixin.android.api.request.EmergencyRequest
import one.mixin.android.api.request.LogoutRequest
import one.mixin.android.api.request.PinRequest
import one.mixin.android.api.request.RawTransactionsRequest
import one.mixin.android.api.request.SessionRequest
import one.mixin.android.api.request.SessionSecretRequest
import one.mixin.android.api.request.StickerAddRequest
import one.mixin.android.api.request.VerificationRequest
import one.mixin.android.api.response.AuthorizationResponse
import one.mixin.android.api.response.ConversationResponse
import one.mixin.android.api.response.MultisigsResponse
import one.mixin.android.api.response.PaymentCodeResponse
import one.mixin.android.api.response.VerificationResponse
import one.mixin.android.api.service.AccountService
import one.mixin.android.api.service.AuthorizationService
import one.mixin.android.api.service.ConversationService
import one.mixin.android.api.service.EmergencyService
import one.mixin.android.api.service.GiphyService
import one.mixin.android.api.service.GithubService
import one.mixin.android.api.service.UserService
import one.mixin.android.db.AppDao
import one.mixin.android.db.FavoriteAppDao
import one.mixin.android.db.StickerAlbumDao
import one.mixin.android.db.StickerDao
import one.mixin.android.db.StickerRelationshipDao
import one.mixin.android.db.UserDao
import one.mixin.android.db.insertUpdate
import one.mixin.android.db.insertUpdateList
import one.mixin.android.extension.within24Hours
import one.mixin.android.session.Session
import one.mixin.android.session.encryptPin
import one.mixin.android.util.ErrorHandler
import one.mixin.android.vo.Account
import one.mixin.android.vo.FavoriteApp
import one.mixin.android.vo.Sticker
import one.mixin.android.vo.StickerRelationship
import one.mixin.android.vo.User
import one.mixin.android.vo.github.Latest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository
@Inject
constructor(
    private val accountService: AccountService,
    private val userService: UserService,
    private val conversationService: ConversationService,
    private val userDao: UserDao,
    private val appDao: AppDao,
    private val favoriteAppDao: FavoriteAppDao,
    private val authService: AuthorizationService,
    private val stickerDao: StickerDao,
    private val stickerAlbumDao: StickerAlbumDao,
    private val stickerRelationshipDao: StickerRelationshipDao,
    private val giphyService: GiphyService,
    private val githubService: GithubService,
    private val emergencyService: EmergencyService
) {

    fun verification(request: VerificationRequest): Observable<MixinResponse<VerificationResponse>> =
        accountService.verification(request)

    suspend fun create(id: String, request: AccountRequest): MixinResponse<Account> =
        accountService.create(id, request)

    fun changePhone(id: String, request: AccountRequest): Observable<MixinResponse<Account>> =
        accountService.changePhone(id, request)

    fun update(request: AccountUpdateRequest): Observable<MixinResponse<Account>> =
        accountService.update(request)

    fun updateSession(request: SessionRequest) = accountService.updateSession(request)

    fun deviceCheck() = accountService.deviceCheck()

    fun join(conversationId: String): Observable<MixinResponse<ConversationResponse>> {
        return conversationService.join(conversationId)
    }

    fun searchCode(code: String): Observable<Pair<String, Any>> =
        accountService.code(code).subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
            .map { response ->
                if (!response.isSuccess) {
                    ErrorHandler.handleMixinError(response.errorCode, response.errorDescription)
                    return@map Pair("", "")
                }
                val result: Pair<String, Any>
                val type = response.data?.get("type")?.asString
                result = when (type) {
                    QrCodeType.user.name -> {
                        val user = Gson().fromJson(response.data, User::class.java)
                        userDao.insertUpdate(user, appDao)
                        Pair(type, user)
                    }
                    QrCodeType.conversation.name -> {
                        val conversationResponse =
                            Gson().fromJson(response.data, ConversationResponse::class.java)
                        Pair(type, conversationResponse)
                    }
                    QrCodeType.authorization.name -> {
                        val resp = Gson().fromJson(response.data, AuthorizationResponse::class.java)
                        Pair(type, resp)
                    }
                    QrCodeType.multisig_request.name -> {
                        val resp = Gson().fromJson(response.data, MultisigsResponse::class.java)
                        Pair(type, resp)
                    }
                    QrCodeType.payment.name -> {
                        val resp = Gson().fromJson(response.data, PaymentCodeResponse::class.java)
                        Pair(type, resp)
                    }
                    else -> Pair("", "")
                }
                result
            }.doOnError {
                ErrorHandler.handleError(it)
            }

    fun search(query: String): Observable<MixinResponse<User>> = userService.search(query)

    suspend fun logout(sessionId: String) = accountService.logout(LogoutRequest(sessionId))

    fun findUsersByType(relationship: String) = userDao.findUsersByType(relationship)

    fun updatePin(request: PinRequest) = accountService.updatePin(request)

    suspend fun verifyPin(code: String): MixinResponse<Account> = withContext(Dispatchers.IO) {
        accountService.verifyPin(PinRequest(encryptPin(Session.getPinToken()!!, code)!!))
    }

    fun authorize(request: AuthorizeRequest) = authService.authorize(request)

    fun deAuthorize(deauthorRequest: DeauthorRequest) = authService.deAuthorize(deauthorRequest)

    fun getSystemAlbums() = stickerAlbumDao.getSystemAlbums()

    suspend fun getPersonalAlbums() = stickerAlbumDao.getPersonalAlbums()

    fun observeStickers(id: String) = stickerRelationshipDao.observeStickersByAlbumId(id)

    fun observePersonalStickers() = stickerRelationshipDao.observePersonalStickers()

    fun recentUsedStickers() = stickerDao.recentUsedStickers()

    suspend fun updateUsedAt(stickerId: String, at: String) = stickerDao.updateUsedAt(stickerId, at)

    fun addStickerAsync(request: StickerAddRequest) = accountService.addStickerAsync(request)

    fun addStickerLocal(sticker: Sticker, albumId: String) {
        stickerDao.insertUpdate(sticker)
        stickerRelationshipDao.insert(StickerRelationship(albumId, sticker.stickerId))
    }

    fun trendingGifs(limit: Int, offset: Int) = giphyService.trendingGifs(limit, offset)

    fun searchGifs(query: String, limit: Int, offset: Int) =
        giphyService.searchGifs(query, limit, offset)

    suspend fun createEmergency(request: EmergencyRequest) = emergencyService.create(request)

    suspend fun createVerifyEmergency(id: String, request: EmergencyRequest) =
        emergencyService.createVerify(id, request)

    suspend fun loginVerifyEmergency(id: String, request: EmergencyRequest) =
        emergencyService.loginVerify(id, request)

    suspend fun showEmergency(pin: String) =
        emergencyService.show(PinRequest(encryptPin(Session.getPinToken()!!, pin)!!))

    suspend fun deleteEmergency(pin: String) =
        emergencyService.delete(PinRequest(encryptPin(Session.getPinToken()!!, pin)!!))

    suspend fun getFiats() = accountService.getFiats()

    suspend fun getPinLogs(category: String? = null, offset: String? = null, limit: Int? = null) = accountService.getPinLogs(category, offset, limit)

    suspend fun errorCount(): Int = PIN_ERROR_MAX - withContext(Dispatchers.IO) {
        val response = getPinLogs("PIN_INCORRECT", limit = PIN_ERROR_MAX)
        if (response.isSuccess) {
            val list = response.data ?: return@withContext 0
            for ((index, item) in list.withIndex()) {
                if (index == PIN_ERROR_MAX - 1 && item.createdAt.within24Hours()) {
                    return@withContext PIN_ERROR_MAX
                } else if (item.createdAt.within24Hours()) {
                    continue
                } else {
                    return@withContext index
                }
            }
            return@withContext 0
        } else {
            return@withContext 0
        }
    }

    suspend fun preferences(request: AccountUpdateRequest) = accountService.preferences(request)

    suspend fun signMultisigs(requestId: String, pinRequest: PinRequest) =
        accountService.signMultisigs(requestId, pinRequest)

    suspend fun unlockMultisigs(requestId: String, pinRequest: PinRequest) =
        accountService.unlockMultisigs(requestId, pinRequest)

    suspend fun cancelMultisigs(requestId: String) =
        accountService.cancelMultisigs(requestId)

    suspend fun transactions(rawTransactionsRequest: RawTransactionsRequest) =
        accountService.transactions(rawTransactionsRequest)

    suspend fun addFavoriteApp(appId: String) = userService.addFavoriteApp(appId)

    suspend fun insertFavoriteApp(favoriteApp: FavoriteApp) = favoriteAppDao.insert(favoriteApp)

    suspend fun insertFavoriteApps(userId: String, favoriteApps: List<FavoriteApp>) {
        favoriteAppDao.deleteByUserId(userId)
        favoriteAppDao.insertList(favoriteApps)
    }

    suspend fun getUserFavoriteApps(userId: String) = userService.getUserFavoriteApps(userId)

    suspend fun getFavoriteAppsByUserId(userId: String) = appDao.getFavoriteAppsByUserId(userId)

    suspend fun getUnfavoriteApps() = appDao.getUnfavoriteApps(Session.getAccountId()!!)

    suspend fun removeFavoriteApp(appId: String) = userService.removeFavoriteApp(appId)

    suspend fun deleteByAppIdAndUserId(appId: String, userId: String) =
        favoriteAppDao.deleteByAppIdAndUserId(appId, userId)

    suspend fun getApps() = appDao.getApps()

    suspend fun refreshAppNotExist(appIds: List<String>) {
        appIds.filter { id ->
            appDao.findAppById(id) == null || userDao.suspendFindUserById(id) == null
        }.let { ids ->
            val response = userService.fetchUsers(ids)
            if (response.isSuccess) {
                response.data?.apply {
                    userDao.insertUpdateList(this, appDao)
                }
            }
        }
    }

    suspend fun modifySessionSecret(request: SessionSecretRequest) = accountService.modifySessionSecret(request)

    suspend fun latest(): Latest = githubService.latest()
}
