package com.xpdfviewer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.xpdfviewer.MainActivity
import com.xpdfviewer.R

class AudioPlaybackService : Service() {

    interface AudioPlaybackCommander {
        fun onTogglePlayPause()
        fun onSkipNext()
        fun onSkipPrevious()
        fun onStop()
    }

    companion object {
        const val CHANNEL_ID = "xpdf_audiobook_channel"
        const val CHANNEL_NAME = "Audiobook Playback"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.xpdfviewer.action.AUDIO_START"
        const val ACTION_UPDATE = "com.xpdfviewer.action.AUDIO_UPDATE"
        const val ACTION_PLAY_PAUSE = "com.xpdfviewer.action.AUDIO_PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "com.xpdfviewer.action.AUDIO_SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.xpdfviewer.action.AUDIO_SKIP_PREV"
        const val ACTION_STOP = "com.xpdfviewer.action.AUDIO_STOP"
        const val ACTION_STOP_SERVICE = "com.xpdfviewer.action.AUDIO_STOP_SERVICE"
        const val ACTION_USER_STOP = "com.xpdfviewer.action.AUDIO_USER_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PAGE_INFO = "extra_page_info"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        var commander: AudioPlaybackCommander? = null

        fun start(context: Context, title: String, pageInfo: String, isPlaying: Boolean) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PAGE_INFO, pageInfo)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(context: Context, title: String, pageInfo: String, isPlaying: Boolean) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PAGE_INFO, pageInfo)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    private var currentTitle: String = "xPDF Document"
    private var currentPageInfo: String = "Playing"
    private var isPlaying: Boolean = true
    private var mediaSession: MediaSession? = null
    private var isStopping: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "xPDF_Audiobook_Session").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    commander?.onTogglePlayPause()
                }

                override fun onPause() {
                    commander?.onTogglePlayPause()
                }

                override fun onSkipToNext() {
                    commander?.onSkipNext()
                }

                override fun onSkipToPrevious() {
                    commander?.onSkipPrevious()
                }

                override fun onStop() {
                    commander?.onStop()
                    stopForegroundService()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isStopping = false
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentPageInfo = intent.getStringExtra(EXTRA_PAGE_INFO) ?: currentPageInfo
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                updateMediaSessionState()
                startInForeground()
            }
            ACTION_UPDATE -> {
                if (!isStopping) {
                    currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                    currentPageInfo = intent.getStringExtra(EXTRA_PAGE_INFO) ?: currentPageInfo
                    isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
                    updateMediaSessionState()
                    updateNotification()
                }
            }
            ACTION_PLAY_PAUSE -> {
                commander?.onTogglePlayPause()
            }
            ACTION_SKIP_NEXT -> {
                commander?.onSkipNext()
            }
            ACTION_SKIP_PREV -> {
                commander?.onSkipPrevious()
            }
            ACTION_USER_STOP -> {
                commander?.onStop()
                stopForegroundService()
            }
            ACTION_STOP_SERVICE, ACTION_STOP -> {
                // Programmatic stop from ViewModel: do NOT echo back to commander.onStop() to prevent infinite loops
                stopForegroundService()
            }
        }
        return START_NOT_STICKY
    }

    private fun updateMediaSessionState() {
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_STOP
            )
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundService() {
        isStopping = true
        mediaSession?.isActive = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification Actions
        val prevIntent = Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_SKIP_PREV }
        val prevPending = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playPauseIntent = Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePending = PendingIntent.getService(this, 2, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_SKIP_NEXT }
        val nextPending = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_USER_STOP }
        val stopPending = PendingIntent.getService(this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentPageInfo)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(playPauseIcon, playPauseTitle, playPausePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and status for continuous document audio reading"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
