package com.example.floatingidiot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.random.Random

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private val overlayViews = mutableListOf<View>()
    private val overlayParams = mutableListOf<WindowManager.LayoutParams>()
    private val velocitiesX = mutableListOf<Float>()
    private val velocitiesY = mutableListOf<Float>()

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val NOTIF_ID = 1
    private val CHANNEL_ID = "floating_channel"
    private val ACTION_STOP = "com.example.floatingidiot.STOP_ALL"

    private var screenW = 0
    private var screenH = 0
    private val spawnDelay = 400L

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP) {
                stopAllWindows()
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels

        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.idiot_song)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, IntentFilter(ACTION_STOP), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, IntentFilter(ACTION_STOP))
        }

        spawnLoop()
        handler.post(mover)
    }

    private fun spawnLoop() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                spawnWindow()
                handler.postDelayed(this, spawnDelay)
            }
        }, 0)
    }

    private fun spawnWindow() {
        try {
            val view = LayoutInflater.from(this).inflate(R.layout.overlay_window, null)

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = Random.nextInt(0, maxOf(1, screenW - 200))
            params.y = Random.nextInt(0, maxOf(1, screenH - 200))

            windowManager.addView(view, params)

            overlayViews.add(view)
            overlayParams.add(params)
            velocitiesX.add(if (Random.nextBoolean()) 8f else -8f)
            velocitiesY.add(if (Random.nextBoolean()) 8f else -8f)
        } catch (e: Exception) {}
    }

    private val mover = object : Runnable {
        override fun run() {
            for (i in overlayViews.indices) {
                try {
                    val view = overlayViews[i]
                    val params = overlayParams[i]

                    params.x += velocitiesX[i].toInt()
                    params.y += velocitiesY[i].toInt()

                    if (params.x <= 0) { params.x = 0; velocitiesX[i] = -velocitiesX[i] }
                    if (params.y <= 0) { params.y = 0; velocitiesY[i] = -velocitiesY[i] }
                    if (params.x + view.width >= screenW) {
                        params.x = screenW - view.width
                        velocitiesX[i] = -velocitiesX[i]
                    }
                    if (params.y + view.height >= screenH) {
                        params.y = screenH - view.height
                        velocitiesY[i] = -velocitiesY[i]
                    }

                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {}
            }
            handler.postDelayed(this, 16)
        }
    }

    private fun stopAllWindows() {
        handler.removeCallbacksAndMessages(null)
        for (view in overlayViews) {
            try { windowManager.removeView(view) } catch (e: Exception) {}
        }
        overlayViews.clear()
        overlayParams.clear()
        velocitiesX.clear()
        velocitiesY.clear()

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllWindows()
        try { unregisterReceiver(stopReceiver) } catch (e: Exception) {}
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Floating",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(ACTION_STOP).setPackage(packageName)
        val stopPending = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating окна")
            .setContentText("Работает. Нажми STOP, чтобы закрыть все.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPending)
            .build()
    }
}
