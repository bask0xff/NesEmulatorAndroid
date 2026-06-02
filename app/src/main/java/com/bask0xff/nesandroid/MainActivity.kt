package com.bask0xff.nesandroid

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.widget.*
import androidx.activity.ComponentActivity
import android.widget.LinearLayout.LayoutParams
import kotlin.concurrent.thread
import java.io.File

class MainActivity : ComponentActivity() {

    private var nes: NES? = null
    private var running = false
    private var renderThread: Thread? = null

    private lateinit var logTextView: TextView
    private lateinit var cartridgeInfoTextView: TextView
    private val logLines = mutableListOf<String>()
    private val maxLogLines = 500

    private var lastPc = -1; private var lastOpcode = -1; private var lastA = -1
    private var lastX = -1;  private var lastY = -1;  private var lastSP = -1; private var lastStatus = -1

    private lateinit var surfaceView: SurfaceView
    private lateinit var gamepadView: GamepadView
    private lateinit var gamepadView2: GamepadView
    private lateinit var emulatorLayout: LinearLayout
    private lateinit var gameSelectLayout: LinearLayout

    private data class GameInfo(
        val name: String,
        val modelName: String,
        val supported: Boolean,
        val unsupportedReason: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Демо ROM теперь в памяти — не сохраняем его в assets/files.

        val rootLayout = FrameLayout(this).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(24, 40, 24, 24 + insets.systemWindowInsetBottom)
                insets
            }
        }

        // ── Экран эмулятора ──────────────────────────────────────────────────
        emulatorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = android.view.View.GONE
        }

        // SurfaceView + GamepadView в одном FrameLayout
        val gameFrame = FrameLayout(this).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 3f)
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

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 2f)
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
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.5f)
        }

        cartridgeInfoTextView = TextView(this).apply {
            textSize = 12f; typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#A8FF9C"))
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(16, 8, 16, 8)
            text = "Тип модели: —"
        }

        val authorInfoTextView = TextView(this).apply {
            textSize = 11f; typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#A8FF9C"))
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(16, 8, 16, 8)
            text = "Разработчик: Sergey Baskov, Senior Android Developer"
            gravity = Gravity.CENTER
        }

        // Контейнер для отображения второго геймпада под экраном
        val padHeight = (resources.displayMetrics.density * 120).toInt()
        val bottomGamepads = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, padHeight)
        }

        gamepadView2 = GamepadView(this).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }

        bottomGamepads.addView(gamepadView2)

        logTextView = TextView(this).apply {
            textSize = 10f; typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00FF00"))
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(16, 8, 16, 16)
            text = "=== NES EMULATOR LOG ===\n"
        }

        scrollView.addView(logTextView)
        bottomBar.addView(cartridgeInfoTextView)
        bottomBar.addView(authorInfoTextView)
        bottomBar.addView(bottomGamepads)
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
        val gameInfos = loadGameList()
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
        val authorInfo = TextView(this).apply {
            text = "Developed by Sergey Baskov https://bask0xff.com"
            textSize = 12f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            setTextColor(Color.parseColor("#A8FF9C"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        outer.addView(title)
        outer.addView(authorInfo)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        if (gameInfos.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Нет .nes файлов в папке assets"
                textSize = 14f; setTextColor(Color.parseColor("#FF4444"))
                typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
                setPadding(0, 64, 0, 0)
            })
        } else {
            val groupColors = listOf(
                Color.parseColor("#F5F9F2"),
                Color.parseColor("#E9F3FB"),
                Color.parseColor("#FAF0E9"),
                Color.parseColor("#F7F2FA"),
                Color.parseColor("#F1F7F0"),
                Color.parseColor("#F9EEF0")
            )

            val groups = gameInfos
                .sortedWith(compareBy({ it.modelName }, { it.name }))
                .groupBy { it.modelName }
                .toSortedMap()

            groups.entries.forEachIndexed { groupIndex, entry ->
                val groupColor = groupColors[groupIndex % groupColors.size]
                val header = TextView(this).apply {
                    text = entry.key
                    textSize = 14f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    setTextColor(Color.parseColor("#2A3A32"))
                    setPadding(16, 14, 16, 14)
                    setBackgroundColor(groupColor)
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                        topMargin = if (groupIndex == 0) 0 else 12
                        bottomMargin = 2
                    }
                }
                list.addView(header)

                entry.value.forEach { gameInfo ->
                    list.addView(buildGameCard(gameInfo.name, groupColor, gameInfo.supported, gameInfo.unsupportedReason))
                }
            }
        }

        scroll.addView(list)
        outer.addView(scroll)
        return outer
    }

    private fun buildGameCard(
        gameName: String,
        backgroundColor: Int,
        supported: Boolean,
        unsupportedReason: String?
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(24, 20, 24, 20)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = 4 }
            gravity = Gravity.CENTER_VERTICAL
            alpha = if (supported) 1f else 0.6f

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

                addView(TextView(this@MainActivity).apply {
                    text = gameName.uppercase(); textSize = 16f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    setTextColor(Color.parseColor("#2A3A32"))
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(this@MainActivity).apply {
                    text = if (supported) "▶ ИГРАТЬ" else "НЕ ПОДДЕРЖИВАЕТСЯ"
                    textSize = 12f; typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#2A3A32")); setPadding(16, 8, 16, 8)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setStroke(2, Color.parseColor("#2A3A32"))
                        setColor(Color.TRANSPARENT); cornerRadius = 4f
                    }
                })
            })

            if (!supported && !unsupportedReason.isNullOrEmpty()) {
                addView(TextView(this@MainActivity).apply {
                    text = unsupportedReason
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#5B5B5B"))
                    setPadding(0, 8, 0, 0)
                })
            }

            if (supported) {
                setOnClickListener { startGame(gameName) }
            }
        }
    }

    private fun loadGameList(): List<GameInfo> = try {
        val gamesList = mutableListOf<GameInfo>()
        // Добавляем встроенное демо в память
        try {
            val demoBytes = SimpleNesProgram.createSimpleDemoRom()
            val header = demoBytes.copyOfRange(0, 16)
            val formatType = if ((header[7].toInt() shr 2) and 0x03 == 2) "NES 2.0" else "iNES"
            val mapperId = ((header[6].toInt() shr 4) or ((header[7].toInt() shr 4) shl 4)) and 0xFF
            val modelName = "$formatType Mapper $mapperId"
            val supported = mapperId in 0..4
            gamesList.add(GameInfo("simple-demo", modelName, supported, if (supported) null else "Mapper $mapperId не поддерживается"))
        } catch (ex: Exception) {
            println("Ошибка при создании встроенного демо: ${ex.message}")
        }

        // Загружаем из assets
        try {
            assets.list("")
                ?.filter { it.endsWith(".nes") }
                ?.mapNotNull { filename ->
                    val gameName = filename.removeSuffix(".nes")
                    var modelName = "Unknown model"
                    var supported = false
                    var unsupportedReason: String? = null

                    try {
                        assets.open(filename).use { stream ->
                            val header = ByteArray(16)
                            if (stream.read(header) == 16) {
                                val formatType = if ((header[7].toInt() shr 2) and 0x03 == 2) "NES 2.0" else "iNES"
                                val mapperId = ((header[6].toInt() shr 4) or ((header[7].toInt() shr 4) shl 4)) and 0xFF
                                modelName = "$formatType Mapper $mapperId"
                                supported = mapperId in 0..4
                                unsupportedReason = if (supported) null else "Mapper $mapperId не поддерживается"
                            }
                        }
                    } catch (ex: Exception) {
                        modelName = "Unknown model"
                        unsupportedReason = "Ошибка чтения файла: ${ex.message}"
                    }

                    GameInfo(gameName, modelName, supported, unsupportedReason)
                }
                ?.let { gamesList.addAll(it) }
        } catch (ex: Exception) {
            println("Ошибка при чтении assets: ${ex.message}")
        }

        // Загружаем из внутреннего хранилища (где сохранён демо ROM)
        try {
            filesDir.listFiles()?.filter { it.name.endsWith(".nes") }?.forEach { file ->
                val gameName = file.name.removeSuffix(".nes")
                var modelName = "Unknown model"
                var supported = false
                var unsupportedReason: String? = null

                try {
                    file.inputStream().use { stream ->
                        val header = ByteArray(16)
                        if (stream.read(header) == 16) {
                            val formatType = if ((header[7].toInt() shr 2) and 0x03 == 2) "NES 2.0" else "iNES"
                            val mapperId = ((header[6].toInt() shr 4) or ((header[7].toInt() shr 4) shl 4)) and 0xFF
                            modelName = "$formatType Mapper $mapperId"
                            supported = mapperId in 0..4
                            unsupportedReason = if (supported) null else "Mapper $mapperId не поддерживается"
                        }
                    }
                } catch (ex: Exception) {
                    modelName = "Unknown model"
                    unsupportedReason = "Ошибка чтения файла: ${ex.message}"
                }

                gamesList.add(GameInfo(gameName, modelName, supported, unsupportedReason))
                println("Загруженная игра из internal: $gameName - $modelName")
            }
        } catch (ex: Exception) {
            println("Ошибка при чтении internal storage: ${ex.message}")
        }

        println("Всего загружено игр: ${gamesList.size}")
        gamesList.sortedWith(compareBy({ it.modelName }, { it.name }))
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

        try {
            // Сначала пытаемся загрузить из assets
            val romBytes = try {
                assets.open("$gameName.nes").readBytes()
            } catch (e: Exception) {
                // Если нет в assets, пытаемся загрузить из внутреннего хранилища
                val file = File(filesDir, "$gameName.nes")
                if (file.exists()) {
                    file.readBytes()
                } else {
                    // Если это встроенное демо, создаём NES из памяти
                    if (gameName == "simple-demo") {
                        null
                    } else {
                        throw IllegalArgumentException("ROM '$gameName.nes' not found")
                    }
                }
            }

            nes = if (romBytes != null) NES(romBytes) else SimpleNesProgram.createDemoNes(this)
        } catch (e: Exception) {
            addLog("Ошибка загрузки $gameName.nes: ${e.message ?: "неизвестная ошибка"}")
            Toast.makeText(this, "Игра не поддерживается: $gameName.nes", Toast.LENGTH_LONG).show()
            gameSelectLayout.visibility = android.view.View.VISIBLE
            emulatorLayout.visibility = android.view.View.GONE
            return
        }

        logLines.clear()
        logTextView.text = "=== $gameName.nes ===\n"
        cartridgeInfoTextView.text = "Тип модели: ${nes?.bus?.cartridge?.description ?: "—"}"

        gamepadView.reset()
        gamepadView2.reset()

        gamepadView.onStateChanged = { state ->
            nes?.bus?.input?.setControllerState1(state)
        }

        gamepadView2.onStateChanged = { state ->
            nes?.bus?.input?.setControllerState2(state)
        }

        gameSelectLayout.visibility = android.view.View.GONE
        emulatorLayout.visibility = android.view.View.VISIBLE

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
        running = false
        renderThread?.join()
        renderThread = null
        nes?.bus?.apu?.stop()
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