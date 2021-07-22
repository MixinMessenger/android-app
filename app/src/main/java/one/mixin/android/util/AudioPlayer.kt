package one.mixin.android.util

import android.media.AudioManager
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.RxBus
import one.mixin.android.db.MixinDatabase
import one.mixin.android.event.ProgressEvent.Companion.errorEvent
import one.mixin.android.event.ProgressEvent.Companion.pauseEvent
import one.mixin.android.event.ProgressEvent.Companion.playEvent
import one.mixin.android.event.RecallEvent
import one.mixin.android.extension.fileExists
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.openMedia
import one.mixin.android.extension.toast
import one.mixin.android.session.Session
import one.mixin.android.util.video.MixinPlayer
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.Message
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.absolutePath
import one.mixin.android.vo.isAudio
import one.mixin.android.vo.isData
import one.mixin.android.vo.mediaDownloaded
import one.mixin.android.widget.CircleProgress.Companion.STATUS_DONE
import one.mixin.android.widget.CircleProgress.Companion.STATUS_ERROR
import one.mixin.android.widget.CircleProgress.Companion.STATUS_PAUSE
import one.mixin.android.widget.CircleProgress.Companion.STATUS_PLAY
import org.threeten.bp.ZonedDateTime
import timber.log.Timber
import java.util.concurrent.TimeUnit

class AudioPlayer private constructor() {
    companion object {
        @Synchronized
        private fun get(): AudioPlayer {
            if (instance == null) {
                instance = AudioPlayer()
            }
            return instance as AudioPlayer
        }

        private var instance: AudioPlayer? = null

        fun release() {
            instance?.let {
                it.recallDisposable?.let { disposable ->
                    if (!disposable.isDisposed) {
                        disposable.dispose()
                    }
                }
                it.player.release()
                it.stopTimber()
            }
            instance = null
        }

        fun pause() {
            instance?.pause()
        }

        fun resume() {
            instance?.resume()
        }

        fun isEnd(): Boolean {
            return instance == null || instance?.status != STATUS_PLAY
        }

        fun switchAudioStreamType(useFrontSpeaker: Boolean) {
            instance?.switchAudioStreamType(useFrontSpeaker)
        }

        fun audioFilePlaying(): Boolean {
            return instance?.messageItem?.isData() == true
        }

        fun isPlay(id: String): Boolean = instance.notNullWithElse({ return it.status == STATUS_PLAY && it.id == id }, false)

        fun isLoaded(id: String): Boolean = instance.notNullWithElse({ it.isLoaded(id) }, false)

        fun seekTo(progress: Int, max: Float = 100f) = instance?.seekTo(progress, max)

        fun getProgress(): Float = instance?.progress ?: 0f

        private var statusListener: StatusListener? = null

        fun setStatusListener(statusListener: StatusListener) {
            this.statusListener = statusListener
        }

        fun play(
            messageItem: MessageItem,
            autoPlayNext: Boolean = true,
            continuePlayOnlyToday: Boolean = false,
            whenPlayNewAudioMessage: ((Message) -> Unit)? = null
        ) {
            get().play(messageItem, autoPlayNext, continuePlayOnlyToday, whenPlayNewAudioMessage)
        }
    }

    private var recallDisposable: Disposable? = null

    init {
        recallDisposable = RxBus.listen(RecallEvent::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                Timber.d("recall:" + it.messageId)
                if (id == it.messageId) {
                    pause()
                }
            }
    }

    fun switchAudioStreamType(useFrontSpeaker: Boolean) {
        if (useFrontSpeaker) {
            player.setAudioStreamType(AudioManager.STREAM_MUSIC)
        } else {
            player.setAudioStreamType(AudioManager.STREAM_VOICE_CALL)
        }
    }

    private val player: MixinPlayer = MixinPlayer(true).also {
        it.setCycle(false)
        it.setOnVideoPlayerListener(
            object : MixinPlayer.VideoPlayerListenerWrapper() {
                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        id?.let { id -> RxBus.publish(pauseEvent(id)) }
                        stopTimber()
                        status = STATUS_DONE

                        if (autoPlayNext) {
                            checkNext()
                        }
                    }
                    if (playWhenReady) {
                        MusicPlayer.pause()
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
                        status = STATUS_PAUSE
                        id?.let { id -> RxBus.publish(pauseEvent(id)) }
                    }
                    stopTimber()
                    it.stop()

                    reportExoPlayerException("AudioPlayer", error)
                }
            }
        )
    }

    private var id: String? = null
    private var messageItem: MessageItem? = null
    private var status = STATUS_PAUSE
        set(value) {
            if (field != value) {
                field = value
                statusListener?.onStatusChange(value)
            }
        }

    private var autoPlayNext: Boolean = true
    private var continuePlayOnlyToday: Boolean = false

    interface StatusListener {
        fun onStatusChange(status: Int)
    }

    private fun play(
        messageItem: MessageItem,
        autoPlayNext: Boolean = true,
        continuePlayOnlyToday: Boolean = false,
        whenPlayNewAudioMessage: ((Message) -> Unit)? = null
    ) {
        this.autoPlayNext = autoPlayNext
        this.continuePlayOnlyToday = continuePlayOnlyToday
        if (messageItem.mediaUrl == null) {
            MixinApplication.appContext.toast(R.string.error_bad_data)
            return
        } else if (!messageItem.absolutePath()!!.fileExists()) {
            MixinApplication.appContext.toast(R.string.error_file_exists)
            return
        }
        if (id != messageItem.messageId) {
            id = messageItem.messageId
            this.messageItem = messageItem
            player.loadAudio(messageItem.absolutePath()!!)

            if (autoPlayNext && messageItem.isAudio()) {
                markAudioReadAndCheckNextAudioAvailable(messageItem, whenPlayNewAudioMessage)
            }
        } else if (status == STATUS_DONE || status == STATUS_ERROR) {
            player.loadAudio(messageItem.absolutePath()!!)
        }
        status = STATUS_PLAY
        player.start()
        id?.let {
            RxBus.publish(playEvent(it))
        }
        startTimer()
    }

    private fun resume() {
        if (messageItem != null && (status == STATUS_PAUSE || status == STATUS_DONE || status == STATUS_ERROR)) {
            play(messageItem!!)
        }
    }

    private fun pause() {
        status = STATUS_PAUSE
        player.pause()
        id?.let { id ->
            RxBus.publish(pauseEvent(id, -1f))
        }
        stopTimber()
    }

    private fun isLoaded(id: String): Boolean {
        return this.id == id && status != STATUS_ERROR && status != STATUS_DONE
    }

    private fun seekTo(progress: Int, max: Float = 100f) {
        val p = progress * player.duration() / max
        player.seekTo(p.toInt())
        id?.let { id -> RxBus.publish(playEvent(id, p)) }
    }

    var timerDisposable: Disposable? = null
    var progress = 0f
    private fun startTimer() {
        if (timerDisposable == null) {
            timerDisposable = Observable.interval(0, 100, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread()).subscribe {
                    if (player.duration() == 0) {
                        return@subscribe
                    }
                    progress = player.getCurrentPos().toFloat() / player.duration()
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

    private fun checkNext() {
        messageItem?.let { item ->
            if (!item.isAudio()) return
            MixinApplication.appScope.launch(Dispatchers.IO) {
                val nextMessage = MixinDatabase.getDatabase(MixinApplication.appContext)
                    .messageDao()
                    .findNextAudioMessageItem(item.conversationId, item.createdAt, item.messageId)
                    ?: return@launch
                if (!nextMessage.mediaDownloaded() || !nextMessage.isAudio() || nextMessage.mediaUrl == null) return@launch

                if (continuePlayOnlyToday) {
                    val currentMessageDate = ZonedDateTime.parse(item.createdAt)
                    val nextMessageDate = ZonedDateTime.parse(nextMessage.createdAt)
                    if (currentMessageDate.year != nextMessageDate.year) return@launch
                    if (currentMessageDate.dayOfYear != nextMessageDate.dayOfYear) return@launch
                }

                withContext(Dispatchers.Main) {
                    play(nextMessage)
                }
            }
        }
    }

    private fun markAudioReadAndCheckNextAudioAvailable(
        currentMessage: MessageItem,
        whenPlayNewAction: ((Message) -> Unit)? = null
    ) = MixinApplication.appScope.launch(Dispatchers.IO) {
        val messageDao = MixinDatabase.getDatabase(MixinApplication.appContext).messageDao()
        if (currentMessage.mediaStatus == MediaStatus.DONE.name) {
            messageDao.updateMediaStatus(MediaStatus.READ.name, currentMessage.messageId)
        }
        val message = messageDao.findNextAudioMessage(
            currentMessage.conversationId,
            currentMessage.createdAt,
            currentMessage.messageId
        )
            ?: return@launch
        if (message.userId == Session.getAccountId()) return@launch
        if (!mediaDownloaded(message.mediaStatus)) {
            whenPlayNewAction?.invoke(message)
        }
    }
}
