package com.garyapp.ytdl.core.ytdlp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.testing.ProjectTestPaths
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParserVersionManagerTest {
    private lateinit var testRoot: File
    private lateinit var versionsDirectory: File
    private lateinit var store: FakeParserVersionSelectionStore

    @Before
    fun setUp() {
        testRoot = ProjectTestPaths.createTempDirectory("parser-versions-").toFile()
        versionsDirectory = File(testRoot, "versions")
        store = FakeParserVersionSelectionStore()
    }

    @After
    fun tearDown() {
        testRoot.deleteRecursively()
        assertFalse(testRoot.exists())
    }

    @Test
    fun validOfficialPyPiMetadataSelectsUniversalWheel() {
        val release = ParserReleaseMetadata.fromPyPiJson(
            pypiJson(
                version = "2026.4.1",
                filename = "yt_dlp-2026.4.1-py3-none-any.whl",
                url = "https://files.pythonhosted.org/packages/aa/bb/yt_dlp-2026.4.1-py3-none-any.whl",
                sha256 = "a".repeat(64),
            ),
        )

        assertEquals("2026.4.1", release.version)
        assertEquals("yt_dlp-2026.4.1-py3-none-any.whl", release.filename)
        assertEquals("a".repeat(64), release.sha256)
        assertEquals(
            "https://files.pythonhosted.org/packages/aa/bb/yt_dlp-2026.4.1-py3-none-any.whl",
            release.wheelUrl.toString(),
        )
    }

    @Test
    fun metadataRejectsUnofficialOrMismatchedWheelIdentity() {
        val valid = MetadataCase(
            packageName = "yt-dlp",
            version = "2026.4.1",
            filename = "yt_dlp-2026.4.1-py3-none-any.whl",
            url = "https://files.pythonhosted.org/packages/aa/bb/yt_dlp-2026.4.1-py3-none-any.whl",
            sha256 = "a".repeat(64),
        )
        val invalidCases = listOf(
            valid.copy(packageName = "not-yt-dlp"),
            valid.copy(version = "latest"),
            valid.copy(filename = "other-2026.4.1-py3-none-any.whl"),
            valid.copy(filename = "yt_dlp-2026.4.1-py2-none-any.whl"),
            valid.copy(url = "http://files.pythonhosted.org/packages/yt_dlp-2026.4.1-py3-none-any.whl"),
            valid.copy(url = "https://evil.example/yt_dlp-2026.4.1-py3-none-any.whl"),
            valid.copy(url = "https://files.pythonhosted.org/packages/yt_dlp-2026.4.2-py3-none-any.whl"),
            valid.copy(sha256 = "not-a-sha256"),
        )

        invalidCases.forEach { candidate ->
            assertTrue(
                "必须拒绝 $candidate",
                runCatching { ParserReleaseMetadata.fromPyPiJson(candidate.toJson()) }.isFailure,
            )
        }
    }

    @Test
    fun downloadLatestStreamsHashThenAtomicallyPublishesVerifiedWheel() {
        val wheelBytes = "verified wheel".toByteArray()
        val manager = managerFor("2026.4.1", wheelBytes)

        val downloaded = manager.downloadLatest("2026.4.1").getOrThrow()

        assertEquals("2026.4.1", downloaded.version)
        assertFalse(downloaded.isBuiltIn)
        assertTrue(File(versionsDirectory, "yt_dlp-2026.4.1-py3-none-any.whl").isFile)
        assertEquals(
            sha256(wheelBytes),
            File(versionsDirectory, "yt_dlp-2026.4.1-py3-none-any.whl.sha256").readText(),
        )
        assertTrue(versionsDirectory.listFiles().orEmpty().none { it.name.endsWith(".part") })
        assertEquals(
            listOf(YtdlpBridge.PINNED_YTDLP_VERSION, "2026.4.1"),
            manager.listVersions().map { it.version },
        )
    }

    @Test
    fun latestEqualToBuiltInReturnsBuiltInWithoutPersistingWheel() {
        val manager = managerFor(YtdlpBridge.PINNED_YTDLP_VERSION, "must not persist".toByteArray())

        val result = manager.downloadLatest(YtdlpBridge.PINNED_YTDLP_VERSION).getOrThrow()

        assertTrue(result.isBuiltIn)
        assertEquals(YtdlpBridge.PINNED_YTDLP_VERSION, result.version)
        assertEquals(listOf(YtdlpBridge.PINNED_YTDLP_VERSION), manager.listVersions().map { it.version })
        assertTrue(versionsDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun legacyDownloadMatchingBuiltInIsFoldedAndRemovedWithoutTouchingOtherVersions() {
        val builtInWheel = writeVerifiedVersion(
            YtdlpBridge.PINNED_YTDLP_VERSION,
            "legacy built in duplicate".toByteArray(),
        )
        val otherWheel = writeVerifiedVersion("2026.4.1", "other version".toByteArray())
        val manager = managerFor("2026.5.1", "unused".toByteArray())

        assertEquals(
            listOf(YtdlpBridge.PINNED_YTDLP_VERSION, "2026.4.1"),
            manager.listVersions().map { it.version },
        )
        assertFalse(builtInWheel.exists())
        assertFalse(File(versionsDirectory, "${builtInWheel.name}.sha256").exists())
        assertTrue(otherWheel.isFile)
        assertTrue(File(versionsDirectory, "${otherWheel.name}.sha256").isFile)
    }

    @Test
    fun failedHashLeavesNoPartAndDoesNotPolluteVerifiedVersions() {
        val verifiedBytes = "already verified".toByteArray()
        val manager = managerFor("2026.4.1", verifiedBytes)
        manager.downloadLatest("2026.4.1").getOrThrow()
        val badManager = managerFor(
            version = "2026.5.1",
            wheelBytes = "tampered".toByteArray(),
            advertisedSha = "b".repeat(64),
        )

        assertTrue(badManager.downloadLatest("2026.5.1").isFailure)

        assertEquals(
            listOf(YtdlpBridge.PINNED_YTDLP_VERSION, "2026.4.1"),
            badManager.listVersions().map { it.version },
        )
        assertTrue(versionsDirectory.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun selfConsistentLocalWheelWithDifferentOfficialShaIsReplaced() {
        val localBytes = "locally consistent old wheel".toByteArray()
        val officialBytes = "current official wheel".toByteArray()
        writeVerifiedVersion("2026.4.1", localBytes)
        var openCount = 0
        val filename = "yt_dlp-2026.4.1-py3-none-any.whl"
        val manager = ParserVersionManager(
            versionsDirectory = versionsDirectory,
            selectionStore = store,
            fetchMetadataJson = {
                pypiJson(
                    version = "2026.4.1",
                    filename = filename,
                    url = "https://files.pythonhosted.org/packages/aa/bb/$filename",
                    sha256 = sha256(officialBytes),
                )
            },
            openWheel = {
                openCount += 1
                ByteArrayInputStream(officialBytes)
            },
        )

        manager.downloadLatest("2026.4.1").getOrThrow()

        assertEquals(1, openCount)
        assertEquals(
            officialBytes.toList(),
            File(versionsDirectory, filename).readBytes().toList(),
        )
        assertEquals(
            sha256(officialBytes),
            File(versionsDirectory, "$filename.sha256").readText(),
        )
    }

    @Test
    fun emptyWheelIsRejectedAndPartIsRemoved() {
        val manager = managerFor("2026.4.1", byteArrayOf())

        assertTrue(manager.downloadLatest("2026.4.1").isFailure)
        assertTrue(versionsDirectory.listFiles().orEmpty().none { it.name.endsWith(".part") })
        assertEquals(listOf(YtdlpBridge.PINNED_YTDLP_VERSION), manager.listVersions().map { it.version })
    }

    @Test
    fun startupRemovesOnlyStrictlyNamedStaleVersionParts() {
        versionsDirectory.mkdirs()
        val staleParts = listOf(
            File(versionsDirectory, "yt_dlp-2026.4.1-py3-none-any.whl.123.part"),
            File(versionsDirectory, "yt_dlp-2026.4.1-py3-none-any.whl.sha256.456.part"),
        ).onEach { it.writeText("stale") }
        val unrelated = listOf(
            File(versionsDirectory, "notes.part"),
            File(versionsDirectory, "yt_dlp-2026.4.1-py3-none-any.whl.custom.part"),
        ).onEach { it.writeText("keep") }

        managerFor("2026.5.1", "unused".toByteArray())

        assertTrue(staleParts.none(File::exists))
        assertTrue(unrelated.all(File::isFile))
    }

    @Test
    fun startupRemovesOnlyStrictlyNamedDeleteTemps() {
        versionsDirectory.mkdirs()
        val staleDeleteTemps = listOf(
            File(versionsDirectory, "yt_dlp-2026.4.1-py3-none-any.whl.delete.123.tmp"),
            File(versionsDirectory, "yt_dlp-2026.4.1-py3-none-any.whl.sha256.delete.123.tmp"),
            File(versionsDirectory, "yt_dlp-2026.5.1-py3-none-any.whl.sha256.delete.456.tmp"),
        ).onEach { it.writeText("stale") }
        val unrelated = listOf(
            File(versionsDirectory, "notes.delete.123.tmp"),
            File(versionsDirectory, "yt_dlp-2026.4.1-py3-none-any.whl.delete.bad.tmp"),
            File(versionsDirectory, "yt_dlp-2026.4.1-py3-none-any.whl.delete.123.tmp.keep"),
        ).onEach { it.writeText("keep") }

        managerFor("2026.6.1", "unused".toByteArray())

        assertTrue(staleDeleteTemps.none(File::exists))
        assertTrue(unrelated.all(File::isFile))
    }

    @Test
    fun activeVersionPartIsNotDeletedWhenAnotherManagerStarts() {
        val wheelBytes = "actively downloaded wheel".toByteArray()
        val filename = "yt_dlp-2026.4.1-py3-none-any.whl"
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val downloadResult = AtomicReference<Result<ParserVersionInfo>>()
        val blockingInput = object : InputStream() {
            private var index = 0

            override fun read(): Int {
                if (index >= wheelBytes.size) return -1
                if (index == 0) {
                    readStarted.countDown()
                    check(releaseRead.await(2, TimeUnit.SECONDS))
                }
                return wheelBytes[index++].toInt() and 0xff
            }
        }
        val downloadingManager = ParserVersionManager(
            versionsDirectory = versionsDirectory,
            selectionStore = store,
            fetchMetadataJson = {
                pypiJson(
                    version = "2026.4.1",
                    filename = filename,
                    url = "https://files.pythonhosted.org/packages/aa/bb/$filename",
                    sha256 = sha256(wheelBytes),
                )
            },
            openWheel = { blockingInput },
        )
        val worker = Thread {
            downloadResult.set(downloadingManager.downloadLatest("2026.4.1"))
        }
        try {
            worker.start()
            assertTrue(readStarted.await(2, TimeUnit.SECONDS))
            val activePart = versionsDirectory.listFiles().orEmpty().single {
                it.name.matches(Regex("^yt_dlp-2026\\.4\\.1-py3-none-any\\.whl\\.[0-9]+\\.part$"))
            }

            managerFor("2026.5.1", "unused".toByteArray())

            assertTrue("新 manager 不得删除进程内正在写的 part", activePart.isFile)
        } finally {
            releaseRead.countDown()
            worker.join(2_000)
        }
        assertFalse(worker.isAlive)
        assertTrue(downloadResult.get().isSuccess)
    }

    @Test
    fun registeredActivePartIsSkippedUntilItsOperationFinishes() {
        versionsDirectory.mkdirs()
        val activePart = File(
            versionsDirectory,
            "yt_dlp-2026.4.1-py3-none-any.whl.123.part",
        ).apply { writeText("active") }

        ActiveParserVersionParts.register(listOf(activePart)).use {
            managerFor("2026.5.1", "unused".toByteArray())
            assertTrue(activePart.isFile)
        }

        managerFor("2026.5.1", "unused".toByteArray())
        assertFalse(activePart.exists())
    }

    @Test
    fun selectionDeletionFallbackAndRedownloadRemainAvailable() {
        val manager = managerFor("2026.4.1", "wheel one".toByteArray())
        manager.downloadLatest("2026.4.1").getOrThrow()
        manager.select("2026.4.1").getOrThrow()
        assertEquals("2026.4.1", store.selectedVersion)
        assertTrue(manager.listVersions().single { it.version == "2026.4.1" }.isSelected)

        manager.delete("2026.4.1").getOrThrow()

        assertEquals(YtdlpBridge.PINNED_YTDLP_VERSION, store.selectedVersion)
        assertEquals(listOf(YtdlpBridge.PINNED_YTDLP_VERSION), manager.listVersions().map { it.version })
        assertTrue(manager.delete(YtdlpBridge.PINNED_YTDLP_VERSION).isFailure)

        val nextManager = managerFor("2026.5.1", "wheel two".toByteArray())
        assertEquals("2026.5.1", nextManager.downloadLatest().getOrThrow().version)
    }

    @Test
    fun selectedVersionDeleteRollsBackPairAndSelectionWhenStagingFails() {
        val version = "2026.4.1"
        val bytes = "wheel to keep after rollback".toByteArray()
        val filename = "yt_dlp-$version-py3-none-any.whl"
        writeVerifiedVersion(version, bytes)
        store.selectedVersion = version
        val manager = ParserVersionManager(
            versionsDirectory = versionsDirectory,
            selectionStore = store,
            fetchMetadataJson = { error("unused") },
            openWheel = { error("unused") },
            moveForDeletion = { source, target ->
                if (source.name.endsWith(".sha256")) false else source.renameTo(target)
            },
        )

        val result = manager.delete(version)

        assertTrue(result.isFailure)
        assertEquals(version, store.selectedVersion)
        assertEquals(bytes.toList(), File(versionsDirectory, filename).readBytes().toList())
        assertTrue(File(versionsDirectory, "$filename.sha256").isFile)
        assertTrue(
            versionsDirectory.listFiles().orEmpty().none {
                it.name.matches(Regex("^yt_dlp-.*\\.delete\\.[0-9]+\\.tmp$"))
            },
        )
    }

    @Test
    fun failedFinalDeleteLeavesRecoverableTempsThatNextStartupCleans() {
        val version = "2026.4.1"
        val filename = "yt_dlp-$version-py3-none-any.whl"
        writeVerifiedVersion(version, "wheel to delete".toByteArray())
        store.selectedVersion = version
        val unrelated = File(versionsDirectory, "$filename.delete.bad.tmp").apply {
            writeText("keep")
        }
        val manager = ParserVersionManager(
            versionsDirectory = versionsDirectory,
            selectionStore = store,
            fetchMetadataJson = { error("unused") },
            openWheel = { error("unused") },
            deleteStagedArtifact = { false },
        )

        manager.delete(version).getOrThrow()

        assertEquals(YtdlpBridge.PINNED_YTDLP_VERSION, store.selectedVersion)
        assertFalse(File(versionsDirectory, filename).exists())
        assertFalse(File(versionsDirectory, "$filename.sha256").exists())
        val pendingDeleteTemps = versionsDirectory.listFiles().orEmpty().filter {
            it.name.matches(
                Regex(
                    "^yt_dlp-2026\\.4\\.1-py3-none-any\\.whl(?:\\.sha256)?" +
                        "\\.delete\\.[0-9]+\\.tmp$",
                ),
            )
        }
        assertEquals(2, pendingDeleteTemps.size)

        managerFor("2026.5.1", "unused".toByteArray())

        assertTrue(pendingDeleteTemps.none(File::exists))
        assertTrue(unrelated.isFile)
    }

    @Test
    fun selectedVerifiedWheelCreatesIndependentRuntimeCopyAndNextLaunchFallsBackIfSourceIsGone() {
        val wheelBytes = "runtime wheel".toByteArray()
        val manager = managerFor("2026.4.1", wheelBytes)
        manager.downloadLatest("2026.4.1").getOrThrow()
        manager.select("2026.4.1").getOrThrow()
        val runtimeDirectory = File(testRoot, "runtime")

        val runtimeWheel = requireNotNull(manager.prepareSelectedRuntimeWheel(runtimeDirectory))

        assertEquals(wheelBytes.toList(), runtimeWheel.readBytes().toList())
        assertTrue(runtimeWheel.name.contains("2026.4.1-${sha256(wheelBytes)}"))
        assertTrue(runtimeDirectory.listFiles().orEmpty().none { it.name.endsWith(".part") })

        manager.delete("2026.4.1").getOrThrow()
        assertTrue("当前进程运行副本不能随源版本删除", runtimeWheel.isFile)

        store.selectedVersion = "2026.4.1"
        val nextLaunchManager = managerFor("2026.5.1", "unused".toByteArray())
        assertEquals(null, nextLaunchManager.prepareSelectedRuntimeWheel(runtimeDirectory))
        assertEquals(YtdlpBridge.PINNED_YTDLP_VERSION, store.selectedVersion)
    }

    @Test
    fun prepareRuntimeCleansStrictOldArtifactsButKeepsActiveCopyAndUnrelatedFiles() {
        val wheelBytes = "new runtime wheel".toByteArray()
        val manager = managerFor("2026.4.1", wheelBytes)
        manager.downloadLatest("2026.4.1").getOrThrow()
        manager.select("2026.4.1").getOrThrow()
        val runtimeDirectory = File(testRoot, "runtime").apply { mkdirs() }
        val activeCopy = File(
            runtimeDirectory,
            "yt_dlp-2026.2.1-${"a".repeat(64)}.whl",
        ).apply { writeText("active") }
        val staleWheel = File(
            runtimeDirectory,
            "yt_dlp-2026.3.1-${"b".repeat(64)}.whl",
        ).apply { writeText("stale") }
        val stalePart = File(
            runtimeDirectory,
            "yt_dlp-2026.4.1-${sha256(wheelBytes)}.whl.123.part",
        ).apply { writeText("stale part") }
        val unrelated = File(runtimeDirectory, "notes.part").apply { writeText("keep") }
        val previousActivePath = ParserRuntimeState.activeRuntimeWheelPath
        try {
            ParserRuntimeState.activeRuntimeWheelPath = activeCopy.absolutePath

            val prepared = requireNotNull(manager.prepareSelectedRuntimeWheel(runtimeDirectory))

            assertTrue(prepared.isFile)
            assertTrue(activeCopy.isFile)
            assertFalse(staleWheel.exists())
            assertFalse(stalePart.exists())
            assertTrue(unrelated.isFile)
        } finally {
            ParserRuntimeState.activeRuntimeWheelPath = previousActivePath
        }
    }

    @Test
    fun tamperedSelectedWheelIsNeverCopiedToRuntime() {
        val manager = managerFor("2026.4.1", "verified".toByteArray())
        manager.downloadLatest("2026.4.1").getOrThrow()
        manager.select("2026.4.1").getOrThrow()
        File(versionsDirectory, "yt_dlp-2026.4.1-py3-none-any.whl").writeText("tampered")

        assertEquals(null, manager.prepareSelectedRuntimeWheel(File(testRoot, "runtime")))
        assertEquals(YtdlpBridge.PINNED_YTDLP_VERSION, store.selectedVersion)
    }

    @Test
    fun mainActivityPreparesAndInsertsRuntimeWheelBeforeUiCanImportBridge() {
        val source = ProjectTestPaths.repositoryRoot
            .resolve("android/app/src/main/java/com/garyapp/ytdl/MainActivity.kt")
            .toFile()
            .readText()

        val pythonStart = source.indexOf("Python.start(AndroidPlatform(this))")
        val prepare = source.indexOf("prepareSelectedRuntimeWheel")
        val sysPath = source.indexOf("getModule(\"sys\")")
        val setContent = source.indexOf("setContent {")

        assertTrue(pythonStart >= 0)
        assertTrue(prepare > pythonStart)
        assertTrue(sysPath > prepare)
        assertTrue(setContent > sysPath)
        assertFalse(source.substring(0, sysPath).contains("ytdl_bridge"))
        assertFalse(source.substring(0, sysPath).contains("yt_dlp"))
        assertFalse(source.contains("sys.modules"))
        assertTrue(source.contains("ParserRuntimeBootstrap.initializeOnce"))
        assertTrue(source.contains("ParserRuntimeState.activeRuntimeWheelPath = runtimeWheel.absolutePath"))
    }

    @Test
    fun contextManagerPersistsSelectedVersionAcrossInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("parser-version-selection", Context.MODE_PRIVATE)
        val directory = File(context.filesDir, ParserVersionManager.VersionsDirectoryName)
        preferences.edit().clear().commit()
        directory.deleteRecursively()
        val bytes = "persisted wheel".toByteArray()
        val filename = "yt_dlp-2026.4.1-py3-none-any.whl"
        try {
            File(directory, filename).apply {
                parentFile?.mkdirs()
                writeBytes(bytes)
            }
            File(directory, "$filename.sha256").writeText(sha256(bytes))

            ParserVersionManager.fromContext(context).select("2026.4.1").getOrThrow()

            assertTrue(
                ParserVersionManager.fromContext(context).listVersions()
                    .single { it.version == "2026.4.1" }
                    .isSelected,
            )
        } finally {
            preferences.edit().clear().commit()
            directory.deleteRecursively()
        }
    }

    @Test
    fun oversizedNumericVersionFilenameIsIgnoredWithoutBreakingValidList() {
        writeVerifiedVersion("2026.4.1", "valid".toByteArray())
        writeVerifiedVersion("999999999999999999999.1", "invalid version".toByteArray())
        val manager = managerFor("2026.5.1", "unused".toByteArray())

        assertEquals(
            listOf(YtdlpBridge.PINNED_YTDLP_VERSION, "2026.4.1"),
            manager.listVersions().map { it.version },
        )
    }

    private fun writeVerifiedVersion(version: String, bytes: ByteArray): File {
        val filename = "yt_dlp-$version-py3-none-any.whl"
        val wheel = File(versionsDirectory, filename).apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }
        File(versionsDirectory, "$filename.sha256").writeText(sha256(bytes))
        return wheel
    }

    private fun managerFor(
        version: String,
        wheelBytes: ByteArray,
        advertisedSha: String = sha256(wheelBytes),
    ): ParserVersionManager {
        val filename = "yt_dlp-$version-py3-none-any.whl"
        return ParserVersionManager(
            versionsDirectory = versionsDirectory,
            selectionStore = store,
            fetchMetadataJson = {
                pypiJson(
                    version = version,
                    filename = filename,
                    url = "https://files.pythonhosted.org/packages/aa/bb/$filename",
                    sha256 = advertisedSha,
                )
            },
            openWheel = { ByteArrayInputStream(wheelBytes) },
        )
    }
}

private class FakeParserVersionSelectionStore(
    override var selectedVersion: String = YtdlpBridge.PINNED_YTDLP_VERSION,
) : ParserVersionSelectionStore

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { byte -> "%02x".format(byte) }

private data class MetadataCase(
    val packageName: String,
    val version: String,
    val filename: String,
    val url: String,
    val sha256: String,
) {
    fun toJson(): String = pypiJson(version, filename, url, sha256, packageName)
}

private fun pypiJson(
    version: String,
    filename: String,
    url: String,
    sha256: String,
    packageName: String = "yt-dlp",
): String = """
    {
      "info": {"name": "$packageName", "version": "$version"},
      "urls": [
        {
          "packagetype": "bdist_wheel",
          "filename": "$filename",
          "url": "$url",
          "digests": {"sha256": "$sha256"}
        }
      ]
    }
""".trimIndent()
