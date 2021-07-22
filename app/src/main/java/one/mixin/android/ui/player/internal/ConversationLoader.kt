package one.mixin.android.ui.player.internal

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import one.mixin.android.db.MixinDatabase
import one.mixin.android.extension.fileSize
import one.mixin.android.ui.player.internal.AlbumArtCache.DEFAULT_ALBUM_ART
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.absolutePath

class ConversationLoader(
    private val database: MixinDatabase,
    val conversationId: String,
) : MusicLoader() {

    override suspend fun load(): List<MediaMetadataCompat> {
        var messageItems = database.messageDao().findAudiosByConversationId(conversationId)
        messageItems = messageItems.filter { !ignoreSet.contains(it.messageId) }
        return loadMessageItems(messageItems)
    }

    suspend fun loadByIds(items: Array<String>): List<MediaMetadataCompat> {
        var messageItems = database.messageDao().suspendFindMessagesByIds(conversationId, items.toList())
        messageItems = messageItems.filter { !ignoreSet.contains(it.messageId) }
        return loadMessageItems(messageItems)
    }

    private fun loadMessageItems(messageItems: List<MessageItem>): List<MediaMetadataCompat> {
        val mediaMetadataCompats = mutableListOf<MediaMetadataCompat>()
        messageItems.forEach { m ->
            when (m.mediaStatus) {
                MediaStatus.CANCELED.name, MediaStatus.PENDING.name -> {
                    mediaMetadataCompats.add(
                        MediaMetadataCompat.Builder()
                            .from(m)
                            .build()
                    )
                }
                else -> {
                    val url = m.absolutePath() ?: return@forEach
                    val musicMeta = retrieveMetadata(m.messageId, url) ?: return@forEach
                    mediaMetadataCompats.add(
                        MediaMetadataCompat.Builder()
                            .from(m, musicMeta)
                            .build()
                    )
                }
            }
        }

        mediaMetadataCompats.forEach { it.description.extras?.putAll(it.bundle) }
        return mediaMetadataCompats
    }

    fun MediaMetadataCompat.Builder.from(messageItem: MessageItem): MediaMetadataCompat.Builder {
        id = messageItem.messageId
        title = messageItem.mediaName
        val subtitle = messageItem.mediaSize?.fileSize()
        artist = subtitle
        album = conversationId
        flag = MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        messageItem.mediaStatus?.let { s ->
            downloadStatus = getDownloadStatus(s)
        }
        albumArtUri = DEFAULT_ALBUM_ART
        displayTitle = messageItem.mediaName
        displaySubtitle = subtitle
        displayIconUri = DEFAULT_ALBUM_ART
        return this
    }

    fun MediaMetadataCompat.Builder.from(messageItem: MessageItem, musicMeta: MusicMeta): MediaMetadataCompat.Builder {
        id = messageItem.messageId
        val titleString = musicMeta.title ?: messageItem.mediaName ?: unknownString
        val subtitle = musicMeta.artist ?: unknownString
        title = titleString
        artist = subtitle
        album = conversationId
        mediaUri = messageItem.absolutePath()
        flag = MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        messageItem.mediaStatus?.let { s ->
            downloadStatus = getDownloadStatus(s)
        }

        albumArtUri = musicMeta.albumArt
        displayTitle = titleString
        displaySubtitle = subtitle
        displayIconUri = musicMeta.albumArt
        return this
    }

    private fun getDownloadStatus(mediaStatus: String?) = when (mediaStatus) {
        MediaStatus.CANCELED.name -> MediaDescriptionCompat.STATUS_NOT_DOWNLOADED
        MediaStatus.PENDING.name -> MediaDescriptionCompat.STATUS_DOWNLOADING
        else -> {
            MediaDescriptionCompat.STATUS_DOWNLOADED
        }
    }
}
