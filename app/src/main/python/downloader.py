import os
import yt_dlp

def download_audio(query, output_dir="/storage/emulated/0/Download", bitrate="128", callback=None):
    """
    Downloads audio using yt-dlp targeting YouTube Music with the specified quality.
    
    :param query: Song title / artist search string or direct URL.
    :param output_dir: Public storage destination folder.
    :param bitrate: Target bitrate string ('48', '128', '256').
    :param callback: Chaquopy Java/Kotlin callback instance.
    """
    os.makedirs(output_dir, exist_ok=True)
    out_tmpl = os.path.join(output_dir, "%(title)s.%(ext)s")

    def progress_hook(d):
        if callback is None:
            return

        status = d.get('status', '')
        if status == 'downloading':
            total_bytes = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
            downloaded = d.get('downloaded_bytes', 0)
            percent = (downloaded / total_bytes * 100.0) if total_bytes > 0 else 0.0
            speed = d.get('speed', 0.0) or 0.0
            filename = d.get('filename', '')
            
            # Format speed to readable string
            speed_str = f"{speed / 1024 / 1024:.2f} MB/s" if speed > 1024 * 1024 else f"{speed / 1024:.1f} KB/s"
            
            try:
                callback.onProgress("downloading", float(percent), speed_str, os.path.basename(filename))
            except Exception as e:
                print(f"Progress callback error: {e}")

        elif status == 'finished':
            filename = d.get('filename', '')
            try:
                callback.onProgress("converting", 100.0, "0 KB/s", os.path.basename(filename))
            except Exception as e:
                print(f"Finished callback error: {e}")

    def postprocessor_hook(d):
        if callback is None:
            return
        pp_status = d.get('status', '')
        if pp_status == 'started':
            try:
                callback.onProgress("converting", 100.0, "0 KB/s", "")
            except Exception:
                pass

    ydl_opts = {
        'default_search': 'ytmsearch',
        'format': 'bestaudio/best',
        'outtmpl': out_tmpl,
        'quiet': True,
        'no_warnings': True,
        'nocheckcertificate': True,
        'writethumbnail': True,
        'postprocessors': [
            {
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'mp3',
                'preferredquality': str(bitrate),
            },
            {
                'key': 'FFmpegMetadata',
                'add_metadata': True,
            },
            {
                'key': 'EmbedThumbnail',
            }
        ],
        'progress_hooks': [progress_hook],
        'postprocessor_hooks': [postprocessor_hook]
    }

    try:
        if callback:
            callback.onProgress("queued", 0.0, "0 KB/s", "")

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(query, download=True)
            title = info.get('title', query) if info else query

            if callback:
                callback.onProgress("completed", 100.0, "0 KB/s", f"{title}.mp3")
            return {"success": True, "title": title}
    except Exception as e:
        error_msg = str(e)
        if callback:
            callback.onError(error_msg)
        return {"success": False, "error": error_msg}