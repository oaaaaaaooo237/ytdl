package com.garyapp.ytdl.download

import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.testing.ProjectTestPaths
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryFileDeletionTest {
    private val taskId = "a".repeat(32)
    private fun roots(): Pair<File, File> {
        val base = ProjectTestPaths.createTempDirectory("history-delete-").toFile()
        return base.resolve("files/gui-downloads") to base.resolve("cache/gui-downloads")
    }
    private fun row(id: Long, output: String?, subtitles: String? = null) = HistoryItemEntity().apply {
        this.id = id; outputUri = output; subtitleOutputUris = subtitles
    }
    private fun uri(name: String) = "app-private://outputs/task-$taskId/$name"

    @Test fun zeroByteFileKindUriCanBeDeletedWithoutAllowingArbitraryQueries() {
        val (files, cache) = roots()
        val output = files.resolve("task-$taskId/outputs/empty.pdf").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf()) }
        val deletion = HistoryFileDeletion(files, cache, FakeSaf())
        val selected = row(1, "${uri("outputs/empty.pdf")}?kind=file")
        assertEquals(1, deletion.delete(selected, listOf(selected), emptySet()).getOrThrow())
        assertFalse(output.exists())
        output.writeBytes(byteArrayOf())
        assertTrue(deletion.delete(row(2, "${uri("outputs/empty.pdf")}?kind=media"), emptyList(), emptySet()).isFailure)
        assertTrue(output.isFile)
    }

    @Test fun unrelatedPrivateHistoryCanBeDeletedWhileAnotherTaskRuns() {
        val (files, cache) = roots()
        DownloadCoordinator.resetForTests()
        try {
            val active = DownloadRequest("https://example.test/video", "当前任务", DownloadRoute.DirectSingleFile("18"))
            DownloadCoordinator.enqueueForServiceStart(active, files).getOrThrow()
            val launch = DownloadCoordinator.claimPendingLaunch()!!
            DownloadCoordinator.activateLaunch(launch)
            val activeFile = files.resolve("task-${active.taskId}/keep.part").apply { parentFile!!.mkdirs(); writeText("active") }
            val oldFile = files.resolve("task-$taskId/old.mp4").apply { parentFile!!.mkdirs(); writeText("old") }
            val selected = row(1, uri("old.mp4"))
            val deletion = HistoryFileDeletion(files, cache, FakeSaf())
            val result = deletion.runWhenSafe(selected) {
                deletion.delete(selected, listOf(selected), setOf(active.taskId))
            }
            assertTrue(result is IdleDownloadActionResult.Executed)
            assertEquals(1, (result as IdleDownloadActionResult.Executed).value.getOrThrow())
            assertFalse(oldFile.exists())
            assertEquals("active", activeFile.readText())
            val activeRow = row(2, "app-private://outputs/task-${active.taskId}/keep.part")
            val protected = deletion.runWhenSafe(activeRow) {
                deletion.delete(activeRow, listOf(activeRow), setOf(active.taskId))
            }
            assertTrue((protected as IdleDownloadActionResult.Executed).value.isFailure)
            assertEquals("active", activeFile.readText())
        } finally { DownloadCoordinator.resetForTests() }
    }

    @Test fun unfinishedHistoryWithoutOutputsDoesNotWaitForUnrelatedDownload() {
        val (files, cache) = roots()
        DownloadCoordinator.resetForTests()
        try {
            DownloadCoordinator.enqueueForServiceStart(
                DownloadRequest("https://example.test/video", "当前任务", DownloadRoute.DirectSingleFile("18")), files,
            ).getOrThrow()
            val deletion = HistoryFileDeletion(files, cache, FakeSaf())
            assertEquals(IdleDownloadActionResult.Executed("deleted"),
                deletion.runWhenSafe(row(1, null)) { "deleted" })
            assertEquals(IdleDownloadActionResult.ActiveDownload,
                deletion.runWhenSafe(row(2, "content://test/document/output")) { "must not run" })
        } finally { DownloadCoordinator.resetForTests() }
    }

    @Test fun deletesOnlyRecordedFilesAndAcceptsMissingTargets() {
        val (files, cache) = roots()
        val task = files.resolve("task-$taskId").apply { mkdirs() }
        val media = task.resolve("media.mp4").apply { writeText("media") }
        val subtitle = task.resolve("subtitle.srt").apply { writeText("subtitle") }
        val sentinel = task.resolve("sentinel.txt").apply { writeText("keep") }
        val deletion = HistoryFileDeletion(files, cache, FakeSaf())
        val result = deletion.delete(row(1, uri("media.mp4"), "${uri("subtitle.srt")}\n${uri("missing.vtt")}"), emptyList(), emptySet())
        assertTrue(result.isSuccess)
        assertFalse(media.exists())
        assertFalse(subtitle.exists())
        assertEquals("keep", sentinel.readText())
    }

    @Test fun legacyTaskDirectoryAndDuplicateOutputIdentityDeleteExactlyOnce() {
        val (files, cache) = roots()
        val legacy = "task-1783250902008-1z"
        val target = files.resolve("$legacy/media file.mp4").apply { parentFile!!.mkdirs(); writeText("media") }
        val uri = "app-private://outputs/$legacy/media%20file.mp4"
        assertEquals(1, HistoryFileDeletion(files, cache, FakeSaf()).delete(
            row(1, uri, uri), emptyList(), emptySet()).getOrThrow())
        assertFalse(target.exists())
    }

    @Test fun trustedAncestorAliasCanResolveControlledRoot() {
        val base = ProjectTestPaths.createTempDirectory("history-ancestor-").toFile()
        val real = base.resolve("real").apply { mkdirs() }
        val alias = base.resolve("alias")
        Files.createSymbolicLink(alias.toPath(), real.toPath())
        val file = real.resolve("gui-downloads/task-$taskId/media.mp4").apply {
            parentFile!!.mkdirs(); writeText("media")
        }
        val result = HistoryFileDeletion(alias.resolve("gui-downloads"), base.resolve("cache/gui-downloads"),
            FakeSaf()).delete(row(1, uri("media.mp4")), emptyList(), emptySet())
        assertEquals(1, result.getOrThrow())
        assertFalse(file.exists())
    }

    @Test @Config(sdk = [24]) fun api24DeletesOrdinaryPrivateFileWithoutNio26() {
        val (files, cache) = roots()
        val task = files.resolve("task-$taskId").apply { mkdirs() }
        val ordinary = task.resolve("ordinary.mp4").apply { writeText("ordinary") }
        val outside = files.parentFile!!.resolve("outside").apply { writeText("outside") }
        val linked = task.resolve("linked.mp4")
        Files.createSymbolicLink(linked.toPath(), outside.toPath())
        val deletion = HistoryFileDeletion(files, cache, FakeSaf())
        val lstatMode = android.system.Os.lstat(linked.path).st_mode
        if (android.system.OsConstants.S_ISLNK(lstatMode)) {
            assertTrue(deletion.delete(row(1, uri("linked.mp4")), emptyList(), emptySet()).isFailure)
        } else {
            // Robolectric on Windows reports a host symlink as a regular file via API24 Os.lstat.
            assertTrue(Files.isSymbolicLink(linked.toPath()))
        }
        assertEquals(1, deletion.delete(row(1, uri("ordinary.mp4")), emptyList(), emptySet()).getOrThrow())
        assertFalse(ordinary.exists())
        assertEquals("outside", outside.readText())
    }

    @Test fun preflightRejectsTraversalDirectoryLinkAndAmbiguousRootsWithoutMutation() {
        val (files, cache) = roots()
        val task = files.resolve("task-$taskId").apply { mkdirs() }
        val media = task.resolve("media.mp4").apply { writeText("keep") }
        val directory = task.resolve("folder").apply { mkdir() }
        val outside = files.parentFile!!.resolve("outside").apply { writeText("outside") }
        Files.createSymbolicLink(task.resolve("link.mp4").toPath(), outside.toPath())
        val deletion = HistoryFileDeletion(files, cache, FakeSaf())
        for (bad in listOf(uri("%2e%2e/outside"), uri("%2Foutside"), uri("folder"), uri("link.mp4"),
            "file:///tmp/media.mp4", "app-private://outputs/task-$taskId/media.mp4?x=1")) {
            assertTrue(deletion.delete(row(1, uri("media.mp4"), bad), emptyList(), emptySet()).isFailure)
            assertEquals("keep", media.readText())
        }
        cache.resolve("task-$taskId").apply { mkdirs() }.resolve("media.mp4").writeText("other")
        assertTrue(deletion.delete(row(1, uri("media.mp4")), emptyList(), emptySet()).isFailure)
        assertEquals("keep", media.readText())
        assertEquals("outside", outside.readText())
        assertTrue(directory.isDirectory)
        val linkedDirectory = task.resolve("linked")
        Files.createSymbolicLink(linkedDirectory.toPath(), files.parentFile!!.toPath())
        assertTrue(deletion.delete(row(1, uri("linked/outside")), emptyList(), emptySet()).isFailure)
        assertEquals("outside", outside.readText())
    }

    @Test fun sharedAndPausedTaskReferencesBlockDeletion() {
        val (files, cache) = roots()
        val media = files.resolve("task-$taskId/media.mp4").apply { parentFile!!.mkdirs(); writeText("keep") }
        val deletion = HistoryFileDeletion(files, cache, FakeSaf())
        assertTrue(deletion.delete(row(1, uri("media.mp4")), listOf(row(2, uri("media.mp4"))), emptySet()).isFailure)
        assertTrue(deletion.delete(row(1, uri("media.mp4")), emptyList(), setOf(taskId)).isFailure)
        assertEquals("keep", media.readText())
    }

    @Test fun safRejectsTreeDirectoryAndSharedIdentityAndPreservesRecordOnFailure() {
        val (files, cache) = roots()
        val saf = FakeSaf()
        val deletion = HistoryFileDeletion(files, cache, saf)
        val doc = "content://provider/document/123"
        assertTrue(deletion.delete(row(1, "content://provider/tree/root"), emptyList(), emptySet()).isFailure)
        saf.mime = "vnd.android.document/directory"
        assertTrue(deletion.delete(row(1, doc), emptyList(), emptySet()).isFailure)
        saf.mime = "video/mp4"
        assertTrue(deletion.delete(row(1, doc), listOf(row(2, "content://provider/document/%31%32%33")), emptySet()).isFailure)
        saf.failDelete = true
        assertTrue(deletion.delete(row(1, doc), emptyList(), emptySet()).isFailure)
        assertEquals(1, saf.deleteCalls)
        saf.failDelete = false
        saf.missing = true
        assertEquals(0, deletion.delete(row(1, doc), emptyList(), emptySet()).getOrThrow())
        saf.missing = false
        saf.failInspect = true
        assertTrue(deletion.delete(row(1, doc), emptyList(), emptySet()).isFailure)
    }

    @Test fun safUsesActualDocumentIdAndRechecksBeforeDeletion() {
        val (files, cache) = roots()
        val saf = FakeSaf().apply { actualId = "123" }
        val deletion = HistoryFileDeletion(files, cache, saf)
        assertEquals(1, deletion.delete(row(1, "content://provider/document/123"), emptyList(), emptySet()).getOrThrow())
        assertEquals(2, saf.inspectCalls)
        assertEquals(1, saf.deleteCalls)
        saf.deleteCalls = 0
        saf.inspectCalls = 0
        saf.revokedAfterFirstInspect = true
        assertTrue(deletion.delete(row(1, "content://provider/document/123"), emptyList(), emptySet()).isFailure)
        assertEquals(0, saf.deleteCalls)
    }

    @Test fun treeDerivedLeafCanDeleteButTreeRootAndAliasSharedReferenceCannot() {
        val (files, cache) = roots()
        val saf = FakeSaf().apply { actualId = "leaf" }
        val deletion = HistoryFileDeletion(files, cache, saf)
        val leaf = "content://provider/tree/root/document/leaf"
        assertTrue(deletion.delete(row(1, leaf), listOf(row(2, "content://provider/document/leaf")), emptySet()).isFailure)
        assertEquals(0, saf.deleteCalls)
        assertTrue(deletion.delete(row(1, "content://provider/tree/root/document/root"), emptyList(), emptySet()).isFailure)
        assertEquals(1, deletion.delete(row(1, leaf), emptyList(), emptySet()).getOrThrow())
        assertEquals(1, saf.deleteCalls)
    }

    @Test fun partialDeletionFailureReportsDeletedCountAndKeepsLaterTargets() {
        val (files, cache) = roots()
        val media = files.resolve("task-$taskId/media.mp4").apply { parentFile!!.mkdirs(); writeText("media") }
        val saf = FakeSaf().apply { failDelete = true }
        val result = HistoryFileDeletion(files, cache, saf).delete(row(1, uri("media.mp4"), "content://provider/document/456"), emptyList(), emptySet())
        assertTrue(result.isFailure)
        assertFalse(media.exists())
        assertTrue(result.exceptionOrNull() is HistoryFileDeleteFailure)
        assertEquals(1, (result.exceptionOrNull() as HistoryFileDeleteFailure).deletedCount)
    }

    private class FakeSaf : HistorySafDocuments {
        var mime = "video/mp4"
        var failDelete = false
        var failInspect = false
        var missing = false
        var deleteCalls = 0
        var inspectCalls = 0
        var actualId: String? = null
        var revokedAfterFirstInspect = false
        override fun inspect(uri: String, documentId: String): SafDocument? {
            inspectCalls++
            if (revokedAfterFirstInspect && inspectCalls > 1) throw SecurityException("revoked")
            if (failInspect) throw SecurityException("revoked")
            return if (missing) null else SafDocument(actualId ?: documentId, mime, true)
        }
        override fun delete(uri: String): Boolean { deleteCalls++; return !failDelete }
    }
}
