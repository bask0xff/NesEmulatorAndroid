package com.bask0xff.nesandroid

import android.content.Context

class NES(context: Context) {

    private var bus: Bus
    private var testMode = false  // Временный режим для отладки

    init {
        try {
            val romBytes = context.assets.open("nestest.nes").readBytes()
            println("ROM loaded: size: ${romBytes.size} bytes")
            val cartridge = Cartridge(romBytes)
            bus = Bus(cartridge)
            testMode = false
        } catch (e: Exception) {
            println("Failed to load ROM: ${e.message}, using test mode")
            // Создаём пустую заглушку если ROM не загружен
            val emptyCartridge = Cartridge(byteArrayOf())
            bus = Bus(emptyCartridge)
            testMode = true
        }

        bus.cpu.reset()
        println("NES initialized, testMode=$testMode")
    }

    fun runFrame() {
        if (testMode) {
            // В тестовом режиме просто рисуем паттерн
            bus.ppu.drawTestPattern()
            bus.ppu.updateBitmap()
            return
        }

        var cycles = 0
        while (cycles < 29781) {
            val used = bus.cpu.executeInstruction()
            cycles += used
            bus.ppu.step(used * 3)
        }
        bus.ppu.updateBitmap()
    }

    fun getBitmap() = bus.ppu.bitmap
}