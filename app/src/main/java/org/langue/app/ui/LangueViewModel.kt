package org.langue.app.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.langue.app.R
import org.langue.app.data.AppRepository
import org.langue.app.data.QuizStats
import org.langue.engine.Entry
import org.langue.engine.LanguagePack
import org.langue.engine.QuizEngine
import org.langue.engine.QuizQuestion

data class LibraryState(
    val packs: List<LanguagePack> = emptyList(),
    val active: LanguagePack? = null,
    val stats: QuizStats = QuizStats(),
    val viewed: Set<String> = emptySet(),
)

data class PracticeState(val question: QuizQuestion? = null, val selectedId: String? = null)
data class UiNotice(@StringRes val message: Int, val detail: String? = null)

class LangueViewModel(val repository: AppRepository) : ViewModel() {
    val library = combine(repository.packs, repository.activePack, repository.progress, repository.viewedEntryIds) {
            packs, active, stats, viewed -> LibraryState(packs, active, stats, viewed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())
    val busy = MutableStateFlow(false)
    val initializationFailed = MutableStateFlow(false)
    val practice = MutableStateFlow(PracticeState())
    private val _notices = MutableSharedFlow<UiNotice>(extraBufferCapacity = 8)
    val notices = _notices.asSharedFlow()
    private var quizPack: LanguagePack? = null

    init {
        initialize()
        viewModelScope.launch {
            repository.activePack.distinctUntilChanged().collect { pack ->
                quizPack = pack
                practice.value = PracticeState(pack?.let { QuizEngine.next(it.entries) })
            }
        }
    }

    fun initialize() = operation(R.string.initialize_error) {
        initializationFailed.value = false
        try { repository.initialize() } catch (error: Exception) {
            initializationFailed.value = true
            throw error
        }
    }

    private fun operation(@StringRes errorMessage: Int = R.string.operation_error, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block() } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _notices.emit(UiNotice(errorMessage))
            } finally { busy.value = false }
        }
    }

    fun importPack(resolver: ContentResolver, uri: Uri) = operation {
        withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { repository.importPack(it) }
                ?: throw IOException()
        }
        _notices.emit(UiNotice(R.string.import_success))
    }

    fun selectPack(id: String) = operation { repository.selectPack(id) }
    fun deletePack(id: String) = operation {
        repository.deletePack(id)
        _notices.emit(UiNotice(R.string.delete_success))
    }

    fun markViewed(entry: Entry, packId: String) {
        viewModelScope.launch {
            try { repository.markViewed(entry.id, packId) } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _notices.emit(UiNotice(R.string.operation_error))
            }
        }
    }

    fun answer(id: String) {
        val state = practice.value
        val question = state.question ?: return
        val packId = quizPack?.manifest?.id ?: return
        if (state.selectedId != null || busy.value) return
        practice.value = state.copy(selectedId = id)
        operation {
            try { repository.recordAnswer(id == question.entry.id, packId) }
            catch (error: Exception) {
                practice.value = state
                throw error
            }
        }
    }

    fun nextQuestion() {
        if (busy.value) return
        val pack = quizPack ?: return
        practice.value = PracticeState(QuizEngine.next(pack.entries, practice.value.question?.entry?.id))
    }

    fun resetProgress() = operation {
        repository.resetProgress()
        practice.value = PracticeState(quizPack?.let { QuizEngine.next(it.entries) })
        _notices.emit(UiNotice(R.string.reset_success))
    }

    fun exportProgress(resolver: ContentResolver, uri: Uri) = operation {
        val json = repository.exportProgress()
        withContext(Dispatchers.IO) {
            resolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) }
                ?: throw IOException()
        }
        _notices.emit(UiNotice(R.string.export_success))
    }

    fun restoreProgress(resolver: ContentResolver, uri: Uri) = operation {
        val json = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 32 * 1024 * 1024)
                    output.write(buffer, 0, count)
                }
                output.toByteArray().toString(Charsets.UTF_8)
            } ?: throw IOException()
        }
        repository.restoreProgress(json)
        practice.value = PracticeState(quizPack?.let { QuizEngine.next(it.entries) })
        _notices.emit(UiNotice(R.string.restore_success))
    }

    fun audioError() { _notices.tryEmit(UiNotice(R.string.audio_error)) }
}
