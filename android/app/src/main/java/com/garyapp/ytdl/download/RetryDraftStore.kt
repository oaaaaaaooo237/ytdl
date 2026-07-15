package com.garyapp.ytdl.download

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface RetryPayloadCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(ciphertext: ByteArray): ByteArray
}

internal data class RetryDownloadDraft(
    val url: String,
    val route: DownloadRoute,
) {
    companion object {
        fun fromRequest(request: DownloadRequest): RetryDownloadDraft {
            return RetryDownloadDraft(url = request.url, route = request.route)
        }
    }
}

internal interface RetryDraftStore {
    fun save(historyId: Long, draft: RetryDownloadDraft): Result<Unit>

    fun load(historyId: Long): Result<RetryDownloadDraft>

    fun isAvailable(historyId: Long): Boolean

    fun delete(historyId: Long): Result<Boolean>
}

internal fun deleteHistoryWithRetryPayload(
    historyId: Long,
    retryStore: RetryDraftStore,
    deleteHistory: () -> Int,
): Result<Int> {
    return retryStore.delete(historyId).mapCatching { retryPayloadDeleted ->
        check(retryPayloadDeleted) { "重试信息不可删除。" }
        deleteHistory()
    }
}

internal class AesGcmRetryPayloadCipher(
    private val secretKey: SecretKey,
) : RetryPayloadCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        require(iv.size == IvSizeBytes) { "重试信息不可保存。" }
        val encrypted = cipher.doFinal(plaintext)
        return ByteArray(1 + iv.size + encrypted.size).also { envelope ->
            envelope[0] = EnvelopeVersion
            iv.copyInto(envelope, destinationOffset = 1)
            encrypted.copyInto(envelope, destinationOffset = 1 + iv.size)
        }
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size > 1 + IvSizeBytes && ciphertext[0] == EnvelopeVersion) {
            "重试信息不可用。"
        }
        val iv = ciphertext.copyOfRange(1, 1 + IvSizeBytes)
        val encrypted = ciphertext.copyOfRange(1 + IvSizeBytes, ciphertext.size)
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TagSizeBits, iv))
        return cipher.doFinal(encrypted)
    }

    private companion object {
        const val Transformation = "AES/GCM/NoPadding"
        const val IvSizeBytes = 12
        const val TagSizeBits = 128
        const val EnvelopeVersion: Byte = 1
    }
}

internal class AndroidKeyStoreRetryPayloadCipher : RetryPayloadCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        return AesGcmRetryPayloadCipher(secretKey()).encrypt(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        return AesGcmRetryPayloadCipher(secretKey()).decrypt(ciphertext)
    }

    private fun secretKey(): SecretKey = synchronized(KeyLock) {
        val keyStore = KeyStore.getInstance(KeyStoreProvider).apply { load(null) }
        (keyStore.getKey(KeyAlias, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KeyStoreProvider,
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KeyStoreProvider = "AndroidKeyStore"
        const val KeyAlias = "ytdl_retry_request_v1"
        val KeyLock = Any()
    }
}

internal class FileRetryDraftStore(
    private val rootDirectory: File,
    private val cipher: RetryPayloadCipher,
) : RetryDraftStore {
    override fun save(historyId: Long, draft: RetryDownloadDraft): Result<Unit> = runCatching {
        val target = fileFor(historyId)
        check(rootDirectory.mkdirs() || rootDirectory.isDirectory) { "重试信息不可保存。" }
        val encrypted = cipher.encrypt(encode(draft))
        check(encrypted.size <= MaxEncryptedBytes) { "重试信息不可保存。" }
        val temporary = File(rootDirectory, ".${historyId}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(encrypted)
                output.fd.sync()
            }
            if (target.exists()) check(target.delete()) { "重试信息不可保存。" }
            check(temporary.renameTo(target)) { "重试信息不可保存。" }
        } finally {
            temporary.delete()
        }
    }

    override fun load(historyId: Long): Result<RetryDownloadDraft> = runCatching {
        val source = fileFor(historyId)
        check(source.isFile && source.length() in 1..MaxEncryptedBytes.toLong()) {
            "重试信息不可用。"
        }
        decode(cipher.decrypt(source.readBytes()))
    }

    override fun isAvailable(historyId: Long): Boolean {
        return historyId > 0L && load(historyId).isSuccess
    }

    override fun delete(historyId: Long): Result<Boolean> = runCatching {
        val target = fileFor(historyId)
        !target.exists() || target.delete()
    }

    private fun fileFor(historyId: Long): File {
        require(historyId > 0L) { "重试信息不可用。" }
        return File(rootDirectory, "$historyId.bin")
    }

    private fun encode(draft: RetryDownloadDraft): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeInt(PayloadMagic)
            output.writeInt(PayloadVersion)
            output.writeString(draft.url)
            when (val route = draft.route) {
                is DownloadRoute.DirectSingleFile -> {
                    output.writeInt(RouteDirect)
                    output.writeString(route.formatId)
                }
                is DownloadRoute.VideoOnly -> {
                    output.writeInt(RouteVideoOnly)
                    output.writeString(route.videoFormatId)
                }
                is DownloadRoute.AudioOnly -> {
                    output.writeInt(RouteAudioOnly)
                    output.writeString(route.audioFormatId)
                }
                is DownloadRoute.MergeRequired -> {
                    output.writeInt(RouteMerge)
                    output.writeString(route.videoFormatId)
                    output.writeString(route.audioFormatId)
                }
            }
        }
        return buffer.toByteArray().also { payload ->
            require(payload.size <= MaxPayloadBytes) { "重试信息不可保存。" }
        }
    }

    private fun decode(payload: ByteArray): RetryDownloadDraft {
        require(payload.size in 1..MaxPayloadBytes) { "重试信息不可用。" }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readInt() == PayloadMagic && input.readInt() == PayloadVersion) {
                "重试信息不可用。"
            }
            val url = input.readString()
            val route = when (input.readInt()) {
                RouteDirect -> DownloadRoute.DirectSingleFile(input.readString())
                RouteVideoOnly -> DownloadRoute.VideoOnly(input.readString())
                RouteAudioOnly -> DownloadRoute.AudioOnly(input.readString())
                RouteMerge -> DownloadRoute.MergeRequired(input.readString(), input.readString())
                else -> throw IllegalArgumentException("重试信息不可用。")
            }
            require(input.available() == 0) { "重试信息不可用。" }
            RetryDownloadDraft(url = url, route = route)
        }
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MaxStringBytes) { "重试信息不可保存。" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        require(size in 0..MaxStringBytes && size <= available()) { "重试信息不可用。" }
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    companion object {
        fun fromContext(context: Context): FileRetryDraftStore {
            return FileRetryDraftStore(
                rootDirectory = File(context.noBackupFilesDir, RetryDirectoryName),
                cipher = AndroidKeyStoreRetryPayloadCipher(),
            )
        }

        private const val RetryDirectoryName = "retry-requests"
        private const val PayloadMagic = 0x5954444C
        private const val PayloadVersion = 2
        private const val RouteDirect = 1
        private const val RouteVideoOnly = 2
        private const val RouteAudioOnly = 3
        private const val RouteMerge = 4
        private const val MaxStringBytes = 256 * 1024
        private const val MaxPayloadBytes = 1024 * 1024
        private const val MaxEncryptedBytes = MaxPayloadBytes + 1024
    }
}
