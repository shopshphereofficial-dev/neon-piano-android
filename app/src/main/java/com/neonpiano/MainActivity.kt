package com.neonpiano

import android.app.Activity
import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.concurrent.thread

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pianoView = NeonPianoView(this)
        setContentView(pianoView)
    }

    class NeonPianoView(context: Activity) : View(context) {
        private val whiteNotes = doubleArrayOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25)
        private val whiteLabels = arrayOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5")
        private val blackNotes = doubleArrayOf(277.18, 311.13, 0.0, 369.99, 415.30, 466.16)
        private val blackLabels = arrayOf("C#", "D#", "", "F#", "G#", "A#")
        
        private val pressedKeys = BooleanArray(15)
        private val activePointers = HashMap<Int, Int>()

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN
            textAlign = Paint.Align.CENTER
            textSize = 36f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.parseColor("#070714"))

            val w = width.toFloat()
            val h = height.toFloat()
            val keyWidth = w / 8f
            val blackWidth = keyWidth * 0.6f
            val blackHeight = h * 0.58f

            // Draw title header
            textPaint.textSize = 42f
            textPaint.color = Color.parseColor("#00FFFF")
            canvas.drawText("★ NEON PIANO ★", w / 2f, 60f, textPaint)

            val topOffset = 100f
            val whiteHeight = h - topOffset

            // Draw white keys
            for (i in 0 until 8) {
                val left = i * keyWidth
                val right = left + keyWidth
                val isPressed = pressedKeys[i]

                paint.style = Paint.Style.FILL
                paint.color = if (isPressed) Color.parseColor("#00E5FF") else Color.parseColor("#121829")
                canvas.drawRoundRect(left + 3, topOffset, right - 3, h - 10, 16f, 16f, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (isPressed) 6f else 2.5f
                paint.color = if (isPressed) Color.WHITE else Color.parseColor("#00B0FF")
                canvas.drawRoundRect(left + 3, topOffset, right - 3, h - 10, 16f, 16f, paint)

                textPaint.textSize = 32f
                textPaint.color = if (isPressed) Color.BLACK else Color.CYAN
                canvas.drawText(whiteLabels[i], left + keyWidth / 2f, h - 40f, textPaint)
            }

            // Draw black keys
            for (i in 0 until 7) {
                if (i == 2) continue
                val left = (i + 1) * keyWidth - blackWidth / 2f
                val right = left + blackWidth
                val keyIndex = 8 + i
                val isPressed = pressedKeys[keyIndex]

                paint.style = Paint.Style.FILL
                paint.color = if (isPressed) Color.parseColor("#FF007F") else Color.parseColor("#1A0926")
                canvas.drawRoundRect(left, topOffset, right, topOffset + blackHeight, 12f, 12f, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (isPressed) 5f else 2f
                paint.color = if (isPressed) Color.WHITE else Color.parseColor("#FF1493")
                canvas.drawRoundRect(left, topOffset, right, topOffset + blackHeight, 12f, 12f, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val pointerIndex = event.actionIndex
            val pointerId = event.getPointerId(pointerIndex)
            val action = event.actionMasked

            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val key = getKeyAt(event.getX(pointerIndex), event.getY(pointerIndex))
                    if (key != -1) {
                        activePointers[pointerId] = key
                        pressedKeys[key] = true
                        playNote(key)
                        invalidate()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    activePointers.remove(pointerId)?.let {
                        pressedKeys[it] = false
                        invalidate()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    for (i in pressedKeys.indices) pressedKeys[i] = false
                    activePointers.clear()
                    invalidate()
                }
            }
            return true
        }

        private fun getKeyAt(x: Float, y: Float): Int {
            val w = width.toFloat()
            val h = height.toFloat()
            val keyWidth = w / 8f
            val blackWidth = keyWidth * 0.6f
            val blackHeight = h * 0.58f
            val topOffset = 100f

            if (y < topOffset) return -1

            // Check black keys first
            if (y <= topOffset + blackHeight) {
                for (i in 0 until 7) {
                    if (i == 2) continue
                    val left = (i + 1) * keyWidth - blackWidth / 2f
                    val right = left + blackWidth
                    if (x in left..right) {
                        return 8 + i
                    }
                }
            }

            // Check white keys
            val whiteIndex = (x / keyWidth).toInt().coerceIn(0, 7)
            return whiteIndex
        }

        private fun playNote(key: Int) {
            val freq = if (key < 8) {
                whiteNotes[key]
            } else {
                val bIndex = key - 8
                if (bIndex in blackNotes.indices) blackNotes[bIndex] else 0.0
            }
            if (freq <= 0.0) return

            thread {
                try {
                    val sampleRate = 22050
                    val durationSeconds = 0.45
                    val numSamples = (durationSeconds * sampleRate).toInt()
                    val buffer = ShortArray(numSamples)

                    for (i in 0 until numSamples) {
                        val time = i.toDouble() / sampleRate
                        val envelope = Math.exp(-3.5 * time)
                        val wave = Math.sin(2.0 * Math.PI * freq * time)
                        buffer[i] = (wave * envelope * Short.MAX_VALUE * 0.75).toInt().toShort()
                    }

                    val track = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                        .setAudioFormat(AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                        .setBufferSizeInBytes(buffer.size * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()

                    track.write(buffer, 0, buffer.size)
                    track.play()
                    Thread.sleep(500)
                    track.release()
                } catch (_: Exception) {}
            }
        }
    }
}
