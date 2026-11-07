package com.garyapp.ytdl.media

import java.io.File

internal class TransportStreamMediaProcessor(
    outputRoot: File,
    private val executable: File,
) : MediaProcessor by NativeMuxerMediaProcessor(outputRoot) {
    override fun remuxTransportStream(input: File, output: File, ensureActive: () -> Unit): Result<Unit> = runCatching {
        ensureActive()
        val process = ProcessBuilder(
            executable.absolutePath, "-nostdin", "-loglevel", "quiet", "-n",
            "-i", input.absolutePath, "-map", "0:v?", "-map", "0:a?",
            "-c", "copy", "-f", "mp4", output.absolutePath,
        ).redirectErrorStream(true).start()
        try {
            while (true) {
                ensureActive()
                val status = try {
                    process.exitValue()
                } catch (_: IllegalThreadStateException) {
                    Thread.sleep(100)
                    continue
                }
                check(status == 0 && output.length() > 0) { "MP4 封装失败。" }
                break
            }
        } finally {
            process.destroy()
            process.waitFor()
            process.inputStream.close()
            process.outputStream.close()
        }
    }
}
