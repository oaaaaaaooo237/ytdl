package com.garyapp.ytdl.core.ytdlp

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

data class ParserReleaseMetadata(
    val version: String,
    val filename: String,
    val wheelUrl: URI,
    val sha256: String,
) {
    companion object {
        fun fromPyPiJson(json: String): ParserReleaseMetadata {
            val root = JSONObject(json)
            val info = root.getJSONObject("info")
            require(info.getString("name") == PackageName) { "解析器包名无效。" }
            val version = latestVersionFromPyPiInfo(info)
            val expectedFilename = "yt_dlp-$version-py3-none-any.whl"
            val urls = root.getJSONArray("urls")
            for (index in 0 until urls.length()) {
                val item = urls.getJSONObject(index)
                if (item.optString("packagetype") != "bdist_wheel") continue
                val filename = item.getString("filename")
                require(filename == expectedFilename) { "解析器文件名无效。" }
                val wheelUrl = URI(item.getString("url"))
                require(
                    wheelUrl.scheme.equals("https", ignoreCase = true) &&
                        wheelUrl.host.equals(OfficialWheelHost, ignoreCase = true) &&
                        wheelUrl.port == -1 &&
                        wheelUrl.rawUserInfo == null &&
                        wheelUrl.rawQuery == null &&
                        wheelUrl.rawFragment == null &&
                        wheelUrl.path.substringAfterLast('/') == expectedFilename,
                ) { "解析器下载地址无效。" }
                val sha256 = item.getJSONObject("digests").getString("sha256")
                require(Sha256Pattern.matches(sha256)) { "解析器校验值无效。" }
                return ParserReleaseMetadata(
                    version = version,
                    filename = filename,
                    wheelUrl = wheelUrl,
                    sha256 = sha256.lowercase(),
                )
            }
            throw IllegalArgumentException("缺少可用的解析器安装包。")
        }

        fun latestVersionFromPyPiJson(json: String): String {
            return latestVersionFromPyPiInfo(JSONObject(json).getJSONObject("info"))
        }

        private fun latestVersionFromPyPiInfo(info: JSONObject): String {
            val version = info.getString("version")
            require(isValidVersion(version)) { "解析器版本无效。" }
            return version
        }

        private fun isValidVersion(version: String): Boolean {
            return VersionPattern.matches(version) && version.split('.').all { it.toIntOrNull() != null }
        }

        private const val PackageName = "yt-dlp"
        private const val OfficialWheelHost = "files.pythonhosted.org"
        private val VersionPattern = Regex("""^[0-9]+(?:\.[0-9]+)+$""")
        private val Sha256Pattern = Regex("""^[0-9a-fA-F]{64}$""")
    }
}

data class ParserVersionInfo(
    val version: String,
    val isBuiltIn: Boolean,
    val isSelected: Boolean,
    val canDelete: Boolean = !isBuiltIn,
)

interface ParserVersionSelectionStore {
    var selectedVersion: String
}

interface ParserVersionControl {
    fun listVersions(): List<ParserVersionInfo>
    fun downloadLatest(expectedVersion: String? = null): Result<ParserVersionInfo>
    fun select(version: String): Result<Unit>
    fun delete(version: String): Result<Unit>
}

object ParserRuntimeState {
    @Volatile
    var activeVersion: String = YtdlpBridge.PINNED_YTDLP_VERSION
        internal set

    @Volatile
    var activeRuntimeWheelPath: String? = null
        internal set
}

object ParserRuntimeBootstrap {
    private var initialized = false

    @Synchronized
    fun initializeOnce(initializer: () -> Unit) {
        if (initialized) return
        initializer()
        initialized = true
    }
}

internal object ActiveParserVersionParts {
    private val paths = mutableSetOf<String>()

    @Synchronized
    fun register(files: Collection<File>): AutoCloseable {
        val registered = files.map(File::getAbsolutePath)
        paths += registered
        return AutoCloseable {
            synchronized(this) {
                paths -= registered.toSet()
            }
        }
    }

    @Synchronized
    fun contains(file: File): Boolean = file.absolutePath in paths
}

class ParserVersionManager(
    private val versionsDirectory: File,
    private val selectionStore: ParserVersionSelectionStore,
    private val fetchMetadataJson: () -> String = ParserUpdateHttpClient::fetchJson,
    private val openWheel: (URI) -> InputStream = ParserUpdateHttpClient::openWheel,
    private val moveForDeletion: (File, File) -> Boolean = { source, target -> source.renameTo(target) },
    private val deleteStagedArtifact: (File) -> Boolean = File::delete,
) : ParserVersionControl {
    init {
        cleanupStaleVersionArtifacts()
    }

    @Synchronized
    override fun listVersions(): List<ParserVersionInfo> {
        val downloaded = verifiedDownloads()
        val selected = effectiveSelectedVersion(downloaded)
        return listOf(
            ParserVersionInfo(
                version = YtdlpBridge.PINNED_YTDLP_VERSION,
                isBuiltIn = true,
                isSelected = selected == YtdlpBridge.PINNED_YTDLP_VERSION,
            ),
        ) + downloaded
            .sortedWith { left, right -> compareVersions(left.version, right.version) }
            .map { verified ->
                ParserVersionInfo(
                    version = verified.version,
                    isBuiltIn = false,
                    isSelected = selected == verified.version,
                )
            }
    }

    @Synchronized
    override fun downloadLatest(expectedVersion: String?): Result<ParserVersionInfo> = runCatching {
        check(versionsDirectory.mkdirs() || versionsDirectory.isDirectory) {
            "无法准备解析器版本目录。"
        }
        val release = ParserReleaseMetadata.fromPyPiJson(fetchMetadataJson())
        require(expectedVersion == null || expectedVersion == release.version) {
            "解析器版本已变化，请重新检查。"
        }
        if (release.version == YtdlpBridge.PINNED_YTDLP_VERSION) {
            cleanupBundledDuplicateArtifacts()
            return@runCatching ParserVersionInfo(
                version = YtdlpBridge.PINNED_YTDLP_VERSION,
                isBuiltIn = true,
                isSelected = selectionStore.selectedVersion == YtdlpBridge.PINNED_YTDLP_VERSION,
            )
        }
        verifiedDownload(release.version)?.let { verified ->
            if (verified.sha256 == release.sha256) {
                return@runCatching ParserVersionInfo(
                    version = verified.version,
                    isBuiltIn = false,
                    isSelected = selectionStore.selectedVersion == verified.version,
                )
            }
            verified.wheel.deleteIfPresent()
            verified.sidecar.deleteIfPresent()
        }

        val target = File(versionsDirectory, release.filename)
        val sidecar = File(versionsDirectory, "${release.filename}.sha256")
        val part = File(versionsDirectory, "${release.filename}.${System.nanoTime()}.part")
        val sidecarPart = File(versionsDirectory, "${release.filename}.sha256.${System.nanoTime()}.part")
        val activeParts = ActiveParserVersionParts.register(listOf(part, sidecarPart))
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var byteCount = 0L
            openWheel(release.wheelUrl).use { input ->
                part.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        byteCount += count
                        require(byteCount <= MaxWheelBytes) { "解析器安装包大小无效。" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(byteCount > 0L) { "解析器安装包为空。" }
            val actualSha = digest.digest().toHex()
            require(actualSha == release.sha256) { "解析器安装包校验失败。" }
            sidecarPart.writeText(actualSha, Charsets.US_ASCII)
            target.deleteIfPresent()
            sidecar.deleteIfPresent()
            check(part.renameTo(target)) { "无法保存解析器安装包。" }
            if (!sidecarPart.renameTo(sidecar)) {
                target.delete()
                error("无法保存解析器校验信息。")
            }
            ParserVersionInfo(
                version = release.version,
                isBuiltIn = false,
                isSelected = selectionStore.selectedVersion == release.version,
            )
        } finally {
            part.delete()
            sidecarPart.delete()
            activeParts.close()
        }
    }

    @Synchronized
    override fun select(version: String): Result<Unit> = runCatching {
        require(
            version == YtdlpBridge.PINNED_YTDLP_VERSION || verifiedDownload(version) != null,
        ) { "解析器版本不可用。" }
        selectionStore.selectedVersion = version
    }

    @Synchronized
    override fun delete(version: String): Result<Unit> = runCatching {
        require(version != YtdlpBridge.PINNED_YTDLP_VERSION) { "内置解析器不可删除。" }
        val verified = requireNotNull(verifiedDownload(version)) { "解析器版本不可用。" }
        val nonce = System.nanoTime()
        val stagedWheel = File(versionsDirectory, "${verified.wheel.name}.delete.$nonce.tmp")
        val stagedSidecar = File(versionsDirectory, "${verified.sidecar.name}.delete.$nonce.tmp")
        check(!stagedWheel.exists() && !stagedSidecar.exists()) { "无法准备解析器删除操作。" }
        val activeTemps = ActiveParserVersionParts.register(listOf(stagedWheel, stagedSidecar))
        try {
            check(moveForDeletion(verified.wheel, stagedWheel)) { "无法暂存待删除的解析器版本。" }
            var sidecarStaged = false
            try {
                check(moveForDeletion(verified.sidecar, stagedSidecar)) { "无法暂存待删除的解析器校验信息。" }
                sidecarStaged = true
                if (selectionStore.selectedVersion == version) {
                    selectionStore.selectedVersion = YtdlpBridge.PINNED_YTDLP_VERSION
                }
            } catch (failure: Throwable) {
                if (sidecarStaged) {
                    check(moveForDeletion(stagedSidecar, verified.sidecar)) { "无法回滚解析器校验信息。" }
                }
                check(moveForDeletion(stagedWheel, verified.wheel)) { "无法回滚解析器版本。" }
                throw failure
            }
            runCatching { deleteStagedArtifact(stagedWheel) }
            runCatching { deleteStagedArtifact(stagedSidecar) }
        } finally {
            activeTemps.close()
        }
    }

    @Synchronized
    fun prepareSelectedRuntimeWheel(cacheDir: File): File? {
        val selected = selectionStore.selectedVersion
        val verified = selected
            .takeUnless { it == YtdlpBridge.PINNED_YTDLP_VERSION }
            ?.let(::verifiedDownload)
        if (selected != YtdlpBridge.PINNED_YTDLP_VERSION && verified == null) {
            selectionStore.selectedVersion = YtdlpBridge.PINNED_YTDLP_VERSION
        }
        val targetName = verified?.let { "yt_dlp-${it.version}-${it.sha256}.whl" }
        cleanupRuntimeArtifacts(cacheDir, targetName)
        if (verified == null) return null
        if (!cacheDir.mkdirs() && !cacheDir.isDirectory) {
            selectionStore.selectedVersion = YtdlpBridge.PINNED_YTDLP_VERSION
            return null
        }
        cleanupRuntimeArtifacts(cacheDir, targetName)
        val target = File(cacheDir, requireNotNull(targetName))
        if (target.absolutePath == ParserRuntimeState.activeRuntimeWheelPath && target.isFile) {
            return target
        }
        if (
            target.isFile &&
            target.length() in 1..MaxWheelBytes &&
            runCatching { target.inputStream().use(::sha256) }.getOrNull() == verified.sha256
        ) {
            return target
        }
        target.delete()
        val part = File(cacheDir, "${target.name}.${System.nanoTime()}.part")
        val activePart = ActiveParserVersionParts.register(listOf(part))
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var byteCount = 0L
            verified.wheel.inputStream().use { input ->
                part.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        byteCount += count
                        if (byteCount > MaxWheelBytes) return@use
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val copiedSha = digest.digest().toHex()
            if (byteCount !in 1..MaxWheelBytes || copiedSha != verified.sha256 || !part.renameTo(target)) {
                part.delete()
                selectionStore.selectedVersion = YtdlpBridge.PINNED_YTDLP_VERSION
                null
            } else {
                target
            }
        } catch (_: Exception) {
            selectionStore.selectedVersion = YtdlpBridge.PINNED_YTDLP_VERSION
            null
        } finally {
            part.delete()
            activePart.close()
        }
    }

    private fun cleanupRuntimeArtifacts(cacheDir: File, targetName: String?) {
        if (!cacheDir.isDirectory) return
        val protectedPath = ParserRuntimeState.activeRuntimeWheelPath
        cacheDir.listFiles().orEmpty().forEach { candidate ->
            if (
                !candidate.isFile ||
                candidate.absolutePath == protectedPath ||
                candidate.name == targetName ||
                ActiveParserVersionParts.contains(candidate)
            ) {
                return@forEach
            }
            if (
                RuntimeWheelFilenamePattern.matches(candidate.name) ||
                RuntimePartFilenamePattern.matches(candidate.name)
            ) {
                candidate.delete()
            }
        }
    }

    private fun effectiveSelectedVersion(downloaded: List<VerifiedDownload>): String {
        val selected = selectionStore.selectedVersion
        if (selected == YtdlpBridge.PINNED_YTDLP_VERSION || downloaded.any { it.version == selected }) {
            return selected
        }
        selectionStore.selectedVersion = YtdlpBridge.PINNED_YTDLP_VERSION
        return YtdlpBridge.PINNED_YTDLP_VERSION
    }

    private fun verifiedDownloads(): List<VerifiedDownload> {
        if (!versionsDirectory.isDirectory) return emptyList()
        cleanupBundledDuplicateArtifacts()
        return versionsDirectory.listFiles().orEmpty().mapNotNull { candidate ->
            val match = WheelFilenamePattern.matchEntire(candidate.name) ?: return@mapNotNull null
            if (match.groupValues[1] == YtdlpBridge.PINNED_YTDLP_VERSION) return@mapNotNull null
            verifiedDownload(match.groupValues[1])
        }.distinctBy { it.version }
    }

    private fun cleanupStaleVersionArtifacts() {
        if (!versionsDirectory.isDirectory) return
        versionsDirectory.listFiles().orEmpty().forEach { candidate ->
            if (
                candidate.isFile &&
                (
                    VersionPartFilenamePattern.matches(candidate.name) ||
                        VersionDeleteTempFilenamePattern.matches(candidate.name)
                    ) &&
                !ActiveParserVersionParts.contains(candidate)
            ) {
                candidate.delete()
            }
        }
    }

    private fun cleanupBundledDuplicateArtifacts() {
        val filename = "yt_dlp-${YtdlpBridge.PINNED_YTDLP_VERSION}-py3-none-any.whl"
        listOf(
            File(versionsDirectory, filename),
            File(versionsDirectory, "$filename.sha256"),
        ).forEach { artifact ->
            if (artifact.isFile) artifact.delete()
        }
    }

    private fun verifiedDownload(version: String): VerifiedDownload? {
        if (!VersionPattern.matches(version) || version.split('.').any { it.toIntOrNull() == null }) return null
        val filename = "yt_dlp-$version-py3-none-any.whl"
        val wheel = File(versionsDirectory, filename)
        val sidecar = File(versionsDirectory, "$filename.sha256")
        if (!wheel.isFile || !sidecar.isFile || wheel.length() !in 1..MaxWheelBytes) return null
        val expectedSha = runCatching { sidecar.readText(Charsets.US_ASCII) }.getOrNull() ?: return null
        if (!Sha256Pattern.matches(expectedSha)) return null
        val actualSha = runCatching { wheel.inputStream().use(::sha256) }.getOrNull() ?: return null
        if (actualSha != expectedSha.lowercase()) return null
        return VerifiedDownload(version, wheel, sidecar, actualSha)
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split('.').map(String::toInt)
        val rightParts = right.split('.').map(String::toInt)
        repeat(maxOf(leftParts.size, rightParts.size)) { index ->
            val compared = (leftParts.getOrNull(index) ?: 0).compareTo(rightParts.getOrNull(index) ?: 0)
            if (compared != 0) return compared
        }
        return 0
    }

    private data class VerifiedDownload(
        val version: String,
        val wheel: File,
        val sidecar: File,
        val sha256: String,
    )

    companion object {
        const val VersionsDirectoryName = "parser-versions"
        const val RuntimeDirectoryName = "parser-runtime"
        const val MaxWheelBytes = 64L * 1024L * 1024L
        private val WheelFilenamePattern = Regex("""^yt_dlp-([0-9]+(?:\.[0-9]+)+)-py3-none-any\.whl$""")
        private val VersionPartFilenamePattern = Regex(
            """^yt_dlp-[0-9]+(?:\.[0-9]+)+-py3-none-any\.whl(?:\.sha256)?\.[0-9]+\.part$""",
        )
        private val VersionDeleteTempFilenamePattern = Regex(
            """^yt_dlp-[0-9]+(?:\.[0-9]+)+-py3-none-any\.whl(?:\.sha256)?\.delete\.[0-9]+\.tmp$""",
        )
        private val RuntimeWheelFilenamePattern = Regex(
            """^yt_dlp-[0-9]+(?:\.[0-9]+)+-[0-9a-f]{64}\.whl$""",
        )
        private val RuntimePartFilenamePattern = Regex(
            """^yt_dlp-[0-9]+(?:\.[0-9]+)+-[0-9a-f]{64}\.whl\.[0-9]+\.part$""",
        )
        private val VersionPattern = Regex("""^[0-9]+(?:\.[0-9]+)+$""")
        private val Sha256Pattern = Regex("""^[0-9a-fA-F]{64}$""")

        fun fromContext(context: Context): ParserVersionManager {
            val appContext = context.applicationContext
            val preferences = appContext.getSharedPreferences("parser-version-selection", Context.MODE_PRIVATE)
            return ParserVersionManager(
                versionsDirectory = File(appContext.filesDir, VersionsDirectoryName),
                selectionStore = object : ParserVersionSelectionStore {
                    override var selectedVersion: String
                        get() = preferences.getString("selected_version", null)
                            ?: YtdlpBridge.PINNED_YTDLP_VERSION
                        set(value) {
                            preferences.edit().putString("selected_version", value).apply()
                        }
                },
            )
        }
    }
}

private fun sha256(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count > 0) digest.update(buffer, 0, count)
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

private fun File.deleteIfPresent() {
    if (exists()) check(delete()) { "无法替换无效解析器文件。" }
}
