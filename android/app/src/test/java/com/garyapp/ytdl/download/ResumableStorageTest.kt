package com.garyapp.ytdl.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumableStorageTest {
    @Test
    fun requestCanRetainOriginalExportTarget() {
        assertNotNull("恢复请求需要保留原导出目录", DownloadRequest::class.java.methods.firstOrNull { it.name == "getStorageTarget" })
    }

    @Test
    fun requestHasStableValidatedIdentityAndCookieUsageFlag() {
        val request = DownloadRequest("https://example.com/watch", "标题", DownloadRoute.DirectSingleFile("18"))
        val getter = request.javaClass.methods.firstOrNull { it.name == "getTaskId" }
        assertNotNull("DownloadRequest must carry a stable task identity", getter)
        val taskId = getter!!.invoke(request) as String
        assertTrue(taskId.matches(Regex("^[0-9a-f]{32}$")))
        assertEquals(taskId, getter.invoke(request.copy(title = "新标题")))
        assertTrue(runCatching { request.copy(taskId = "../unsafe") }.isFailure)
        val authenticated = request.copy(cookiesPath = "temporary/cookies.txt", usesCookies = true)
        assertTrue(authenticated.copy(cookiesPath = null).usesCookies)
    }
}
