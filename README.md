# Glass Music Downloader 🎵

TLDR; It's a very helpful app which Downloads any song you wish just by 
typing its name. Additionally a very **crazy** feature: it can Download 
your entire Spotify playlist in one click!

> **Note:** Downloads are now saved directly to a custom folder of your 
> choice using Android's secure Scoped Storage — no longer restricted 
> to `Download/Music`.

A native Android music engine and player with an edge-to-edge glassmorphic 
design. Built with Jetpack Compose and powered by an embedded Python engine, 
**Glass** provides seamless audio downloading, lossless transcoding, and 
intelligent offline playback with zero external setup.

---

[![Download APK](https://img.shields.io/badge/Download-APK-10B981?style=for-the-badge&logo=android&logoColor=white)](https://github.com/DevPiyush27/Glass-Music-downloader/releases/download/v.1.5.0/app-debug.apk)

---

### ✨ Key Features

#### 📥 Advanced Media Downloader

* **Effortless Direct Downloads:** Simply type or paste the names of the 
  songs you want — no links or complex queries needed. The app matches 
  the titles and downloads them directly to your storage.
* ⭐ **One-Click Spotify Playlist Extractor:** Paste any public Spotify 
  playlist URL to parse and queue every single track automatically into 
  the downloader engine with a single tap.
* **Lossless Transcoding & Metadata:** Directly extracts high-quality 
  audio streams without heavy external server dependencies. Uses native 
  FFmpeg to embed thumbnails as album art and injects Artist/Title 
  metadata directly into the files.
* **Secure "Cache-and-Copy" Storage:** Respects modern Android Scoped 
  Storage (SAF). Downloads safely process in an internal cache before 
  being seamlessly copied to your chosen persistent local folder, 
  preventing corrupted downloads.

#### 🎧 Intelligent Local Player

* **Continuous Auto-Play:** Build custom listening queues. When the custom 
  queue finishes, the player intelligently calculates the timeline and 
  seamlessly falls back to your main library so the music never stops.
* **Smart Media Controls:** The "Previous Track" button mimics 
  industry-standard players (restarts the song if past 3 seconds, or 
  skips back if at the beginning). Dedicated `-10s` rewind and `+10s` 
  forward buttons are mathematically bounded for smooth, crash-free 
  scrubbing.
* **System Audio Integration:** Features dynamic Audio Focus (auto-pauses 
  for calls, other media, or headphone disconnects) and a custom 
  Foreground Service notification with an animated waveform equalizer.
* **Modern Glassmorphic UI:** Smooth Compose animations, customizable 
  audio bitrate presets, and automatic media indexing that scans 
  downloaded files straight into your device's MediaStore.

#### 🛠 Under the Hood

* **Memory Leak Patched:** Resolved a critical process-level memory leak 
  by nullifying singleton callbacks (`MusicStateBridge`) and cleanly 
  releasing ExoPlayer instances during the ViewModel's `onCleared()` 
  lifecycle.
* **Playback Crash Prevention:** Fixed an `IllegalSeekPositionException` 
  by implementing manual fallback bounds checking. The app now gracefully 
  handles edge cases where the custom Jetpack Compose queue falls out of 
  sync with ExoPlayer's internal timeline.

---

### 📥 Getting Started

1. Tap the **[Download APK](https://github.com/DevPiyush27/Glass-Music-downloader/releases/download/v.1.5.0/app-debug.apk)** 
   button above.
2. Install the `.apk` on your Android device 
   (enable *Install unknown apps* if prompted).
3. Open **Glass Music Downloader**, select your preferred download folder, 
   paste a list of song titles or a Spotify playlist link, and hit 
   **Download All**.
