package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.files.*
import com.garyapp.ytdl.storage.ExportController
import java.io.*
import java.nio.file.Files
import javax.crypto.KeyGenerator
import org.junit.*
import org.junit.Assert.*

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class HttpFileRequestContractTest {
    private lateinit var root: File
    private val metadata = FileMetadata("http://host/file?secret=token", "https://cdn/file?sig=private", "server.pdf", "application/pdf", 0,
        "\"one\"", null, FileKind.Other, ResumeSupport.Supported)
    private fun route() = DownloadRoute.HttpFile(metadata, "改名.pdf")
    private fun cipher() = AesGcmRetryPayloadCipher(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
    @Before fun setup() { root = Files.createTempDirectory("file-contract").toFile() }
    @After fun cleanup() { root.deleteRecursively() }
    @Test fun encryptedResumeAndRetryKeepMetadataAndUserNameSeparate() {
        val request = DownloadRequest(metadata.sourceUrl, "文件", route())
        val resume = FileResumeRequestStore(File(root, "resume"), cipher())
        resume.save(request).getOrThrow()
        assertEquals(request, resume.load(request.taskId).getOrThrow())
        val retry = FileRetryDraftStore(File(root, "retry"), cipher())
        val draft = RetryDownloadDraft.fromRequest(request)
        retry.save(1, draft).getOrThrow(); assertEquals(draft, retry.load(1).getOrThrow())
        root.walkTopDown().filter { it.isFile }.forEach { assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains("secret=token")) }
    }
    @Test fun scheduledTimeSurvivesEncryptedRequestReload() {
        val request = DownloadRequest(metadata.sourceUrl, "file", route(), scheduledAtMillis = 1_800_000_000_000L)
        val directory = File(root, "scheduled")
        val cipher = cipher()
        FileResumeRequestStore(directory, cipher).save(request).getOrThrow()
        assertEquals(request, FileResumeRequestStore(directory, cipher).load(request.taskId).getOrThrow())
    }

    @Test fun oldResumeVersionsStillRead() {
        for (version in 1..2) {
            val request = DownloadRequest("https://example/video", "旧任务", DownloadRoute.AudioOnly("a"), usesCookies = false)
            val dir = File(root, "v$version").apply { mkdirs() }; val cipher = cipher()
            val bytes = ByteArrayOutputStream().also { b -> DataOutputStream(b).use { o ->
                o.writeInt(0x59545253); o.writeInt(version); o.writeUTF(request.taskId); o.writeUTF(request.url)
                o.writeUTF(request.title); o.writeUTF(""); o.writeBoolean(false); o.writeInt(0)
                o.writeInt(3); o.writeUTF("a"); o.writeInt(0)
                if (version >= 2) { o.writeBoolean(false); o.writeBoolean(false) }
            } }.toByteArray()
            File(dir, "${request.taskId}.bin").writeBytes(cipher.encrypt(bytes))
            assertEquals(request, FileResumeRequestStore(dir, cipher).load(request.taskId).getOrThrow())
        }
    }
    @Test fun oldRetryVersionStillReads() {
        val cipher = cipher(); val dir = File(root, "retry").apply { mkdirs() }
        val bytes = ByteArrayOutputStream().also { b -> DataOutputStream(b).use { o ->
            fun string(s: String) { val data = s.toByteArray(); o.writeInt(data.size); o.write(data) }
            o.writeInt(0x5954444C); o.writeInt(2); string("https://example/video"); o.writeInt(4); string("v"); string("a")
        } }.toByteArray()
        File(dir, "1.bin").writeBytes(cipher.encrypt(bytes))
        assertEquals(RetryDownloadDraft("https://example/video", DownloadRoute.MergeRequired("v", "a")), FileRetryDraftStore(dir, cipher).load(1).getOrThrow())
    }
    @Test fun zeroByteFileExportRequiresExplicitFilePermissionAndKeepsMimeAndHistoryUri() {
        val task = File(root, "task-${"a".repeat(32)}").apply { mkdirs() }
        val outputs = File(task, "outputs").apply { mkdirs() }
        val pdf = File(outputs, "empty.pdf").apply { writeBytes(byteArrayOf()) }
        assertTrue(ExportController.discoverAppPrivateOutput(pdf, root).isFailure)
        val output = ExportController.discoverAppPrivateOutput(pdf, root, allowEmptyFile = true).getOrThrow()
        assertEquals("application/pdf", output.mimeType)
        assertEquals(0L, output.bytesWritten)
        assertEquals(0L, ExportController.discoverAppPrivateOutputUri(output.appPrivateUri, root).getOrThrow().bytesWritten)
        ExportController.markAppPrivateTaskCompleted(listOf(output)).getOrThrow()
        val documents = mutableMapOf<String, ByteArrayOutputStream>()
        val copied = ExportController.copyToSafTreeWithDocuments("content://provider/tree/downloads", listOf(output),
            { _, mime, _ -> assertEquals("application/pdf", mime); "content://provider/document/new" },
            { ByteArrayOutputStream().also { stream -> documents[it] = stream } }, { documents.remove(it) }, { false })
        assertEquals(0L, copied.bytesWritten); assertEquals(0, documents.values.single().size())
    }
    @Test fun fileNamesKeepLeadingDotsAndSpacesThroughPrivateHistoryUriAndSaf() {
        val task = File(root, "task-${"c".repeat(32)}").apply { mkdirs() }
        val directory = File(task, "outputs").apply { mkdirs() }
        for (name in listOf(".env", "  config.json")) {
            val file = File(directory, name).apply { writeText("payload") }
            val output = ExportController.discoverAppPrivateOutput(file, root, allowEmptyFile = true).getOrThrow()
            assertEquals(name, output.displayName)
            val reopened = ExportController.discoverAppPrivateOutputUri(output.appPrivateUri, root).getOrThrow()
            assertEquals(file.canonicalFile, reopened.sourceFile)
            assertEquals(name, reopened.displayName)
            var savedName = ""
            ExportController.copyToSafTreeWithDocuments("content://provider/tree/downloads", listOf(output),
                { _, _, displayName -> savedName = displayName; "content://provider/document/new" }, { ByteArrayOutputStream() }, {}, { false })
            assertEquals(name, savedName)
        }
        for (segment in listOf(".", "..", "%2e%2e", "%2fetc", "%5cetc")) {
            assertTrue(ExportController.discoverAppPrivateOutputUri("app-private://outputs/task-${"c".repeat(32)}/$segment?kind=file", root).isFailure)
        }
    }

    @Test fun safDuplicateNamesAreNumberedAndAskReturnsConflictBeforeOpeningStream() {
        val task = File(root, "task-${"b".repeat(32)}").apply { mkdirs() }
        val file = File(task, "report.pdf").apply { writeText("pdf") }
        val auto = ExportController.discoverAppPrivateOutput(file, root).getOrThrow()
        var createdName = ""
        ExportController.copyToSafTreeWithDocuments("content://provider/tree/downloads", listOf(auto),
            { _, _, name -> createdName = name; "content://provider/document/new" }, { ByteArrayOutputStream() }, {}, { false },
            existingDisplayNames = { setOf("report.pdf", "report (1).pdf") })
        assertEquals("report (2).pdf", createdName)
        val ask = ExportController.discoverAppPrivateOutput(file, root, askOnConflict = true).getOrThrow()
        val result = runCatching {
            ExportController.copyToSafTreeWithDocuments("content://provider/tree/downloads", listOf(ask),
                { _, _, _ -> fail("must not create over conflict"); null }, { fail("must not open stream"); null }, {}, { false },
                existingDisplayNames = { setOf("report.pdf") })
        }
        assertTrue(result.exceptionOrNull() is ExportController.NameConflictException)
        assertEquals("pdf", file.readText())
    }

    @Test fun userFileNamesRejectInvalidUnicodeControlsAndExcessiveBytes() {
        for (name in listOf("bad\u0085.pdf", "bad.", "bad ", "中".repeat(80) + ".pdf", "../x", "x\u0000.pdf")) {
            assertTrue(name, runCatching { DownloadRoute.HttpFile(metadata, name) }.isFailure)
        }
        assertEquals("中".repeat(60) + ".pdf", DownloadRoute.HttpFile(metadata, "中".repeat(60) + ".pdf").fileName)
    }
}
