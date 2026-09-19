package com.example.audiodownloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.audiodownloader.MainActivity
import com.example.audiodownloader.R

class MusicNotificationManager(
    private val context: Context,
    private val mediaSession: MediaSessionCompat
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Audio Aurora media controls and playback status"
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Cached artwork to prevent decoding Bitmap on rapid state updates
    private var cachedTrackKey: String? = null
    private var cachedBitmap: Bitmap? = null

    private fun getOrLoadArtwork(state: PlayerState, cornerRadiusDp: Float): Bitmap? {
        val trackKey = "${state.title}_${state.artist}_${state.albumArtUri}"
        if (trackKey == cachedTrackKey && cachedBitmap != null) {
            return cachedBitmap
        }
        cachedTrackKey = trackKey
        cachedBitmap = loadBitmapOrPlaceholder(state.albumArtUri, cornerRadiusDp)
        return cachedBitmap
    }

    fun buildNotification(state: PlayerState): Notification {
        val artworkBitmap = getOrLoadArtwork(state, 10f)

        val collapsedView = RemoteViews(context.packageName, R.layout.notification_collapsed).apply {
            setTextViewText(R.id.tv_collapsed_title, state.title.ifBlank { "Audio Aurora" })
            setTextViewText(R.id.tv_collapsed_artist, state.artist.ifBlank { "No Artist" })

            if (artworkBitmap != null) {
                setImageViewBitmap(R.id.iv_collapsed_art, artworkBitmap)
            } else {
                setImageViewResource(R.id.iv_collapsed_art, R.drawable.ic_launcher)
            }

            // Waveform: XML AnimationDrawable when playing, frozen flat bars when paused
            if (state.isPlaying) {
                setImageViewResource(R.id.iv_collapsed_waveform, R.drawable.anim_equalizer)
            } else {
                setImageViewResource(R.id.iv_collapsed_waveform, R.drawable.ic_waveform_frozen)
            }
        }

        val expandedView = RemoteViews(context.packageName, R.layout.notification_expanded).apply {
            setTextViewText(R.id.tv_expanded_title, state.title.ifBlank { "Audio Aurora" })
            setTextViewText(R.id.tv_expanded_artist, state.artist.ifBlank { "Unknown Artist" })
            setTextViewText(R.id.tv_expanded_album, state.album.ifBlank { "Single" })

            if (artworkBitmap != null) {
                setImageViewBitmap(R.id.iv_expanded_art, artworkBitmap)
            } else {
                setImageViewResource(R.id.iv_expanded_art, R.drawable.ic_launcher)
            }

            // Play/Pause icon toggle
            setImageViewResource(
                R.id.btn_notif_play_pause,
                if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )

            // Bind PendingIntents to the 6 buttons
            setOnClickPendingIntent(R.id.btn_notif_prev, actionPendingIntent(MusicAction.PREVIOUS))
            setOnClickPendingIntent(R.id.btn_notif_rewind, actionPendingIntent(MusicAction.REWIND))
            setOnClickPendingIntent(R.id.btn_notif_play_pause, actionPendingIntent(MusicAction.PLAY_PAUSE))
            setOnClickPendingIntent(R.id.btn_notif_forward, actionPendingIntent(MusicAction.FAST_FORWARD))
            setOnClickPendingIntent(R.id.btn_notif_next, actionPendingIntent(MusicAction.NEXT))
            setOnClickPendingIntent(R.id.btn_notif_queue, actionPendingIntent(MusicAction.QUEUE))
        }

        // Tap notification body to launch MainActivity
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_waveform)
            .setColor(0xFFFFB6C1.toInt())
            .setColorized(true)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Non-dismissible while playing; swipeable when paused
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun actionPendingIntent(action: MusicAction): PendingIntent {
        val intent = Intent(context, MusicService::class.java).apply {
            this.action = action.name
        }
        return PendingIntent.getService(
            context,
            action.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun loadBitmapOrPlaceholder(uri: android.net.Uri?, cornerRadiusDp: Float): Bitmap? {
        if (uri == null) return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val rawBitmap = BitmapFactory.decodeStream(stream) ?: return null
                val radiusPx = cornerRadiusDp * context.resources.displayMetrics.density
                getRoundedCornerBitmap(rawBitmap, radiusPx)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Clips the given Bitmap into smooth curved corners with antialiasing (no sharp edges).
     */
    private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadiusPx: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
        }
        val rect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = android.graphics.RectF(rect)

        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }

    companion object {
        const val CHANNEL_ID = "audio_aurora_playback_channel"
        const val NOTIFICATION_ID = 1001
    }
}
