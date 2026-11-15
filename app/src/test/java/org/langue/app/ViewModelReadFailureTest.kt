package org.langue.app

import android.content.Context
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.langue.app.data.AppRepository
import org.langue.app.ui.LangueViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLog

/** Real Room with a failing preference read, including recovery through the visible retry action. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class ViewModelReadFailureTest {
    @Test fun initialPreferenceAccessFailureIsSafeAndRetryRestartsTheFailedReaders() {
        exerciseReadFailure(SecurityException("PRIVATE_CONTENT /private/secret.preferences_pb"),
            R.string.error_storage_unavailable, failBeforeLaunch = true)
    }

    @Test fun readFailureAfterAQuestionInvalidatesItsSessionAndRetryRestoresIt() {
        exerciseReadFailure(IOException("PRIVATE_CONTENT /private/secret.preferences_pb"),
            R.string.initialize_error, failBeforeLaunch = false)
    }

    private fun exerciseReadFailure(error: Exception, expectedResource: Int, failBeforeLaunch: Boolean) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val suffix = UUID.randomUUID().toString()
        val root = File(context.cacheDir, "read-failure-$suffix").also { it.mkdirs() }
        val databaseName = "read-failure-$suffix.db"
        val preferences = RecoverablePreferences()
        val repository = AppRepository(context, databaseName, File(root, "packs"), preferences)
        val store = ViewModelStore()
        val fixture = TestPacks.pack()
        try {
            runBlocking { repository.importPack(TestPacks.zip(fixture)) }
            if (failBeforeLaunch) preferences.failWith(error)
            ShadowLog.clear()
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = LangueViewModel(repository) as T
            }
            val vm = ViewModelProvider(store, factory)[LangueViewModel::class.java]
            if (!failBeforeLaunch) {
                await("The initial question was not prepared") {
                    !vm.busy.value && vm.practice.value.question != null && !vm.quizPreparing.value
                }
                preferences.failWith(error)
            }
            // Idling the real main looper also exposes any uncaught exception from viewModelScope.
            await("Preference failure was not presented safely") {
                vm.initializationFailed.value && !vm.busy.value &&
                    vm.library.value.active == null && vm.practice.value.question == null && !vm.quizPreparing.value
            }
            assertEquals(expectedResource, vm.initializationMessage.value)
            val frenchMessage = context.getString(vm.initializationMessage.value)
            assertTrue(frenchMessage.isNotBlank())
            assertFalse(frenchMessage.contains("PRIVATE_CONTENT"))
            assertFalse(frenchMessage.contains("/private/"))
            val diagnostics = ShadowLog.getLogsForTag("LangueErrors")
            assertTrue("The read failure should have a safe diagnostic", diagnostics.isNotEmpty())
            diagnostics.forEach { diagnostic ->
                assertNull(diagnostic.throwable)
                assertFalse(diagnostic.msg.contains("PRIVATE_CONTENT"))
                assertFalse(diagnostic.msg.contains("/private/"))
            }
            val subscriptionsBeforeRetry = preferences.subscriptions.get()
            preferences.recover()
            vm.initialize()
            await("Retry did not resubscribe the failed readers and prepare a new session") {
                !vm.busy.value && !vm.initializationFailed.value && !vm.quizPreparing.value &&
                    vm.library.value.active?.manifest?.id == fixture.manifest.id && vm.practice.value.question != null
            }
            assertTrue(preferences.subscriptions.get() > subscriptionsBeforeRetry)
            val question = vm.practice.value.question!!
            assertTrue(fixture.entries.any { it.id == question.entry.id })
            vm.answer(question.entry.id)
            await("The recovered session could not persist an answer") {
                !vm.busy.value && vm.library.value.stats.answered == 1 && vm.library.value.stats.correct == 1
            }
        } finally {
            store.clear()
            shadowOf(Looper.getMainLooper()).idle()
            repository.close()
            context.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    private fun await(message: String, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(10)
        }
        fail(message)
    }

    private class RecoverablePreferences : DataStore<Preferences> {
        val subscriptions = AtomicInteger()
        private val values = MutableStateFlow<Preferences>(emptyPreferences())
        private val failure = MutableStateFlow<Exception?>(null)
        private val writes = Mutex()

        override val data: Flow<Preferences> = flow {
            subscriptions.incrementAndGet()
            emitAll(combine(values, failure) { preferences, error ->
                if (error != null) throw error
                preferences
            })
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            writes.withLock { transform(values.value).also { values.value = it } }

        fun failWith(error: Exception) { failure.value = error }
        fun recover() { failure.value = null }
    }
}
