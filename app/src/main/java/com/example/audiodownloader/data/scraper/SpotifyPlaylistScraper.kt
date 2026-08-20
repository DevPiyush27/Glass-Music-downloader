package com.example.audiodownloader.data.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.util.regex.Pattern

class SpotifyPlaylistScraper(
    private val client: OkHttpClient = OkHttpClient()
) {

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private val PLAYLIST_ID_PATTERN = Pattern.compile("playlist/([a-zA-Z0-9]{22})")
    }

    /**
     * Sanitizes input and extracts the 22-character alphanumeric Spotify Playlist ID.
     */
    fun extractPlaylistId(url: String): String? {
        val matcher = PLAYLIST_ID_PATTERN.matcher(url)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    /**
     * Fetches the Spotify embed HTML and parses track items into search queries.
     */
    suspend fun fetchPlaylistTracks(playlistId: String): List<String> = withContext(Dispatchers.IO) {
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"

        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Failed to load playlist embed. HTTP Code: ${response.code}")
        }

        val htmlBody = response.body?.string()
            ?: throw IOException("Empty response body received from Spotify")

        val rawJson = extractNextDataJson(htmlBody)
            ?: throw IllegalStateException("Could not locate __NEXT_DATA__ script payload in embed HTML")

        parseTrackListFromJson(rawJson)
    }

    /**
     * Extracts the raw JSON string from <script id="__NEXT_DATA__" type="application/json"> using Jsoup.
     */
    private fun extractNextDataJson(html: String): String? {
        val document = Jsoup.parse(html)
        val scriptTag = document.selectFirst("script#__NEXT_DATA__[type=application/json]")
            ?: document.getElementById("__NEXT_DATA__")
        return scriptTag?.data() ?: scriptTag?.html()
    }

    /**
     * Traverses the JSON tree: props -> pageProps -> state -> data -> entity -> trackList
     * and extracts formatted search strings ("Track Name Artist Name").
     */
    private fun parseTrackListFromJson(jsonString: String): List<String> {
        val trackQueries = mutableListOf<String>()
        val root = JSONObject(jsonString)

        val props = root.optJSONObject("props") ?: return emptyList()
        val pageProps = props.optJSONObject("pageProps") ?: return emptyList()
        val state = pageProps.optJSONObject("state") ?: return emptyList()
        val data = state.optJSONObject("data") ?: return emptyList()
        val entity = data.optJSONObject("entity") ?: return emptyList()
        val trackList = entity.optJSONArray("trackList") ?: return emptyList()

        for (i in 0 until trackList.length()) {
            val trackItem = trackList.optJSONObject(i) ?: continue

            // Spotify embeds typically store title in 'title' or 'name'
            val title = trackItem.optString("title").ifEmpty {
                trackItem.optString("name")
            }.trim()

            // Artists can be represented as 'subtitle' or an array under 'artists'
            var artist = trackItem.optString("subtitle").trim()
            if (artist.isEmpty()) {
                val artistsArray = trackItem.optJSONArray("artists")
                if (artistsArray != null && artistsArray.length() > 0) {
                    val artistNames = mutableListOf<String>()
                    for (j in 0 until artistsArray.length()) {
                        val artistObj = artistsArray.optJSONObject(j)
                        val name = artistObj?.optString("name") ?: artistsArray.optString(j)
                        if (!name.isNullOrBlank()) {
                            artistNames.add(name)
                        }
                    }
                    artist = artistNames.joinToString(", ")
                }
            }

            if (title.isNotEmpty()) {
                val query = if (artist.isNotEmpty()) "$title $artist" else title
                trackQueries.add(query)
            }
        }

        return trackQueries
    }
}