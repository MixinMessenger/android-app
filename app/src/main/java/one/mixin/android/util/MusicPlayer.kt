package one.mixin.android.util

import android.media.browse.MediaBrowser
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlaybackException.TYPE_SOURCE
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_REMOVE
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import one.mixin.android.BuildConfig
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.RxBus
import one.mixin.android.event.ProgressEvent.Companion.errorEvent
import one.mixin.android.event.ProgressEvent.Companion.pauseEvent
import one.mixin.android.event.ProgressEvent.Companion.playEvent
import one.mixin.android.event.RecallEvent
import one.mixin.android.extension.fileExists
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.openMedia
import one.mixin.android.extension.toast
import one.mixin.android.ui.player.FloatingPlayer
import one.mixin.android.ui.player.internal.CacheDataSourceFactory
import one.mixin.android.ui.player.internal.album
import one.mixin.android.ui.player.internal.downloadStatus
import one.mixin.android.ui.player.internal.flag
import one.mixin.android.ui.player.internal.id
import one.mixin.android.ui.player.internal.mediaUri
import one.mixin.android.ui.player.internal.title
import one.mixin.android.ui.player.internal.toMediaSource
import one.mixin.android.ui.player.isMusicServiceRunning
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.absolutePath
import one.mixin.android.vo.isData
import one.mixin.android.widget.CircleProgress.Companion.STATUS_DONE
import one.mixin.android.widget.CircleProgress.Companion.STATUS_ERROR
import one.mixin.android.widget.CircleProgress.Companion.STATUS_PAUSE
import one.mixin.android.widget.CircleProgress.Companion.STATUS_PLAY
import timber.log.Timber
import java.util.concurrent.TimeUnit

class MusicPlayer private constructor() {
    companion object {
        @Synchronized
        fun get(): MusicPlayer {
            if (instance == null) {
                instance = MusicPlayer()
            }
            return instance as MusicPlayer
        }

        private var instance: MusicPlayer? = null

        fun release() {
            instance?.let {
                it.recallDisposable?.let { disposable ->
                    if (!disposable.isDisposed) {
                        disposable.dispose()
                    }
                }
                it.exoPlayer.release()
                it.stopTimber()
            }
            instance = null
        }

        fun pause() {
            instance?.pause()
        }

        fun sameChatAudioFilePlaying(conversationId: String): Boolean {
            val playItem = instance?.messageItem
            return playItem?.conversationId == conversationId && playItem.isData()
        }

        fun isPlay(id: String): Boolean = instance.notNullWithElse({ return it.status == STATUS_PLAY && it.id == id }, false)

        fun seekTo(progress: Int, max: Float = 100f) = instance?.seekTo(progress, max)

        fun resetModeAndSpeed() {
            instance?.exoPlayer?.let {
                it.playbackParameters = PlaybackParameters(1f)
                it.repeatMode = Player.REPEAT_MODE_OFF
            }
        }

        fun preparePlaylist(
            metadataList: List<MediaMetadataCompat>,
            itemToPlay: MediaMetadataCompat?,
            playWhenReady: Boolean,
            playbackStartPositionMs: Long
        ) {
            get().preparePlaylist(metadataList, itemToPlay, playWhenReady, playbackStartPositionMs)
        }

        fun playMusic(messageItem: MessageItem) {
            get().playMusic(messageItem)
        }
    }

    private var recallDisposable: Disposable? = null

    init {
        recallDisposable = RxBus.listen(RecallEvent::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { event ->
                val index = currentPlaylistItems.indexOfFirst { it.description.mediaId == event.messageId }
                Timber.d("recall: ${event.messageId}, index: $index, id: $id")
                if (index != -1) {
                    concatenatingMediaSource.removeMediaSource(index)
                    currentPlaylistItems.removeAt(index)
                }
                if (id == event.messageId) {
                    pause()
                    exoPlayer.setMediaSource(concatenatingMediaSource)
                    exoPlayer.prepare()
                }
            }
    }

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val playerListener = PlayerListener()

    val exoPlayer: SimpleExoPlayer by lazy {
        SimpleExoPlayer.Builder(MixinApplication.appContext).build().apply {
            setAudioAttributes(this@MusicPlayer.audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            addListener(playerListener)
        }
    }

    private inner class PlayerListener : Player.EventListener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                id?.let { id -> RxBus.publish(pauseEvent(id)) }
                stopTimber()
                status = STATUS_DONE
                if (isMusicServiceRunning(MixinApplication.appContext)) {
                    FloatingPlayer.getInstance().stopAnim()
                }
            } else if (playbackState == Player.STATE_READY) {
                if (exoPlayer.playWhenReady) {
                    resume()
                } else {
                    pause()
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                AudioPlayer.pause()
                resume()
            } else {
                pause()
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            if (error.cause is UnrecognizedInputFormatException) {
                status = STATUS_ERROR
                id?.let { id -> RxBus.publish(errorEvent(id)) }
                MixinApplication.appContext.toast(R.string.error_not_supported_audio_format)
                messageItem?.let {
                    MixinApplication.appContext.openMedia(it)
                }
            } else {
                if (error.type == TYPE_SOURCE) {
                    MixinApplication.appContext.toast(R.string.player_playback_failed)
                }

                status = STATUS_PAUSE
                id?.let { id -> RxBus.publish(pauseEvent(id)) }
            }
            stopTimber()
            exoPlayer.stop()

            reportExoPlayerException("MusicPlayer", error)
        }

        override fun onPositionDiscontinuity(reason: Int) {
            if (reason == DISCONTINUITY_REASON_REMOVE) return

            val newMediaItem = exoPlayer.currentMediaItem ?: return
            if (newMediaItem.mediaId != id) {
                id = newMediaItem.mediaId
                resume()
            }
        }
    }

    private val dataSourceFactory: DefaultDataSourceFactory by lazy {
        DefaultDataSourceFactory(
            MixinApplication.appContext,
            Util.getUserAgent(MixinApplication.appContext, BuildConfig.APPLICATION_ID),
            null
        )
    }

    private val cacheDataSourceFactory = CacheDataSourceFactory(MixinApplication.appContext)

    private var id: String? = null
    private var messageItem: MessageItem? = null
    private var status = STATUS_PAUSE

    var currentPlaylistItems: MutableList<MediaMetadataCompat> = mutableListOf()

    private var concatenatingMediaSource = ConcatenatingMediaSource()

    fun preparePlaylist(
        metadataList: List<MediaMetadataCompat>,
        itemToPlay: MediaMetadataCompat?,
        playWhenReady: Boolean = true,
        playbackStartPositionMs: Long = 0,
    ) {
        val downloadedList = metadataList.filter { it.downloadStatus == MediaDescriptionCompat.STATUS_DOWNLOADED }
        currentPlaylistItems = downloadedList.toMutableList()
        playMusicList(downloadedList, itemToPlay, playWhenReady, playbackStartPositionMs)
    }

    private fun playMusic(messageItem: MessageItem) {
        if (messageItem.mediaUrl == null) {
            MixinApplication.appContext.toast(R.string.error_bad_data)
            return
        } else if (!messageItem.absolutePath()!!.fileExists()) {
            MixinApplication.appContext.toast(R.string.error_file_exists)
            return
        }

        checkAddToPlaylist(messageItem)

        if (id != messageItem.messageId) {
            id = messageItem.messageId
            this.messageItem = messageItem
            playMusicList(currentPlaylistItems)
        } else if (status == STATUS_DONE || status == STATUS_ERROR) {
            playMusicList(currentPlaylistItems)
        }

        resume()
    }

    fun setMediaSource(metadataList: List<MediaMetadataCompat>) {
        val downloadedList = metadataList.filter { it.downloadStatus == MediaDescriptionCompat.STATUS_DOWNLOADED }
        exoPlayer.apply {
            val itemToPlay = currentMediaItem?.mediaId
            val index = currentPlaylistItems.indexOfFirst { it.description.mediaId == itemToPlay }
            val remain = currentPlaylistItems.size - index
            currentPlaylistItems = downloadedList.toMutableList()
            val initialWindowIndex = if (itemToPlay == null) 0 else downloadedList.indexOfFirst { it.description.mediaId == itemToPlay }
            if (initialWindowIndex == -1) {
                val mediaSource = downloadedList.toMediaSource(dataSourceFactory, cacheDataSourceFactory)
                concatenatingMediaSource = mediaSource
                setMediaSource(mediaSource)
                val pos = getCurrentPos()
                prepare()
                if (initialWindowIndex != -1) {
                    seekTo(initialWindowIndex, pos)
                }
            } else {
                try {
                    concatenatingMediaSource.removeMediaSourceRange(0, index)
                    concatenatingMediaSource.removeMediaSourceRange(1, remain)
                } catch (e: Exception) {
                    val msg = """remove mediaSource from concatenatingMediaSource meet exception, 
                        |index: $index, remain: $remain, concatenatingMediaSource size: ${concatenatingMediaSource.size}"""
                    Timber.w(e, msg)
                    reportException(msg, e)
                }
                val pre = downloadedList.subList(0, initialWindowIndex).map {
                    it.toMediaSource(dataSourceFactory, cacheDataSourceFactory)
                }
                val post = downloadedList.subList(initialWindowIndex + 1, downloadedList.size).map {
                    it.toMediaSource(dataSourceFactory, cacheDataSourceFactory)
                }
                concatenatingMediaSource.addMediaSources(0, pre)
                concatenatingMediaSource.addMediaSources(post)
                if (index != -1) {
                    currentPlaylistItems.removeAt(index)
                }
            }
        }
    }

    private fun resume() {
        status = STATUS_PLAY
        if (!exoPlayer.playWhenReady) {
            exoPlayer.playWhenReady = true
        }
        id?.let {
            RxBus.publish(playEvent(it))
        }
        startTimer()

        if (isMusicServiceRunning(MixinApplication.appContext)) {
            FloatingPlayer.getInstance().startAnim()
        }
    }

    private fun pause() {
        status = STATUS_PAUSE
        if (exoPlayer.playWhenReady) {
            exoPlayer.playWhenReady = false
        }
        id?.let { id ->
            RxBus.publish(pauseEvent(id, -1f))
        }
        stopTimber()

        if (isMusicServiceRunning(MixinApplication.appContext)) {
            FloatingPlayer.getInstance().stopAnim()
        }
    }

    private fun seekTo(progress: Int, max: Float = 100f) {
        val p = progress * duration() / max
        exoPlayer.seekTo(p.toLong())
        id?.let { id -> RxBus.publish(playEvent(id, p)) }
    }

    private fun duration() = if (exoPlayer.duration == C.TIME_UNSET) 0 else exoPlayer.duration.toInt()

    private val period = Timeline.Period()

    private fun getCurrentPos(): Long {
        var position = exoPlayer.currentPosition
        val currentTimeline = exoPlayer.currentTimeline
        if (!currentTimeline.isEmpty) {
            position -= currentTimeline.getPeriod(exoPlayer.currentPeriodIndex, period)
                .positionInWindowMs
        }
        return position
    }

    private fun playMusicList(
        metadataList: List<MediaMetadataCompat>,
        itemToPlay: MediaMetadataCompat? = null,
        playWhenReady: Boolean = false,
        playbackStartPositionMs: Long = 0,
    ) {
        val initialWindowIndex = if (itemToPlay == null) 0 else metadataList.indexOf(itemToPlay)
        exoPlayer.apply {
            if (itemToPlay != null) {
                id = itemToPlay.description.mediaId
            }
            val currentPlayId = exoPlayer.currentMediaItem?.mediaId
            val changed = itemToPlay?.description?.mediaId != currentPlayId
            this.playWhenReady = playWhenReady
            val index = metadataList.indexOfFirst { it.description.mediaId == currentPlayId }
            if (changed || index == -1 || metadataList.size == 1) {
                val mediaSource = metadataList.toMediaSource(dataSourceFactory, cacheDataSourceFactory)
                concatenatingMediaSource = mediaSource
                setMediaSource(mediaSource)
                prepare()
                if (initialWindowIndex != -1) {
                    seekTo(initialWindowIndex, playbackStartPositionMs)
                }
            } else {
                if (mediaItemCount == 1 && metadataList.size > 1) {
                    val pre = metadataList.subList(0, index).map {
                        it.toMediaSource(dataSourceFactory, cacheDataSourceFactory)
                    }
                    val post = metadataList.subList(index + 1, metadataList.size).map {
                        it.toMediaSource(dataSourceFactory, cacheDataSourceFactory)
                    }

                    concatenatingMediaSource.addMediaSources(0, pre)
                    concatenatingMediaSource.addMediaSources(post)
                }
            }

            if (playWhenReady) {
                resume()
            }
        }
    }

    private var timerDisposable: Disposable? = null
    var progress = 0f
    private fun startTimer() {
        if (timerDisposable == null) {
            timerDisposable = Observable.interval(0, 100, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread()).subscribe {
                    if (duration() == 0) {
                        return@subscribe
                    }
                    progress = getCurrentPos().toFloat() / duration()
                    id?.let { id ->
                        RxBus.publish(playEvent(id, progress))
                    }
                }
        }
    }

    private fun stopTimber() {
        timerDisposable?.let {
            if (!it.isDisposed) {
                it.dispose()
            }
        }
        timerDisposable = null
    }

    private fun checkAddToPlaylist(messageItem: MessageItem) {
        if (messageItem.isData() && MimeTypes.isAudio(messageItem.mediaMimeType)) {
            val mediaMetadata = buildFromMessageItem(messageItem)
            if (currentPlaylistItems.isNullOrEmpty() || currentPlaylistItems[0].album != messageItem.conversationId) {
                currentPlaylistItems = mutableListOf(mediaMetadata)
            } else {
                val exists = currentPlaylistItems.find { it.description.mediaId == messageItem.messageId }
                if (exists == null) {
                    currentPlaylistItems.add(mediaMetadata)
                }
            }
        }
    }

    private fun buildFromMessageItem(messageItem: MessageItem) =
        MediaMetadataCompat.Builder().apply {
            id = messageItem.messageId
            title = messageItem.mediaName
            album = messageItem.conversationId
            mediaUri = messageItem.absolutePath()
            flag = MediaBrowser.MediaItem.FLAG_PLAYABLE
        }.build()
}
