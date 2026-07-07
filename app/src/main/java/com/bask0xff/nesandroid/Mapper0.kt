package com.bask0xff.nesandroid

class Mapper0(private val cartridge: Cartridge) : Mapper {

    override fun reset() {}

    override fun cpuRead(address: UShort): UByte {
        val addr = address.toInt()
        return when {
            addr in 0x6000..0x7FFF -> {
                val index = addr - 0x6000
                cartridge.prgRAM[index].toUByte()
            }
            addr in 0x8000..0xFFFF -> {
                val index = if (cartridge.prgBanks == 1) {
                    (addr - 0x8000) and 0x3FFF
                } else {
                    addr - 0x8000
                }
                cartridge.prgROM[index % cartridge.prgROM.size].toUByte()
            }
            else -> 0u
        }
    }

    override fun cpuWrite(address: UShort, value: UByte) {
        val addr = address.toInt()
        if (addr in 0x6000..0x7FFF) {
            val index = addr - 0x6000
            cartridge.prgRAM[index] = value.toByte()
        }
    }

    override fun ppuRead(address: UShort): UByte {
        val addr = address.toInt() and 0x1FFF
        return if (addr < 0x2000) {
            if (cartridge.chrBanks != 0) {
                cartridge.chrROM[addr % cartridge.chrROM.size].toUByte()
            } else {
                cartridge.chrRAM[addr].toUByte()
            }
        } else {
            0u
        }
    }

    override fun ppuWrite(address: UShort, value: UByte) {
        val addr = address.toInt() and 0x1FFF
        if (addr < 0x2000 && cartridge.chrBanks == 0) {
            cartridge.chrRAM[addr] = value.toByte()
        }
    }
}