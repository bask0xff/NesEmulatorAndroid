package com.bask0xff.nesandroid

class Mapper0(private val cartridge: Cartridge) : Mapper() {

    override fun cpuRead(address: UShort): UByte {
        val addr = address.toInt()
        return when {
            addr < 0x8000 -> cartridge.prgRAM[addr].toUByte()
            addr <= 0xFFFF -> {
                val index = addr - 0x8000
                if (index < cartridge.prgROM.size) {
                    cartridge.prgROM[index].toUByte()
                } else {
                    val mirrorIndex = index % cartridge.prgROM.size
                    cartridge.prgROM[mirrorIndex].toUByte()
                }
            }
            else -> 0u
        }
    }

    override fun cpuWrite(address: UShort, value: UByte) {
        val addr = address.toInt()
        if (addr < 0x8000) {
            cartridge.prgRAM[addr] = value.toByte()
        }
    }

    override fun ppuRead(address: UShort): UByte {
        val addr = address.toInt()
        return if (addr < cartridge.chrROM.size) {
            cartridge.chrROM[addr].toUByte()
        } else if (addr < cartridge.chrRAM.size) {
            cartridge.chrRAM[addr].toUByte()
        } else {
            0u
        }
    }

    override fun ppuWrite(address: UShort, value: UByte) {
        val addr = address.toInt()
        if (addr < cartridge.chrRAM.size) {
            cartridge.chrRAM[addr] = value.toByte()
        }
    }

    override fun reset() {
        // Nothing to do for Mapper0
    }
}