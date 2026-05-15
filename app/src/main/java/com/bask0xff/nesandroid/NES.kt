package com.bask0xff.nesandroid

import android.content.Context

class NES(context: Context) {

    private val _bus: Bus
    val bus: Bus get() = _bus

    private val games = arrayOf("tanks", "contra", "mario")


    init {
        val romBytes = context.assets.open("${games[0]}.nes").readBytes()
        println("ROM loaded: ${romBytes.size} bytes")
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