package org.langue.app

import android.content.Context
import android.media.MediaPlayer
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.langue.app.audio.OfflineAudioPlayer
import org.langue.app.data.AppRepository

/** Runs Android's real local decoder; all generated PCM audio remains in the instrumentation APK. */
@RunWith(AndroidJUnit4::class)
class LocalAudioDeviceTest {
    @Test fun importedWaveActuallyPreparesStartsAndCompletesWithAndroidDecoder() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val id = UUID.randomUUID().toString()
        val root = File(context.cacheDir, "local-audio-$id").also { it.mkdirs() }
        val database = "audio-$id.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = PreferenceDataStoreFactory.create(scope = scope) { File(root, "prefs.preferences_pb") }
        val repository = AppRepository(context, database, File(root, "packs"), preferences)
        var player: MediaPlayer? = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            val pack = repository.importPack(TestPacks.zip(TestPacks.pack()))
            val source = repository.audioFile(pack.manifest.id, pack.entries.first())
            assertNotNull(source)
            val prepared = CountDownLatch(1)
            val completed = CountDownLatch(1)
            val duration = AtomicInteger()
            val playing = AtomicBoolean()
            val error = AtomicReference<String?>(null)
            instrumentation.runOnMainSync {
                player = MediaPlayer().also { media ->
                    media.setOnPreparedListener {
                        try {
                            duration.set(it.duration)
                            it.start()
                            playing.set(it.isPlaying)
                        } catch (failure: Exception) {
                            error.set(failure.toString())
                            completed.countDown()
                        } finally { prepared.countDown() }
                    }
                    media.setOnCompletionListener { completed.countDown() }
                    media.setOnErrorListener { _, what, extra ->
                        error.set("Android decoder error $what/$extra")
                        prepared.countDown()
                        completed.countDown()
                        true
                    }
                    media.setDataSource(source!!.absolutePath)
                    media.prepareAsync()
                }
            }
            assertTrue("Local audio did not prepare", prepared.await(20, TimeUnit.SECONDS))
            assertNull(error.get())
            assertTrue("Decoder returned no audio duration", duration.get() > 0)
            assertTrue("Prepared local audio did not start", playing.get())
            assertTrue("Local audio did not complete", completed.await(20, TimeUnit.SECONDS))
            assertNull(error.get())
        } finally {
            instrumentation.runOnMainSync { player?.release() }
            repository.close()
            scope.cancel()
            context.deleteDatabase(database)
            root.deleteRecursively()
        }
    }

    @Test fun reusablePlayerReportsMissingLocalFileAndCanBeReleasedRepeatedly() {
        val context: Context = ApplicationProvider.getApplicationContext()
        var errors = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val audio = OfflineAudioPlayer(context)
            try {
                audio.play(File(context.cacheDir, "absent-${UUID.randomUUID()}.wav")) { errors++ }
                audio.stop()
                audio.release()
                audio.release()
            } finally { audio.release() }
        }
        assertEquals(1, errors)
    }
}
