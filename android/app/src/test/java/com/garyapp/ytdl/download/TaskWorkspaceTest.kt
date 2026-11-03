package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.testing.ProjectTestPaths
import java.io.File
import java.nio.file.Files
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TaskWorkspaceTest {
    private val id = "a".repeat(32)
    private val other = "b".repeat(32)
    private fun root() = ProjectTestPaths.createTempDirectory("workspace-").toFile()

    @Test fun rejectsEveryUnsafeIdentityAndKeepsOtherTasks() {
        val root = root()
        val workspace = TaskWorkspace(root)
        val sentinel = root.resolve("sentinel").apply { writeText("keep") }
        workspace.directory(other, true).resolve("media").writeText("keep")
        for (bad in listOf("", "..", "/", "../$id", "A".repeat(32), "%2e%2e", "C:\\test", "a".repeat(33))) {
            assertTrue(runCatching { workspace.directory(bad, true) }.isFailure)
            assertTrue(workspace.deleteTask(bad).isFailure)
        }
        workspace.directory(id, true).resolve("data.part").writeText("partial")
        assertTrue(workspace.deleteTask(id).isSuccess)
        assertTrue(workspace.deleteTask(id).isSuccess)
        assertEquals("keep", sentinel.readText())
        assertEquals("keep", workspace.directory(other).resolve("media").readText())
    }

    @Test fun checkpointRoundTripsAndRejectsChangedMediaAndTaskMismatch() {
        val workspace = TaskWorkspace(root())
        val media = workspace.directory(id, true).resolve("video.mp4").apply { writeText("video") }
        val checkpoint = TaskCheckpoint(id, "DownloadingAudio", "UserPause", 2, 8, 123,
            listOf(CompletedTaskFile("video", "137", "video.mp4", media.length())))
        workspace.saveCheckpoint(id, checkpoint).getOrThrow()
        assertEquals(checkpoint, workspace.loadCheckpoint(id).getOrThrow())
        assertTrue(workspace.saveCheckpoint(other, checkpoint).isFailure)
        media.appendText("changed")
        assertTrue(workspace.loadCheckpoint(id).isFailure)
    }

    @Test fun rejectsEscapingRelativePathsAndCorruptCheckpoints() {
        val root = root()
        val workspace = TaskWorkspace(root)
        workspace.directory(id, true)
        for (path in listOf("../sentinel", "/sentinel", "C:/sentinel", "sub/../../sentinel", "sub\\sentinel", "./file", "")) {
            assertTrue(workspace.saveCheckpoint(id, TaskCheckpoint(id, "Paused", completedFiles =
                listOf(CompletedTaskFile("video", "137", path, 3)))).isFailure)
        }
        workspace.directory(id).resolve("checkpoint.bin").writeText("broken")
        assertTrue(workspace.loadCheckpoint(id).isFailure)
    }

    @Test fun failedAtomicReplacementPreservesPreviousCheckpoint() {
        val root = root()
        val workspace = TaskWorkspace(root)
        val first = TaskCheckpoint(id, "Paused", downloadedBytes = 4)
        workspace.saveCheckpoint(id, first).getOrThrow()
        val broken = TaskWorkspace(root, AtomicSnapshotWriter { _, _ -> error("injected write failure") })
        assertTrue(broken.saveCheckpoint(id, first.copy(downloadedBytes = 8)).isFailure)
        assertEquals(first, workspace.loadCheckpoint(id).getOrThrow())
    }

    @Test fun linksInsideAndAtTaskDirectoryCannotDeleteOutsideSentinels() {
        val root = root()
        val outside = root.resolve("outside").apply { mkdir() }
        val sentinel = outside.resolve("sentinel").apply { writeText("keep") }
        val workspace = TaskWorkspace(root)
        val task = workspace.directory(id, true)
        Files.createSymbolicLink(task.resolve("link").toPath(), outside.toPath())
        assertTrue(workspace.deleteTask(id).isFailure)
        assertEquals("keep", sentinel.readText())
        Files.delete(task.resolve("link").toPath())
        task.delete()
        Files.createSymbolicLink(task.toPath(), outside.toPath())
        assertTrue(runCatching { workspace.directory(id) }.isFailure)
        assertTrue(workspace.deleteTask(id).isFailure)
        assertEquals("keep", sentinel.readText())
        Files.delete(task.toPath())
    }

    @Test fun rootAndCheckpointLinksCannotBeReadOrOverwritten() {
        val parent = root()
        val outside = parent.resolve("outside").apply { mkdir() }
        val linkRoot = parent.resolve("linked-root")
        Files.createSymbolicLink(linkRoot.toPath(), outside.toPath())
        val linked = TaskWorkspace(linkRoot)
        assertTrue(runCatching { linked.directory(id, true) }.isFailure)
        assertTrue(linked.deleteTask(id).isFailure)
        Files.delete(linkRoot.toPath())
        val workspace = TaskWorkspace(parent.resolve("controlled"))
        val task = workspace.directory(id, true)
        val sentinel = outside.resolve("sentinel").apply { writeText("keep") }
        Files.createSymbolicLink(task.resolve("checkpoint.bin").toPath(), sentinel.toPath())
        assertTrue(workspace.saveCheckpoint(id, TaskCheckpoint(id, "Paused")).isFailure)
        assertTrue(workspace.loadCheckpoint(id).isFailure)
        assertEquals("keep", sentinel.readText())
        Files.delete(task.resolve("checkpoint.bin").toPath())
    }

    @Test fun unexpectedTemporaryDirectoryIsPreservedWhenWriteFails() {
        val workspace = TaskWorkspace(root())
        val temporary = workspace.directory(id, true).resolve(".checkpoint.bin.tmp").apply { mkdir() }
        assertTrue(workspace.saveCheckpoint(id, TaskCheckpoint(id, "Paused")).isFailure)
        assertTrue(temporary.isDirectory)
    }

    @Test fun checkpointRejectsRawUrlsAndErrorsInUnencryptedStateFields() {
        val workspace = TaskWorkspace(root())
        workspace.directory(id, true).resolve("video.mp4").writeText("video")
        assertTrue(workspace.saveCheckpoint(id, TaskCheckpoint(id, "https://private.example")).isFailure)
        assertTrue(workspace.saveCheckpoint(id, TaskCheckpoint(id, "Paused", "HTTP 403 private-url")).isFailure)
        assertTrue(workspace.saveCheckpoint(id, TaskCheckpoint(id, "Paused", completedFiles = listOf(
            CompletedTaskFile("video", "https://private.example/media?token=secret", "video.mp4", 5),
        ))).isFailure)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResumeRequestStoreTest {
    private val id = "a".repeat(32)
    private val other = "b".repeat(32)
    private val cipher = AesGcmRetryPayloadCipher(SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES"))
    private fun root() = ProjectTestPaths.createTempDirectory("resume-requests-").toFile()
    private fun request(route: DownloadRoute = DownloadRoute.MergeRequired("137", "140")) = DownloadRequest(
        "https://example.com/watch?token=secret", "标题", route, "https://image.example/private",
        listOf(SubtitleInfo("zh", "vtt")), "private/cookies.txt", "视频+音频", id,
    )

    @Test fun originalExportTargetRoundTripsOnlyInEncryptedRequest() {
        val root = root()
        val store = FileResumeRequestStore(root, cipher)
        val targets = listOf(null, com.garyapp.ytdl.core.storage.StorageTarget.AppPrivate,
            com.garyapp.ytdl.core.storage.StorageTarget.SafTree("content://documents/tree/primary%3AMovies", "下载目录"))
        targets.forEach { target ->
            store.save(request().copy(storageTarget = target)).getOrThrow()
            assertEquals(target, store.load(id).getOrThrow().storageTarget)
            assertFalse(root.resolve("$id.bin").readBytes().toString(Charsets.ISO_8859_1).contains("content://documents"))
        }
    }

    @Test fun analysisSnapshotKeyIsNeverPersistedWithTheResumeRequest() {
        val root = root()
        val store = FileResumeRequestStore(root, cipher)
        store.save(request().copy(analysisKey = "memory-only-snapshot")).getOrThrow()
        assertEquals(null, store.load(id).getOrThrow().analysisKey)
        assertFalse(cipher.decrypt(root.resolve("$id.bin").readBytes()).toString(Charsets.UTF_8)
            .contains("memory-only-snapshot"))
    }

    @Test fun encryptedRoundTripRetainsIdentityAndChoicesButNoCookiePathOrThumbnail() {
        val root = root()
        val store = FileResumeRequestStore(root, cipher)
        for (route in listOf(DownloadRoute.DirectSingleFile("18"), DownloadRoute.VideoOnly("137"),
            DownloadRoute.AudioOnly("140"), DownloadRoute.MergeRequired("137", "140"))) {
            val request = request(route)
            store.save(request).getOrThrow()
            val restored = store.load(id).getOrThrow()
            assertEquals(request.copy(cookiesPath = null, thumbnailUrl = null), restored)
            assertTrue(restored.usesCookies)
            val bytes = root.resolve("$id.bin").readBytes()
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(request.url))
            val payload = cipher.decrypt(bytes).toString(Charsets.UTF_8)
            assertFalse(payload.contains("private/cookies.txt"))
            assertFalse(payload.contains("image.example"))
        }
    }

    @Test fun rejectsSwappedCorruptAndWrongKeyPayloadsWithoutAffectingAnotherTask() {
        val root = root()
        val store = FileResumeRequestStore(root, cipher)
        store.save(request()).getOrThrow()
        root.resolve("$id.bin").copyTo(root.resolve("$other.bin"))
        assertTrue(store.load(other).isFailure)
        root.resolve("$other.bin").writeText("broken")
        assertTrue(store.load(other).isFailure)
        assertTrue(store.load(id).isSuccess)
        val wrong = AesGcmRetryPayloadCipher(SecretKeySpec(ByteArray(32), "AES"))
        assertTrue(FileResumeRequestStore(root, wrong).load(id).isFailure)
        assertTrue(store.load("../$id").isFailure)
    }

    @Test fun failedReplacementAndOversizedRequestPreserveOldCiphertext() {
        val root = root()
        val store = FileResumeRequestStore(root, cipher)
        store.save(request()).getOrThrow()
        val old = root.resolve("$id.bin").readBytes()
        val broken = FileResumeRequestStore(root, cipher, AtomicSnapshotWriter { _, _ -> error("injected") })
        assertTrue(broken.save(request().copy(title = "changed")).isFailure)
        assertArrayEquals(old, root.resolve("$id.bin").readBytes())
        assertTrue(store.save(request().copy(url = "x".repeat(1024 * 1024 + 1))).isFailure)
        assertArrayEquals(old, root.resolve("$id.bin").readBytes())
    }
}
