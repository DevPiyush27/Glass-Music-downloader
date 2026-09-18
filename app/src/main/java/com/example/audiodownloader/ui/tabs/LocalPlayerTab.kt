package com.example.audiodownloader.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiodownloader.domain.model.FolderPlaylist
import com.example.audiodownloader.domain.model.LocalTrack
import com.example.audiodownloader.ui.components.*
import com.example.audiodownloader.ui.viewmodel.LocalPlayerViewModel

/**
 * Local music player tab:
 * - Separated Library & Playlists views via Neomorphic segmented toggle.
 * - Physical folder-based playlist creation and deletion in the shared Music directory.
 * - Docked Spotify-inspired compact Mini-Player at the bottom (< 15% screen height).
 */
@Composable
fun LocalPlayerTab(
    viewModel: LocalPlayerViewModel,
    hasPermission: Boolean,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentPlaylistTab.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val activePlaybackTracks by viewModel.activePlaybackTracks.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentTrackIndex.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // Resolve active playing track from active playlist queue or library
    val currentTrack = remember(currentIndex, activePlaybackTracks, tracks) {
        val activeQueue = if (activePlaybackTracks.isNotEmpty()) activePlaybackTracks else tracks
        activeQueue.getOrNull(currentIndex)
    }

    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistInput by remember { mutableStateOf("") }
    var trackForPlaylistDialog by remember { mutableStateOf<LocalTrack?>(null) }
    var playlistToDelete by remember { mutableStateOf<FolderPlaylist?>(null) }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.refreshTracks()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Top Header: Neomorphic Segmented Toggle + Refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NeomorphicSegmentedToggle(
                options = listOf("Library (${tracks.size})", "Playlists (${playlists.size})"),
                selectedIndex = currentTab,
                onOptionSelected = {
                    viewModel.setPlaylistTab(it)
                    if (it == 0) viewModel.selectPlaylist(null)
                },
                modifier = Modifier.weight(1f)
            )

            // Extruded Refresh button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .neumorphicExtruded(cornerRadius = 12.dp, backgroundColor = NeumorphColors.Surface)
                    .clickable(enabled = hasPermission && !isLoading) {
                        viewModel.refreshTracks()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = NeumorphColors.AccentCopper,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh library",
                        tint = NeumorphColors.AccentCopper,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Center Content Area (Scrollable, weight 1f)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                !hasPermission -> {
                    Box(modifier = Modifier.padding(16.dp)) {
                        PermissionNoticeCard()
                    }
                }
                isLoading && tracks.isEmpty() && playlists.isEmpty() -> {
                    Box(modifier = Modifier.padding(16.dp)) {
                        LoadingCard()
                    }
                }
                currentTab == 0 -> {
                    // Device Library View
                    if (tracks.isEmpty()) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            EmptyLibraryCard()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                                TrackRow(
                                    index = index,
                                    track = track,
                                    isCurrent = currentTrack?.id == track.id,
                                    isPlaying = isPlaying,
                                    onClick = { viewModel.playTrack(index) },
                                    onAddToPlaylist = { trackForPlaylistDialog = track }
                                )
                            }
                        }
                    }
                }
                else -> {
                    // Playlists View
                    if (selectedPlaylist == null) {
                        // All Playlists list + Minimal Create Button
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .neumorphicExtruded(cornerRadius = 14.dp, backgroundColor = NeumorphColors.Surface)
                                        .clickable { showCreateDialog = true }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(NeumorphColors.AccentCopper),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "New Playlist",
                                            tint = NeumorphColors.TextCream,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "New Playlist",
                                        color = NeumorphColors.TextCream,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            item {
                                Text(
                                    text = "Folder Playlists (${playlists.size})",
                                    color = NeumorphColors.TextCream,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (playlists.isEmpty()) {
                                item {
                                    EmptyPlaylistsCard()
                                }
                            } else {
                                items(playlists, key = { it.path }) { playlist ->
                                    FolderPlaylistRow(
                                        playlist = playlist,
                                        onClick = { viewModel.selectPlaylist(playlist) },
                                        onDelete = { playlistToDelete = playlist }
                                    )
                                }
                            }
                        }
                    } else {
                        // Single Playlist Tracks View
                        val activePlaylist = selectedPlaylist!!
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            // Back Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .neumorphicExtruded(cornerRadius = 10.dp, backgroundColor = NeumorphColors.Surface)
                                        .clickable { viewModel.selectPlaylist(null) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to playlists",
                                        tint = NeumorphColors.AccentCopper,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = activePlaylist.name,
                                        color = NeumorphColors.TextCream,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${activePlaylist.trackCount} track(s) in folder",
                                        color = NeumorphColors.AccentCopperLight,
                                        fontSize = 12.sp
                                    )
                                }

                                IconButton(
                                    onClick = { playlistToDelete = activePlaylist },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete playlist folder",
                                        tint = NeumorphColors.StatusError,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (activePlaylist.tracks.isEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Folder Playlist is Empty",
                                        color = NeumorphColors.TextCream,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Place audio files into:\n${activePlaylist.path}\nthen tap refresh above to scan them.",
                                        color = NeumorphColors.TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    itemsIndexed(activePlaylist.tracks, key = { _, track -> track.id }) { index, track ->
                                        TrackRow(
                                            index = index,
                                            track = track,
                                            isCurrent = currentTrack?.id == track.id,
                                            isPlaying = isPlaying,
                                            onClick = { viewModel.playPlaylistTrack(activePlaylist, index) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Docked Mini-Player (<= 15% screen height, fixed at bottom)
        MiniPlayerCard(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            hasSelection = currentTrack != null,
            onTogglePlayPause = viewModel::togglePlayPause,
            onSkipNext = viewModel::skipToNext,
            onSkipPrevious = viewModel::skipToPrevious
        )
    }

    // Dialog: Create New Playlist Folder
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistInput = ""
            },
            containerColor = NeumorphColors.Surface,
            title = {
                Text(
                    text = "New Playlist Folder",
                    color = NeumorphColors.TextCream,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a name for the folder in Music/Playlists:",
                        color = NeumorphColors.TextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassTextField(
                        value = newPlaylistInput,
                        onValueChange = { newPlaylistInput = it },
                        placeholder = "e.g. Chill, Workout, Favorites",
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = NeumorphColors.AccentCopper
                            )
                        }
                    )
                }
            },
            confirmButton = {
                GlassButton(
                    text = "Create",
                    enabled = newPlaylistInput.isNotBlank(),
                    onClick = {
                        viewModel.createPlaylist(newPlaylistInput)
                        newPlaylistInput = ""
                        showCreateDialog = false
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    newPlaylistInput = ""
                }) {
                    Text("Cancel", color = NeumorphColors.TextMuted)
                }
            }
        )
    }

    // Dialog: Add Track to Playlist Folder
    if (trackForPlaylistDialog != null) {
        val targetTrack = trackForPlaylistDialog!!
        AlertDialog(
            onDismissRequest = { trackForPlaylistDialog = null },
            containerColor = NeumorphColors.Surface,
            title = {
                Text(
                    text = "Add to Playlist",
                    color = NeumorphColors.TextCream,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Select a folder playlist for \"${targetTrack.title}\":",
                        color = NeumorphColors.TextMuted,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (playlists.isEmpty()) {
                        Text(
                            text = "No playlists found. Please create a playlist folder first.",
                            color = NeumorphColors.TextFaint,
                            fontSize = 13.sp
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(playlists, key = { it.path }) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NeumorphColors.SurfacePressed)
                                        .clickable {
                                             viewModel.addTrackToPlaylist(targetTrack, playlist) { success ->
                                                 if (success) {
                                                     Toast.makeText(
                                                         context,
                                                         "Added to ${playlist.name}",
                                                         Toast.LENGTH_SHORT
                                                     ).show()
                                                 } else {
                                                     Toast.makeText(
                                                         context,
                                                         "Failed to copy audio file",
                                                         Toast.LENGTH_SHORT
                                                     ).show()
                                                 }
                                             }
                                             trackForPlaylistDialog = null
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = NeumorphColors.AccentCopper,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            color = NeumorphColors.TextCream,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${playlist.trackCount} track(s)",
                                            color = NeumorphColors.TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { trackForPlaylistDialog = null }) {
                    Text("Cancel", color = NeumorphColors.TextMuted)
                }
            }
        )
    }

    // Pop-up Confirmation Dialog: Delete Playlist Folder
    if (playlistToDelete != null) {
        val targetPlaylist = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = NeumorphColors.Surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeumorphColors.StatusError.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = NeumorphColors.StatusError,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Delete Playlist?",
                        color = NeumorphColors.TextCream,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to delete playlist \"${targetPlaylist.name}\"?",
                        color = NeumorphColors.TextCream,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This will permanently remove the physical folder and all ${targetPlaylist.trackCount} track(s) inside:\n${targetPlaylist.path}",
                        color = NeumorphColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = targetPlaylist
                        val playlistName = target.name
                        playlistToDelete = null
                        viewModel.deletePlaylist(target) { success ->
                            if (success) {
                                Toast.makeText(
                                    context,
                                    "Playlist \"$playlistName\" deleted",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to delete \"$playlistName\"",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeumorphColors.StatusError,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Delete",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel", color = NeumorphColors.TextMuted)
                }
            }
        )
    }
}

/**
 * Compact Spotify-inspired Mini-Player
 * Height ~64dp, features top recessed progress bar and extruded neomorphic play/pause.
 */
@Composable
private fun MiniPlayerCard(
    currentTrack: LocalTrack?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    hasSelection: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .neumorphicExtruded(cornerRadius = 18.dp, elevation = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Sleek Recessed Neomorphic Progress Bar at the very top
            val progress = if (durationMs > 0L) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(NeumorphColors.SurfacePressed)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress)
                        .background(
                            Brush.horizontalGradient(
                                listOf(NeumorphColors.AccentCopper, NeumorphColors.AccentCopperLight)
                            )
                        )
                )
            }

            // Mini Player Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact icon indicator
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .neumorphicRecessed(cornerRadius = 10.dp, backgroundColor = NeumorphColors.SurfacePressed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.Album,
                        contentDescription = null,
                        tint = NeumorphColors.AccentCopper,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Track Title & Artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack?.title ?: "Audio Aurora",
                        color = NeumorphColors.TextCream,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentTrack?.artist ?: "Select a track to play",
                        color = if (hasSelection) NeumorphColors.AccentCopperLight else NeumorphColors.TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Previous Button
                IconButton(
                    onClick = onSkipPrevious,
                    enabled = hasSelection,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous track",
                        tint = if (hasSelection) NeumorphColors.TextCream else NeumorphColors.TextMuted.copy(alpha = 0.35f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Extruded Play/Pause Circle
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(NeumorphColors.AccentCopper)
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.4f))
                            ),
                            RoundedCornerShape(21.dp)
                        )
                        .clickable(enabled = hasSelection, onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = NeumorphColors.TextCream,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Next Button
                IconButton(
                    onClick = onSkipNext,
                    enabled = hasSelection,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next track",
                        tint = if (hasSelection) NeumorphColors.TextCream else NeumorphColors.TextMuted.copy(alpha = 0.35f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderPlaylistRow(
    playlist: FolderPlaylist,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicExtruded(
                cornerRadius = 16.dp,
                shape = shape,
                backgroundColor = NeumorphColors.Surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .neumorphicRecessed(cornerRadius = 12.dp, backgroundColor = NeumorphColors.SurfacePressed),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder",
                tint = NeumorphColors.AccentCopper,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = NeumorphColors.TextCream,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.trackCount} track(s) • /Playlists/${playlist.name}",
                color = NeumorphColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete playlist folder",
                tint = NeumorphColors.StatusError,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TrackRow(
    index: Int,
    track: LocalTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isCurrent) NeumorphColors.SurfaceLight
                else NeumorphColors.Surface
            )
            .border(
                width = 1.dp,
                brush = if (isCurrent) {
                    Brush.linearGradient(listOf(NeumorphColors.AccentCopper, NeumorphColors.AccentCopperDark))
                } else {
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.4f)))
                },
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .neumorphicRecessed(cornerRadius = 10.dp, backgroundColor = NeumorphColors.SurfacePressed),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrent && isPlaying) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Now playing",
                    tint = NeumorphColors.AccentCopper,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "${index + 1}",
                    color = if (isCurrent) NeumorphColors.AccentCopperLight else NeumorphColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isCurrent) NeumorphColors.AccentCopperLight else NeumorphColors.TextCream,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.album}",
                color = NeumorphColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatDuration(track.durationMs),
            color = NeumorphColors.TextFaint,
            fontSize = 12.sp
        )
        if (onAddToPlaylist != null) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onAddToPlaylist,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = "Add to playlist",
                    tint = NeumorphColors.AccentCopperLight,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionNoticeCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Audio Permission Required",
            color = NeumorphColors.TextCream,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Grant the \"Music and audio\" permission (or restart the app and accept the prompt) so tracks on this device can be listed and played.",
            color = NeumorphColors.TextMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun LoadingCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color = NeumorphColors.AccentCopper,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Scanning your music library...",
                color = NeumorphColors.TextMuted,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EmptyLibraryCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "No Music Found",
            color = NeumorphColors.TextCream,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Download some songs first, then tap the refresh icon to rescan.",
            color = NeumorphColors.TextMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun EmptyPlaylistsCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "No Playlists Found",
            color = NeumorphColors.TextCream,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Create a physical folder playlist above to organize and play your local music files.",
            color = NeumorphColors.TextMuted,
            fontSize = 13.sp
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}