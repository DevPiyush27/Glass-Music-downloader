package com.example.audiodownloader.domain.model

import java.io.File

/**
 * Represents a physical folder-based playlist stored in the device's storage.
 *
 * @param name       User-defined playlist folder name.
 * @param file       The physical [File] directory on the device storage.
 * @param path       Absolute path to the playlist directory.
 * @param trackCount Number of recognized audio tracks inside this folder.
 * @param tracks     List of resolved [LocalTrack] items within this playlist.
 */
data class FolderPlaylist(
    val name: String,
    val file: File,
    val path: String = file.absolutePath,
    val trackCount: Int = 0,
    val tracks: List<LocalTrack> = emptyList()
)
