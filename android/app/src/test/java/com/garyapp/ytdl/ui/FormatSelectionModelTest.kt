package com.garyapp.ytdl.ui

import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatSelectionModelTest {
    @Test
    fun progressive360pIsDirectSelectableVideoAndAudioRow() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                progressiveFormat(id = "18", height = 360),
            ),
            selection = FormatSelection(mode = FormatMode.VideoAndAudio, selectedHeight = 360),
        )

        val row = rows.single { it.height == 360 }
        assertEquals("360p", row.label)
        assertTrue(row.selectable)
        assertTrue(row.selected)
        assertTrue(row.direct)
        assertFalse(row.mergeRequired)
        assertEquals("18", row.videoFormatId)
        assertEquals(null, row.audioFormatId)
    }

    @Test
    fun videoOnly1080pWithStandaloneAudioIsSelectableAndMergeRequired() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                videoOnlyFormat(id = "137", height = 1080),
                audioOnlyFormat(id = "140"),
            ),
            selection = FormatSelection(mode = FormatMode.VideoAndAudio, selectedHeight = 1080),
        )

        val row = rows.single { it.height == 1080 }
        assertEquals("1080p", row.label)
        assertTrue(row.selectable)
        assertTrue(row.selected)
        assertFalse(row.direct)
        assertTrue(row.mergeRequired)
        assertEquals("137", row.videoFormatId)
        assertEquals("140", row.audioFormatId)
        assertTrue(row.summary.contains("需原生合并"))
    }

    @Test
    fun mergeRequiredVideoAndAudioPrefersMp4CompatibleAudioOverLargerWebmAudio() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                videoOnlyFormat(id = "137", height = 1080),
                audioOnlyFormat(id = "140", filesizeBytes = 1_000_000),
                audioOnlyFormat(id = "251", ext = "webm", audioCodec = "opus", filesizeBytes = 2_000_000),
            ),
            selection = FormatSelection(mode = FormatMode.VideoAndAudio, selectedHeight = 1080),
        )

        val row = rows.single { it.height == 1080 }
        assertTrue(row.selectable)
        assertTrue(row.mergeRequired)
        assertEquals("137", row.videoFormatId)
        assertEquals("140", row.audioFormatId)
        assertTrue(row.summary.contains("MP4"))
    }

    @Test
    fun automaticVideoAndAudioSelectionPrefersNativeMuxerCompatibleVideoOverHigherWebmVideo() {
        val selection = defaultFormatSelection(
            analysisWith(
                videoOnlyFormat(id = "248", height = 1440, ext = "webm", videoCodec = "vp9", filesizeBytes = 2_000_000),
                videoOnlyFormat(id = "137", height = 1080, ext = "mp4", videoCodec = "avc1", filesizeBytes = 1_000_000),
                audioOnlyFormat(id = "140", filesizeBytes = 500_000),
            ),
        )

        assertNull(selection.selectedHeight)
        assertEquals("137", selection.selectedVideoFormatId)
        assertEquals("140", selection.selectedAudioFormatId)
        assertTrue(selection.mergeRequired)
    }

    @Test
    fun mergeRequiredWebmOnlyResolutionExplainsNativeMp4MergeLimit() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                videoOnlyFormat(id = "248", height = 1440, ext = "webm", videoCodec = "vp9"),
                audioOnlyFormat(id = "140"),
            ),
            selection = FormatSelection(mode = FormatMode.VideoAndAudio, selectedHeight = 1440),
        )

        val row = rows.single { it.height == 1440 }
        assertFalse(row.selectable)
        assertEquals("当前视频未提供可原生合并的 MP4 格式", row.reason)
    }

    @Test
    fun verticalShortsNativeMergeHeightAppearsAsSelectableResolutionRow() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                videoOnlyFormat(id = "137", height = 1920),
                audioOnlyFormat(id = "140"),
            ),
            selection = FormatSelection(mode = FormatMode.VideoAndAudio, selectedHeight = 1920),
        )

        val row = rows.single { it.height == 1920 }
        assertEquals("1920p", row.label)
        assertTrue(row.selectable)
        assertTrue(row.selected)
        assertEquals("137", row.videoFormatId)
        assertEquals("140", row.audioFormatId)
    }

    @Test
    fun missingResolutionIsDisabledWithVisibleReason() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                progressiveFormat(id = "18", height = 360),
            ),
            selection = FormatSelection(mode = FormatMode.VideoAndAudio, selectedHeight = 480),
        )

        val row = rows.single { it.height == 480 }
        assertEquals("480p", row.label)
        assertFalse(row.selectable)
        assertFalse(row.selected)
        assertEquals("当前视频未提供", row.reason)
    }

    @Test
    fun videoOnlyModeSelectsVideoStreamWithoutMerge() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                progressiveFormat(id = "18", height = 360),
                videoOnlyFormat(id = "137", height = 1080),
            ),
            selection = FormatSelection(mode = FormatMode.VideoOnly, selectedHeight = 1080),
        )

        val row = rows.single { it.height == 1080 }
        assertTrue(row.selectable)
        assertTrue(row.selected)
        assertTrue(row.direct)
        assertFalse(row.mergeRequired)
        assertEquals("137", row.videoFormatId)
        assertEquals(null, row.audioFormatId)
        assertTrue(row.summary.contains("1080p"))
        assertTrue(row.summary.contains("单文件"))
    }

    @Test
    fun videoOnlyModeDoesNotSelectProgressiveFormatAtSameHeight() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                progressiveFormat(id = "18", height = 1080),
                videoOnlyFormat(id = "137", height = 1080),
            ),
            selection = FormatSelection(mode = FormatMode.VideoOnly, selectedHeight = 1080),
        )

        val row = rows.single { it.height == 1080 }
        assertTrue(row.selectable)
        assertTrue(row.selected)
        assertEquals("137", row.videoFormatId)
        assertNull(row.audioFormatId)
    }

    @Test
    fun videoOnlyModeDisablesProgressiveRowWhenNoStandaloneVideoExists() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                progressiveFormat(id = "18", height = 360),
            ),
            selection = FormatSelection(mode = FormatMode.VideoOnly, selectedHeight = 360),
        )

        val row = rows.single { it.height == 360 }
        assertFalse(row.selectable)
        assertFalse(row.selected)
        assertNull(row.videoFormatId)
        assertNull(row.audioFormatId)
        assertTrue(row.reason.orEmpty().contains("独立视频"))
    }

    @Test
    fun audioOnlyAutoRowSelectsBestStandaloneAudioWithoutResolution() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                progressiveFormat(id = "18", height = 360),
                audioOnlyFormat(id = "139", filesizeBytes = 1024),
                audioOnlyFormat(id = "140", filesizeBytes = 4096),
            ),
            selection = FormatSelection(mode = FormatMode.AudioOnly, selectedHeight = null),
        )

        val row = rows.single { it.height == null }
        assertEquals("自动（推荐）", row.label)
        assertTrue(row.selectable)
        assertTrue(row.selected)
        assertTrue(row.direct)
        assertFalse(row.mergeRequired)
        assertNull(row.videoFormatId)
        assertEquals("140", row.audioFormatId)
        assertTrue(row.summary.contains("音频"))
    }

    @Test
    fun audioOnlyResolutionRowsAreUnsupportedAndGreyedOut() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                progressiveFormat(id = "18", height = 360),
                audioOnlyFormat(id = "140", filesizeBytes = 4096),
            ),
            selection = FormatSelection(mode = FormatMode.AudioOnly, selectedHeight = 360),
        )

        val row = rows.single { it.height == 360 }
        assertFalse(row.selectable)
        assertFalse(row.selected)
        assertNull(row.videoFormatId)
        assertNull(row.audioFormatId)
        assertEquals("仅音频不使用分辨率", row.reason)
    }

    private fun analysisWith(vararg formats: VideoFormat) = VideoAnalysis(
        title = "测试视频",
        durationSeconds = 60,
        thumbnailUrl = null,
        formats = formats.toList(),
        subtitles = emptyList<SubtitleInfo>(),
    )

    private fun progressiveFormat(id: String, height: Int) = VideoFormat(
        id = id,
        ext = "mp4",
        height = height,
        label = "${height}p",
        hasVideo = true,
        hasAudio = true,
        mergeRequired = false,
        isSupported = true,
        videoCodec = "avc1",
        audioCodec = "mp4a",
    )

    private fun videoOnlyFormat(
        id: String,
        height: Int,
        ext: String = "mp4",
        videoCodec: String = "avc1",
        filesizeBytes: Long? = null,
    ) = VideoFormat(
        id = id,
        ext = ext,
        height = height,
        label = "${height}p 需合并音频",
        hasVideo = true,
        hasAudio = false,
        mergeRequired = true,
        isSupported = true,
        filesizeBytes = filesizeBytes,
        videoCodec = videoCodec,
        audioCodec = "none",
    )

    private fun audioOnlyFormat(
        id: String,
        ext: String = "m4a",
        audioCodec: String = "mp4a",
        filesizeBytes: Long? = null,
    ) = VideoFormat(
        id = id,
        ext = ext,
        height = null,
        label = "音频",
        hasVideo = false,
        hasAudio = true,
        mergeRequired = false,
        isSupported = false,
        videoCodec = "none",
        audioCodec = audioCodec,
        filesizeBytes = filesizeBytes,
    )
}
