package com.garyapp.ytdl.cookies

import android.content.Context
import android.net.Uri
import com.garyapp.ytdl.core.settings.CookieSites
import com.garyapp.ytdl.core.settings.CookiesReference
import com.garyapp.ytdl.download.AndroidKeyStoreRetryPayloadCipher
import com.garyapp.ytdl.download.RetryPayloadCipher
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal class SiteLoginStore(
    private val directory: File,
    private val authority: String,
    private val cipher: RetryPayloadCipher,
) {
    fun save(domain: String, cookieHeader: String): CookiesReference {
        val host = CookieSites.normalizeDomain(domain) ?: error("网站域名无效。")
        val data = netscapeCookies(host, cookieHeader)
        check(directory.mkdirs() || directory.isDirectory)
        val id = domainKey(host) + "-" + UUID.randomUUID().toString().replace("-", "")
        val file = File(directory, "$id.bin")
        try {
            file.writeBytes(cipher.encrypt(data.toByteArray(Charsets.UTF_8)))
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        return CookiesReference.fromUserReference("content://$authority/$id", "App 内登录")!!
    }

    fun owns(value: String): Boolean = Uri.parse(value).authority == authority

    fun read(value: String): ByteArray {
        val uri = Uri.parse(value)
        require(uri.scheme == "content" && uri.authority == authority)
        val id = uri.path?.removePrefix("/").orEmpty()
        require(id.matches(Regex("[a-f0-9]{64}-[a-f0-9]{32}")))
        val file = File(directory, "$id.bin")
        require(file.length() in 1..1024 * 1024L)
        return cipher.decrypt(file.readBytes())
    }

    fun clear(domain: String) {
        val host = CookieSites.normalizeDomain(domain) ?: return
        val prefix = domainKey(host) + "-"
        directory.listFiles()?.filter { it.name.startsWith(prefix) && it.extension == "bin" }?.forEach {
            check(it.delete() || !it.exists()) { "登录状态清理失败，请重试。" }
        }
    }

    private fun domainKey(domain: String) = MessageDigest.getInstance("SHA-256")
        .digest(domain.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        fun fromContext(context: Context) = SiteLoginStore(
            File(context.noBackupFilesDir, "site-logins"), context.packageName + ".sitecookies",
            AndroidKeyStoreRetryPayloadCipher(),
        )

        fun netscapeCookies(domain: String, header: String): String {
            require(CookieSites.normalizeDomain(domain) == domain)
            require(!header.contains('\n') && !header.contains('\r') && !header.contains('\t'))
            val pairs = header.split(';').mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null else part.substring(0, index).trim() to part.substring(index + 1).trim()
            }.distinctBy { it.first }
            require(pairs.isNotEmpty()) { "该网站尚无登录 cookies，请先登录后再保存。" }
            require(pairs.all { it.first.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) })
            return "# Netscape HTTP Cookie File\n" + pairs.joinToString("\n", postfix = "\n") {
                ".$domain\tTRUE\t/\tTRUE\t0\t${it.first}\t${it.second}"
            }
        }
    }
}
