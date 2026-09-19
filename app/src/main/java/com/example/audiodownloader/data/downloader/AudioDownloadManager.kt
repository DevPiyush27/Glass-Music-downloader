package com.example.audiodownloader.data.downloader

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
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

    suspend fun downloadAudio(
        query: String,
        quality: AudioQuality = AudioQuality.NORMAL,
        customOutputDir: String? = null
    ): Result<String> = withContext(ioDispatcher) {
        try {
            ensurePythonStarted()

            // 1. Target directory: app's internal cache directory for Python download & transcoding
            val cacheDirectory = context.cacheDir
            if (!cacheDirectory.exists()) {
                cacheDirectory.mkdirs()
            }
            val targetDir = customOutputDir ?: cacheDirectory.absolutePath

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
                        "completed" -> {
                            // Python has completed downloading/transcoding to cache.
                            // State transitions to Converting while copying to SAF.
                            _downloadState.value = DownloadState.Converting(songTitle = query)
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

            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val ffmpegBinary = File(nativeLibDir, "libffmpeg.so").takeIf { it.exists() }?.absolutePath
            val ffmpegLibDir = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")
                .takeIf { it.exists() }?.absolutePath

            // Execute Python download & transcoding inside cacheDir
            val result: PyObject = downloaderModule.callAttr(
                "download_audio",
                query,
                targetDir,
                quality.bitrate,
                callback,
                ffmpegBinary,
                ffmpegLibDir
            )

            val resultMap = result.asMap()
            val isSuccess = resultMap[py.getBuiltins().callAttr("str", "success")]?.toBoolean() ?: false

            if (isSuccess) {
                val title = resultMap[py.getBuiltins().callAttr("str", "title")]?.toString() ?: query
                val finalFile = resultMap[py.getBuiltins().callAttr("str", "filename")]?.toString() ?: ""

                val cachedFile = if (File(finalFile).isAbsolute) {
                    File(finalFile)
                } else {
                    File(targetDir, finalFile)
                }

                var finalDestinationPathOrUri = cachedFile.absolutePath

                if (cachedFile.exists()) {
                    // 2. SAF COPY PIPELINE: Securely copy from cache to user's selected SAF folder
                    val safPrefs = context.getSharedPreferences("audio_aurora_saf_prefs", Context.MODE_PRIVATE)
                    val savedSafUriString = safPrefs.getString("music_folder_tree_uri", null)

                    if (!savedSafUriString.isNullOrBlank()) {
                        val treeUri = Uri.parse(savedSafUriString)
                        val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                        val mimeType = if (cachedFile.name.endsWith(".flac", ignoreCase = true)) {
                            "audio/flac"
                        } else {
                            "audio/mpeg"
                        }

                        val targetFile = pickedDir?.createFile(mimeType, cachedFile.name)
                            ?: throw IllegalStateException("Failed to create SAF document for: ${cachedFile.name}")

                        // 3. STREAM THE BYTES
                        cachedFile.inputStream().use { inputStream ->
                            context.contentResolver.openOutputStream(targetFile.uri)?.use { outputStream ->
                                inputStream.copyTo(outputStream)
                            } ?: throw IllegalStateException("Failed to open OutputStream for SAF URI: ${targetFile.uri}")
                        }

                        finalDestinationPathOrUri = targetFile.uri.toString()

                        // 5. MediaStore scan for system indexers
                        try {
                            MediaScannerConnection.scanFile(
                                context.applicationContext,
                                arrayOf(targetFile.uri.toString()),
                                arrayOf(mimeType),
                                null
                            )
                        } catch (_: Exception) {}
                    } else {
                        // Fallback: Copy to public Download/Music if SAF tree URI is not yet configured
                        val publicDir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Music"
                        )
                        if (publicDir.exists() || publicDir.mkdirs()) {
                            val destinationFile = File(publicDir, cachedFile.name)
                            cachedFile.inputStream().use { input ->
                                destinationFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            finalDestinationPathOrUri = destinationFile.absolutePath
                            try {
                                val mimeType = if (destinationFile.name.endsWith(".flac", ignoreCase = true)) "audio/flac" else "audio/*"
                                MediaScannerConnection.scanFile(
                                    context.applicationContext,
                                    arrayOf(destinationFile.absolutePath),
                                    arrayOf(mimeType),
                                    null
                                )
                            } catch (_: Exception) {}
                        }
                    }

                    // 4. CLEANUP: Delete cached file to prevent internal storage bloating
                    cachedFile.delete()
                }

                _downloadState.value = DownloadState.Completed(
                    songTitle = query,
                    filePath = finalDestinationPathOrUri
                )

                Result.success(title)
            } else {
                val errorMsg = resultMap[py.getBuiltins().callAttr("str", "error")]?.toString()
                    ?: "yt-dlp download failed"
                _downloadState.value = DownloadState.Failed(songTitle = query, errorMessage = errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Audio download encountered an error"
            _downloadState.value = DownloadState.Failed(songTitle = query, errorMessage = err)
            Result.failure(e)
        }
    }

    suspend fun extractStreamInfo(query: String): Result<StreamInfo> = withContext(ioDispatcher) {
        try {
            ensurePythonStarted()
            val py = Python.getInstance()
            val downloaderModule = py.getModule("downloader")
            val result: PyObject = downloaderModule.callAttr("extract_stream_url", query)
            val resultMap = result.asMap()
            val isSuccess = resultMap[py.getBuiltins().callAttr("str", "success")]?.toBoolean() ?: false

            if (isSuccess) {
                val url = resultMap[py.getBuiltins().callAttr("str", "url")]?.toString() ?: ""
                val title = resultMap[py.getBuiltins().callAttr("str", "title")]?.toString() ?: query
                val artist = resultMap[py.getBuiltins().callAttr("str", "artist")]?.toString() ?: ""
                val duration = resultMap[py.getBuiltins().callAttr("str", "duration")]?.toLong() ?: 0L
                val thumb = resultMap[py.getBuiltins().callAttr("str", "thumbnail")]?.toString() ?: ""

                Result.success(
                    StreamInfo(
                        url = url,
                        title = title,
                        artist = artist,
                        durationSeconds = duration,
                        thumbnailUrl = thumb
                    )
                )
            } else {
                val errorMsg = resultMap[py.getBuiltins().callAttr("str", "error")]?.toString()
                    ?: "Failed to extract stream"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadQueue(
        queries: List<String>,
        quality: AudioQuality = AudioQuality.NORMAL
    ): Map<String, Boolean> = withContext(ioDispatcher) {
        val results = mutableMapOf<String, Boolean>()
        for (song in queries) {
            val result = downloadAudio(song, quality)
            results[song] = result.isSuccess
        }
        results
    }

    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }
}

data class StreamInfo(
    val url: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val thumbnailUrl: String
)

