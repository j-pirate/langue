package org.langue.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File

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
                        try { prepared.start() } catch (_: RuntimeException) { stop(); onError() }
                    }
                }
            }
            next.setOnCompletionListener { completed ->
                synchronized(this) { if (player === completed) stop() }
            }
            next.setOnErrorListener { failed, _, _ ->
                synchronized(this) {
                    if (token == generation && player === failed) { stop(); onError() }
                }
                true
            }
            next.prepareAsync()
        } catch (_: Exception) {
            stop()
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
}
