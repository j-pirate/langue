package org.langue.app

import android.content.Context
import android.system.ErrnoException
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.langue.app.ui.errors.ErrorOperation
import org.langue.app.ui.errors.UiErrorMapper
import org.langue.engine.FailureReason
import org.langue.engine.PackException
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** The same safe, localized presentation on the minimum Android version and a recent SDK. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class ErrorPresentationTest {
    @Test fun knownFailuresMapToFrenchResourcesWithoutDisplayingTheirDetails() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val cases = mapOf(
            FailureReason.INVALID_ARCHIVE to R.string.error_invalid_archive,
            FailureReason.INCOMPATIBLE_PACK to R.string.error_incompatible_pack,
            FailureReason.INVALID_VERSION to R.string.error_invalid_version,
            FailureReason.MISSING_AUDIO to R.string.error_missing_audio,
            FailureReason.UNKNOWN_CATEGORY to R.string.error_unknown_category,
            FailureReason.INSUFFICIENT_STORAGE to R.string.error_insufficient_storage,
            FailureReason.INVALID_BACKUP to R.string.error_invalid_backup,
            FailureReason.MISSING_PACK to R.string.error_missing_pack,
            FailureReason.STORAGE_UNAVAILABLE to R.string.error_storage_unavailable,
            FailureReason.INVALID_CONTENT to R.string.error_invalid_content,
        )
        for ((reason, resource) in cases) {
            val failure = PackException(reason, "PERSONAL_CONTENT /storage/private/recording.mp3")
            assertEquals(resource, UiErrorMapper.message(failure, R.string.operation_error))
            val message = context.getString(resource)
            assertTrue(message.isNotBlank())
            assertFalse(message.contains("PERSONAL_CONTENT"))
            assertFalse(message.contains("/storage/"))
        }
        assertEquals(R.string.operation_error,
            UiErrorMapper.message(IllegalStateException("PERSONAL_CONTENT"), R.string.operation_error))
    }

    @Test fun storageFullIsRecognizedInsideAnAndroidErrnoCause() {
        val osFailure = ErrnoException("write", OsConstants.ENOSPC)
        val wrapped = PackException(FailureReason.INVALID_ARCHIVE, "PERSONAL_CONTENT", osFailure)
        assertEquals(R.string.error_insufficient_storage,
            UiErrorMapper.message(wrapped, R.string.operation_error))
    }

    @Test fun diagnosticsContainOnlyFixedOperationCodeAndExceptionType() {
        ShadowLog.clear()
        val failure = PackException(FailureReason.MISSING_AUDIO,
            "PERSONAL_CONTENT /storage/private/recording.mp3", IllegalStateException("private_uri"))
        UiErrorMapper.log(ErrorOperation.IMPORT_PACK, failure)
        val entries = ShadowLog.getLogsForTag("LangueErrors")
        assertEquals(1, entries.size)
        assertEquals("operation=IMPORT_PACK code=MISSING_AUDIO type=PackException", entries.single().msg)
        assertNull(entries.single().throwable)
        assertFalse(entries.single().msg.contains("PERSONAL_CONTENT"))
        assertFalse(entries.single().msg.contains("private_uri"))
    }
}
