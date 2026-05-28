package com.bask0xff.nesandroid


// Mapper3.kt
class Mapper3(private val cartridge: Cartridge) : Mapper {
    private var chrBank: Int = 0

    override fun reset() { chrBank = 0 }

    override fun cpuRead(address: UShort): UByte {
        val addr = address.toInt()
        return when {
            addr in 0x8000..0xFFFF -> {
                val index = if (cartridge.prgBanks == 1) addr and 0x3FFF else addr - 0x8000
                cartridge.prgROM[index % cartridge.prgROM.size].toUByte()
            }
            else -> 0u
        }
    }

    override fun cpuWrite(address: UShort, value: UByte) {
        if (address.toInt() >= 0x8000) {
            chrBank = value.toInt() and 0x03
        }
    }

    override fun ppuRead(address: UShort): UByte {
        val addr = address.toInt()
        if (addr < 0x2000) {
            val index = chrBank * 0x2000 + addr
            return cartridge.chrROM[index % cartridge.chrROM.size].toUByte()
        }
        return 0u
    }

    override fun ppuWrite(address: UShort, value: UByte) {}
}
