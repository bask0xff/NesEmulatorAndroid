package com.bask0xff.nesandroid

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.*
import androidx.activity.ComponentActivity
import android.widget.LinearLayout.LayoutParams
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private var nes: NES? = null
    private var running = false
    private var renderThread: Thread? = null

    private lateinit var logTextView: TextView
    private val logLines = mutableListOf<String>()
    private val maxLogLines = 500

    private var lastPc = -1; private var lastOpcode = -1; private var lastA = -1
    private var lastX = -1;  private var lastY = -1;  private var lastSP = -1; private var lastStatus = -1

    private lateinit var surfaceView: SurfaceView
    private lateinit var gamepadView: GamepadView
    private lateinit var emulatorLayout: LinearLayout
    private lateinit var gameSelectLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = FrameLayout(this).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
        }

        // ── Экран эмулятора ──────────────────────────────────────────────────
        emulatorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = android.view.View.GONE
        }

        // SurfaceView + GamepadView в одном FrameLayout
        val gameFrame = FrameLayout(this).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        surfaceView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        gamepadView = GamepadView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        gameFrame.addView(surfaceView)
        gameFrame.addView(gamepadView)

        // Нижняя панель: кнопка смены + лог
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }

        val changeGameBtn = Button(this).apply {
            text = "◀ СМЕНИТЬ ИГРУ"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setOnClickListener { showGameSelect() }
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 300)  // ← выше и больше
        }
        logTextView = TextView(this).apply {
            textSize = 10f; typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00FF00"))
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(16, 8, 16, 16)
            text = "=== NES EMULATOR LOG ===\n"
        }
        scrollView.addView(logTextView)
        bottomBar.addView(changeGameBtn)
        bottomBar.addView(scrollView)

        emulatorLayout.addView(gameFrame)
        emulatorLayout.addView(bottomBar)

        // ── Экран выбора игры ────────────────────────────────────────────────
        gameSelectLayout = buildGameSelectScreen()

        rootLayout.addView(emulatorLayout)
        rootLayout.addView(gameSelectLayout)
        setContentView(rootLayout)

        setupSurface()
        showGameSelect()
    }

    // ── Экран выбора ─────────────────────────────────────────────────────────

    private fun buildGameSelectScreen(): LinearLayout {
        val gameNames = loadGameList()
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(24, 40, 24, 24)
        }
        val title = TextView(this).apply {
            text = "🎮  ВЫБОР ИГРЫ"
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.parseColor("#00FF00"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        if (gameNames.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Нет .nes файлов в папке assets"
                textSize = 14f; setTextColor(Color.parseColor("#FF4444"))
                typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
                setPadding(0, 64, 0, 0)
            })
        } else {
            gameNames.forEachIndexed { i, name -> list.addView(buildGameCard(name, i)) }
        }
        scroll.addView(list)
        outer.addView(title)
        outer.addView(scroll)
        return outer
    }

    private fun buildGameCard(gameName: String, index: Int): LinearLayout {
        val bg = if (index % 2 == 0) Color.parseColor("#1A1A1A") else Color.parseColor("#141414")
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bg)
            setPadding(24, 20, 24, 20)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = 4 }
            gravity = Gravity.CENTER_VERTICAL

            addView(TextView(this@MainActivity).apply {
                text = String.format("%02d", index + 1); textSize = 12f
                typeface = Typeface.MONOSPACE; setTextColor(Color.parseColor("#555555"))
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { rightMargin = 20 }
            })
            addView(TextView(this@MainActivity).apply {
                text = gameName.uppercase(); textSize = 16f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(Color.parseColor("#E0E0E0"))
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = "▶ ИГРАТЬ"; textSize = 12f; typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#00FF00")); setPadding(16, 8, 16, 8)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setStroke(2, Color.parseColor("#00FF00"))
                    setColor(Color.TRANSPARENT); cornerRadius = 4f
                }
            })
            setOnClickListener { startGame(gameName) }
        }
    }

    private fun loadGameList(): List<String> = try {
        assets.list("")
            ?.filter { it.endsWith(".nes") }
            ?.map { it.removeSuffix(".nes") }
            ?.sorted() ?: emptyList()
    } catch (e: Exception) { emptyList() }

    // ── Управление экранами ──────────────────────────────────────────────────

    private fun showGameSelect() {
        stopEmulator()
        gamepadView.reset()
        gameSelectLayout.visibility = android.view.View.VISIBLE
        emulatorLayout.visibility   = android.view.View.GONE
    }

    private fun startGame(gameName: String) {
        stopEmulator()
        resetLogState()

        nes = NES(this, gameName)
        logLines.clear()
        logTextView.text = "=== $gameName.nes ===\n"

        // Подключаем геймпад к эмулятору
        gamepadView.reset()
        gamepadView.onStateChanged = { state ->
            nes?.bus?.input?.setControllerState(state)
        }

        gameSelectLayout.visibility = android.view.View.GONE
        emulatorLayout.visibility   = android.view.View.VISIBLE

        startRenderThread()
        startLogThread()
    }

    // ── SurfaceView ──────────────────────────────────────────────────────────

    private fun setupSurface() {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {}
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                running = false; renderThread?.join()
            }
        })
    }

    private fun startRenderThread() {
        running = true
        renderThread = thread(start = true) {
            while (running) {
                val t0 = System.nanoTime()
                nes?.runFrame()

                val canvas = surfaceView.holder.lockCanvas() ?: continue
                try {
                    val bmp = nes?.getBitmap() ?: continue
                    canvas.drawColor(Color.BLACK)
                    val scale = minOf(canvas.width / bmp.width.toFloat(), canvas.height / bmp.height.toFloat())
                    val matrix = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate((canvas.width - bmp.width * scale) / 2f, 10f)
                    }
                    canvas.drawBitmap(bmp, matrix, Paint().apply { isFilterBitmap = false })
                } finally {
                    surfaceView.holder.unlockCanvasAndPost(canvas)
                }

                val sleep = 16 - (System.nanoTime() - t0) / 1_000_000
                if (sleep > 0) Thread.sleep(sleep)
            }
        }
    }

    private fun startLogThread() {
        thread(start = true) {
            while (running) {
                Thread.sleep(100)
                val cpu = nes?.bus?.cpu ?: continue
                val pc = cpu.PC; val op = cpu.getLastExecutedOpcode()
                val a = cpu.A;   val x = cpu.X; val y = cpu.Y
                val sp = cpu.SP; val st = cpu.status

                if (pc != lastPc || op != lastOpcode || a != lastA ||
                    x != lastX || y != lastY || sp != lastSP || st != lastStatus) {
                    lastPc=pc; lastOpcode=op; lastA=a; lastX=x; lastY=y; lastSP=sp; lastStatus=st
                    val sb = String.format("%08d", Integer.toBinaryString(st).toInt())
                    addLog(String.format("PC:%04X OP:%02X A:%02X X:%02X Y:%02X SP:%02X S:%s", pc,op,a,x,y,sp,sb))
                }
            }
        }
    }

    // ── Утилиты ──────────────────────────────────────────────────────────────

    private fun stopEmulator() {
        running = false; renderThread?.join(); renderThread = null
    }

    private fun resetLogState() {
        lastPc=-1; lastOpcode=-1; lastA=-1; lastX=-1; lastY=-1; lastSP=-1; lastStatus=-1
    }

    private fun addLog(msg: String) = runOnUiThread {
        logLines.add(msg)
        while (logLines.size > maxLogLines) logLines.removeAt(0)
        logTextView.text = logLines.joinToString("\n")
        (logTextView.parent as ScrollView).post {
            (logTextView.parent as ScrollView).fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}