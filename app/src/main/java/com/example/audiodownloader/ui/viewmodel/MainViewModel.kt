package com.example.audiodownloader.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiodownloader.data.downloader.AudioDownloadManager
import com.example.audiodownloader.data.repository.ISpotifyScraperRepository
import com.example.audiodownloader.data.repository.SpotifyScraperRepository
import com.example.audiodownloader.domain.model.AudioQuality
import com.example.audiodownloader.domain.model.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SpotifyExtractionUiState {
    data object Idle : SpotifyExtractionUiState
    data object Loading : SpotifyExtractionUiState
    data class Success(val tracks: List<String>) : SpotifyExtractionUiState
    data class Error(val message: String) : SpotifyExtractionUiState
}

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val spotifyRepository: ISpotifyScraperRepository = SpotifyScraperRepository(),
    private val downloadManager: AudioDownloadManager = AudioDownloadManager(application)
) : AndroidViewModel(application) {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _queryInput = MutableStateFlow("")
    val queryInput: StateFlow<String> = _queryInput.asStateFlow()

    private val _songChips = MutableStateFlow<List<String>>(emptyList())
    val songChips: StateFlow<List<String>> = _songChips.asStateFlow()

    private val _selectedQuality = MutableStateFlow(AudioQuality.NORMAL)
    val selectedQuality: StateFlow<AudioQuality> = _selectedQuality.asStateFlow()

    val downloadState: StateFlow<DownloadState> = downloadManager.downloadState
        .stateIn(viewModelScope, SharingStarted.Lazily, DownloadState.Idle)

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _spotifyUrlInput = MutableStateFlow("")
    val spotifyUrlInput: StateFlow<String> = _spotifyUrlInput.asStateFlow()

    private val _spotifyState = MutableStateFlow<SpotifyExtractionUiState>(SpotifyExtractionUiState.Idle)
    val spotifyState: StateFlow<SpotifyExtractionUiState> = _spotifyState.asStateFlow()

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun onQueryInputChange(newText: String) {
        _queryInput.value = newText
    }

    fun addCurrentQueryToQueue() {
        val text = _queryInput.value.trim()
        if (text.isNotEmpty()) {
            val lines = text.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            _songChips.value = _songChips.value + lines
            _queryInput.value = ""
        }
    }

    fun removeChip(index: Int) {
        val updated = _songChips.value.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            _songChips.value = updated
        }
    }

    fun clearQueue() {
        _songChips.value = emptyList()
        downloadManager.resetState()
    }

    fun onQualitySelected(quality: AudioQuality) {
        _selectedQuality.value = quality
    }

    fun startBatchDownload() {
        val itemsToDownload = _songChips.value
        if (itemsToDownload.isEmpty()) {
            Toast.makeText(getApplication(), "Queue is empty. Add songs first!", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _isDownloading.value = true
            downloadManager.downloadQueue(itemsToDownload, _selectedQuality.value)
            _isDownloading.value = false
            Toast.makeText(getApplication(), "All downloads finished!", Toast.LENGTH_SHORT).show()
        }
    }

    fun onSpotifyUrlChange(newUrl: String) {
        _spotifyUrlInput.value = newUrl
    }

    fun extractSpotifyPlaylist() {
        val url = _spotifyUrlInput.value.trim()
        if (url.isEmpty()) {
            _spotifyState.value = SpotifyExtractionUiState.Error("Please enter a Spotify playlist URL.")
            return
        }

        viewModelScope.launch {
            _spotifyState.value = SpotifyExtractionUiState.Loading
            val result = spotifyRepository.getPlaylistTrackQueries(url)
            result.fold(
                onSuccess = { tracks ->
                    _spotifyState.value = SpotifyExtractionUiState.Success(tracks)
                },
                onFailure = { error ->
                    _spotifyState.value = SpotifyExtractionUiState.Error(
                        error.localizedMessage ?: "Failed to extract Spotify playlist."
                    )
                }
            )
        }
    }

    fun sendExtractedToDownloader() {
        val state = _spotifyState.value
        if (state is SpotifyExtractionUiState.Success && state.tracks.isNotEmpty()) {
            _songChips.value = (_songChips.value + state.tracks).distinct()
            _selectedTab.value = 0
            Toast.makeText(
                getApplication(),
                "Added ${state.tracks.size} tracks to the queue!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun copyExtractedToClipboard() {
        val state = _spotifyState.value
        if (state is SpotifyExtractionUiState.Success && state.tracks.isNotEmpty()) {
            val textToCopy = state.tracks.joinToString("\n")
            val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Spotify Extracted Playlist", textToCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(getApplication(), "Copied ${state.tracks.size} tracks to clipboard!", Toast.LENGTH_SHORT).show()
        }
    }
}
