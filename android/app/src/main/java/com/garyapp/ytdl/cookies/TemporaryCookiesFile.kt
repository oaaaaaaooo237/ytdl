package com.garyapp.ytdl.cookies

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.Collections

class TemporaryCookiesFile private constructor(
    val file: File,
) {
    fun delete(): Boolean {
        return deleteIfManagedPath(file.absolutePath)
    }

    override fun toString(): String {
        return "TemporaryCookiesFile(name=${file.name}, exists=${file.exists()})"
    }

    companion object {
        private const val DirectoryName = "temporary-cookies"
        private const val FilePrefix = "ytdl-cookies-"
        private val materializedPaths = Collections.synchronizedSet(mutableSetOf<String>())

        fun materialize(
            context: Context,
            reference: CookiesReference,
            taskId: String,
        ): Result<TemporaryCookiesFile> {
            return runCatching {
                openReferenceStream(context, reference).use { stream ->
                    materializeFromStream(
                        input = stream,
                        privateCacheDirectory = context.cacheDir,
                        taskId = taskId,
                    ).getOrThrow()
                }
            }
        }

        fun materialize(
            reference: CookiesReference,
            privateCacheDirectory: File,
            taskId: String,
        ): Result<TemporaryCookiesFile> {
            return runCatching {
                openLocalReference(reference).use { stream ->
                    materializeFromStream(
                        input = stream,
                        privateCacheDirectory = privateCacheDirectory,
                        taskId = taskId,
                    ).getOrThrow()
                }
            }
        }

        fun deleteIfManagedPath(path: String?): Boolean {
            val rawPath = path?.takeIf { it.isNotBlank() } ?: return false
            val file = File(rawPath)
            val canonicalPath = file.safeCanonicalPath()
            if (!materializedPaths.contains(canonicalPath)) return false
            if (!file.exists()) {
                materializedPaths.remove(canonicalPath)
                return true
            }
            val deleted = file.delete()
            if (deleted || !file.exists()) {
                materializedPaths.remove(canonicalPath)
                return true
            }
            return false
        }

        fun isManagedTemporaryPath(path: String?): Boolean {
            val rawPath = path?.takeIf { it.isNotBlank() } ?: return false
            return materializedPaths.contains(File(rawPath).safeCanonicalPath())
        }

        fun materializeFromStream(
            input: InputStream,
            privateCacheDirectory: File,
            taskId: String,
        ): Result<TemporaryCookiesFile> {
            return runCatching {
                val directory = File(privateCacheDirectory, DirectoryName).apply {
                    mkdirs()
                }
                val output = File(
                    directory,
                    "$FilePrefix${taskId.safeFileToken()}-${System.nanoTime()}.txt",
                )
                try {
                    output.outputStream().use { stream ->
                        input.copyTo(stream)
                    }
                    materializedPaths += output.safeCanonicalPath()
                    TemporaryCookiesFile(output)
                } catch (throwable: Throwable) {
                    runCatching {
                        if (output.exists()) {
                            output.delete()
                        }
                    }
                    throw throwable
                }
            }
        }

        private fun openReferenceStream(
            context: Context,
            reference: CookiesReference,
        ): InputStream {
            val value = reference.value
            return when {
                value.startsWith("content://", ignoreCase = true) -> {
                    context.contentResolver.openInputStream(Uri.parse(value))
                        ?: throw IllegalArgumentException("无法读取所选 cookies 文件。")
                }
                value.startsWith("file://", ignoreCase = true) -> {
                    val path = Uri.parse(value).path
                        ?: throw IllegalArgumentException("无法读取所选 cookies 文件。")
                    File(path).inputStream()
                }
                else -> File(value).inputStream()
            }
        }

        private fun openLocalReference(reference: CookiesReference): InputStream {
            val value = reference.value
            if (value.startsWith("content://", ignoreCase = true)) {
                throw IllegalArgumentException("content URI 需要通过 Android Context 读取。")
            }
            val file = if (value.startsWith("file://", ignoreCase = true)) {
                val path = Uri.parse(value).path
                    ?: throw IllegalArgumentException("无法读取所选 cookies 文件。")
                File(path)
            } else {
                File(value)
            }
            return file.inputStream()
        }

        private fun String.safeFileToken(): String {
            return trim()
                .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
                .take(48)
                .ifBlank { "task" }
        }

        private fun File.safeCanonicalPath(): String {
            return runCatching { canonicalPath }.getOrDefault(absolutePath)
        }
    }
}
