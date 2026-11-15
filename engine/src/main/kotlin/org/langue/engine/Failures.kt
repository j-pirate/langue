package org.langue.engine

import java.io.IOException
import java.io.FileNotFoundException
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.util.Collections
import java.util.IdentityHashMap

/** Stable machine-readable codes. Never derive user-visible messages from exception details. */
enum class FailureReason {
    INVALID_ARCHIVE,
    INCOMPATIBLE_PACK,
    INVALID_VERSION,
    MISSING_AUDIO,
    UNKNOWN_CATEGORY,
    INSUFFICIENT_STORAGE,
    INVALID_BACKUP,
    MISSING_PACK,
    STORAGE_UNAVAILABLE,
    INVALID_CONTENT,
    UNKNOWN,
}

interface ReasonedFailure { val reason: FailureReason }

object FailureClassifier {
    /** Examines only standard OS failure signals; paths and messages must never be logged. */
    fun reason(error: Throwable, fallback: FailureReason = FailureReason.UNKNOWN): FailureReason {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = error
        var typed: FailureReason? = null
        var storageUnavailable = false
        while (current != null && seen.add(current)) {
            // A wrapper's semantic code must not conceal the more actionable full-disk cause.
            if (isFullStorage(current)) return FailureReason.INSUFFICIENT_STORAGE
            if (typed == null && current is ReasonedFailure) typed = current.reason
            if (current is AccessDeniedException || current is SecurityException || current is FileNotFoundException) {
                storageUnavailable = true
            }
            if (current is FileSystemException &&
                (current.reason?.contains("Read-only file system", ignoreCase = true) == true ||
                    current.reason?.contains("Permission denied", ignoreCase = true) == true)) {
                storageUnavailable = true
            }
            current = current.cause
        }
        return typed ?: if (storageUnavailable) FailureReason.STORAGE_UNAVAILABLE else fallback
    }

    private fun isFullStorage(error: Throwable): Boolean {
        if (error.javaClass.simpleName == "SQLiteFullException") return true
        // Android's ErrnoException is absent from the JVM engine classpath. Its public errno field
        // carries the POSIX error without interpreting a localized or user-controlled pathname.
        if (error.javaClass.name == "android.system.ErrnoException") {
            val errno = runCatching { error.javaClass.getField("errno").getInt(error) }.getOrNull()
            if (errno == 28) return true // ENOSPC
        }
        if (error is IOException) {
            val reason = (error as? FileSystemException)?.reason ?: error.message.orEmpty()
            return reason.contains("No space left on device", ignoreCase = true) ||
                Regex("\\bENOSPC\\b").containsMatchIn(reason)
        }
        return false
    }
}
