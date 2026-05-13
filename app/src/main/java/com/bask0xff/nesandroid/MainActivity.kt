package com.bask0xff.nesandroid

import CPU
import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NES(private val context: Context) {
    val bus = Bus()
    val cpu: CPU
    val ppu: PPU

    init {
        ppu = PPU(bus)
        cpu = CPU(bus)
        bus.cpu = cpu
        bus.ppu = ppu

        // Загрузка тестового ROM (замените на ваш путь)
        loadROM()
    }

    private fun loadROM() {
        // Загрузите ваш ROM файл
        // Пример: val romData = context.assets.open("test.nes").readBytes()
        // bus.cartridge.loadROM(romData)
    }

    fun runFrame() {
        // Эмулируем ~29780 циклов CPU на кадр (262 scanlines * 341/3)
        val cyclesPerFrame = 29780
        var cycles = 0

        while (cycles < cyclesPerFrame) {
            val cpuCycles = cpu.executeInstruction()
            cycles += cpuCycles

            // PPU шагает в 3 раза быстрее CPU
            ppu.step(cpuCycles * 3)
        }

        // Обновляем bitmap после полного кадра
        ppu.updateBitmap()
    }

    fun getBitmap() = ppu.bitmap

    @Composable
    fun NesScreen(nes: NES) {
        var frameCount by remember { mutableIntStateOf(0) }
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(Unit) {
            while (true) {
                withContext(Dispatchers.Default) {
                    nes.runFrame()
                    bitmap = nes.getBitmap()
                }
                frameCount++
                if (frameCount % 60 == 0) {
                    println("Frame $frameCount rendered, bitmap=${bitmap != null}")
                }
            }
        }

        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_XY
                    setBackgroundColor(0xFF00FF00.toInt()) // Временный зелёный фон
                }
            },
            update = { imageView ->
                bitmap?.let {
                    imageView.setImageBitmap(it)
                    println("ImageView updated with bitmap")
                } ?: println("Bitmap is null!")
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}