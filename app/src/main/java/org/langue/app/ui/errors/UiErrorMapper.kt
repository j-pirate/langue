package org.langue.app.ui.errors

import android.util.Log
import androidx.annotation.StringRes
import org.langue.app.R
import org.langue.engine.FailureClassifier
import org.langue.engine.FailureReason

/** Operation names are fixed identifiers, never pack names, entry IDs, file paths or URI values. */
enum class ErrorOperation {
    INITIALIZE, IMPORT_PACK, SELECT_PACK, DELETE_PACK, MARK_VIEWED, RECORD_ANSWER,
    RESET_PROGRESS, EXPORT_PROGRESS, RESTORE_PROGRESS, AUDIO, PREPARE_QUIZ,
}

object UiErrorMapper {
    @StringRes
    fun message(error: Throwable, @StringRes fallback: Int): Int = when (FailureClassifier.reason(error)) {
        FailureReason.INVALID_ARCHIVE -> R.string.error_invalid_archive
        FailureReason.INCOMPATIBLE_PACK -> R.string.error_incompatible_pack
        FailureReason.INVALID_VERSION -> R.string.error_invalid_version
        FailureReason.MISSING_AUDIO -> R.string.error_missing_audio
        FailureReason.UNKNOWN_CATEGORY -> R.string.error_unknown_category
        FailureReason.INSUFFICIENT_STORAGE -> R.string.error_insufficient_storage
        FailureReason.INVALID_BACKUP -> R.string.error_invalid_backup
        FailureReason.MISSING_PACK -> R.string.error_missing_pack
        FailureReason.STORAGE_UNAVAILABLE -> R.string.error_storage_unavailable
        FailureReason.INVALID_CONTENT -> R.string.error_invalid_content
        FailureReason.UNKNOWN -> fallback
    }

    /** Deliberately omits exception messages, stack traces and causes containing imported content. */
    fun log(operation: ErrorOperation, error: Throwable) {
        Log.w("LangueErrors", diagnostic(operation, error))
    }

    internal fun diagnostic(operation: ErrorOperation, error: Throwable): String =
        "operation=${operation.name} code=${FailureClassifier.reason(error).name} type=${error.javaClass.simpleName}"
}
