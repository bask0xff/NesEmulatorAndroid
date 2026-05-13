package com.bask0xff.nesandroid

class Mapper2(private val cartridge: Cartridge) : Mapper() {

    private var prgBankSelect = 0

    override fun cpuRead(address: UShort): UByte {
        val addr = address.toInt()
        return when {
            addr < 0x8000 -> {
                if (addr < cartridge.prgRAM.size) {
                    cartridge.prgRAM[addr].toUByte()
                } else {
                    0u
                }
            }
            addr <= 0xBFFF -> {
                val bank = prgBankSelect
                val offset = addr - 0x8000
                val index = bank * 0x4000 + offset
                if (index < cartridge.prgROM.size) {
                    cartridge.prgROM[index].toUByte()
                } else {
                    0u
                }
            }
            addr <= 0xFFFF -> {
                val bank = cartridge.prgBanks - 1
                val offset = addr - 0xC000
                val index = bank * 0x4000 + offset
                if (index < cartridge.prgROM.size) {
                    cartridge.prgROM[index].toUByte()
                } else {
                    0u
                }
            }
            else -> 0u
        }
    }

    override fun cpuWrite(address: UShort, value: UByte) {
        val addr = address.toInt()
        when {
            addr in 0x8000..0xFFFF -> {
                prgBankSelect = value.toInt() and 0x0F
            }
            addr < cartridge.prgRAM.size -> {
                cartridge.prgRAM[addr] = value.toByte()
            }
        }
    }

    override fun ppuRead(address: UShort): UByte {
        val addr = address.toInt()
        return if (addr < cartridge.chrRAM.size) {
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
        prgBankSelect = 0
    }
}