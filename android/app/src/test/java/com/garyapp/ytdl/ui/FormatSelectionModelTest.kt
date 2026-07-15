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
    fun singleFileMediaDoesNotCreateVideoAndAudioRows() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                progressiveFormat(id = "18", height = 360),
            ),
            selection = FormatSelection(mode = FormatMode.VideoAndAudio, selectedHeight = 360),
        )

        assertTrue(rows.isEmpty())
        assertFalse(isFormatModeAvailable(analysisWith(progressiveFormat(id = "18", height = 360)), FormatMode.VideoAndAudio))
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
    fun videoAndAudioRequiresKnownNativeCompatibleCodecs() {
        val unknownVideoCodec = analysisWith(
            videoOnlyFormat(id = "video", height = 1080, videoCodec = ""),
            audioOnlyFormat(id = "audio"),
        )
        val unknownAudioCodec = analysisWith(
            videoOnlyFormat(id = "video", height = 1080),
            audioOnlyFormat(id = "audio", audioCodec = ""),
        )

        assertFalse(isFormatModeAvailable(unknownVideoCodec, FormatMode.VideoAndAudio))
        assertFalse(isFormatModeAvailable(unknownAudioCodec, FormatMode.VideoAndAudio))
        assertTrue(buildFormatResolutionRows(unknownVideoCodec, FormatSelection(mode = FormatMode.VideoAndAudio)).isEmpty())
        assertTrue(buildFormatResolutionRows(unknownAudioCodec, FormatSelection(mode = FormatMode.VideoAndAudio)).isEmpty())
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

        assertTrue(rows.isEmpty())
    }

    @Test
    fun nativeMergeResolutionUsesRealHeightInsteadOfWidth() {
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
        assertEquals("137", row.videoFormatId)
        assertEquals("140", row.audioFormatId)
    }

    @Test
    fun missingResolutionIsHiddenInsteadOfShownAsUnavailable() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                progressiveFormat(id = "18", height = 360),
            ),
            selection = FormatSelection(mode = FormatMode.VideoOnly, selectedHeight = 480),
        )

        assertEquals(listOf(null, 360), rows.map { it.height })
        assertFalse(rows.any { it.height == 480 })
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
    fun videoDownloadGroupsOneRowPerResolutionAndDefaultsToCompatibleCodec() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                progressiveFormat(id = "av1-1080", height = 1080, videoCodec = "av01"),
                progressiveFormat(id = "h264-1080", height = 1080, videoCodec = "avc1"),
            ),
            selection = FormatSelection(
                mode = FormatMode.VideoOnly,
                selectedHeight = 1080,
            ),
        )

        val row = rows.single { it.height == 1080 }
        assertEquals("h264-1080", row.videoFormatId)
        assertEquals("H.264", row.selectedCodecLabel)
        assertEquals(listOf("H.264", "AV1"), row.codecOptions.map { it.label })
        assertEquals(listOf(true, false), row.codecOptions.map { it.selected })
    }

    @Test
    fun videoDownloadKeepsExplicitCodecAndExactFormatId() {
        val analysis = analysisWith(
            progressiveFormat(id = "h264-1080", height = 1080, videoCodec = "avc1"),
            progressiveFormat(id = "av1-1080", height = 1080, videoCodec = "av01"),
        )
        val row = buildFormatResolutionRows(
            analysis = analysis,
            selection = FormatSelection(
                mode = FormatMode.VideoOnly,
                selectedHeight = 1080,
                selectedVideoFormatId = "av1-1080",
            ),
        ).single { it.height == 1080 }

        assertEquals("av1-1080", row.videoFormatId)
        assertEquals("AV1", row.selectedCodecLabel)
        assertEquals(listOf(false, true), row.codecOptions.map { it.selected })
        assertEquals("av1-1080", selectionFromRow(FormatMode.VideoOnly, row).selectedVideoFormatId)
    }

    @Test
    fun videoDownloadModeSelectsSingleFileMediaDirectly() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                progressiveFormat(id = "18", height = 360),
            ),
            selection = FormatSelection(mode = FormatMode.VideoOnly, selectedHeight = 360),
        )

        val row = rows.single { it.height == 360 }
        assertTrue(row.selectable)
        assertTrue(row.selected)
        assertTrue(row.direct)
        assertEquals("18", row.videoFormatId)
        assertNull(row.audioFormatId)
        assertEquals("360p MP4 H.264 单文件", row.summary)
    }

    @Test
    fun unknownCodecSingleFileDefaultsToVideoDownloadMode() {
        val analysis = analysisWith(unknownSingleFileFormat(id = "single-file", height = 1080))

        val selection = defaultFormatSelection(analysis)

        assertEquals("视频", FormatMode.VideoOnly.label)
        assertEquals("音频", FormatMode.AudioOnly.label)
        assertEquals(FormatMode.VideoOnly, selection.mode)
        assertEquals("single-file", selection.selectedVideoFormatId)
        assertNull(selection.selectedAudioFormatId)
        assertFalse(selection.mergeRequired)
        assertFalse(isFormatModeAvailable(analysis, FormatMode.VideoAndAudio))
        assertTrue(isFormatModeAvailable(analysis, FormatMode.VideoOnly))
    }

    @Test
    fun audioOnlyFormatsDoNotAppearInVideoDownloadRows() {
        val analysis = analysisWith(audioOnlyFormat(id = "140"))

        val rows = buildFormatResolutionRows(
            analysis = analysis,
            selection = FormatSelection(mode = FormatMode.VideoOnly),
        )

        assertTrue(rows.isEmpty())
        assertFalse(isFormatModeAvailable(analysis, FormatMode.VideoOnly))
    }

    @Test
    fun videoDownloadGroupsDifferentCodecsWithoutShowingInternalIds() {
        val analysis = analysisWith(
            progressiveFormat(id = "progressive-1080", height = 1080, videoCodec = "avc1"),
            unknownSingleFileFormat(id = "unknown-1080", height = 1080),
            videoOnlyFormat(id = "vp9-1080", height = 1080, ext = "webm", videoCodec = "vp9"),
            audioOnlyFormat(id = "140"),
        )

        val rows = buildFormatResolutionRows(
            analysis = analysis,
            selection = FormatSelection(mode = FormatMode.VideoOnly),
        ).filter { it.height != null }

        val row = rows.single()
        assertEquals("1080p", row.label)
        assertEquals(listOf("H.264", "VP9", "其他"), row.codecOptions.map { it.label })
        assertTrue(row.codecOptions.none { it.label.contains("1080") || it.label.contains("140") })
    }

    @Test
    fun noAnalysisUsesOneEmptyStateInsteadOfUnavailableFormatRows() {
        val rows = buildFormatResolutionRows(
            analysis = null,
            selection = FormatSelection(mode = FormatMode.VideoOnly),
        )

        assertTrue(rows.isEmpty())
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
    fun audioOnlyModeHidesResolutionRows() {
        val rows = buildFormatResolutionRows(
            analysis = analysisWith(
                progressiveFormat(id = "18", height = 360),
                audioOnlyFormat(id = "140", filesizeBytes = 4096),
            ),
            selection = FormatSelection(mode = FormatMode.AudioOnly, selectedHeight = 360),
        )

        assertEquals(listOf(null), rows.map { it.height })
        assertFalse(rows.any { it.height == 360 })
    }

    private fun analysisWith(vararg formats: VideoFormat) = VideoAnalysis(
        title = "测试视频",
        durationSeconds = 60,
        thumbnailUrl = null,
        formats = formats.toList(),
        subtitles = emptyList<SubtitleInfo>(),
    )

    private fun progressiveFormat(
        id: String,
        height: Int,
        videoCodec: String = "avc1",
    ) = VideoFormat(
        id = id,
        ext = "mp4",
        height = height,
        label = "${height}p",
        hasVideo = true,
        hasAudio = true,
        mergeRequired = false,
        isSupported = true,
        videoCodec = videoCodec,
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

    private fun unknownSingleFileFormat(id: String, height: Int) = VideoFormat(
        id = id,
        ext = "mp4",
        height = height,
        label = "${height}p",
        hasVideo = true,
        hasAudio = true,
        mergeRequired = false,
        isSupported = true,
        videoCodec = null,
        audioCodec = null,
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
