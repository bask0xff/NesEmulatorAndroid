package com.bask0xff.nesandroid

import android.content.Context

class NES(context: Context) {

    private var bus: Bus
    private var testMode = false  // Временный режим для отладки
    fun isTestMode(): Boolean = testMode

    init {
        try {
            val romBytes = context.assets.open("test.nes").readBytes()
            println("ROM loaded: size: ${romBytes.size} bytes")
            val cartridge = Cartridge(romBytes)
            bus = Bus(cartridge)

            // Отладка: проверяем первые байты CHR-ROM
            for (i in 0 until 16) {
                val chr = bus.cartridge.ppuRead(i.toUShort())
                println("CHR-ROM[$i] = 0x${chr.toInt().toString(16)}")
            }

            testMode = false
        } catch (e: Exception) {
            println("Failed to load ROM: ${e.message}, using test mode")
            val emptyCartridge = Cartridge(byteArrayOf())
            bus = Bus(emptyCartridge)
            testMode = true
        }

        bus.cpu.reset()
        println("NES initialized, testMode=$testMode")
    }

    fun runFrame() {
        if (testMode) {
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
        println("runFrame completed, testMode=false, bitmap updated") // Добавьте эту строку
    }

    fun getBitmap() = bus.ppu.bitmap
}