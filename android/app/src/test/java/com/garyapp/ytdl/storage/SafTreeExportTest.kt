package com.garyapp.ytdl.storage

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SafTreeExportTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun treeExportCopiesMediaAndSubtitleUnderSelectedTreeDocumentUri() {
        val root = temp.newFolder("private")
        val media = File(root, "video.mp4").apply { writeText("media") }
        val subtitle = File(root, "subtitle.vtt").apply { writeText("subtitle") }
        val outputs = listOf(media, subtitle).map {
            ExportController.discoverAppPrivateOutput(it, root).getOrThrow()
        }
        val parents = mutableListOf<String>()
        val names = mutableListOf<String>()
        val destinations = linkedMapOf<String, ByteArrayOutputStream>()

        val result = ExportController.copyToSafTreeWithDocuments(
            treeUri = TreeUri,
            outputs = outputs,
            createDocument = { parent, _, name ->
                parents += parent
                names += name
                "content://com.android.externalstorage.documents/document/export-${names.size}"
            },
            openOutputStream = { uri ->
                ByteArrayOutputStream().also { destinations[uri] = it }
            },
            deleteDocument = {},
            isCancellationRequested = { false },
        )

        assertEquals(media.length() + subtitle.length(), result.bytesWritten)
        assertEquals(listOf(TreeDocumentUri, TreeDocumentUri), parents)
        assertEquals(listOf("video.mp4", "subtitle.vtt"), names)
        assertEquals(listOf("media", "subtitle"), destinations.values.map { it.toString(Charsets.UTF_8.name()) })
        assertTrue(media.isFile)
        assertTrue(subtitle.isFile)
    }

    @Test
    fun detailedTreeExportReturnsEveryCreatedDocumentUriInInputOrder() {
        val root = temp.newFolder("private-detailed")
        val media = File(root, "video.mp4").apply { writeText("media") }
        val subtitle = File(root, "subtitle.vtt").apply { writeText("subtitle") }
        val outputs = listOf(media, subtitle).map {
            ExportController.discoverAppPrivateOutput(it, root).getOrThrow()
        }
        val created = mutableListOf<String>()

        val result = ExportController.copyToSafTreeWithDocuments(
            treeUri = TreeUri,
            outputs = outputs,
            createDocument = { _, _, _ ->
                "content://com.android.externalstorage.documents/document/export-${created.size + 1}"
                    .also(created::add)
            },
            openOutputStream = { ByteArrayOutputStream() },
            deleteDocument = {},
            isCancellationRequested = { false },
        )

        assertEquals(media.length() + subtitle.length(), result.bytesWritten)
        assertEquals(created, result.documentUris)
    }

    @Test
    fun completedSafExportCleanupDeletesOnlyValidatedPrivateTaskDirectory() {
        val root = temp.newFolder("private-cleanup")
        val task = File(root, "task-123-1").apply { mkdirs() }
        val media = File(task, "video.mp4").apply { writeText("media") }
        val subtitle = File(task, "subtitle.vtt").apply { writeText("subtitle") }
        val intermediate = File(task, "video.part").apply { writeText("temporary") }
        val outside = File(root.parentFile, "outside.mp4").apply { writeText("outside") }
        val outputs = listOf(media, subtitle).map {
            ExportController.discoverAppPrivateOutput(it, root).getOrThrow()
        }

        ExportController.cleanupExportedPrivateTask(outputs).getOrThrow()

        assertFalse(task.exists())
        assertFalse(intermediate.exists())
        assertTrue(outside.isFile)
    }

    @Test
    fun completedSafExportCleanupRejectsFilesOutsideTaskSubdirectory() {
        val root = temp.newFolder("private-cleanup-reject")
        val completedFile = File(root, "completed.mp4").apply { writeText("completed") }
        val output = ExportController.discoverAppPrivateOutput(completedFile, root).getOrThrow()

        assertTrue(ExportController.cleanupExportedPrivateTask(listOf(output)).isFailure)
        assertTrue(completedFile.isFile)
    }

    @Test
    fun completedSafExportCleanupReportsDeletionFailure() {
        val root = temp.newFolder("private-cleanup-failure")
        val task = File(root, "task-123-2").apply { mkdirs() }
        val media = File(task, "video.mp4").apply { writeText("media") }
        val output = ExportController.discoverAppPrivateOutput(media, root).getOrThrow()

        val result = ExportController.cleanupExportedPrivateTask(
            outputs = listOf(output),
            deleteTaskDirectory = { false },
        )

        assertTrue(result.isFailure)
        assertTrue(task.isDirectory)
        assertTrue(media.isFile)
    }

    @Test
    fun failedTreeExportDeletesIncompleteDestinationButKeepsPrivateSource() {
        val root = temp.newFolder("private-failure")
        val media = File(root, "video.mp4").apply { writeText("media") }
        val output = ExportController.discoverAppPrivateOutput(media, root).getOrThrow()
        val createdUri = "content://com.android.externalstorage.documents/document/incomplete"
        val deleted = mutableListOf<String>()

        val failure = runCatching {
            ExportController.copyToSafTreeWithDocuments(
                treeUri = TreeUri,
                outputs = listOf(output),
                createDocument = { _, _, _ -> createdUri },
                openOutputStream = { throw IllegalStateException("raw failure $TreeUri ${media.absolutePath}") },
                deleteDocument = { deleted += it },
                isCancellationRequested = { false },
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(media.isFile)
        assertEquals(listOf(createdUri), deleted)
        val message = failure?.message.orEmpty()
        assertTrue(message.contains("私有中转文件"))
        assertFalse(message.contains("content://"))
        assertFalse(message.contains(media.absolutePath))
    }

    @Test
    fun canceledTreeExportDeletesDestinationAndDoesNotCreateTheNextFile() {
        val root = temp.newFolder("private-canceled")
        val outputs = listOf(
            File(root, "video.mp4").apply { writeText("media") },
            File(root, "subtitle.vtt").apply { writeText("subtitle") },
        ).map { ExportController.discoverAppPrivateOutput(it, root).getOrThrow() }
        val created = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var cancellationRequested = false

        val failure = runCatching {
            ExportController.copyToSafTreeWithDocuments(
                treeUri = TreeUri,
                outputs = outputs,
                createDocument = { _, _, _ ->
                    "content://com.android.externalstorage.documents/document/export-${created.size + 1}"
                        .also(created::add)
                },
                openOutputStream = {
                    object : ByteArrayOutputStream() {
                        override fun write(buffer: ByteArray, offset: Int, length: Int) {
                            super.write(buffer, offset, length)
                            cancellationRequested = true
                        }
                    }
                },
                deleteDocument = { deleted += it },
                isCancellationRequested = { cancellationRequested },
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(1, created.size)
        assertEquals(created, deleted)
        assertTrue(outputs.all { File(root, it.displayName).isFile })
    }

    @Test
    fun longTitleFileNameKeepsExtensionAndCanBeRediscoveredFromHistoryUri() {
        val root = temp.newFolder("private-long-name")
        val task = File(root, "task-123-long").apply { mkdirs() }
        val fileName = "a".repeat(120) + ".mp4"
        val media = File(task, fileName).apply { writeText("media") }

        val output = ExportController.discoverAppPrivateOutput(media, root).getOrThrow()
        val historyUri = ExportController.appPrivateOutputUri(media.absolutePath, root.absolutePath)
        val rediscovered = ExportController.discoverAppPrivateOutputUri(historyUri, root).getOrThrow()

        assertEquals(fileName, output.displayName)
        assertEquals(media.canonicalFile, rediscovered.sourceFile)
    }

    private companion object {
        const val TreeUri = "content://com.android.externalstorage.documents/tree/primary%3AMovies"
        const val TreeDocumentUri =
            "content://com.android.externalstorage.documents/tree/primary%3AMovies/document/primary%3AMovies"
    }
}
