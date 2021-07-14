package one.mixin.android.di

import android.app.Application
import android.content.ComponentName
import android.content.ContentResolver
import androidx.core.net.toUri
import com.birbit.android.jobqueue.config.Configuration
import com.birbit.android.jobqueue.scheduling.FrameworkJobSchedulerService
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioSwitch
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import one.mixin.android.BuildConfig
import one.mixin.android.Constants.API.FOURSQUARE_URL
import one.mixin.android.Constants.API.GIPHY_URL
import one.mixin.android.Constants.API.Mixin_URL
import one.mixin.android.Constants.API.URL
import one.mixin.android.Constants.DNS
import one.mixin.android.api.service.AccountService
import one.mixin.android.api.service.AddressService
import one.mixin.android.api.service.AssetService
import one.mixin.android.api.service.AuthorizationService
import one.mixin.android.api.service.CircleService
import one.mixin.android.api.service.ContactService
import one.mixin.android.api.service.ConversationService
import one.mixin.android.api.service.EmergencyService
import one.mixin.android.api.service.FoursquareService
import one.mixin.android.api.service.GiphyService
import one.mixin.android.api.service.MessageService
import one.mixin.android.api.service.ProvisioningService
import one.mixin.android.api.service.SignalKeyService
import one.mixin.android.api.service.UserService
import one.mixin.android.crypto.SignalProtocol
import one.mixin.android.db.ConversationDao
import one.mixin.android.db.FloodMessageDao
import one.mixin.android.db.JobDao
import one.mixin.android.db.MessageDao
import one.mixin.android.db.OffsetDao
import one.mixin.android.extension.filterNonAscii
import one.mixin.android.job.BaseJob
import one.mixin.android.job.JobLogger
import one.mixin.android.job.JobNetworkUtil
import one.mixin.android.job.MixinJobManager
import one.mixin.android.job.MyJobService
import one.mixin.android.ui.player.MusicService
import one.mixin.android.ui.player.internal.MusicServiceConnection
import one.mixin.android.util.LiveDataCallAdapterFactory
import one.mixin.android.util.cronet.CronetCallback
import one.mixin.android.util.cronet.CronetInterceptor
import one.mixin.android.vo.CallStateLiveData
import one.mixin.android.vo.LinkState
import one.mixin.android.websocket.ChatWebSocket
import org.chromium.net.CronetEngine
import org.chromium.net.ExperimentalCronetEngine
import org.chromium.net.RequestFinishedInfo
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module(includes = [(BaseDbModule::class)])
object AppModule {

    val LOCALE = Locale.getDefault().language + "-" + Locale.getDefault().country
    val API_UA = (
        "Mixin/" + BuildConfig.VERSION_NAME +
            " (Android " + android.os.Build.VERSION.RELEASE + "; " + android.os.Build.FINGERPRINT + "; " + LOCALE + ")"
        ).filterNonAscii()

    @Singleton
    @Provides
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor? {
        if (!BuildConfig.DEBUG) {
            return null
        }
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Singleton
    @Provides
    fun provideDispatcher(): Dispatcher = Dispatcher()

    @Singleton
    @Provides
    fun provideOkHttp(
        resolver: ContentResolver,
        dispatcher: Dispatcher,
        cronetEngine: ExperimentalCronetEngine,
        httpLoggingInterceptor: HttpLoggingInterceptor?,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
        builder.dispatcher(dispatcher)
        builder.addInterceptor(HostSelectionInterceptor.get())
        httpLoggingInterceptor?.let { interceptor ->
            builder.addInterceptor(interceptor)
        }
        builder.connectTimeout(10, TimeUnit.SECONDS)
        builder.writeTimeout(10, TimeUnit.SECONDS)
        builder.readTimeout(10, TimeUnit.SECONDS)
        builder.pingInterval(15, TimeUnit.SECONDS)
        builder.retryOnConnectionFailure(false)
        builder.dns(DNS)
        builder.addInterceptor(CronetInterceptor(resolver, cronetEngine, dispatcher.executorService))
        return builder.build()
    }

    @Singleton
    @Provides
    fun provideHttpService(okHttp: OkHttpClient): Retrofit {
        val builder = Retrofit.Builder()
            .baseUrl(URL)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addCallAdapterFactory(LiveDataCallAdapterFactory())
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttp)
        return builder.build()
    }

    @Singleton
    @Provides
    fun provideCronetEngine(
        app: Application,
        dispatcher: Dispatcher
    ): ExperimentalCronetEngine {
        val cacheDir = app.applicationContext.cacheDir.resolve("cronet-cache")
        cacheDir.mkdir()
        return ExperimentalCronetEngine.Builder(app.applicationContext)
            // .enableNetworkQualityEstimator(true)
            .addQuicHint(URL.toUri().host, 443, 443)
            .addQuicHint(Mixin_URL.toUri().host, 443, 443)
            .enableHttp2(true)
            .enableQuic(true)
            .setStoragePath(cacheDir.path)
            // .enableBrotli(true)
            .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 10 * 1024 * 1024)
            .build().apply {
                if (BuildConfig.DEBUG) {
                    addRequestFinishedListener(object : RequestFinishedInfo.Listener(dispatcher.executorService) {
                        override fun onRequestFinished(requestInfo: RequestFinishedInfo) {
                            CronetCallback.onRequestFinishedHandle(requestInfo)
                        }
                    })
                }
            }
    }

    @Singleton
    @Provides
    fun provideAccountService(retrofit: Retrofit) = retrofit.create(AccountService::class.java) as AccountService

    @Singleton
    @Provides
    fun provideUserService(retrofit: Retrofit) = retrofit.create(UserService::class.java) as UserService

    @Singleton
    @Provides
    fun provideContactService(retrofit: Retrofit) = retrofit.create(ContactService::class.java) as ContactService

    @Singleton
    @Provides
    fun provideSignalKeyService(retrofit: Retrofit) = retrofit.create(SignalKeyService::class.java) as SignalKeyService

    @Singleton
    @Provides
    fun provideConversationService(retrofit: Retrofit) =
        retrofit.create(ConversationService::class.java) as ConversationService

    @Singleton
    @Provides
    fun provideMessageService(retrofit: Retrofit) = retrofit.create(MessageService::class.java) as MessageService

    @Singleton
    @Provides
    fun provideAssetService(retrofit: Retrofit) = retrofit.create(AssetService::class.java) as AssetService

    @Singleton
    @Provides
    fun provideAuthService(retrofit: Retrofit) =
        retrofit.create(AuthorizationService::class.java) as AuthorizationService

    @Singleton
    @Provides
    fun provideAddressService(retrofit: Retrofit) = retrofit.create(AddressService::class.java) as AddressService

    @Singleton
    @Provides
    fun provideProvisioningService(retrofit: Retrofit) = retrofit.create(ProvisioningService::class.java) as ProvisioningService

    @Singleton
    @Provides
    fun provideEmergencyService(retrofit: Retrofit) = retrofit.create(EmergencyService::class.java) as EmergencyService

    @Singleton
    @Provides
    fun provideCircleService(retrofit: Retrofit) = retrofit.create(CircleService::class.java) as CircleService

    @Singleton
    @Provides
    fun provideContentResolver(app: Application) = app.contentResolver as ContentResolver

    @Provides
    @Singleton
    fun provideJobNetworkUtil(app: Application, linkState: LinkState) =
        JobNetworkUtil(app.applicationContext, linkState)

    @Suppress("INACCESSIBLE_TYPE")
    @Provides
    @Singleton
    fun jobManager(app: Application, jobNetworkUtil: JobNetworkUtil): MixinJobManager {
        val builder = Configuration.Builder(app)
            .consumerKeepAlive(20)
            .resetDelaysOnRestart()
            .maxConsumerCount(6)
            .minConsumerCount(2)
            .injector { job ->
                if (job is BaseJob) {
                    val entryPoint = EntryPointAccessors.fromApplication(app.applicationContext, BaseJob.JobEntryPoint::class.java)
                    entryPoint.inject(job)
                }
            }
            .customLogger(JobLogger())
            .networkUtil(jobNetworkUtil)
        builder.scheduler(
            FrameworkJobSchedulerService
                .createSchedulerFor(app.applicationContext, MyJobService::class.java)
        )
        return MixinJobManager(builder.build())
    }

    @Provides
    @Singleton
    fun provideLinkState() = LinkState()

    @Provides
    @Singleton
    fun provideSignalProtocol(app: Application) = SignalProtocol(app.applicationContext)

    @Provides
    @Singleton
    fun provideChatWebSocket(
        okHttp: OkHttpClient,
        app: Application,
        accountService: AccountService,
        conversationDao: ConversationDao,
        messageDao: MessageDao,
        offsetDao: OffsetDao,
        floodMessageDao: FloodMessageDao,
        jobManager: MixinJobManager,
        linkState: LinkState,
        jobDao: JobDao
    ): ChatWebSocket =
        ChatWebSocket(okHttp, app, accountService, conversationDao, messageDao, offsetDao, floodMessageDao, jobManager, linkState, jobDao)

    @Provides
    @Singleton
    fun provideGiphyService(httpLoggingInterceptor: HttpLoggingInterceptor?): GiphyService {
        val client = OkHttpClient.Builder().apply {
            httpLoggingInterceptor?.let { interceptor ->
                addNetworkInterceptor(interceptor)
            }
        }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl(GIPHY_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .client(client)
            .build()
        return retrofit.create(GiphyService::class.java)
    }

    @Provides
    @Singleton
    fun provideFoursquareService(httpLoggingInterceptor: HttpLoggingInterceptor?): FoursquareService {
        val client = OkHttpClient.Builder().apply {
            httpLoggingInterceptor?.let { interceptor ->
                addNetworkInterceptor(interceptor)
            }
        }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl(FOURSQUARE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .client(client)
            .build()
        return retrofit.create(FoursquareService::class.java)
    }

    @Provides
    @Singleton
    fun provideCallState() = CallStateLiveData()

    @Provides
    @Singleton
    fun provideAudioSwitch(app: Application): AudioSwitch =
        AudioSwitch(
            app.applicationContext, BuildConfig.DEBUG,
            preferredDeviceList = listOf(
                AudioDevice.BluetoothHeadset::class.java,
                AudioDevice.WiredHeadset::class.java,
                AudioDevice.Speakerphone::class.java,
                AudioDevice.Earpiece::class.java
            )
        )

    @Provides
    @Singleton
    fun provideMusicServiceConnection(app: Application): MusicServiceConnection =
        MusicServiceConnection.getInstance(
            app.applicationContext,
            ComponentName(app.applicationContext, MusicService::class.java)
        )
}
