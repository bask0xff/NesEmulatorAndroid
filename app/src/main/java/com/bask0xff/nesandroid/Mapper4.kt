package com.bask0xff.nesandroid

class Mapper4(private val cartridge: Cartridge) : Mapper() {

    var irqAsserted = false
    private var prgBankSelect = 0
    private var chrBankSelect = 0

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
            addr <= 0x9FFF -> {
                val index = prgBankSelect * 0x2000 + (addr - 0x8000)
                cartridge.prgROM[index].toUByte()
            }
            addr <= 0xFFFF -> {
                val bank = cartridge.prgBanks - 1
                val index = bank * 0x4000 + (addr - 0xC000)
                cartridge.prgROM[index].toUByte()
            }
            else -> 0u
        }
    }

    override fun cpuWrite(address: UShort, value: UByte) {
        val addr = address.toInt()
        when {
            addr in 0x8000..0x9FFF -> {
                prgBankSelect = value.toInt() and 0x0F
            }
            addr in 0xA000..0xBFFF -> {
                chrBankSelect = value.toInt() and 0x1F
            }
            addr in 0xC000..0xDFFF -> {
                // IRQ control
            }
            addr in 0xE000..0xFFFF -> {
                // IRQ acknowledge
                irqAsserted = false
            }
            addr < cartridge.prgRAM.size -> {
                cartridge.prgRAM[addr] = value.toByte()
            }
        }
    }

    override fun ppuRead(address: UShort): UByte {
        val addr = address.toInt()
        return if (addr < 0x1000) {
            val bank = chrBankSelect
            val index = bank * 0x1000 + addr
            cartridge.chrROM[index].toUByte()
        } else if (addr < 0x2000) {
            val bank = chrBankSelect
            val index = bank * 0x1000 + (addr - 0x1000)
            cartridge.chrROM[index].toUByte()
        } else {
            0u
        }
    }

    override fun ppuWrite(address: UShort, value: UByte) {
        // CHR-RAM not supported for Mapper4
    }

    fun runScanlineIRQ() {
        // Simplified IRQ for MMC3
    }

    override fun reset() {
        prgBankSelect = 0
        chrBankSelect = 0
        irqAsserted = false
    }
}