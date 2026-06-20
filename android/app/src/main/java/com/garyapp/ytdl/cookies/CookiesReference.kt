package com.garyapp.ytdl.cookies

class CookiesReference private constructor(
    val value: String,
    val displayName: String?,
) {
    override fun toString(): String {
        return "CookiesReference(displayName=${displayName ?: "cookies 文件"}, kind=${referenceKind(value)})"
    }

    companion object {
        fun fromUserSelectedReference(
            value: String,
            displayName: String? = null,
        ): Result<CookiesReference> {
            return runCatching {
                val settingsReference = com.garyapp.ytdl.core.settings.CookiesReference
                    .fromUserReference(value, displayName)
                    ?: throw IllegalArgumentException("cookies 只保存文件 URI 或路径引用，请重新选择 cookies.txt 文件。")

                CookiesReference(
                    value = settingsReference.value,
                    displayName = settingsReference.displayName,
                )
            }
        }

        fun fromSettings(
            reference: com.garyapp.ytdl.core.settings.CookiesReference?,
        ): CookiesReference? {
            reference ?: return null
            return CookiesReference(
                value = reference.value,
                displayName = reference.displayName,
            )
        }

        private fun referenceKind(reference: String): String {
            return when {
                reference.startsWith("content://", ignoreCase = true) -> "content-uri"
                reference.startsWith("file://", ignoreCase = true) -> "file-uri"
                else -> "path"
            }
        }
    }
}
