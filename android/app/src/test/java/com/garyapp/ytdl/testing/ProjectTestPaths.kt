package com.garyapp.ytdl.testing

import java.nio.file.Files
import java.nio.file.Path

object ProjectTestPaths {
    val repositoryRoot: Path by lazy {
        generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull(::hasProjectMarkers)
            ?.toFile()
            ?.canonicalFile
            ?.toPath()
            ?: error("Cannot derive repository root from Gradle test working directory")
    }

    val qaDataRoot: Path by lazy {
        val candidate = repositoryRoot.resolve(".qa-data")
            .normalize()
            .toFile()
            .canonicalFile
            .toPath()
        check(candidate.startsWith(repositoryRoot)) {
            "Project .qa-data must stay inside repository root: $candidate"
        }
        Files.createDirectories(candidate)
        candidate
    }

    fun createTempDirectory(prefix: String): Path {
        return requireInsideQaData(Files.createTempDirectory(qaDataRoot, prefix))
    }

    fun requireInsideQaData(path: Path): Path {
        val canonicalPath = path.toAbsolutePath()
            .normalize()
            .toFile()
            .canonicalFile
            .toPath()
        check(canonicalPath.startsWith(repositoryRoot) && canonicalPath.startsWith(qaDataRoot)) {
            "Test temporary path must stay inside $qaDataRoot: $canonicalPath"
        }
        return canonicalPath
    }

    private fun hasProjectMarkers(candidate: Path): Boolean {
        return Files.isRegularFile(candidate.resolve("pyproject.toml")) &&
            Files.isRegularFile(candidate.resolve("android/settings.gradle.kts"))
    }
}
