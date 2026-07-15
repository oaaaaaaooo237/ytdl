package com.garyapp.ytdl.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryHistoryPersistenceTest {
    @Test
    fun failedHistoryStoresExactRequestWithoutCookiesAfterInsertSucceeds() {
        val request = DownloadRequest(
            url = "https://example.com/private",
            title = "failed",
            route = DownloadRoute.DirectSingleFile("av1-1080"),
            cookiesPath = "/private/stale-cookies.txt",
        )
        val store = RecordingRetryDraftStore()

        val result = updateRetryDraftForHistory(
            state = DownloadTaskState(
                stage = DownloadStage.Failed,
                request = request,
                errorMessage = "offline",
            ),
            historyRecordResult = Result.success(71L),
            retryStore = store,
        )

        assertTrue(result.isSuccess)
        assertEquals(71L, store.savedHistoryId)
        assertEquals(RetryDownloadDraft(request.url, request.route), store.savedDraft)
        assertNull(store.deletedHistoryId)
    }

    @Test
    fun canceledHistoryAlsoStoresRetryDraftWithoutCookies() {
        val request = DownloadRequest(
            url = "https://example.com/canceled",
            title = "canceled",
            route = DownloadRoute.VideoOnly("137"),
            cookiesPath = "/private/stale-cookies.txt",
        )
        val store = RecordingRetryDraftStore()

        val result = updateRetryDraftForHistory(
            state = DownloadTaskState(stage = DownloadStage.Canceled, request = request),
            historyRecordResult = Result.success(72L),
            retryStore = store,
        )

        assertTrue(result.isSuccess)
        assertEquals(72L, store.savedHistoryId)
        assertEquals(RetryDownloadDraft(request.url, request.route), store.savedDraft)
        assertNull(store.deletedHistoryId)
    }

    @Test
    fun completedOrUnrecordedHistoryNeverCreatesRetryPayload() {
        val request = DownloadRequest(
            url = "https://example.com/completed",
            title = "completed",
            route = DownloadRoute.AudioOnly("140"),
        )
        val store = RecordingRetryDraftStore()
        assertTrue(
            updateRetryDraftForHistory(
                state = DownloadTaskState(stage = DownloadStage.Completed, request = request),
                historyRecordResult = Result.success(1L),
                retryStore = store,
            ).isSuccess,
        )
        assertNull(store.savedDraft)
        assertEquals(1L, store.deletedHistoryId)

        val failedInsert = RecordingRetryDraftStore()
        assertTrue(
            updateRetryDraftForHistory(
                state = DownloadTaskState(
                    stage = DownloadStage.Failed,
                    request = request,
                    errorMessage = "offline",
                ),
                historyRecordResult = Result.failure(IllegalStateException("database unavailable")),
                retryStore = failedInsert,
            ).isSuccess,
        )
        assertNull(failedInsert.savedDraft)
        assertNull(failedInsert.deletedHistoryId)
    }

    @Test
    fun historyDeletionStopsWhenRetryPayloadCannotBeRemoved() {
        var historyDeleteCalls = 0
        val failingStore = RecordingRetryDraftStore().apply {
            deleteResult = Result.failure(IllegalStateException("filesystem unavailable"))
        }

        val failed = deleteHistoryWithRetryPayload(
            historyId = 91L,
            retryStore = failingStore,
            deleteHistory = {
                historyDeleteCalls += 1
                1
            },
        )

        assertTrue(failed.isFailure)
        assertEquals(0, historyDeleteCalls)

        val deleted = deleteHistoryWithRetryPayload(
            historyId = 92L,
            retryStore = RecordingRetryDraftStore(),
            deleteHistory = {
                historyDeleteCalls += 1
                1
            },
        )
        assertEquals(1, deleted.getOrThrow())
        assertEquals(1, historyDeleteCalls)
    }

    private class RecordingRetryDraftStore : RetryDraftStore {
        var savedHistoryId: Long? = null
        var savedDraft: RetryDownloadDraft? = null
        var deletedHistoryId: Long? = null
        var deleteResult: Result<Boolean> = Result.success(true)

        override fun save(historyId: Long, draft: RetryDownloadDraft): Result<Unit> {
            savedHistoryId = historyId
            savedDraft = draft
            return Result.success(Unit)
        }

        override fun load(historyId: Long): Result<RetryDownloadDraft> {
            return Result.failure(IllegalStateException("unused"))
        }

        override fun isAvailable(historyId: Long): Boolean = false

        override fun delete(historyId: Long): Result<Boolean> {
            deletedHistoryId = historyId
            return deleteResult
        }
    }
}
