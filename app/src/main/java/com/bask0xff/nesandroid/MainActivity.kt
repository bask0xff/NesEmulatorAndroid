// MainActivity.kt
package com.bask0xff.nesandroid

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity

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
                            val sx = canvas.width.toFloat() / bmp.width
                            val sy = canvas.height.toFloat() / bmp.height
                            canvas.scale(sx, sy)
                            canvas.drawBitmap(bmp, 0f, 0f, null)
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