package com.bask0xff.nesandroid

import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import android.graphics.Color

class MainActivity : ComponentActivity() {

    private lateinit var nes: NES
    private var running = false
    private var renderThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nes = NES(this)

        val surfaceView = SurfaceView(this)
        setContentView(surfaceView)

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

                            // Очищаем фон чёрным
                            canvas.drawColor(Color.BLACK)

                            // NES соотношение сторон: 256x240 отображалось на 4:3 TV
                            // Масштабируем с сохранением пропорций, центрируем
                            val screenW = canvas.width.toFloat()
                            val screenH = canvas.height.toFloat()
                            val nesW = bmp.width.toFloat()
                            val nesH = bmp.height.toFloat()

                            val scale = minOf(screenW / nesW, screenH / nesH)

                            val scaledW = nesW * scale
                            val scaledH = nesH * scale

                            val offsetX = (screenW - scaledW) / 2f
                            val offsetY = (screenH - scaledH) / 2f

                            val matrix = Matrix()
                            matrix.setScale(scale, scale)
                            matrix.postTranslate(offsetX, offsetY)

                            canvas.drawBitmap(bmp, matrix, Paint().apply {
                                isFilterBitmap = false // Пиксельный стиль без сглаживания
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
}