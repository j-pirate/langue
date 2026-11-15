package org.langue.app

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.langue.app.audio.OfflineAudioPlayer
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

/** Verifies Android player callbacks/resource ownership; physical codec output needs a device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineAudioPlayerTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var audio: OfflineAudioPlayer
    private lateinit var manager: AudioManager
    private val created = mutableListOf<Pair<MediaPlayer, ShadowMediaPlayer>>()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        root = File(context.cacheDir, "audio-${UUID.randomUUID()}").also { it.mkdirs() }
        manager = context.getSystemService(AudioManager::class.java)
        shadowOf(manager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        ShadowMediaPlayer.setCreateListener { player, shadow -> created += player to shadow }
        // Explicit preparation lets the tests verify asynchronous stale-callback protection.
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(10_000, -1) }
        audio = OfflineAudioPlayer(context)
    }

    @After fun tearDown() {
        audio.release()
        root.deleteRecursively()
    }

    private fun wave(name: String = "local.wav") = File(root, name).also { it.writeBytes(TestPacks.tinyWave()) }

    @Test fun preparesLocalWaveAndReleasesPreviousPlayerBeforeStartingAnother() {
        var errors = 0
        val first = wave()
        audio.play(first) { errors++ }
        val (firstPlayer, firstShadow) = created.single()
        assertEquals(DataSource.toDataSource(first.absolutePath), firstShadow.dataSource)
        assertEquals(ShadowMediaPlayer.State.PREPARING, firstShadow.state)
        firstShadow.invokePreparedListener()
        assertTrue(firstPlayer.isPlaying)
        val stalePreparedCallback = firstShadow.onPreparedListener

        audio.play(wave("second.wav")) { errors++ }
        val (secondPlayer, secondShadow) = created.last()
        assertEquals(ShadowMediaPlayer.State.END, firstShadow.state)
        assertEquals(ShadowMediaPlayer.State.PREPARING, secondShadow.state)
        stalePreparedCallback.onPrepared(firstPlayer)
        assertEquals(ShadowMediaPlayer.State.END, firstShadow.state)
        secondShadow.invokePreparedListener()
        assertTrue(secondPlayer.isPlaying)
        audio.release()
        assertEquals(ShadowMediaPlayer.State.END, secondShadow.state)
        assertNotNull(shadowOf(manager).lastAbandonedAudioFocusRequest)
        assertEquals(0, errors)
    }

    @Test fun completionAndAudioFocusLossReleaseResources() {
        audio.play(wave()) { fail("Valid configured source must not fail") }
        val (_, first) = created.single()
        first.invokePreparedListener()
        first.invokeCompletionListener()
        assertEquals(ShadowMediaPlayer.State.END, first.state)
        audio.play(wave("next.wav")) { fail("Valid configured source must not fail") }
        val (_, second) = created.last()
        second.invokePreparedListener()
        shadowOf(manager).lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(ShadowMediaPlayer.State.END, second.state)
    }

    @Test fun missingFileAndDeniedAudioFocusReportFailureWithoutAllocatingAPlayer() {
        var errors = 0
        audio.play(File(root, "missing.wav")) { errors++ }
        assertEquals(1, errors)
        assertTrue(created.isEmpty())
        shadowOf(manager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        audio.play(wave()) { errors++ }
        assertEquals(2, errors)
        assertTrue(created.isEmpty())
        assertNotNull(shadowOf(manager).lastAbandonedAudioFocusRequest)
    }

    @Test fun synchronousSourceFailureAndAsynchronousDecodeFailureAreHandled() {
        var errors = 0
        val broken = wave("broken.wav")
        ShadowMediaPlayer.addException(DataSource.toDataSource(broken.absolutePath), IOException("Corrupt source"))
        audio.play(broken) { errors++ }
        assertEquals(1, errors)
        assertEquals(ShadowMediaPlayer.State.END, created.last().second.state)
        audio.play(wave("decode.wav")) { errors++ }
        val (_, decoding) = created.last()
        decoding.invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, MediaPlayer.MEDIA_ERROR_MALFORMED)
        assertEquals(2, errors)
        assertEquals(ShadowMediaPlayer.State.END, decoding.state)
    }
}
