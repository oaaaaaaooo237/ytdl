package com.garyapp.ytdl.storage

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
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

        val bytes = invokeTreeExport(
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
        )

        assertEquals(media.length() + subtitle.length(), bytes)
        assertEquals(listOf(TreeDocumentUri, TreeDocumentUri), parents)
        assertEquals(listOf("video.mp4", "subtitle.vtt"), names)
        assertEquals(listOf("media", "subtitle"), destinations.values.map { it.toString(Charsets.UTF_8.name()) })
        assertTrue(media.isFile)
        assertTrue(subtitle.isFile)
    }

    @Test
    fun failedTreeExportDeletesIncompleteDestinationButKeepsPrivateSource() {
        val root = temp.newFolder("private-failure")
        val media = File(root, "video.mp4").apply { writeText("media") }
        val output = ExportController.discoverAppPrivateOutput(media, root).getOrThrow()
        val createdUri = "content://com.android.externalstorage.documents/document/incomplete"
        val deleted = mutableListOf<String>()

        val failure = runCatching {
            invokeTreeExport(
                treeUri = TreeUri,
                outputs = listOf(output),
                createDocument = { _, _, _ -> createdUri },
                openOutputStream = { throw IllegalStateException("raw failure $TreeUri ${media.absolutePath}") },
                deleteDocument = { deleted += it },
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(media.isFile)
        assertEquals(listOf(createdUri), deleted)
        val message = failure?.message.orEmpty()
        assertTrue(message.contains("App 私有文件"))
        assertFalse(message.contains("content://"))
        assertFalse(message.contains(media.absolutePath))
    }

    private fun invokeTreeExport(
        treeUri: String,
        outputs: List<ExportController.AppPrivateOutput>,
        createDocument: (String, String, String) -> String?,
        openOutputStream: (String) -> OutputStream?,
        deleteDocument: (String) -> Unit,
    ): Long {
        val method = ExportController::class.java.methods.firstOrNull {
            it.name == "copyToSafTree" && it.parameterCount == 5
        }
        assertNotNull("缺少 SAF tree 自动导出入口", method)
        return try {
            method!!.invoke(
                null,
                treeUri,
                outputs,
                createDocument,
                openOutputStream,
                deleteDocument,
            ) as Long
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private companion object {
        const val TreeUri = "content://com.android.externalstorage.documents/tree/primary%3AMovies"
        const val TreeDocumentUri =
            "content://com.android.externalstorage.documents/tree/primary%3AMovies/document/primary%3AMovies"
    }
}
