package com.orbyte.canvasstudio.export

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentExportCoordinatorTest {
    private class Destination(private val deleteResult: Boolean = true) : ExportDestination {
        val bytes = ByteArrayOutputStream()
        var opens = 0
        var deletes = 0
        override fun open(): OutputStream { opens++; return bytes }
        override fun deleteIncomplete(): Boolean { deletes++; return deleteResult }
    }

    @Test fun routesFormatsAndKeepsSuccessfulFiles() {
        ExportFormat.entries.forEach { format ->
            val destination = Destination()
            val outcome = DocumentExportCoordinator.execute(format, ExportWriter { selected, open ->
                assertEquals(format, selected)
                open().use { it.write(42) }
            }, destination)
            assertSame(ExportOutcome.Success, outcome)
            assertEquals(1, destination.opens)
            assertEquals(0, destination.deletes)
            assertEquals(42, destination.bytes.toByteArray()[0].toInt())
        }
    }

    @Test fun failedEncodingDeletesTheNewDocumentAndReportsCleanupFailure() {
        listOf(true, false).forEach { canDelete ->
            val destination = Destination(canDelete)
            val outcome = DocumentExportCoordinator.execute(ExportFormat.PNG, ExportWriter { _, _ ->
                throw IOException("encoding failed")
            }, destination) as ExportOutcome.Failure
            assertEquals(0, destination.opens)
            assertEquals(1, destination.deletes)
            assertEquals(!canDelete, outcome.incompleteFileMayRemain)
        }
    }

    @Test fun copyFailureAttemptsCleanup() {
        val destination = Destination()
        val outcome = DocumentExportCoordinator.execute(ExportFormat.PSD, ExportWriter { _, open ->
            open().use { it.write(3) }
            throw IOException("copy failed")
        }, destination) as ExportOutcome.Failure
        assertEquals(1, destination.opens)
        assertEquals(1, destination.deletes)
        assertFalse(outcome.incompleteFileMayRemain)
    }

    @Test fun cancellationIsRethrownAfterCleanup() {
        val destination = Destination()
        val failure = runCatching {
            DocumentExportCoordinator.execute(ExportFormat.PNG, ExportWriter { _, _ ->
                throw CancellationException("cancelled")
            }, destination)
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(1, destination.deletes)
    }

    @Test fun safeNameAndCaptureFailureAreConsistent() {
        assertEquals("Canvas Studio.ora", exportFileName("///", ExportFormat.OPEN_RASTER))
        assertEquals("Mi dibujo.psd", exportFileName("Mi dibujo", ExportFormat.PSD))
        val destination = Destination(false)
        val failure = DocumentExportCoordinator.captureFailed(IOException("snapshot"), destination)
        assertTrue(failure.incompleteFileMayRemain)
        assertEquals(1, destination.deletes)
    }
}
