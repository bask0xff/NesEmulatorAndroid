package com.bask0xff.nesandroid

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import android.widget.LinearLayout.LayoutParams
import android.graphics.Bitmap
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var nes: NES
    private var running = false
    private var renderThread: Thread? = null
    private lateinit var logTextView: TextView
    private val logLines = mutableListOf<String>()
    private val maxLogLines = 500

    private var lastPc = -1
    private var lastOpcode = -1
    private var lastA = -1
    private var lastX = -1
    private var lastY = -1
    private var lastSP = -1
    private var lastStatus = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nes = NES(this)

        // Создаём вертикальный контейнер
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val surfaceView = SurfaceView(this).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 3f)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 2f)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }

        logTextView = TextView(this).apply {
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00FF00"))
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(16, 16, 16, 16)
            text = "=== NES EMULATOR LOG ===\n"
        }

        scrollView.addView(logTextView)
        mainLayout.addView(surfaceView)
        mainLayout.addView(scrollView)
        setContentView(mainLayout)

        // Логирование в отдельном потоке
        thread(start = true) {
            while (true) {
                Thread.sleep(100)
                val cpu = nes.bus.cpu
                val pc = cpu.PC
                val opcode = cpu.getLastExecutedOpcode()
                val a = cpu.A
                val x = cpu.X
                val y = cpu.Y
                val sp = cpu.SP
                val status = cpu.status

                if (pc != lastPc || opcode != lastOpcode || a != lastA ||
                    x != lastX || y != lastY || sp != lastSP || status != lastStatus) {

                    lastPc = pc
                    lastOpcode = opcode
                    lastA = a
                    lastX = x
                    lastY = y
                    lastSP = sp
                    lastStatus = status

                    val statusStr = String.format("%08d", Integer.toBinaryString(status).toInt())
                    val log = String.format(
                        "PC:%04X OP:%02X A:%02X X:%02X Y:%02X SP:%02X S:%s",
                        pc, opcode, a, x, y, sp, statusStr
                    )

                    addLog(log)
                }
            }
        }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                running = true
                renderThread = Thread {
                    while (running) {
                        val frameStart = System.nanoTime()

                        nes.runFrame()

                        val canvas = holder.lockCanvas() ?: continue
                        try {
                            val bmp = nes.getBitmap()

                            canvas.drawColor(Color.BLACK)

                            val screenW = canvas.width.toFloat()
                            val screenH = canvas.height.toFloat()
                            val nesW = bmp.width.toFloat()
                            val nesH = bmp.height.toFloat()

                            val scale = minOf(screenW / nesW, screenH / nesH)

                            val scaledW = nesW * scale
                            val scaledH = nesH * scale

                            val offsetX = (screenW - scaledW) / 2f
                            val offsetY = 10f;//(screenH - scaledH) / 2f

                            val matrix = Matrix()
                            matrix.setScale(scale, scale)
                            matrix.postTranslate(offsetX, offsetY)

                            canvas.drawBitmap(bmp, matrix, Paint().apply {
                                isFilterBitmap = false
                            })
                        } finally {
                            holder.unlockCanvasAndPost(canvas)
                        }

                        val elapsed = (System.nanoTime() - frameStart) / 1_000_000
                        val sleep = 16 - elapsed
                        if (sleep > 0) Thread.sleep(sleep)
                    }
                }.also { it.start() }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                running = false
                renderThread?.join()
            }
        })
    }

    private fun addLog(message: String) {
        runOnUiThread {
            logLines.add(message)
            while (logLines.size > maxLogLines) {
                logLines.removeAt(0)
            }
            logTextView.text = logLines.joinToString("\n")
            // Автоскролл вниз
            val scrollView = logTextView.parent as ScrollView
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }
}