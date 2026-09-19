package com.example.audiodownloader.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.audiodownloader.domain.model.FolderPlaylist
import com.example.audiodownloader.domain.model.LocalTrack
import android.media.MediaScannerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import android.app.RecoverableSecurityException
import android.content.IntentSender
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.util.Size
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import com.example.audiodownloader.service.MusicAction
import com.example.audiodownloader.service.MusicService
import com.example.audiodownloader.service.MusicStateBridge
import com.example.audiodownloader.service.PlayerState
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

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

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val player: ExoPlayer = ExoPlayer.Builder(application)
        .setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        .build()

    // 0 = Device Library, 1 = Playlists
    private val _currentPlaylistTab = MutableStateFlow(0)
    val currentPlaylistTab: StateFlow<Int> = _currentPlaylistTab.asStateFlow()

    private val _tracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val tracks: StateFlow<List<LocalTrack>> = _tracks.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredTracks: StateFlow<List<LocalTrack>> = combine(_tracks, _searchQuery) { trackList, query ->
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            trackList
        } else {
            trackList.filter { track ->
                track.title.contains(trimmed, ignoreCase = true) ||
                    track.artist.contains(trimmed, ignoreCase = true) ||
                    track.album.contains(trimmed, ignoreCase = true) ||
                    track.displayName.contains(trimmed, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _playlists = MutableStateFlow<List<FolderPlaylist>>(emptyList())
    val playlists: StateFlow<List<FolderPlaylist>> = _playlists.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<FolderPlaylist?>(null)
    val selectedPlaylist: StateFlow<FolderPlaylist?> = _selectedPlaylist.asStateFlow()

    private val _newPlaylistName = MutableStateFlow("")
    val newPlaylistName: StateFlow<String> = _newPlaylistName.asStateFlow()

    private val _activePlaybackTracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val activePlaybackTracks: StateFlow<List<LocalTrack>> = _activePlaybackTracks.asStateFlow()

    val playingQueue: StateFlow<List<LocalTrack>> = _activePlaybackTracks.asStateFlow()

    // Persistent Storage Access Framework (SAF) folder permission management
    private val safPrefs = application.getSharedPreferences("audio_aurora_saf_prefs", Context.MODE_PRIVATE)

    private val _folderPermissionGranted = MutableStateFlow(hasFolderPermission())
    val folderPermissionGranted: StateFlow<Boolean> = _folderPermissionGranted.asStateFlow()

    fun getSavedFolderTreeUri(): Uri? {
        val uriStr = safPrefs.getString(KEY_MUSIC_FOLDER_TREE_URI, null) ?: return null
        return Uri.parse(uriStr)
    }

    fun hasFolderPermission(): Boolean {
        val treeUri = getSavedFolderTreeUri() ?: return false
        val permissions = getApplication<Application>().contentResolver.persistedUriPermissions
        return permissions.any { it.uri == treeUri && it.isWritePermission }
    }

    fun saveFolderTreeUri(uri: Uri) {
        val context = getApplication<Application>()
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            safPrefs.edit().putString(KEY_MUSIC_FOLDER_TREE_URI, uri.toString()).apply()
            _folderPermissionGranted.value = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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

    // Thread-safe memory cache for extracted artwork colors: key -> ARGB Long
    private val paletteColorCache = ConcurrentHashMap<String, Long>()

    // Current accent color (defaults to Light Pink #FFB6C1)
    private val _accentColor = MutableStateFlow(DEFAULT_ACCENT_COLOR)
    val accentColor: StateFlow<Long> = _accentColor.asStateFlow()

    // Observable playback state consumed directly by Compose UI and notifications
    val playerState: StateFlow<PlayerState> = MusicStateBridge.playerState

    private var playlistSynced = false

    private fun updatePlayingState() {
        val shouldPlay = player.playWhenReady &&
            player.playbackState != Player.STATE_ENDED &&
            player.playbackState != Player.STATE_IDLE &&
            player.mediaItemCount > 0
        if (_isPlaying.value != shouldPlay) {
            _isPlaying.value = shouldPlay
            if (shouldPlay) {
                val context = getApplication<Application>()
                val intent = Intent(context, MusicService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updatePlayingState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlayingState()
            if (playbackState == Player.STATE_READY) {
                _durationMs.value = player.duration.coerceAtLeast(0L)
            } else if (playbackState == Player.STATE_ENDED) {
                // Auto-Continue: When the custom queue finishes, automatically play the next song from main library
                if (!player.hasNextMediaItem()) {
                    autoContinueFromLibrary()
                }
            } else if (playbackState == Player.STATE_IDLE) {
                if (player.mediaItemCount == 0) {
                    _durationMs.value = 0L
                    _positionMs.value = 0L
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentTrackIndex.value = if (player.mediaItemCount > 0) player.currentMediaItemIndex else -1
            _positionMs.value = 0L
            updatePlayingState()
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

        // Dynamically extract Palette accent color on Dispatchers.IO when the active track changes
        viewModelScope.launch {
            combine(_activePlaybackTracks, _currentTrackIndex) { activeQueue, index ->
                activeQueue.getOrNull(index)
            }.distinctUntilChanged()
             .collectLatest { track ->
                 val extractedColor = extractAccentColor(track)
                 _accentColor.value = extractedColor
             }
        }

        // Synchronize player state with MusicStateBridge for custom RemoteViews notification
        viewModelScope.launch {
            combine(
                _activePlaybackTracks,
                _currentTrackIndex,
                _isPlaying,
                _positionMs,
                _durationMs,
                _accentColor
            ) { args: Array<Any?> ->
                @Suppress("UNCHECKED_CAST")
                val activeQueue = args[0] as List<LocalTrack>
                val index = args[1] as Int
                val isPlaying = args[2] as Boolean
                val pos = args[3] as Long
                val dur = args[4] as Long
                val accent = args[5] as Long

                val track = activeQueue.getOrNull(index)
                PlayerState(
                    title = track?.title ?: "",
                    artist = track?.artist ?: "",
                    album = track?.album ?: "",
                    albumArtUri = track?.albumArtUri,
                    isPlaying = isPlaying,
                    positionMs = pos,
                    durationMs = dur,
                    queueSize = activeQueue.size,
                    hasNext = index in 0 until activeQueue.lastIndex,
                    hasPrevious = index > 0,
                    accentColor = accent
                )
            }.collectLatest { state ->
                MusicStateBridge.updateState(state)
            }
        }

        // Two-way sync: Handle notification action commands from RemoteViews
        MusicStateBridge.onActionReceived = { action ->
            when (action) {
                MusicAction.PLAY_PAUSE -> togglePlayPause()
                MusicAction.PREVIOUS -> skipToPrevious()
                MusicAction.REWIND -> rewind()
                MusicAction.FAST_FORWARD -> fastForward()
                MusicAction.NEXT -> skipToNext()
                MusicAction.QUEUE -> setPlaylistTab(2)
                MusicAction.STOP -> {
                    player.stop()
                    player.clearMediaItems()
                    _isPlaying.value = false
                    updatePlayingState()
                }
            }
        }
    }

    fun setPlaylistTab(tab: Int) {
        _currentPlaylistTab.value = tab
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearchQuery() {
        _searchQuery.value = ""
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
                        track.displayName.equals(file.name, ignoreCase = true) ||
                        track.contentUri.path?.contains(file.name) == true
                } ?: run {
                    val baseName = file.nameWithoutExtension
                    var pArtist = ""
                    var pTitle = baseName
                    if (baseName.contains(" - ")) {
                        val pParts = baseName.split(" - ", limit = 2)
                        if (pParts.size == 2 && pParts[0].isNotBlank() && pParts[1].isNotBlank()) {
                            pArtist = pParts[0].trim()
                            pTitle = pParts[1].trim()
                        }
                    }
                    if (pArtist.isBlank()) {
                        pArtist = dir.name
                    }
                    LocalTrack(
                        id = file.hashCode().toLong(),
                        title = pTitle,
                        artist = pArtist,
                        album = dir.name,
                        durationMs = 0L,
                        contentUri = Uri.fromFile(file),
                        albumArtUri = null,
                        displayName = file.name
                    )
                }
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
     * Deletes a downloaded track from device storage silently.
     * 1. Stops playback and removes the track from ExoPlayer to release file locks.
     * 2. Resolves the track via Storage Access Framework (SAF) using the persisted tree URI
     *    and calls DocumentFile.delete() to delete without any Android 11+ system prompts.
     * 3. Falls back to standard File.delete() / ContentResolver.delete() if no tree URI is stored.
     * 4. Synchronizes Library StateFlow immediately upon deletion.
     */
    fun deleteTrack(track: LocalTrack, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            // -----------------------------------------------------------------
            // STEP 1: RELEASE EXOPLAYER LOCKS
            // -----------------------------------------------------------------
            val currentItem = player.currentMediaItem
            val isCurrentTrack = currentItem != null && (
                currentItem.localConfiguration?.uri == track.contentUri ||
                currentItem.mediaId == track.id.toString()
            )
            if (isCurrentTrack) {
                player.stop()
            }

            // Loop backwards through player's queue so index shifts don't skip items
            for (i in player.mediaItemCount - 1 downTo 0) {
                val mediaItem = player.getMediaItemAt(i)
                val matchesUri = mediaItem.localConfiguration?.uri == track.contentUri
                val matchesId = mediaItem.mediaId == track.id.toString()
                if (matchesUri || matchesId) {
                    player.removeMediaItem(i)
                }
            }

            // Synchronize ViewModel active queue
            val updatedQueue = _activePlaybackTracks.value.filter {
                it.id != track.id && it.contentUri != track.contentUri
            }
            _activePlaybackTracks.value = updatedQueue

            if (updatedQueue.isEmpty() || player.mediaItemCount == 0) {
                clearQueue()
            } else {
                _currentTrackIndex.value = player.currentMediaItemIndex
                updatePlayingState()
            }

            // -----------------------------------------------------------------
            // STEP 2: STRICT DOCUMENTFILE SILENT DELETION (ZERO CONTENTRESOLVER.DELETE)
            // -----------------------------------------------------------------
            val context = getApplication<Application>()
            val deleteSuccess = withContext(Dispatchers.IO) {
                try {
                    // 1. Retrieve the saved Tree URI string
                    val savedUriString = safPrefs.getString(KEY_MUSIC_FOLDER_TREE_URI, null)
                    if (savedUriString.isNullOrBlank()) {
                        android.util.Log.e("AudioAurora", "No saved folder Tree URI found. Cannot delete track without folder access.")
                        return@withContext false
                    }

                    // 2. Convert to Uri and build root document
                    val treeUri = Uri.parse(savedUriString)
                    val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                    if (rootDoc == null || !rootDoc.exists()) {
                        android.util.Log.e("AudioAurora", "Root DocumentFile does not exist for URI: $treeUri")
                        return@withContext false
                    }

                    // 3. Locate target file (including in subdirectories)
                    val filePath = getFilePathFromUri(context, track.contentUri) ?: track.contentUri.path
                    val targetDocFile = findDocumentFileInTree(rootDoc, track, filePath)

                    // 4. Handle nulls safely: DO NOT fall back to contentResolver.delete()
                    if (targetDocFile == null || !targetDocFile.exists()) {
                        android.util.Log.e("AudioAurora", "Target file not found in granted folder tree: ${track.displayName}")
                        return@withContext false
                    }

                    // 5. Delete solely via DocumentFile
                    val deleted = targetDocFile.delete()

                    // 6. Notify MediaScanner to purge the entry from Android's MediaStore index
                    if (filePath != null) {
                        MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
                    }

                    deleted
                } catch (e: Exception) {
                    android.util.Log.e("AudioAurora", "Exception while deleting via DocumentFile", e)
                    false
                }
            }

            // -----------------------------------------------------------------
            // STEP 3: STATE SYNCHRONIZATION
            // -----------------------------------------------------------------
            if (deleteSuccess) {
                onTrackDeletedSuccessfully(track)
            }

            onResult?.invoke(deleteSuccess)
        }
    }

    /**
     * Resolves a [DocumentFile] for a [LocalTrack] within the user-authorized directory tree.
     * Searches root directly, checks immediate parent directory, and traverses subdirectories.
     */
    private fun findDocumentFileInTree(
        rootTree: DocumentFile,
        track: LocalTrack,
        filePath: String?
    ): DocumentFile? {
        val targetName = track.displayName.takeIf { it.isNotBlank() }
            ?: (filePath?.let { File(it).name })
            ?: "${track.title}.flac"

        // 1. Direct search in root folder (e.g. Music/song.flac)
        val directFile = rootTree.findFile(targetName)
        if (directFile != null && directFile.exists()) {
            return directFile
        }

        // 2. Relative subfolder search (e.g. Music/Playlists/Rock/song.flac)
        if (filePath != null) {
            val file = File(filePath)
            val parentName = file.parentFile?.name
            if (parentName != null && parentName != "Music") {
                val subFolder = rootTree.findFile(parentName)
                if (subFolder != null && subFolder.isDirectory) {
                    val subFile = subFolder.findFile(targetName)
                    if (subFile != null && subFile.exists()) {
                        return subFile
                    }
                }
            }
        }

        // 3. Search child directories recursively
        return findFileRecursive(rootTree, targetName)
    }

    private fun findFileRecursive(dir: DocumentFile, targetName: String): DocumentFile? {
        for (item in dir.listFiles()) {
            if (item.isFile && item.name.equals(targetName, ignoreCase = true)) {
                return item
            } else if (item.isDirectory) {
                val found = findFileRecursive(item, targetName)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Called when a track has been deleted successfully (after silent DocumentFile deletion
     * or legacy deletion). Immediately updates the Library and Playlists StateFlows for the Compose UI.
     */
    fun onTrackDeletedSuccessfully(track: LocalTrack) {
        _tracks.value = _tracks.value.filter { it.id != track.id }
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.tracks.any { it.id == track.id }) {
                playlist.copy(
                    tracks = playlist.tracks.filter { it.id != track.id },
                    trackCount = (playlist.trackCount - 1).coerceAtLeast(0)
                )
            } else {
                playlist
            }
        }
    }

    /**
     * Removes an audio file from a specific folder playlist.
     */
    fun removeTrackFromPlaylist(
        track: LocalTrack,
        playlist: FolderPlaylist,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var success = false
            try {
                val candidateFiles = playlist.file.listFiles { f ->
                    f.isFile && (
                        f.name.equals(track.displayName, ignoreCase = true) ||
                            f.nameWithoutExtension.equals(track.title, ignoreCase = true) ||
                            (track.contentUri.scheme == "file" && f.absolutePath == track.contentUri.path)
                    )
                }

                if (!candidateFiles.isNullOrEmpty()) {
                    for (f in candidateFiles) {
                        if (f.delete()) {
                            success = true
                            MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), null, null)
                        }
                    }
                }

                refreshPlaylistsInternal(_tracks.value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (onResult != null) {
                withContext(Dispatchers.Main) {
                    onResult(success)
                }
            }
        }
    }

    private fun getFilePathFromUri(context: Application, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Starts playback of a specific single [track] from the library.
     * Clears any existing queue and sets *just that single track* as the queue.
     */
    fun playTrack(track: LocalTrack) {
        setPlayerQueue(listOf(track))
        player.seekTo(0, 0L)
        player.prepare()
        player.play()
        _currentTrackIndex.value = 0
        _positionMs.value = 0L
    }

    /**
     * Starts playback of a track at [index] within the device library.
     * Clears any existing queue and sets *just that single track* as the queue.
     */
    fun playTrack(index: Int) {
        val track = _tracks.value.getOrNull(index) ?: return
        playTrack(track)
    }

    /**
     * Starts playback of a track at [index] within a specific folder playlist.
     * Sets the entire playlist's tracks as the queue context and jumps to [index].
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

    private fun LocalTrack.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(contentUri)
            .setMediaId(id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()

    private fun setPlayerQueue(trackList: List<LocalTrack>) {
        if (trackList.isEmpty()) {
            clearQueue()
            return
        }
        val mediaItems = trackList.map { it.toMediaItem() }
        player.setMediaItems(mediaItems)
        _activePlaybackTracks.value = trackList
        playlistSynced = true
    }

    /**
     * Injects a list of tracks immediately after the currently playing song in the ExoPlayer playlist.
     * Uses player.addMediaItems(insertIndex, mediaItems) to dynamically update ExoPlayer's Timeline
     * without interrupting or restarting the current audio stream.
     */
    fun playNext(tracksToInject: List<LocalTrack>) {
        if (tracksToInject.isEmpty()) return

        val currentQueue = _activePlaybackTracks.value.toMutableList()

        if (currentQueue.isEmpty() || player.playbackState == Player.STATE_IDLE) {
            setPlayerQueue(tracksToInject)
            playTrack(tracksToInject.first())
            return
        }

        // Calculate current playing index and insert position
        val currentIndex = player.currentMediaItemIndex.takeIf { it in currentQueue.indices } ?: 0
        val insertIndex = (currentIndex + 1).coerceAtMost(currentQueue.size)

        val mediaItems = tracksToInject.map { it.toMediaItem() }
        // Seamlessly inject into ExoPlayer timeline without disturbing current audio sink
        player.addMediaItems(insertIndex, mediaItems)

        currentQueue.addAll(insertIndex, tracksToInject)
        _activePlaybackTracks.value = currentQueue
        playlistSynced = true
    }

    /**
     * Injects a single track immediately after the currently playing song.
     */
    fun playTrackNext(track: LocalTrack) {
        playNext(listOf(track))
    }

    /**
     * Convenience method to inject an entire FolderPlaylist to play next.
     */
    fun playPlaylistNext(playlist: FolderPlaylist) {
        playNext(playlist.tracks)
    }

    /**
     * Appends a list of tracks to the end of the current ExoPlayer queue.
     */
    fun enqueue(tracksToAppend: List<LocalTrack>) {
        if (tracksToAppend.isEmpty()) return

        val currentQueue = _activePlaybackTracks.value.toMutableList()

        if (currentQueue.isEmpty() || player.playbackState == Player.STATE_IDLE) {
            setPlayerQueue(tracksToAppend)
            playTrack(tracksToAppend.first())
            return
        }

        val insertIndex = currentQueue.size
        val mediaItems = tracksToAppend.map { it.toMediaItem() }
        player.addMediaItems(insertIndex, mediaItems)

        currentQueue.addAll(tracksToAppend)
        _activePlaybackTracks.value = currentQueue
        playlistSynced = true
    }

    fun enqueueTrack(track: LocalTrack) {
        enqueue(listOf(track))
    }

    fun enqueuePlaylist(playlist: FolderPlaylist) {
        enqueue(playlist.tracks)
    }

    /**
     * Reorders an item in the playing queue from [fromIndex] to [toIndex].
     * Synchronizes directly with ExoPlayer's playlist via player.moveMediaItem() and updates StateFlow.
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentQueue = _activePlaybackTracks.value.toMutableList()

        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) return

        // 1. Update ExoPlayer's internal playlist timeline
        player.moveMediaItem(fromIndex, toIndex)

        // 2. Synchronize ViewModel state
        val item = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, item)
        _activePlaybackTracks.value = currentQueue
        _currentTrackIndex.value = player.currentMediaItemIndex
        playlistSynced = true
    }

    /**
     * Removes an item at [index] from the playing queue.
     * Updates ExoPlayer via player.removeMediaItem() and updates StateFlow.
     */
    fun removeQueueItem(index: Int) {
        val currentQueue = _activePlaybackTracks.value.toMutableList()
        if (index !in currentQueue.indices) return

        if (index in 0 until player.mediaItemCount) {
            player.removeMediaItem(index)
        }
        currentQueue.removeAt(index)
        _activePlaybackTracks.value = currentQueue

        if (currentQueue.isEmpty()) {
            player.stop()
            player.clearMediaItems()
            _currentTrackIndex.value = -1
            _positionMs.value = 0L
            _durationMs.value = 0L
            _isPlaying.value = false
            playlistSynced = false
        } else {
            _currentTrackIndex.value = player.currentMediaItemIndex
        }
    }

    /**
     * Clears all tracks from the queue, stops ExoPlayer, and resets playback state.
     */
    fun clearQueue() {
        player.stop()
        player.clearMediaItems()
        _activePlaybackTracks.value = emptyList()
        _currentTrackIndex.value = -1
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
        playlistSynced = false
    }

    /**
     * Plays a specific track from the queue at [index].
     */
    fun playQueueTrack(index: Int) {
        val currentQueue = _activePlaybackTracks.value
        if (index !in currentQueue.indices) return

        if (!playlistSynced) {
            setPlayerQueue(currentQueue)
        }
        player.seekTo(index, 0L)
        player.prepare()
        player.play()
        _currentTrackIndex.value = index
        _positionMs.value = 0L
    }

    /** Toggles between play and pause. */
    fun togglePlayPause() {
        val currentQueue = _activePlaybackTracks.value
        when {
            player.playWhenReady -> player.pause()
            currentQueue.isEmpty() -> Unit
            player.playbackState == Player.STATE_IDLE -> {
                val startIndex = _currentTrackIndex.value
                    .takeIf { it in currentQueue.indices }
                    ?: 0
                playQueueTrack(startIndex)
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

    /**
     * Fast-forwards playback by 10 seconds (10,000 ms).
     * Includes boundary checks to ensure the target position never exceeds total track duration.
     */
    fun fastForward(incrementMs: Long = SEEK_INCREMENT_MS) {
        if (player.playbackState == Player.STATE_IDLE || player.currentMediaItemIndex < 0) return

        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val totalDuration = player.duration.coerceAtLeast(0L).takeIf { it > 0L } ?: _durationMs.value

        val targetPosition = if (totalDuration > 0L) {
            (currentPos + incrementMs).coerceAtMost(totalDuration)
        } else {
            currentPos + incrementMs
        }

        player.seekTo(targetPosition)
        _positionMs.value = targetPosition
    }

    /**
     * Rewinds playback safely by 10 seconds (10,000 ms).
     * Uses Kotlin's coerceAtLeast(0L) to guarantee ExoPlayer never receives a negative position.
     */
    fun rewind() {
        val currentPos = player.currentPosition
        val newPos = (currentPos - 10000L).coerceAtLeast(0L)
        player.seekTo(newPos)
        _positionMs.value = newPos
    }

    /**
     * Auto-Continue: Seamlessly transitions to the next track in the main library
     * when the custom queue has finished playing.
     */
    fun autoContinueFromLibrary() {
        val mainLibraryList = _tracks.value
        if (mainLibraryList.isEmpty()) return

        val currentTrack = _activePlaybackTracks.value.getOrNull(_currentTrackIndex.value)
            ?: _activePlaybackTracks.value.lastOrNull()
        val currentLibIndex = if (currentTrack != null) {
            mainLibraryList.indexOfFirst { it.id == currentTrack.id || it.contentUri == currentTrack.contentUri }
        } else {
            -1
        }
        val nextIndex = if (currentLibIndex != -1) {
            (currentLibIndex + 1) % mainLibraryList.size
        } else {
            0
        }
        val nextTrack = mainLibraryList[nextIndex]
        val nextMediaItem = nextTrack.toMediaItem()

        player.setMediaItem(nextMediaItem)
        _activePlaybackTracks.value = listOf(nextTrack)
        _currentTrackIndex.value = 0
        _positionMs.value = 0L
        playlistSynced = true

        player.prepare()
        player.play()
    }

    fun skipToNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        } else {
            autoContinueFromLibrary()
        }
    }

    /**
     * Custom Previous Track action for the |< button:
     * 1. If playback is past 3 seconds (> 3,000 ms), restarts the current song to 0:00.
     * 2. If <= 3 seconds, manually calculates the previous index in the active queue or main library,
     *    safely wrapping around to the end of the list if at the first song.
     */
    fun skipToPreviousTrack() {
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount == 0) return

        // 1. Standard 3-second restart threshold
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
            _positionMs.value = 0L
            return
        }

        // 2. Resolve active list: current queue if multiple tracks, otherwise full device library
        val currentQueue = _activePlaybackTracks.value
        val mainLibraryList = _tracks.value
        val currentList = if (currentQueue.size > 1) currentQueue else mainLibraryList

        if (currentList.isEmpty()) return

        // 3. Find current track index in the active list
        val currentTrack = currentQueue.getOrNull(_currentTrackIndex.value) ?: currentQueue.firstOrNull()
        val currentIndex = if (currentTrack != null) {
            currentList.indexOfFirst { it.id == currentTrack.id || it.contentUri == currentTrack.contentUri }
        } else {
            _currentTrackIndex.value
        }

        // 4. Calculate previous index with wraparound
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else currentList.size - 1
        val prevTrack = currentList[prevIndex]

        if (currentQueue.size > 1 && player.mediaItemCount > 1 && prevIndex in 0 until player.mediaItemCount) {
            // Within an existing multi-song ExoPlayer queue
            player.seekTo(prevIndex, 0L)
            _currentTrackIndex.value = prevIndex
        } else {
            // Single track or library context: load previous track MediaItem
            val mediaItem = prevTrack.toMediaItem()
            player.setMediaItem(mediaItem)
            _activePlaybackTracks.value = listOf(prevTrack)
            _currentTrackIndex.value = 0
            playlistSynced = true
        }

        _positionMs.value = 0L
        player.prepare()
        player.play()
    }

    fun skipToPrevious() {
        skipToPreviousTrack()
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

                        val rawTitle = if (titleColumn >= 0) cursor.getString(titleColumn)?.trim() else null
                        val rawArtist = if (artistColumn >= 0) cursor.getString(artistColumn)?.trim() else null
                        val rawAlbum = if (albumColumn >= 0) cursor.getString(albumColumn)?.trim() else null

                        val fileNameWithoutExt = displayName.substringBeforeLast('.').trim()

                        var resolvedArtist = when {
                            rawArtist.isNullOrBlank() ||
                                rawArtist.equals(MediaStore.UNKNOWN_STRING, ignoreCase = true) ||
                                rawArtist.equals("unknown", ignoreCase = true) -> ""
                            else -> rawArtist
                        }

                        var resolvedTitle = when {
                            rawTitle.isNullOrBlank() ||
                                rawTitle.equals(MediaStore.UNKNOWN_STRING, ignoreCase = true) ||
                                rawTitle.equals("unknown", ignoreCase = true) -> ""
                            else -> rawTitle
                        }

                        // Fall back to filename if title is missing
                        if (resolvedTitle.isBlank()) {
                            resolvedTitle = if (fileNameWithoutExt.isNotBlank()) fileNameWithoutExt else "Unknown Title"
                        }

                        // If artist metadata was missing, check if title or filename is formatted as "Artist - Title"
                        if (resolvedArtist.isBlank()) {
                            val candidate = when {
                                resolvedTitle.contains(" - ") -> resolvedTitle
                                fileNameWithoutExt.contains(" - ") -> fileNameWithoutExt
                                else -> null
                            }
                            if (candidate != null) {
                                val parts = candidate.split(" - ", limit = 2)
                                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                                    resolvedArtist = parts[0].trim()
                                    if (resolvedTitle == candidate) {
                                        resolvedTitle = parts[1].trim()
                                    }
                                }
                            }
                        }

                        if (resolvedArtist.isBlank()) {
                            resolvedArtist = "Unknown Artist"
                        }

                        val resolvedAlbum = when {
                            rawAlbum.isNullOrBlank() ||
                                rawAlbum.equals(MediaStore.UNKNOWN_STRING, ignoreCase = true) ||
                                rawAlbum.equals("unknown", ignoreCase = true) -> "Single"
                            else -> rawAlbum
                        }

                        tracks.add(
                            LocalTrack(
                                id = id,
                                title = resolvedTitle,
                                artist = resolvedArtist,
                                album = resolvedAlbum,
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

    /**
     * Extracts a contrast-safe accent color from the track's album artwork using AndroidX Palette.
     * Executes strictly on [Dispatchers.IO] to keep the Main / UI thread silky smooth.
     *
     * Bitmap Decoding Pipeline:
     * 1. Android 10+ (API 29+): MediaStore loadThumbnail directly from audio contentUri.
     * 2. Android 9+ (API 28+): ImageDecoder with ALLOCATOR_SOFTWARE (prevents Palette hardware bitmap crash).
     * 3. BitmapFactory.decodeStream via ContentResolver openInputStream.
     * 4. MediaMetadataRetriever embedded picture fallback for standalone audio files.
     *
     * Contrast Safety:
     * - Evaluates against #222222 dark background.
     * - Prefers LightVibrant or LightMuted swatches.
     * - Allows Vibrant or Dominant swatch only if contrast ratio >= 3.0 against #222222.
     * - Gracefully falls back to default Light Pink (#FFB6C1).
     */
    /**
     * Decodes the album artwork into a software-compatible Bitmap.
     * Ensures all file streams, file descriptors, and native metadata retrievers
     * are strictly closed immediately via `.use { }` and `try-finally` blocks.
     */
    private fun decodeTrackBitmap(track: LocalTrack): Bitmap? {
        val context = getApplication<Application>()

        // 1. Decode dedicated album art image if albumArtUri is present
        if (track.albumArtUri != null) {
            // Android 10+ (API 29+): MediaStore thumbnail loader for images
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    return context.contentResolver.loadThumbnail(track.albumArtUri, Size(256, 256), null)
                } catch (_: Exception) {}
            }

            // Android 9+ (API 28+): ImageDecoder with ALLOCATOR_SOFTWARE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val source = ImageDecoder.createSource(context.contentResolver, track.albumArtUri)
                    return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.setTargetSampleSize(2)
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } catch (_: Exception) {}
            }

            // Fallback: ContentResolver openInputStream wrapped in .use { }
            try {
                context.contentResolver.openInputStream(track.albumArtUri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    val decoded = BitmapFactory.decodeStream(stream, null, options)
                    if (decoded != null) return decoded
                }
            } catch (_: Exception) {}
        }

        // 2. Embedded picture in standalone audio file (FLAC / MP3) via MediaMetadataRetriever
        // Wrapped strictly in try-finally with retriever.release() and .use { } for File/FD scopes
        val mmr = MediaMetadataRetriever()
        try {
            val filePath = getFilePathFromUri(context, track.contentUri)
                ?: if (track.contentUri.scheme == "file") track.contentUri.path else null

            val rawBytes: ByteArray? = if (filePath != null && File(filePath).exists()) {
                FileInputStream(filePath).use { fis ->
                    mmr.setDataSource(fis.fd)
                    mmr.embeddedPicture
                }
            } else {
                context.contentResolver.openAssetFileDescriptor(track.contentUri, "r")?.use { afd ->
                    mmr.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    mmr.embeddedPicture
                }
            }

            if (rawBytes != null) {
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
                if (decoded != null) return decoded
            }
        } catch (_: Exception) {
            // Gracefully ignore extraction errors
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {}
        }

        return null
    }

    private suspend fun extractAccentColor(track: LocalTrack?): Long = withContext(Dispatchers.IO) {
        if (track == null) return@withContext DEFAULT_ACCENT_COLOR

        val cacheKey = track.albumArtUri?.toString() ?: track.contentUri.toString()
        paletteColorCache[cacheKey]?.let { return@withContext it }

        // Immutable local val eliminating closure-capture smart-cast failures
        val bitmap: Bitmap? = decodeTrackBitmap(track)

        // Safe unwrapping guaranteeing a non-null Bitmap for Palette.from()
        val extractedColor = bitmap?.let { nonNullBitmap ->
            try {
                val softwareBitmap = if (nonNullBitmap.config == Bitmap.Config.HARDWARE) {
                    nonNullBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: nonNullBitmap
                } else {
                    nonNullBitmap
                }

                val palette = Palette.from(softwareBitmap).generate()
                val darkBg = 0xFF222222.toInt()

                // Contrast Safety: LightVibrant/LightMuted or high-contrast swatches against #222222
                val swatch = palette.lightVibrantSwatch
                    ?: palette.lightMutedSwatch
                    ?: palette.vibrantSwatch?.takeIf { ColorUtils.calculateContrast(it.rgb, darkBg) >= 3.0 }
                    ?: palette.dominantSwatch?.takeIf { ColorUtils.calculateContrast(it.rgb, darkBg) >= 3.0 }

                swatch?.rgb?.toLong()?.let { it and 0xFFFFFFFFL } ?: DEFAULT_ACCENT_COLOR
            } catch (_: Exception) {
                DEFAULT_ACCENT_COLOR
            }
        } ?: DEFAULT_ACCENT_COLOR

        paletteColorCache[cacheKey] = extractedColor
        extractedColor
    }

    override fun onCleared() {
        MusicStateBridge.onActionReceived = null
        player.removeListener(playerListener)
        player.stop()
        player.release()
        super.onCleared()
    }

    companion object {
        private const val DEFAULT_ACCENT_COLOR = 0xFFFFB6C1L
        private const val POSITION_POLL_INTERVAL_MS = 500L
        private const val SEEK_INCREMENT_MS = 10_000L
        private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
        private const val KEY_MUSIC_FOLDER_TREE_URI = "music_folder_tree_uri"
    }
}