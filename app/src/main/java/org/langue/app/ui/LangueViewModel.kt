package org.langue.app.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.langue.app.R
import org.langue.app.data.AppRepository
import org.langue.app.data.ProgressException
import org.langue.app.data.QuizStats
import org.langue.app.ui.errors.ErrorOperation
import org.langue.app.ui.errors.UiErrorMapper
import org.langue.engine.Entry
import org.langue.engine.FailureReason
import org.langue.engine.LanguagePack
import org.langue.engine.PackException
import org.langue.engine.PreparedQuiz
import org.langue.engine.QuizEngine
import org.langue.engine.QuizQuestion

data class LibraryState(
    val packs: List<LanguagePack> = emptyList(),
    val active: LanguagePack? = null,
    val stats: QuizStats = QuizStats(),
    val viewed: Set<String> = emptySet(),
)

data class PracticeState(val question: QuizQuestion? = null, val selectedId: String? = null)
data class UiNotice(@StringRes val message: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class LangueViewModel(val repository: AppRepository) : ViewModel() {
    private val readAttempts = MutableStateFlow(0)
    private var initializationAttempted = false
    val library = readAttempts.flatMapLatest {
        combine(repository.packs, repository.activePack, repository.progress, repository.viewedEntryIds) {
                packs, active, stats, viewed -> LibraryState(packs, active, stats, viewed)
        }.catch { error ->
            if (error is CancellationException || error !is Exception) throw error
            initializationMessage.value = UiErrorMapper.message(error, R.string.initialize_error)
            initializationFailed.value = true
            UiErrorMapper.log(ErrorOperation.INITIALIZE, error)
            _notices.tryEmit(UiNotice(initializationMessage.value))
            // Invalidate the displayed content and prepared quiz together. The retry trigger
            // resubscribes all failed Room/DataStore sources after the user retries initialization.
            emit(LibraryState())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())
    val busy = MutableStateFlow(false)
    val quizPreparing = MutableStateFlow(false)
    val initializationFailed = MutableStateFlow(false)
    val initializationMessage = MutableStateFlow(R.string.initialize_error)
    val practice = MutableStateFlow(PracticeState())
    private val _notices = MutableSharedFlow<UiNotice>(extraBufferCapacity = 8)
    val notices = _notices.asSharedFlow()
    private var quizPack: LanguagePack? = null
    private var preparedQuiz: PreparedQuiz? = null

    init {
        initialize()
        viewModelScope.launch {
            // Comparing packs and preparing equivalence groups can be linear in pack size.
            // Both run away from the main thread. A changed ID/version/content cancels the old
            // preparation and replaces its snapshot; score updates leave the session intact.
            library.map { it.active }.distinctUntilChanged().flowOn(Dispatchers.Default).collectLatest { pack ->
                quizPack = pack
                preparedQuiz = null
                practice.value = PracticeState()
                quizPreparing.value = pack != null
                try {
                    val session = pack?.let { withContext(Dispatchers.Default) { QuizEngine.prepare(it.entries) } }
                    preparedQuiz = session
                    practice.value = PracticeState(session?.next())
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    report(ErrorOperation.PREPARE_QUIZ, error)
                } finally {
                    quizPreparing.value = false
                }
            }
        }
    }

    fun initialize() = operation(ErrorOperation.INITIALIZE, R.string.initialize_error) {
        initializationFailed.value = false
        initializationMessage.value = R.string.initialize_error
        // The constructor's first attempt must not invalidate a session already being prepared.
        // Later attempts restart completed read flows, including when initialize() is already done.
        if (initializationAttempted) readAttempts.value += 1
        initializationAttempted = true
        try { repository.initialize() } catch (error: Exception) {
            if (error is CancellationException) throw error
            initializationMessage.value = UiErrorMapper.message(error, R.string.initialize_error)
            initializationFailed.value = true
            throw error
        }
    }

    private fun operation(operation: ErrorOperation, @StringRes errorMessage: Int = R.string.operation_error, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block() } catch (error: Exception) {
                if (error is CancellationException) throw error
                report(operation, error, errorMessage)
            } finally { busy.value = false }
        }
    }

    private suspend fun report(operation: ErrorOperation, error: Throwable, @StringRes fallback: Int = R.string.operation_error) {
        UiErrorMapper.log(operation, error)
        _notices.emit(UiNotice(UiErrorMapper.message(error, fallback)))
    }

    fun importPack(resolver: ContentResolver, uri: Uri) = operation(ErrorOperation.IMPORT_PACK, R.string.error_invalid_archive) {
        withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { repository.importPack(it) } ?: throw IOException()
        }
        _notices.emit(UiNotice(R.string.import_success))
    }

    fun selectPack(id: String) = operation(ErrorOperation.SELECT_PACK) { repository.selectPack(id) }
    fun deletePack(id: String) = operation(ErrorOperation.DELETE_PACK) {
        repository.deletePack(id)
        _notices.emit(UiNotice(R.string.delete_success))
    }

    fun markViewed(entry: Entry, packId: String) {
        viewModelScope.launch {
            try { repository.markViewed(entry.id, packId) } catch (error: Exception) {
                if (error is CancellationException) throw error
                report(ErrorOperation.MARK_VIEWED, error)
            }
        }
    }

    fun answer(id: String) {
        val state = practice.value
        val question = state.question ?: return
        val packId = quizPack?.manifest?.id ?: return
        if (state.selectedId != null || busy.value || quizPreparing.value || question.options.none { it.id == id }) return
        practice.value = state.copy(selectedId = id)
        operation(ErrorOperation.RECORD_ANSWER) {
            try { repository.recordAnswer(id == question.entry.id, packId) }
            catch (error: Exception) {
                if (practice.value === state || practice.value.question === question) practice.value = state
                throw error
            }
        }
    }

    fun nextQuestion() {
        if (busy.value || quizPreparing.value) return
        // Prepared draws are constant-time; no pack scanning or normalization on the UI thread.
        practice.value = PracticeState(preparedQuiz?.next(practice.value.question?.entry?.id))
    }

    fun resetProgress() = operation(ErrorOperation.RESET_PROGRESS) {
        repository.resetProgress()
        practice.value = PracticeState(preparedQuiz?.next())
        _notices.emit(UiNotice(R.string.reset_success))
    }

    fun exportProgress(resolver: ContentResolver, uri: Uri) = operation(ErrorOperation.EXPORT_PROGRESS) {
        val json = repository.exportProgress()
        withContext(Dispatchers.IO) {
            resolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) } ?: throw IOException()
        }
        _notices.emit(UiNotice(R.string.export_success))
    }

    fun restoreProgress(resolver: ContentResolver, uri: Uri) = operation(ErrorOperation.RESTORE_PROGRESS, R.string.error_invalid_backup) {
        val json = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 32 * 1024 * 1024) throw ProgressException()
                    output.write(buffer, 0, count)
                }
                try {
                    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(output.toByteArray())).toString()
                } catch (error: java.nio.charset.CharacterCodingException) { throw ProgressException(cause = error) }
            } ?: throw IOException()
        }
        repository.restoreProgress(json)
        practice.value = PracticeState(preparedQuiz?.next())
        _notices.emit(UiNotice(R.string.restore_success))
    }

    fun audioError() { _notices.tryEmit(UiNotice(R.string.audio_error)) }
    fun missingAudio() {
        val failure = PackException(FailureReason.MISSING_AUDIO, "Audio indisponible")
        UiErrorMapper.log(ErrorOperation.AUDIO, failure)
        _notices.tryEmit(UiNotice(R.string.error_missing_audio))
    }
}
