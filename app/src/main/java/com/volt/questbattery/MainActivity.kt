package com.volt.questbattery

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvPercent: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnListen: Button
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvPercent = findViewById(R.id.tvPercent)
        tvStatus = findViewById(R.id.tvStatus)
        btnListen = findViewById(R.id.btnListen)

        checkAudioPermission()

        btnListen.setOnClickListener {
            if (!isListening) {
                startVoiceListening()
            } else {
                stopVoiceListening()
            }
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    private fun getBatteryPercent(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return -1
        return (level * 100) / scale
    }

    private fun showBatteryOnScreen() {
        val pct = getBatteryPercent()
        tvPercent.text = if (pct >= 0) "$pct%" else "Н/Д"
    }

    private fun startVoiceListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus.text = "Распознавание речи недоступно на устройстве"
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    btnListen.text = getString(R.string.btn_stop)
                    tvStatus.text = "Слушаю... скажи «батарея»"
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val heard = matches.joinToString(" ").lowercase(Locale.ROOT)
                    tvStatus.text = "Услышано: $heard"
                    if (heard.contains("батаре") || heard.contains("заряд")) {
                        showBatteryOnScreen()
                    }
                    if (isListening) {
                        speechRecognizer?.startListening(createRecognizerIntent())
                    }
                }

                override fun onError(error: Int) {
                    tvStatus.text = "Код ошибки речи: $error"
                    if (isListening) {
                        speechRecognizer?.startListening(createRecognizerIntent())
                    }
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        speechRecognizer?.startListening(createRecognizerIntent())
    }

    private fun stopVoiceListening() {
        isListening = false
        btnListen.text = getString(R.string.btn_listen)
        tvStatus.text = getString(R.string.status_idle)
        speechRecognizer?.stopListening()
    }

    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}