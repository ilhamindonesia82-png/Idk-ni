package com.example.robloxvoicechanger

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private val purple = Color.rgb(172, 102, 255)
    private val cyan = Color.rgb(71, 220, 255)
    private val bg = Color.rgb(12, 12, 22)
    private val panel = Color.rgb(27, 24, 45)
    private var selectedEffect = Effect.NORMAL
    private var pitch = 1.0f
    private var inputGain = 1.0f
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private val running = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private lateinit var status: TextView
    private lateinit var startButton: Button

    enum class Effect { NORMAL, DEEP, HIGH, ROBOT, ALIEN, CRUSH, GLITCH, BROKEN_12X }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 20)
            setBackgroundColor(bg)
        }
        fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
            }

        root.addView(text("NEON VOICE FX", 26f, cyan, true))
        root.addView(text("REAL-TIME MICROPHONE EFFECTS", 12f, purple, true))
        root.addView(text("Android audio preview • Roblox input is not guaranteed", 13f, Color.LTGRAY).apply {
            setPadding(0, 10, 0, 18)
        })

        status = text("● STOPPED", 15f, Color.LTGRAY, true)
        val statusPanel = LinearLayout(this).apply {
            setPadding(16, 14, 16, 14)
            setBackgroundColor(panel)
            addView(status, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(statusPanel)

        root.addView(text("VOICE PRESETS", 14f, purple, true).apply { setPadding(0, 20, 0, 8) })
        val presetGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf(
            listOf("Normal", Effect.NORMAL, "Deep", Effect.DEEP),
            listOf("High", Effect.HIGH, "Robot", Effect.ROBOT),
            listOf("Alien", Effect.ALIEN, "Crush", Effect.CRUSH),
            listOf("Glitch", Effect.GLITCH, "BROKEN 12X", Effect.BROKEN_12X)
        ).forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            var i = 0
            while (i < row.size) {
                val label = row[i] as String
                val effect = row[i + 1] as Effect
                val button = Button(this).apply {
                    text = label
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    setBackgroundColor(if (effect == selectedEffect) purple else panel)
                    setOnClickListener {
                        selectedEffect = effect
                        Toast.makeText(this@MainActivity, "$label selected", Toast.LENGTH_SHORT).show()
                        buildUi()
                    }
                }
                line.addView(button, LinearLayout.LayoutParams(0, 52, 1f).apply {
                    setMargins(4, 4, 4, 4)
                })
                i += 2
            }
            presetGrid.addView(line)
        }
        root.addView(presetGrid)

        root.addView(text("PITCH / SPEED: ${pitchLabel()}", 14f, Color.WHITE, true).apply {
            tag = "pitchLabel"
            setPadding(0, 18, 0, 0)
        })
        val pitchSeek = SeekBar(this).apply {
            max = 150
            progress = 50 // 0.50x to 2.00x
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    pitch = 0.5f + progress / 100f
                    (root.findViewWithTag<TextView>("pitchLabel"))?.text = "PITCH / SPEED: ${pitchLabel()}"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(pitchSeek)

        root.addView(text("MIC GAIN", 14f, Color.WHITE, true).apply { setPadding(0, 8, 0, 0) })
        val gainSeek = SeekBar(this).apply {
            max = 200
            progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    inputGain = progress / 100f
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(gainSeek)

        startButton = Button(this).apply {
            text = if (running.get()) "STOP AUDIO" else "START LIVE PREVIEW"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(purple)
            setOnClickListener {
                if (running.get()) stopAudio() else requestAndStart()
            }
        }
        root.addView(startButton, LinearLayout.LayoutParams(-1, 58).apply {
            setMargins(0, 22, 0, 8)
        })
        root.addView(text("Use headphones to avoid feedback. This preview does not inject audio into Roblox.", 12f, Color.LTGRAY))
        setContentView(root)
    }

    private fun pitchLabel(): String = String.format(java.util.Locale.US, "%.2fx", pitch)

    private fun requestAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
        } else startAudio()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startAudio()
        } else {
            Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startAudio() {
        if (running.get()) return
        val sampleRate = 44100
        val minIn = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val minOut = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minIn <= 0 || minOut <= 0) {
            Toast.makeText(this, "Audio format is not supported on this device.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val input = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, max(minIn * 2, 4096)
            )
            val output = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minOut * 2, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            recorder = input
            player = output
            running.set(true)
            input.startRecording()
            output.play()
            startButton.text = "STOP AUDIO"
            status.text = "● LIVE PREVIEW ACTIVE"
            status.setTextColor(cyan)

            audioThread = thread(name = "VoiceFxAudio", priority = Process.THREAD_PRIORITY_AUDIO) {
                val buffer = ShortArray(1024)
                val processed = ShortArray(2048)
                while (running.get()) {
                    val count = input.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (count <= 0) continue
                    val outCount = process(buffer, count, processed)
                    if (outCount > 0) output.write(processed, 0, outCount, AudioTrack.WRITE_BLOCKING)
                }
            }
        } catch (e: Exception) {
            stopAudio()
            Toast.makeText(this, "Audio start failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }


    // Lightweight real-time DSP prototype.
    // BROKEN_12X means strong distortion intensity, NOT 12x output loudness.
    private fun process(input: ShortArray, count: Int, output: ShortArray): Int {
        if (count <= 0) return 0

        val ratio = when (selectedEffect) {
            Effect.DEEP -> min(pitch, 0.78f)
            Effect.HIGH -> max(pitch, 1.28f)
            Effect.ALIEN -> max(pitch, 1.45f)
            else -> pitch
        }.coerceIn(0.5f, 2.0f)

        val outCount = min(output.size, (count / ratio).toInt().coerceAtLeast(1))
        for (i in 0 until outCount) {
            val srcPos = i * ratio
            val idx = srcPos.toInt().coerceIn(0, count - 1)
            val next = min(idx + 1, count - 1)
            val frac = srcPos - idx
            var sample = (input[idx] * (1f - frac) + input[next] * frac) / 32768f

            when (selectedEffect) {
                Effect.ROBOT -> {
                    val gate = if ((i / 32) % 2 == 0) 1f else 0.48f
                    val carrier = if ((i / 12) % 2 == 0) 1f else -1f
                    sample *= gate * carrier
                }
                Effect.ALIEN -> {
                    val wobble = 0.70f + 0.30f * kotlin.math.sin(i * 0.11).toFloat()
                    sample = kotlin.math.tanh(sample * 2.2f) * wobble
                }
                Effect.CRUSH -> {
                    val driven = (sample * 5.5f).coerceIn(-1f, 1f)
                    val levels = 18f
                    sample = (kotlin.math.round(driven * levels) / levels) * 0.82f
                }
                Effect.GLITCH -> {
                    val gate = when ((i / 24) % 7) {
                        2, 3 -> 0.12f
                        6 -> -0.85f
                        else -> 1f
                    }
                    val stepped = kotlin.math.round(sample * 22f) / 22f
                    sample = kotlin.math.tanh(stepped * 4.5f) * gate
                }
                Effect.BROKEN_12X -> {
                    // Aggressive, intentionally rough digital distortion, with gated bursts
                    // and a metallic ring-modulation texture. Output stays within PCM range.
                    val driven = sample * 12f
                    val clipped = driven.coerceIn(-0.72f, 0.72f)
                    val hard = if (clipped >= 0f) 0.72f else -0.72f
                    val saturated = if (kotlin.math.abs(driven) > 0.38f) {
                        0.72f * (0.78f * hard + 0.22f * clipped / 0.72f)
                    } else {
                        clipped * 1.6f
                    }
                    val carrier = if ((i / 7) % 2 == 0) 1f else -0.72f
                    val stutter = if ((i / 40) % 9 == 7) 0.18f else 1f
                    val bitCrushed = kotlin.math.round(saturated * 13f) / 13f
                    sample = (bitCrushed * carrier * stutter).coerceIn(-0.92f, 0.92f)
                }
                else -> {}
            }

            sample *= inputGain
            // Safe digital ceiling: distortion is created by tone/texture, not dangerous volume.
            sample = sample.coerceIn(-0.98f, 0.98f)
            output[i] = (sample * 32767f).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return outCount
    }

    private fun stopAudio() {
        running.set(false)
        try { recorder?.stop() } catch (_: Exception) {}
        try { player?.pause(); player?.flush(); player?.stop() } catch (_: Exception) {}
        try { audioThread?.join(250) } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        recorder = null
        player = null
        audioThread = null
        if (::startButton.isInitialized) startButton.text = "START LIVE PREVIEW"
        if (::status.isInitialized) {
            status.text = "● STOPPED"
            status.setTextColor(Color.LTGRAY)
        }
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }
}
