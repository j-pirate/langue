package org.langue.app

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.langue.app.data.AppRepository
import org.langue.app.data.QuizStats
import org.langue.app.ui.LangueViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Exercises session ownership through real Room flows, without inspecting the cache internals. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class QuizSessionViewModelTest {
    @Test fun scoreUpdatesKeepFeedbackAndPackRevisionsReplaceThePreparedSession() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val suffix = UUID.randomUUID().toString()
        val root = File(context.cacheDir, "quiz-session-$suffix").also { it.mkdirs() }
        val databaseName = "quiz-session-$suffix.db"
        val preferencesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val observationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val preferences = PreferenceDataStoreFactory.create(scope = preferencesScope) {
            File(root, "prefs.preferences_pb")
        }
        val repository = AppRepository(context, databaseName, File(root, "packs"), preferences)
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LangueViewModel(repository) as T
        }
        val vm = ViewModelProvider(store, factory)[LangueViewModel::class.java]
        // A real screen subscribes to library; exercise its Room/statistics collection as well.
        val observation = observationScope.launch { vm.library.collect { } }
        try {
            await("Repository initialization did not finish") { !vm.busy.value }
            assertFalse(vm.initializationFailed.value)
            val original = TestPacks.pack()
            runBlocking { repository.importPack(TestPacks.zip(original)) }
            await("Active pack did not prepare a question") {
                vm.library.value.active?.manifest?.id == original.manifest.id &&
                    vm.practice.value.question != null && !vm.quizPreparing.value && !vm.busy.value
            }
            val first = vm.practice.value.question!!
            vm.answer(first.entry.id)
            await("Correct answer was not persisted") {
                !vm.busy.value && vm.library.value.stats == QuizStats(1, 1, 1, 1)
            }
            assertEquals("A score emission must retain the answered question", first, vm.practice.value.question)
            assertEquals("Feedback disappeared when statistics changed", first.entry.id, vm.practice.value.selectedId)

            // Another statistics emission must not invalidate the currently displayed feedback.
            runBlocking { repository.recordAnswer(false) }
            val expectedStats = QuizStats(0, 1, 2, 1)
            await("New statistics were not observed") { vm.library.value.stats == expectedStats }
            assertEquals(first, vm.practice.value.question)
            assertEquals(first.entry.id, vm.practice.value.selectedId)
            vm.nextQuestion()
            val next = vm.practice.value.question!!
            assertNotEquals("The next draw repeated the preceding entry", first.entry.id, next.entry.id)
            assertNull(vm.practice.value.selectedId)
            assertEquals(3, next.options.size)

            val revised = original.copy(
                manifest = original.manifest.copy(version = 2),
                entries = original.entries.map { entry ->
                    entry.copy(source = "Updated source ${entry.id}", target = "Updated target ${entry.id}")
                },
            )
            runBlocking { repository.importPack(TestPacks.zip(revised)) }
            await("The updated pack still uses stale quiz content") {
                vm.library.value.active?.manifest?.version == 2 && !vm.quizPreparing.value &&
                    vm.practice.value.question?.entry?.source?.startsWith("Updated source ") == true
            }
            val revisedById = revised.entries.associateBy { it.id }
            val updatedQuestion = vm.practice.value.question!!
            assertEquals(revisedById.getValue(updatedQuestion.entry.id), updatedQuestion.entry)
            updatedQuestion.options.forEach { assertEquals(revisedById.getValue(it.id), it) }
            assertEquals("Updating content reset existing statistics", expectedStats, vm.library.value.stats)

            val insufficient = TestPacks.pack(id = "test.insufficient", entries = original.entries.take(2))
            runBlocking { repository.importPack(TestPacks.zip(insufficient)) }
            await("Switching packs retained an invalid previous question") {
                vm.library.value.active?.manifest?.id == insufficient.manifest.id &&
                    !vm.quizPreparing.value && vm.practice.value.question == null &&
                    vm.library.value.stats == QuizStats()
            }
            assertNull(vm.practice.value.selectedId)
            vm.selectPack(original.manifest.id)
            await("The original pack or its progression was not restored") {
                !vm.busy.value && !vm.quizPreparing.value &&
                    vm.library.value.active?.manifest?.id == original.manifest.id &&
                    vm.library.value.stats == expectedStats &&
                    vm.practice.value.question?.entry?.source?.startsWith("Updated source ") == true
            }
            assertEquals(2, vm.library.value.active!!.manifest.version)
        } finally {
            observation.cancel()
            store.clear()
            observationScope.cancel()
            shadowOf(Looper.getMainLooper()).idle()
            repository.close()
            preferencesScope.cancel()
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
}
