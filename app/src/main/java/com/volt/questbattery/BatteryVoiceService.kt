package com.volt.questbattery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Locale

class BatteryVoiceService : Service() {

    companion object {
        const val CHANNEL_ID = "volt_bg_channel"
        const val NOTIF_ID = 1001
        const val ACTION_BATTERY_UPDATED = "com.volt.questbattery.BATTERY_UPDATED"
        const val EXTRA_PERCENT = "extra_percent"
        const val EXTRA_STATUS = "extra_status"
        const val OVERLAY_DURATION_MS = 3000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRunning = false

    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private val hideOverlayRunnable = Runnable { removeOverlay() }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        val notification = buildNotification("VOLT работает в фоне", "Скажи «батарея» или «заряд»")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }

        mainHandler.post { startListeningLoop() }
        return START_STICKY
    }

    private fun canDrawOverlay(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun showBatteryOverlay(text: String) {
        if (!canDrawOverlay()) {
            notifyActivity(status = "Включи «Отображение поверх других приложений» для VOLT")
            return
        }

        overlayHandler.post {
            overlayHandler.removeCallbacks(hideOverlayRunnable)

            val wm = windowManager ?: return@post
            val existing = overlayView

            if (existing != null) {
                existing.text = text
                overlayHandler.postDelayed(hideOverlayRunnable, OVERLAY_DURATION_MS)
                return@post
            }

            val density = resources.displayMetrics.density
            val padH = (18 * density).toInt()
            val padV = (10 * density).toInt()
            val radius = (16 * density).toInt()

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#E614141C"))
                cornerRadius = radius.toFloat()
                setStroke((1.5f * density).toInt(), Color.parseColor("#9B5DE5"))
            }

            val tv = TextView(this).apply {
                this.text = text
                setTextColor(Color.WHITE)
                textSize = 20f
                setPadding(padH, padV, padH, padV)
                background = bg
                gravity = Gravity.CENTER
                elevation = 12f
            }

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (90 * density).toInt()
            }

            try {
                wm.addView(tv, params)
                overlayView = tv
                overlayHandler.postDelayed(hideOverlayRunnable, OVERLAY_DURATION_MS)
            } catch (_: Exception) {
                notifyActivity(status = "Не удалось показать окно поверх экрана")
            }
        }
    }

    private fun removeOverlay() {
        overlayHandler.removeCallbacks(hideOverlayRunnable)
        val view: View = overlayView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        } finally {
            overlayView = null
        }
    }

    private fun getBatteryPercent(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return -1
        return (level * 100) / scale
    }

    private fun notifyActivity(percent: Int? = null, status: String? = null) {
        val broadcast = Intent(ACTION_BATTERY_UPDATED).apply {
            setPackage(packageName)
            if (percent != null) putExtra(EXTRA_PERCENT, percent)
            if (status != null) putExtra(EXTRA_STATUS, status)
        }
        sendBroadcast(broadcast)
    }

    private fun startListeningLoop() {
        if (!isRunning) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("VOLT в фоне", "Распознавание речи недоступно")
            notifyActivity(status = "Распознавание речи недоступно на устройстве")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    updateNotification("VOLT слушает в фоне", "Скажи «батарея» или «заряд»")
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val heard = matches.joinToString(" ").lowercase(Locale.ROOT)

                    if (heard.contains("батаре") || heard.contains("заряд")) {
                        val pct = getBatteryPercent()
                        val displayText = if (pct >= 0) "VOLT: $pct%" else "VOLT: Н/Д"
                        updateNotification("VOLT • $displayText", "Услышано: $heard")
                        showBatteryOverlay(displayText)
                        notifyActivity(percent = pct, status = "Услышано: $heard")
                    } else if (heard.isNotBlank()) {
                        notifyActivity(status = "Услышано: $heard")
                    }

                    scheduleRestart(300L)
                }

                override fun onError(error: Int) {
                    notifyActivity(status = "Фон • код речи: $error")
                    scheduleRestart(800L)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        try {
            speechRecognizer?.startListening(createRecognizerIntent())
        } catch (_: Exception) {
            scheduleRestart(1000L)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        if (!isRunning) return
        mainHandler.postDelayed({ startListeningLoop() }, delayMs)
    }

    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Фоновый режим VOLT",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Показывает работу голосового контроля заряда в фоне"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(title, content))
    }

    override fun onDestroy() {
        isRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        removeOverlay()
        overlayHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
