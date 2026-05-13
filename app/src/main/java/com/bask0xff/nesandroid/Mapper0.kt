package com.bask0xff.nesandroid

class Mapper0(private val cartridge: Cartridge) : Mapper {

    override fun reset() {}

    override fun cpuRead(address: UShort): UByte {
        val addr = address.toInt()
        return when {
            addr in 0x6000..0x7FFF -> cartridge.prgRAM[addr - 0x6000].toUByte()
            addr in 0x8000..0xFFFF -> {
                val index = if (cartridge.prgBanks == 1) addr and 0x3FFF else addr - 0x8000
                cartridge.prgROM[index].toUByte()
            }
            else -> 0u
        }
    }

    override fun cpuWrite(address: UShort, value: UByte) {
        val addr = address.toInt()
        if (addr in 0x6000..0x7FFF) {
            cartridge.prgRAM[addr - 0x6000] = value.toByte()
        }
    }

    override fun ppuRead(address: UShort): UByte {
        val addr = address.toInt()
        if (addr < 0x2000) {
            return if (cartridge.chrBanks != 0)
                cartridge.chrROM[addr].toUByte()
            else
                cartridge.chrRAM[addr].toUByte()
        }
        return 0u
    }

    override fun ppuWrite(address: UShort, value: UByte) {
        val addr = address.toInt()
        if (addr < 0x2000 && cartridge.chrBanks == 0) {
            cartridge.chrRAM[addr] = value.toByte()
        }
    }
}
