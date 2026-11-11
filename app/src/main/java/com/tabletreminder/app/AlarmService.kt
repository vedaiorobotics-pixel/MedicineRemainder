package com.tabletreminder.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Plays the alarm sound (at media volume) and vibrates until stopped. */
class AlarmService : Service() {

    companion object {
        private const val RING_FOR_MS = 60_000L

        fun ring(ctx: Context) {
            val i = Intent(ctx, AlarmService::class.java)
            try {
                ctx.startForegroundService(i)
            } catch (e: Exception) {
                // If Android refuses to start the sound, at least show the alarm notification.
                Notifs.showRingFallback(ctx)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AlarmService::class.java))
            Notifs.cancelRing(ctx)
        }

        /** A looping player that uses the MEDIA volume. */
        fun createPlayer(ctx: Context): MediaPlayer? {
            val st = Store.settings(ctx)
            val candidates = listOfNotNull(
                st.soundUri?.let { Uri.parse(it) },
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )
            for (uri in candidates) {
                try {
                    val p = MediaPlayer()
                    p.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    p.setDataSource(ctx, uri)
                    p.isLooping = true
                    p.prepare()
                    return p
                } catch (e: Exception) {
                    // try the next sound
                }
            }
            return null
        }

        fun vibrator(ctx: Context): Vibrator =
            if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Vibrator::class.java)
            }
    }

    private var player: MediaPlayer? = null
    private var vib: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifs.ringNotification(this)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Notifs.RING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Notifs.RING_ID, n)
        }

        player?.release()
        player = createPlayer(this)
        player?.start()

        if (Store.settings(this).vibrate) {
            vib = vibrator(this)
            vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        }

        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, RING_FOR_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        player?.release()
        player = null
        vib?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Notifs.updateStatus(this)
        super.onDestroy()
    }
}
