package com.example.audiodownloader.data.repository

import com.example.audiodownloader.data.scraper.SpotifyPlaylistScraper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

interface ISpotifyScraperRepository {
    suspend fun getPlaylistTrackQueries(playlistUrlOrId: String): Result<List<String>>
}

class SpotifyScraperRepository(
    private val scraper: SpotifyPlaylistScraper = SpotifyPlaylistScraper(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ISpotifyScraperRepository {

    override suspend fun getPlaylistTrackQueries(playlistUrlOrId: String): Result<List<String>> =
        withContext(ioDispatcher) {
            try {
                val cleanedInput = playlistUrlOrId.trim()
                if (cleanedInput.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Playlist URL cannot be empty"))
                }

                // Extract 22-char ID or verify direct ID input
                val playlistId = if (cleanedInput.length == 22 && cleanedInput.matches(Regex("[a-zA-Z0-9]{22}"))) {
                    cleanedInput
                } else {
                    scraper.extractPlaylistId(cleanedInput)
                }

                if (playlistId.isNullOrEmpty()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid Spotify Playlist URL. Expected format: https://open.spotify.com/playlist/{PLAYLIST_ID}")
                    )
                }

                val tracks = scraper.fetchPlaylistTracks(playlistId)
                if (tracks.isEmpty()) {
                    Result.failure(NoSuchElementException("No tracks found in the specified playlist or the playlist is private."))
                } else {
                    Result.success(tracks)
                }
            } catch (e: IOException) {
                Result.failure(IOException("Network error occurred while fetching playlist: ${e.localizedMessage}", e))
            } catch (e: Exception) {
                Result.failure(Exception("Failed to extract Spotify playlist: ${e.localizedMessage}", e))
            }
        }
}