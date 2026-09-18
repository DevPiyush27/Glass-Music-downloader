package com.example.audiodownloader.ui.viewmodel

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.audiodownloader.domain.model.FolderPlaylist
import com.example.audiodownloader.domain.model.LocalTrack
import android.media.MediaScannerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel backing the local music player tab.
 *
 * Responsibilities:
 *  - Query [MediaStore] for all music files on the device.
 *  - Manage physical folder-based playlists in the shared Music directory.
 *  - Own the [ExoPlayer] instance and expose playback state.
 *  - Provide playback controls for both library tracks and folder playlists.
 */
class LocalPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val player: ExoPlayer = ExoPlayer.Builder(application)
        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        .build()

    // 0 = Device Library, 1 = Playlists
    private val _currentPlaylistTab = MutableStateFlow(0)
    val currentPlaylistTab: StateFlow<Int> = _currentPlaylistTab.asStateFlow()

    private val _tracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val tracks: StateFlow<List<LocalTrack>> = _tracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<FolderPlaylist>>(emptyList())
    val playlists: StateFlow<List<FolderPlaylist>> = _playlists.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<FolderPlaylist?>(null)
    val selectedPlaylist: StateFlow<FolderPlaylist?> = _selectedPlaylist.asStateFlow()

    private val _newPlaylistName = MutableStateFlow("")
    val newPlaylistName: StateFlow<String> = _newPlaylistName.asStateFlow()

    private val _activePlaybackTracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val activePlaybackTracks: StateFlow<List<LocalTrack>> = _activePlaybackTracks.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(-1)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var playlistSynced = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentTrackIndex.value = player.currentMediaItemIndex
            _positionMs.value = 0L
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _durationMs.value = player.duration.coerceAtLeast(0L)
            }
        }
    }

    init {
        player.addListener(playerListener)
        viewModelScope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    _positionMs.value = player.currentPosition.coerceAtLeast(0L)
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    fun setPlaylistTab(tab: Int) {
        _currentPlaylistTab.value = tab
    }

    fun onNewPlaylistNameChange(name: String) {
        _newPlaylistName.value = name
    }

    /**
     * Resolves the base directory for physical playlists.
     * Prefers shared Music/Playlists, falling back to app external Music/Playlists if restricted.
     */
    fun getPlaylistsBaseDir(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val baseDir = File(musicDir, "Playlists")
        return if (baseDir.exists() || baseDir.mkdirs()) {
            baseDir
        } else {
            val appMusicDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            File(appMusicDir, "Playlists").apply { mkdirs() }
        }
    }

    /**
     * Refreshes both the device MediaStore tracks and the physical folder playlists.
     */
    fun refreshTracks() {
        viewModelScope.launch {
            _isLoading.value = true
            val loaded = queryTracksFromMediaStore()
            _tracks.value = loaded
            if (_activePlaybackTracks.value.isEmpty()) {
                setPlayerQueue(loaded)
            }
            refreshPlaylistsInternal(loaded)
            _isLoading.value = false
        }
    }

    /**
     * Scans physical folders in the Playlists directory.
     */
    fun refreshPlaylists() {
        viewModelScope.launch {
            refreshPlaylistsInternal(_tracks.value)
        }
    }

    private suspend fun refreshPlaylistsInternal(currentTracks: List<LocalTrack>) {
        val baseDir = getPlaylistsBaseDir()
        val subdirs = baseDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        val audioExtensions = setOf("mp3", "m4a", "wav", "flac", "opus", "ogg", "webm", "aac")

        val playlistList = subdirs.map { dir ->
            val files = dir.listFiles { file ->
                file.isFile && file.extension.lowercase() in audioExtensions
            } ?: emptyArray()

            val matchedTracks = files.map { file ->
                currentTracks.find { track ->
                    track.title.equals(file.nameWithoutExtension, ignoreCase = true) ||
                        track.contentUri.path?.contains(file.name) == true
                } ?: LocalTrack(
                    id = file.hashCode().toLong(),
                    title = file.nameWithoutExtension,
                    artist = dir.name,
                    album = dir.name,
                    durationMs = 0L,
                    contentUri = Uri.fromFile(file),
                    albumArtUri = null
                )
            }

            FolderPlaylist(
                name = dir.name,
                file = dir,
                path = dir.absolutePath,
                trackCount = files.size,
                tracks = matchedTracks
            )
        }.sortedBy { it.name.lowercase() }

        _playlists.value = playlistList

        // Keep current selected playlist updated if it still exists
        val currentSelected = _selectedPlaylist.value
        if (currentSelected != null) {
            _selectedPlaylist.value = playlistList.find { it.path == currentSelected.path }
        }
    }

    /**
     * Creates a new physical playlist folder in the storage.
     */
    fun createPlaylist(name: String = _newPlaylistName.value) {
        val cleanName = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (cleanName.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val baseDir = getPlaylistsBaseDir()
            val newFolder = File(baseDir, cleanName)
            if (!newFolder.exists()) {
                newFolder.mkdirs()
            }
            _newPlaylistName.value = ""
            refreshPlaylistsInternal(_tracks.value)
        }
    }

    /**
     * Deletes a physical playlist folder from the storage.
     */
    fun deletePlaylist(playlist: FolderPlaylist, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = try {
                val ok = playlist.file.deleteRecursively()
                if (_selectedPlaylist.value?.path == playlist.path) {
                    _selectedPlaylist.value = null
                }
                refreshPlaylistsInternal(_tracks.value)
                ok
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            if (onResult != null) {
                withContext(Dispatchers.Main) {
                    onResult(success)
                }
            }
        }
    }

    fun selectPlaylist(playlist: FolderPlaylist?) {
        _selectedPlaylist.value = playlist
    }

    /**
     * Copies a physical audio file for [track] into the chosen [playlist] folder
     * on [Dispatchers.IO], then notifies Android's MediaScanner and refreshes the playlist.
     */
    fun addTrackToPlaylist(
        track: LocalTrack,
        playlist: FolderPlaylist,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var success = false
            try {
                val rawName = if (track.displayName.isNotBlank()) {
                    track.displayName
                } else {
                    "${track.title}.mp3"
                }
                val safeName = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val targetFile = File(playlist.file, safeName)

                val inputStream = context.contentResolver.openInputStream(track.contentUri)
                    ?: if (track.contentUri.scheme == "file") track.contentUri.path?.let { File(it).inputStream() } else null

                if (inputStream != null) {
                    inputStream.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Register the newly copied file with Android's MediaScanner
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        null,
                        null
                    )

                    success = true
                    refreshPlaylistsInternal(_tracks.value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.Main) {
                onResult(success)
            }
        }
    }

    /**
     * Starts playback of a track at [index] within the device library.
     */
    fun playTrack(index: Int) {
        val trackList = _tracks.value
        if (index !in trackList.indices) return
        if (_activePlaybackTracks.value != trackList || !playlistSynced) {
            setPlayerQueue(trackList)
        }

        if (player.currentMediaItemIndex == index && player.playbackState != Player.STATE_IDLE) {
            player.play()
        } else {
            player.seekTo(index, 0L)
            player.prepare()
            player.play()
        }
        _currentTrackIndex.value = index
        _positionMs.value = 0L
    }

    /**
     * Starts playback of a track at [index] within a specific folder playlist.
     */
    fun playPlaylistTrack(playlist: FolderPlaylist, index: Int) {
        if (index !in playlist.tracks.indices) return
        setPlayerQueue(playlist.tracks)

        player.seekTo(index, 0L)
        player.prepare()
        player.play()
        _currentTrackIndex.value = index
        _positionMs.value = 0L
    }

    private fun setPlayerQueue(trackList: List<LocalTrack>) {
        if (trackList.isEmpty()) return
        val mediaItems = trackList.map { track ->
            MediaItem.Builder()
                .setUri(track.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .build()
                )
                .build()
        }
        player.setMediaItems(mediaItems)
        _activePlaybackTracks.value = trackList
        playlistSynced = true
    }

    /** Toggles between play and pause. */
    fun togglePlayPause() {
        val currentQueue = if (_activePlaybackTracks.value.isNotEmpty()) _activePlaybackTracks.value else _tracks.value
        when {
            player.isPlaying -> player.pause()
            currentQueue.isEmpty() -> Unit
            player.playbackState == Player.STATE_IDLE -> {
                val startIndex = _currentTrackIndex.value
                    .takeIf { it in currentQueue.indices }
                    ?: 0
                playTrack(startIndex)
            }
            else -> {
                player.prepare()
                player.play()
            }
        }
    }

    /** Seeks the active track to [positionMs]. */
    fun seekTo(positionMs: Long) {
        if (player.playbackState == Player.STATE_IDLE) return
        player.seekTo(positionMs)
        _positionMs.value = positionMs.coerceIn(0L, _durationMs.value)
    }

    fun skipToNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    fun skipToPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.prepare()
            player.play()
        }
    }

    private fun queryTracksFromMediaStore(): List<LocalTrack> {
        val context = getApplication<Application>()
        val tracks = mutableListOf<LocalTrack>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DISPLAY_NAME
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)
                ?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val displayNameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val albumId = cursor.getLong(albumIdColumn)
                        val durationMs =
                            if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
                        val displayName =
                            if (displayNameColumn >= 0) cursor.getString(displayNameColumn) ?: "" else ""

                        tracks.add(
                            LocalTrack(
                                id = id,
                                title = cursor.getString(titleColumn) ?: "Unknown Title",
                                artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                                album = cursor.getString(albumColumn) ?: "Unknown Album",
                                durationMs = durationMs,
                                contentUri = ContentUris.withAppendedId(collection, id),
                                albumArtUri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId),
                                displayName = displayName
                            )
                        )
                    }
                }
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }

        return tracks
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    companion object {
        private const val POSITION_POLL_INTERVAL_MS = 500L
        private const val SEEK_INCREMENT_MS = 10_000L
        private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}