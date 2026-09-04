import os
import shutil
import yt_dlp

def download_audio(query, output_dir=None, bitrate="normal", callback=None):
    if not output_dir:
        output_dir = "/storage/emulated/0/Download/Music"
        
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
            
            speed_str = f"{speed / 1024 / 1024:.2f} MB/s" if speed > 1024 * 1024 else f"{speed / 1024:.1f} KB/s"
            
            try:
                callback.onProgress("downloading", float(percent), speed_str, os.path.basename(filename))
            except Exception:
                pass

        elif status == 'finished':
            filename = d.get('filename', '')
            try:
                callback.onProgress("completed", 100.0, "0 KB/s", os.path.basename(filename))
            except Exception:
                pass

    # Normalize plain search terms to standard ytsearch1
    search_target = query.strip()
    if not (search_target.startswith("http://") or search_target.startswith("https://") or search_target.startswith("ytsearch")):
        search_target = f"ytsearch1:{search_target} audio"

    # Determine format spec based on requested quality:
    # High: itag 251 (~160kbps Opus, YouTube's highest quality audio stream)
    # Normal: itag 140 (~128kbps AAC in .m4a)
    # Low: lowest quality stream (itag 249/250/139 or worstaudio)
    quality_str = str(bitrate).lower().strip() if bitrate else "normal"

    has_ffmpeg = shutil.which("ffmpeg") is not None

    postprocessors = []

    if quality_str in ["high", "320", "251", "best", "opus"]:
        format_spec = "251/bestaudio[acodec=opus]/bestaudio/ba"
        if has_ffmpeg:
            postprocessors.append({
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'opus',
            })
    elif quality_str in ["low", "64", "50", "249", "250", "worst", "lowest", "datasaver"]:
        format_spec = "249/250/139/worstaudio/ba[abr<=70]/worst"
        if has_ffmpeg:
            postprocessors.append({
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'opus',
            })
    else:  # Normal quality: itag 140 (128kbps AAC M4A)
        format_spec = "140/ba[ext=m4a]/bestaudio[ext=m4a]/ba[abr<=160]/ba"

    ydl_opts = {
        'format': format_spec,
        'outtmpl': out_tmpl,
        'quiet': True,
        'no_warnings': True,
        'nocheckcertificate': True,
        'writethumbnail': False,
        'noplaylist': True,
        'progress_hooks': [progress_hook]
    }

    if postprocessors:
        ydl_opts['postprocessors'] = postprocessors

    try:
        if callback:
            callback.onProgress("queued", 0.0, "0 KB/s", "")

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(search_target, download=True)
            if info and 'entries' in info:
                entries = list(info['entries']) if info['entries'] else []
                if entries:
                    info = entries[0]
            
            title = info.get('title', query) if info else query
            
            # Resolve actual final file path (accounting for FFmpeg remuxing to .opus)
            final_file = None
            if info:
                requested = info.get('requested_downloads')
                if requested and len(requested) > 0:
                    final_file = requested[0].get('filepath')
                
                if not final_file or not os.path.exists(final_file):
                    prep = ydl.prepare_filename(info)
                    base, _ = os.path.splitext(prep)
                    if os.path.exists(base + ".opus"):
                        final_file = base + ".opus"
                    elif os.path.exists(prep):
                        final_file = prep
                    else:
                        final_file = prep
            else:
                final_file = f"{title}.m4a"

            if callback:
                callback.onProgress("completed", 100.0, "0 KB/s", os.path.basename(final_file))
                
            return {"success": True, "title": title, "filename": final_file}
    except Exception as e:
        error_msg = str(e)
        if callback:
            callback.onError(error_msg)
        return {"success": False, "error": error_msg}


def extract_stream_url(query):
    """
    Extracts direct audio stream URL and track metadata for in-app playback without downloading.
    """
    search_target = query.strip()
    if not (search_target.startswith("http://") or search_target.startswith("https://")):
        search_target = f"ytsearch1:{search_target} audio"

    ydl_opts = {
        'format': '251/140/bestaudio/ba',
        'quiet': True,
        'no_warnings': True,
        'nocheckcertificate': True,
        'noplaylist': True,
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(search_target, download=False)
            if info and 'entries' in info and len(info['entries']) > 0:
                info = info['entries'][0]

            if not info:
                return {"success": False, "error": "No stream format available"}

            stream_url = info.get('url') or ''
            title = info.get('title', query)
            artist = info.get('uploader') or info.get('artist') or ''
            duration = info.get('duration', 0)
            thumbnail = info.get('thumbnail', '')

            return {
                "success": True,
                "url": stream_url,
                "title": title,
                "artist": artist,
                "duration": duration,
                "thumbnail": thumbnail
            }
    except Exception as e:
        return {"success": False, "error": str(e)}

