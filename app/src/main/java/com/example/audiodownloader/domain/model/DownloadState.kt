package com.example.audiodownloader.domain.model

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Queued(val songTitle: String) : DownloadState
    data class Downloading(
        val songTitle: String,
        val progressPercent: Float,
        val speed: String,
        val fileName: String
    ) : DownloadState
    data class Converting(val songTitle: String) : DownloadState
    data class Completed(val songTitle: String, val filePath: String) : DownloadState
    data class Failed(val songTitle: String, val errorMessage: String) : DownloadState
}