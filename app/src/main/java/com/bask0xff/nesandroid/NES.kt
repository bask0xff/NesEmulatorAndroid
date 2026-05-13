// NES.kt
package com.bask0xff.nesandroid

import android.content.Context

class NES(context: Context) {

    private val bus: Bus

    init {
        val romBytes = context.assets.open("tanks.nes").readBytes()
        println("ROM loaded: ${romBytes.size} bytes")
        val cartridge = Cartridge(romBytes)
        bus = Bus(cartridge)
        bus.cpu.reset()
        println("NES initialized")
    }

    fun runFrame() {
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