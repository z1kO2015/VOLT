package com.volt.questbattery

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvPercent: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnListen: Button
    private var isServiceRunning = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BatteryVoiceService.ACTION_BATTERY_UPDATED) return
            if (intent.hasExtra(BatteryVoiceService.EXTRA_PERCENT)) {
                val pct = intent.getIntExtra(BatteryVoiceService.EXTRA_PERCENT, -1)
                tvPercent.text = if (pct >= 0) "$pct%" else "Н/Д"
            }
            intent.getStringExtra(BatteryVoiceService.EXTRA_STATUS)?.let { status ->
                tvStatus.text = status
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvPercent = findViewById(R.id.tvPercent)
        tvStatus = findViewById(R.id.tvStatus)
        btnListen = findViewById(R.id.btnListen)

        requestNeededPermissions()

        btnListen.setOnClickListener {
            if (!isServiceRunning) {
                startBackgroundService()
            } else {
                stopBackgroundService()
            }
        }
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun startBackgroundService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNeededPermissions()
            tvStatus.text = "Выдай разрешение на микрофон"
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            tvStatus.text = "Включи «Отображение поверх других приложений»"
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        val serviceIntent = Intent(this, BatteryVoiceService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        isServiceRunning = true
        btnListen.text = getString(R.string.btn_stop)
        tvStatus.text = "Фоновый режим запущен"
    }

    private fun stopBackgroundService() {
        stopService(Intent(this, BatteryVoiceService::class.java))
        isServiceRunning = false
        btnListen.text = getString(R.string.btn_listen)
        tvStatus.text = getString(R.string.status_idle)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(BatteryVoiceService.ACTION_BATTERY_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
    }

    override fun onStop() {
        unregisterReceiver(batteryReceiver)
        super.onStop()
    }
}
