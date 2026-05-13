package com.bask0xff.nesandroid

class Mapper2(private val cartridge: Cartridge) : Mapper {

    private var prgBank: Int = 0

    override fun reset() {
        prgBank = 0
    }

    override fun cpuRead(address: UShort): UByte {
        val addr = address.toInt()
        return when {
            addr in 0x8000..0xBFFF -> {
                val index = (prgBank * 0x4000) + (addr - 0x8000)
                if (index < cartridge.prgROM.size) cartridge.prgROM[index].toUByte() else 0xFFu
            }
            addr in 0xC000..0xFFFF -> {
                val index = (cartridge.prgROM.size - 0x4000) + (addr - 0xC000)
                if (index < cartridge.prgROM.size) cartridge.prgROM[index].toUByte() else 0xFFu
            }
            else -> 0u
        }
    }

    override fun cpuWrite(address: UShort, value: UByte) {
        if (address.toInt() >= 0x8000) {
            prgBank = value.toInt() and 0x0F
        }
    }

    override fun ppuRead(address: UShort): UByte {
        val addr = address.toInt()
        if (addr < 0x2000) {
            return if (cartridge.chrBanks == 0)
                cartridge.chrRAM[addr].toUByte()
            else
                cartridge.chrROM[addr % cartridge.chrROM.size].toUByte()
        }
        return 0u
    }

    override fun ppuWrite(address: UShort, value: UByte) {
        val addr = address.toInt()
        if (cartridge.chrBanks == 0 && addr < 0x2000) {
            cartridge.chrRAM[addr] = value.toByte()
        }
    }
}
