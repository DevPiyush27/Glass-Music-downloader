package com.example.audiodownloader.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.ServiceCompat
import androidx.media.MediaBrowserServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Foreground MediaBrowserServiceCompat hosting the MediaSession and managing the RemoteViews notification.
 */
class MusicService : MediaBrowserServiceCompat() {

    // CoroutineScope strictly tied to the Service lifecycle
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: MusicNotificationManager

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "AudioAuroraSession").apply {
            isActive = true
        }
        sessionToken = mediaSession.sessionToken

        notificationManager = MusicNotificationManager(this, mediaSession)

        // Observe playerState emitted by ViewModel, throttled for meaningful visual changes only
        serviceScope.launch {
            MusicStateBridge.playerState
                .distinctUntilChanged { old, new ->
                    old.title == new.title &&
                    old.artist == new.artist &&
                    old.album == new.album &&
                    old.albumArtUri == new.albumArtUri &&
                    old.isPlaying == new.isPlaying
                }
                .collectLatest { state ->
                    updateNotification(state)
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { actionName ->
            try {
                val action = MusicAction.valueOf(actionName)
                // Dispatches notification button press to the ViewModel
                MusicStateBridge.dispatchAction(action)
            } catch (_: IllegalArgumentException) {}
        }
        return START_NOT_STICKY
    }

    private fun updateNotification(state: PlayerState) {
        // If queue is empty and player is idle, dismiss foreground notification
        if (state.title.isBlank() && !state.isPlaying) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            return
        }

        val notification = notificationManager.buildNotification(state)

        if (state.isPlaying) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    MusicNotificationManager.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(MusicNotificationManager.NOTIFICATION_ID, notification)
            }
        } else {
            // When paused: detach from foreground to make notification swipeable/dismissible
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(MusicNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        return BrowserRoot("AUDIO_AURORA_ROOT", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(mutableListOf())
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // 1. Tell ExoPlayer to stop playback immediately
        MusicStateBridge.dispatchAction(MusicAction.STOP)

        // 2. Remove notification and terminate foreground status
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        val manager = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
        manager?.cancel(MusicNotificationManager.NOTIFICATION_ID)

        // 3. Stop this service
        stopSelf()
    }

    override fun onDestroy() {
        // Cancel CoroutineScope when service is destroyed
        serviceScope.cancel()
        mediaSession.isActive = false
        mediaSession.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        val manager = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
        manager?.cancel(MusicNotificationManager.NOTIFICATION_ID)
        super.onDestroy()
    }
}
