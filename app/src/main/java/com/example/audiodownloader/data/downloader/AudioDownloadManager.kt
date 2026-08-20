package com.example.audiodownloader.data.downloader

import android.content.Context
import android.os.Environment
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.audiodownloader.domain.model.AudioQuality
import com.example.audiodownloader.domain.model.DownloadState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

interface DownloadProgressCallback {
    fun onProgress(status: String, percent: Double, speed: String, filename: String)
    fun onError(errorMessage: String)
}

class AudioDownloadManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    init {
        ensurePythonStarted()
    }

    private fun ensurePythonStarted() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
    }

    /**
     * Downloads an audio track or query via Chaquopy / yt-dlp.
     *
     * @param query Search query string (e.g., "Song Title Artist Name") or direct URL.
     * @param quality The target AudioQuality (LOW: 48kbps, NORMAL: 128kbps, HIGH: 256kbps).
     * @param customOutputDir Optional destination directory, defaults to public Downloads/Music.
     */
    suspend fun downloadAudio(
        query: String,
        quality: AudioQuality = AudioQuality.NORMAL,
        customOutputDir: String? = null
    ): Result<String> = withContext(ioDispatcher) {
        try {
            ensurePythonStarted()

            val targetDir = customOutputDir ?: File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Music"
            ).apply { mkdirs() }.absolutePath

            _downloadState.value = DownloadState.Queued(songTitle = query)

            val py = Python.getInstance()
            val downloaderModule = py.getModule("downloader")

            val callback = object : DownloadProgressCallback {
                override fun onProgress(status: String, percent: Double, speed: String, filename: String) {
                    when (status.lowercase()) {
                        "queued" -> {
                            _downloadState.value = DownloadState.Queued(songTitle = query)
                        }
                        "downloading" -> {
                            _downloadState.value = DownloadState.Downloading(
                                songTitle = query,
                                progressPercent = percent.toFloat(),
                                speed = speed,
                                fileName = filename
                            )
                        }
                        "converting" -> {
                            _downloadState.value = DownloadState.Converting(songTitle = query)
                        }
                        "completed" -> {
                            val savedPath = if (filename.isNotEmpty()) "$targetDir/$filename" else targetDir
                            _downloadState.value = DownloadState.Completed(
                                songTitle = query,
                                filePath = savedPath
                            )
                        }
                    }
                }

                override fun onError(errorMessage: String) {
                    _downloadState.value = DownloadState.Failed(
                        songTitle = query,
                        errorMessage = errorMessage
                    )
                }
            }

            // Execute Python download function with dynamic bitrate parameter ("48", "128", "256")
            val result: PyObject = downloaderModule.callAttr(
                "download_audio",
                query,
                targetDir,
                quality.bitrate,
                callback
            )

            val resultMap = result.asMap()
            val isSuccess = resultMap[py.getBuiltins().callAttr("str", "success")]?.toBoolean() ?: false

            if (isSuccess) {
                val title = resultMap[py.getBuiltins().callAttr("str", "title")]?.toString() ?: query
                Result.success(title)
            } else {
                val errorMsg = resultMap[py.getBuiltins().callAttr("str", "error")]?.toString()
                    ?: "Download failed in yt-dlp engine"
                _downloadState.value = DownloadState.Failed(songTitle = query, errorMessage = errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Audio download encountered an unexpected error"
            _downloadState.value = DownloadState.Failed(songTitle = query, errorMessage = err)
            Result.failure(e)
        }
    }

    /**
     * Sequentially downloads a queue of extracted song queries.
     */
    suspend fun downloadQueue(
        queries: List<String>,
        quality: AudioQuality = AudioQuality.NORMAL
    ): Map<String, Boolean> = withContext(ioDispatcher) {
        val results = mutableMapOf<String, Boolean>()
        for (song in queries) {
            val result = downloadAudio(song, quality)
            results[song] = result.isSuccess
        }
        _downloadState.value = DownloadState.Idle
        results
    }

    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }
}