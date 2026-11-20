package org.langue.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File
import android.util.Log

/** One local stream at a time. A new play, lost audio focus, or lifecycle stop releases the old player. */
class OfflineAudioPlayer(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var generation = 0L
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    @Synchronized
    fun play(file: File, onError: () -> Unit) {
        stop()
        if (!file.isFile) {
            logFailure("source_missing")
            onError()
            return
        }
        val token = generation
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                synchronized(this) { if (token == generation && change < 0) stop() }
            }.build()
        focus = request
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stop()
            logFailure("focus_denied")
            onError()
            return
        }
        try {
            val next = MediaPlayer()
            player = next
            next.setAudioAttributes(attributes)
            next.setDataSource(file.absolutePath)
            next.setOnPreparedListener { prepared ->
                synchronized(this) {
                    if (token == generation && player === prepared) {
                        try { prepared.start() } catch (error: RuntimeException) { stop(); logFailure("start", error); onError() }
                    }
                }
            }
            next.setOnCompletionListener { completed ->
                synchronized(this) { if (player === completed) stop() }
            }
            next.setOnErrorListener { failed, what, extra ->
                synchronized(this) {
                    if (token == generation && player === failed) {
                        stop(); logFailure("decode", what = what, extra = extra); onError()
                    }
                }
                true
            }
            next.prepareAsync()
        } catch (error: Exception) {
            stop()
            logFailure("open", error)
            onError()
        }
    }

    @Synchronized
    fun stop() {
        generation++
        player?.release()
        player = null
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
    }

    fun release() = stop()

    private fun logFailure(stage: String, error: Throwable? = null, what: Int = 0, extra: Int = 0) {
        // Stages/classes/numeric decoder codes contain no paths, names or audio content.
        Log.w("LangueAudio", "stage=$stage type=${error?.javaClass?.simpleName ?: "MediaPlayer"} what=$what extra=$extra")
    }
}
