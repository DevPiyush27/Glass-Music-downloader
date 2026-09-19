package com.example.audiodownloader.service

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Immutable state representing current audio playback.
 */
data class PlayerState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtUri: Uri? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queueSize: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)

/**
 * Playback actions fired from the RemoteViews notification buttons.
 */
enum class MusicAction {
    PLAY_PAUSE,
    PREVIOUS,
    REWIND,
    FAST_FORWARD,
    NEXT,
    QUEUE
}

/**
 * Reactive bridge ensuring two-way synchronization between ViewModel/Compose and MusicService.
 */
object MusicStateBridge {
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // Action listener invoked when notification buttons are pressed
    var onActionReceived: ((MusicAction) -> Unit)? = null

    fun updateState(newState: PlayerState) {
        _playerState.value = newState
    }

    fun dispatchAction(action: MusicAction) {
        onActionReceived?.invoke(action)
    }
}
