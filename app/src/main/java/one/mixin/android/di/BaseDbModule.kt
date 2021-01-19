package one.mixin.android.di

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import one.mixin.android.crypto.db.SignalDatabase
import one.mixin.android.db.MixinDatabase
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
internal object BaseDbModule {

    @Singleton
    @Provides
    fun provideSignalDb(app: Application) = SignalDatabase.getDatabase(app)

    @Singleton
    @Provides
    fun provideRatchetSenderKeyDao(db: SignalDatabase) = db.ratchetSenderKeyDao()

    @Singleton
    @Provides
    fun provideDb(app: Application) = MixinDatabase.getDatabase(app)

    @Singleton
    @Provides
    fun provideUserDao(db: MixinDatabase) = db.userDao()

    @Singleton
    @Provides
    fun provideSessionParticipantDao(db: MixinDatabase) = db.participantSessionDao()

    @Singleton
    @Provides
    fun provideConversationDao(db: MixinDatabase) = db.conversationDao()

    @Singleton
    @Provides
    fun provideMessageDao(db: MixinDatabase) = db.messageDao()

    @Singleton
    @Provides
    fun provideParticipantDao(db: MixinDatabase) = db.participantDao()

    @Singleton
    @Provides
    fun provideOffsetDao(db: MixinDatabase) = db.offsetDao()

    @Singleton
    @Provides
    fun provideAssetExtraDao(db: MixinDatabase) = db.assetsExtraDao()

    @Singleton
    @Provides
    fun provideAssetDao(db: MixinDatabase) = db.assetDao()

    @Singleton
    @Provides
    fun provideSnapshotDao(db: MixinDatabase) = db.snapshotDao()

    @Singleton
    @Provides
    fun provideMessageHistoryDao(db: MixinDatabase) = db.messageHistoryDao()

    @Singleton
    @Provides
    fun provideStickerAlbumDao(db: MixinDatabase) = db.stickerAlbumDao()

    @Singleton
    @Provides
    fun provideStickerDao(db: MixinDatabase) = db.stickerDao()

    @Singleton
    @Provides
    fun provideHyperlinkDao(db: MixinDatabase) = db.hyperlinkDao()

    @Singleton
    @Provides
    fun providesAppDao(db: MixinDatabase) = db.appDao()

    @Singleton
    @Provides
    fun providesFloodMessageDao(db: MixinDatabase) = db.floodMessageDao()

    @Singleton
    @Provides
    fun providesJobDao(db: MixinDatabase) = db.jobDao()

    @Singleton
    @Provides
    fun providesAddressDao(db: MixinDatabase) = db.addressDao()

    @Singleton
    @Provides
    fun providesResendSessionMessageDao(db: MixinDatabase) = db.resendSessionMessageDao()

    @Singleton
    @Provides
    fun providesStickerRelationshipDao(db: MixinDatabase) = db.stickerRelationshipDao()

    @Singleton
    @Provides
    fun providesHotAssetDao(db: MixinDatabase) = db.topAssetDao()

    @Singleton
    @Provides
    fun providesFavoriteAppDao(db: MixinDatabase) = db.favoriteAppDao()

    @Singleton
    @Provides
    fun providesMentionMessageDao(db: MixinDatabase) = db.mentionMessageDao()

    @Singleton
    @Provides
    fun providesMessageFts4Dao(db: MixinDatabase) = db.messageFts4Dao()

    @Singleton
    @Provides
    fun providesCircleDao(db: MixinDatabase) = db.circleDao()

    @Singleton
    @Provides
    fun providesCircleConversationDao(db: MixinDatabase) = db.circleConversationDao()

    @Singleton
    @Provides
    fun providesTraceDao(db: MixinDatabase) = db.traceDao()

    @Singleton
    @Provides
    fun providesTranscriptDao(db: MixinDatabase) = db.transcriptDao()

    @Singleton
    @Provides
    fun providesPropertyDao(db: MixinDatabase) = db.propertyDao()
}
