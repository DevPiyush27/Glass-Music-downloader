import os
import shutil
import subprocess
import yt_dlp
from yt_dlp.postprocessor.metadataparser import MetadataParserPP


def get_ffmpeg_paths(custom_binary=None, custom_lib_dir=None):
    """
    Resolves the FFmpeg binary and supporting libraries path on Android or desktop.
    """
    binary = custom_binary
    lib_dir = custom_lib_dir

    if not binary:
        try:
            from com.chaquo.python import Python
            context = Python.getPlatform().getApplication()
            if context:
                native_dir = context.getApplicationInfo().nativeLibraryDir
                candidate = os.path.join(native_dir, "libffmpeg.so")
                if os.path.exists(candidate):
                    binary = candidate
                if not lib_dir:
                    candidate_lib = os.path.join(
                        context.getNoBackupFilesDir().getAbsolutePath(),
                        "youtubedl-android", "packages", "ffmpeg", "usr", "lib"
                    )
                    if os.path.exists(candidate_lib):
                        lib_dir = candidate_lib
        except Exception:
            pass

    if not binary:
        binary = shutil.which("ffmpeg")

    return binary, lib_dir


def setup_ffmpeg_environment(ffmpeg_bin, ffmpeg_lib_dir):
    """
    Configures PATH and LD_LIBRARY_PATH so that the bundled libffmpeg.so
    can locate its shared dependencies and execute properly.
    """
    if not ffmpeg_bin:
        return

    bin_dir = os.path.dirname(os.path.abspath(ffmpeg_bin))

    # Ensure binary directory is in PATH
    current_path = os.environ.get("PATH", "")
    path_entries = current_path.split(os.pathsep) if current_path else []
    if bin_dir not in path_entries:
        os.environ["PATH"] = f"{bin_dir}{os.pathsep}{current_path}" if current_path else bin_dir

    # Ensure lib directory and bin directory are in LD_LIBRARY_PATH
    ld_paths = []
    if ffmpeg_lib_dir and os.path.exists(ffmpeg_lib_dir):
        ld_paths.append(ffmpeg_lib_dir)
    if bin_dir and os.path.exists(bin_dir):
        ld_paths.append(bin_dir)

    if ld_paths:
        current_ld = os.environ.get("LD_LIBRARY_PATH", "")
        new_ld = os.pathsep.join(ld_paths)
        if current_ld:
            os.environ["LD_LIBRARY_PATH"] = f"{new_ld}{os.pathsep}{current_ld}"
        else:
            os.environ["LD_LIBRARY_PATH"] = new_ld


def is_webm_file(filepath):
    """
    Checks if a file is a WebM container by extension or by EBML container header bytes.
    """
    if not filepath or not os.path.exists(filepath):
        return False
    if filepath.lower().endswith(".webm"):
        return True
    try:
        with open(filepath, "rb") as f:
            header = f.read(64)
            if header.startswith(b'\x1a\x45\xdf\xa3') and b'webm' in header:
                return True
    except Exception:
        pass
    return False


def convert_audio_to_m4a(ydl, audio_path, ffmpeg_path=None, info=None, target_bitrate="256"):
    """
    Converts audio file to .m4a locally using yt-dlp FFmpeg post-processing
    with a direct ffmpeg execution fallback, preserving and embedding all metadata tags
    and enforcing constant bitrate (CBR) target_bitrate.
    """
    base, ext = os.path.splitext(audio_path)
    if ext.lower() == ".m4a":
        return audio_path

    target_m4a = base + ".m4a"
    converted = False

    # 1. Use yt-dlp's FFmpegExtractAudioPP post-processor
    try:
        from yt_dlp.postprocessor import FFmpegExtractAudioPP
        pp = FFmpegExtractAudioPP(downloader=ydl, preferredcodec='m4a', preferredquality=target_bitrate)
        info_dict = {
            'filepath': audio_path,
            'ext': ext.lstrip('.'),
        }
        if info:
            info_dict.update(info)
            info_dict['filepath'] = audio_path
            info_dict['ext'] = ext.lstrip('.')
        _, new_info = pp.run(info_dict)
        out_file = new_info.get('filepath') or target_m4a
        if os.path.exists(out_file) and os.path.getsize(out_file) > 0:
            converted = True
            target_m4a = out_file
    except Exception as e:
        print(f"yt-dlp post-processor conversion warning: {e}")

    # 2. Fallback to executing the ffmpeg binary directly if needed
    if not converted or not os.path.exists(target_m4a):
        executable = ffmpeg_path or shutil.which("ffmpeg")
        if executable and os.path.exists(executable):
            cmd = [
                executable, "-y",
                "-i", audio_path,
                "-vn",
                "-c:a", "aac",
                "-b:a", f"{target_bitrate}k",
                "-map_metadata", "0",
                target_m4a
            ]
            res = subprocess.run(cmd, capture_output=True, text=True)
            if res.returncode == 0 and os.path.exists(target_m4a) and os.path.getsize(target_m4a) > 0:
                converted = True

    # 3. Ensure metadata tags are embedded into the converted M4A file
    if converted and os.path.exists(target_m4a) and info:
        try:
            from yt_dlp.postprocessor import FFmpegMetadataPP
            meta_pp = FFmpegMetadataPP(downloader=ydl, add_metadata=True, add_chapters=True)
            m4a_info = dict(info)
            m4a_info['filepath'] = target_m4a
            m4a_info['ext'] = 'm4a'
            meta_pp.run(m4a_info)
        except Exception as meta_err:
            print(f"Warning embedding metadata to M4A: {meta_err}")

    if converted and os.path.exists(target_m4a):
        # Clean up the original file if it still exists
        if os.path.exists(audio_path) and os.path.abspath(audio_path) != os.path.abspath(target_m4a):
            try:
                os.remove(audio_path)
            except Exception:
                pass
        orig_candidate = base + ".orig.webm"
        if os.path.exists(orig_candidate):
            try:
                os.remove(orig_candidate)
            except Exception:
                pass
        return target_m4a

    return audio_path


def convert_webm_to_flac(ydl, webm_path, ffmpeg_path=None, info=None, target_bitrate="256"):
    # Backward compatible alias routing to convert_audio_to_m4a
    return convert_audio_to_m4a(ydl, webm_path, ffmpeg_path=ffmpeg_path, info=info, target_bitrate=target_bitrate)


def resolve_quality_config(bitrate_param):
    """
    Maps bitrate parameter (from Kotlin or CLI) to (target_bitrate_str, format_spec).
    Floor is 160 kbps, ceiling is 360 kbps:
    - 360 kbps: 'bestaudio/best', target 360k
    - 256 kbps: 'bestaudio/best', target 256k
    - 160 kbps: 'bestaudio/best', target 160k
    """
    raw = str(bitrate_param).lower().strip() if bitrate_param is not None else "256"
    cleaned = raw.replace("kbps", "").replace("k", "").strip()

    if cleaned in ["360", "high", "ultra", "ultra high", "max"]:
        return "360", "bestaudio/best"
    elif cleaned in ["160", "standard", "low", "min", "minimum"]:
        return "160", "bestaudio/best"
    elif cleaned in ["256", "normal", "medium", "default"]:
        return "256", "bestaudio/best"
    else:
        try:
            val = int(cleaned)
            if val >= 320:
                return "360", "bestaudio/best"
            elif val <= 180:
                return "160", "bestaudio/best"
            else:
                return "256", "bestaudio/best"
        except ValueError:
            return "256", "bestaudio/best"


def download_audio(query, output_dir=None, bitrate="256", callback=None, ffmpeg_location=None, ffmpeg_lib_dir=None):
    if not output_dir:
        output_dir = "/storage/emulated/0/Download/Music"
        
    os.makedirs(output_dir, exist_ok=True)
    out_tmpl = os.path.join(output_dir, "%(title)s.%(ext)s")

    ffmpeg_bin, resolved_lib_dir = get_ffmpeg_paths(ffmpeg_location, ffmpeg_lib_dir)
    if ffmpeg_bin:
        setup_ffmpeg_environment(ffmpeg_bin, resolved_lib_dir)

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

    target_br = str(bitrate).replace("kbps", "").replace("k", "").strip() if bitrate else "256"
    target_bitrate, format_spec = resolve_quality_config(target_br)

    postprocessors = [
        {
            'key': 'MetadataParser',
            'when': 'pre_process',
            'actions': [
                (MetadataParserPP.Actions.INTERPRET, 'title', '%(artist)s - %(title)s'),
                (MetadataParserPP.Actions.REPLACE, 'artist', r' - Topic$', ''),
            ]
        },
        {
            'key': 'FFmpegExtractAudio',
            'preferredcodec': 'm4a',
            'preferredquality': target_bitrate,
        },
        {
            'key': 'FFmpegThumbnailsConvertor',
            'format': 'jpg'
        },
        {
            'key': 'FFmpegMetadata',
            'add_metadata': True,
            'add_chapters': True
        },
        {
            'key': 'EmbedThumbnail',
            'already_have_thumbnail': False
        }
    ]

    ydl_opts = {
        'format': 'bestaudio/best',
        'outtmpl': out_tmpl,
        'quiet': True,
        'no_warnings': True,
        'nocheckcertificate': True,
        'writethumbnail': True,
        'noplaylist': True,
        'parse_metadata': ['title:%(artist)s - %(title)s'],
        'progress_hooks': [progress_hook],
        'postprocessors': postprocessors,
        'postprocessor_args': {
            'extractaudio': ['-b:a', f'{target_bitrate}k'],
            'ExtractAudio': ['-b:a', f'{target_bitrate}k']
        }
    }

    if ffmpeg_bin:
        ydl_opts['ffmpeg_location'] = ffmpeg_bin

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
            
            # Resolve actual final file path
            final_file = None
            if info:
                requested = info.get('requested_downloads')
                if requested and len(requested) > 0:
                    final_file = requested[0].get('filepath')
                
                if not final_file or not os.path.exists(final_file):
                    prep = ydl.prepare_filename(info)
                    base, _ = os.path.splitext(prep)
                    for candidate_ext in [".m4a", ".flac", ".opus", ".mp3", ".webm"]:
                        if os.path.exists(base + candidate_ext):
                            final_file = base + candidate_ext
                            break
                    if not final_file or not os.path.exists(final_file):
                        final_file = prep if os.path.exists(prep) else prep
            else:
                final_file = f"{title}.m4a"

            # Check if file container needs transcoding to .m4a with target CBR bitrate
            if final_file and (is_webm_file(final_file) or not final_file.lower().endswith(".m4a")):
                try:
                    converted_file = convert_audio_to_m4a(ydl, final_file, ffmpeg_path=ffmpeg_bin, info=info, target_bitrate=target_bitrate)
                    if converted_file and os.path.exists(converted_file):
                        final_file = converted_file
                except Exception as conv_err:
                    print(f"Error converting audio to m4a: {conv_err}")

            if callback:
                callback.onProgress("completed", 100.0, "0 KB/s", os.path.basename(final_file))
                
            return {
                "success": True,
                "title": title,
                "filename": final_file,
                "bitrate": target_bitrate
            }
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
