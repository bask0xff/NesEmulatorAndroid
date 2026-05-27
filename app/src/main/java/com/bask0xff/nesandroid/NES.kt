package com.bask0xff.nesandroid

import android.content.Context

class NES(context: Context, gameName: String) {

    private val _bus: Bus
    val bus: Bus get() = _bus

    init {
        val romBytes = context.assets.open("$gameName.nes").readBytes()
        println("ROM '$gameName' loaded: ${romBytes.size} bytes")

        val cartridge = Cartridge(romBytes)
        _bus = Bus(cartridge)

        _bus.cpu.reset()
        println("NES initialized")
    }

    fun runFrame() {
        var cycles = 0
        while (cycles < 29781) {
            val used = _bus.cpu.executeInstruction()
            cycles += used
            _bus.ppu.step(used * 3)
        }
        _bus.ppu.updateBitmap()
    }

    fun getBitmap() = _bus.ppu.bitmap
}