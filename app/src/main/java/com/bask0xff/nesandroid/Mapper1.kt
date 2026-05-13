package com.bask0xff.nesandroid

class Mapper1(private val cartridge: Cartridge) : Mapper() {

    private val prgRAM = ByteArray(32 * 1024)
    private var prgBank = 0
    private var chrBank = 0
    private var shiftReg = 0
    private var control = 0
    private var shiftCount = 0

    override fun cpuRead(address: UShort): UByte {
        val addr = address.toInt()
        return when {
            addr < 0x8000 -> prgRAM[addr].toUByte()
            addr <= 0x9FFF -> {
                val bank = (control and 0x0C) shr 2
                val index = when (bank) {
                    0, 1 -> 0
                    else -> prgBank
                } * 0x4000 + (addr - 0x8000)
                cartridge.prgROM[index].toUByte()
            }
            addr <= 0xBFFF -> {
                val bank = (control and 0x0C) shr 2
                val index = when (bank) {
                    0 -> 0
                    1 -> 1
                    2, 3 -> prgBank
                    else -> 0
                } * 0x4000 + (addr - 0x8000)
                cartridge.prgROM[index].toUByte()
            }
            addr <= 0xFFFF -> {
                val bank = (control and 0x0C) shr 2
                val index = when (bank) {
                    0, 1 -> cartridge.prgROM.size / 0x4000 - 2
                    2 -> prgBank
                    3 -> cartridge.prgROM.size / 0x4000 - 1
                    else -> 0
                } * 0x4000 + (addr - 0xC000)
                cartridge.prgROM[index].toUByte()
            }
            else -> 0u
        }
    }

    override fun cpuWrite(address: UShort, value: UByte) {
        val addr = address.toInt()
        when {
            addr <= 0x9FFF || addr in 0xA000..0xFFFF -> {
                if ((value.toInt() and 0x80) != 0) {
                    shiftCount = 0
                    shiftReg = 0
                    control = control or 0x0C
                } else {
                    shiftReg = (shiftReg shr 1) or ((value.toInt() and 1) shl 4)
                    shiftCount++
                    if (shiftCount == 5) {
                        val reg = (addr shr 13) and 3
                        when (reg) {
                            0 -> {
                                control = shiftReg
                                when (shiftReg and 0x03) {
                                    0 -> cartridge.mirroringMode = Mirroring.ONE_SCREEN_LOWER
                                    1 -> cartridge.mirroringMode = Mirroring.ONE_SCREEN_UPPER
                                    2 -> cartridge.mirroringMode = Mirroring.VERTICAL
                                    3 -> cartridge.mirroringMode = Mirroring.HORIZONTAL
                                }
                            }
                            1 -> chrBank = shiftReg
                            2 -> prgBank = shiftReg
                            3 -> {
                                prgBank = shiftReg
                                // TODO: Write protect
                            }
                        }
                        shiftCount = 0
                        shiftReg = 0
                    }
                }
            }
            else -> prgRAM[addr] = value.toByte()
        }
    }

    override fun ppuRead(address: UShort): UByte {
        val addr = address.toInt()
        return if (addr < cartridge.chrROM.size) {
            val bank = chrBank
            val index = bank * 0x1000 + (addr % 0x1000)
            cartridge.chrROM[index].toUByte()
        } else {
            0u
        }
    }

    override fun ppuWrite(address: UShort, value: UByte) {
        // CHR-RAM not supported yet for Mapper1
    }

    override fun reset() {
        prgBank = 0
        chrBank = 0
        shiftReg = 0
        control = 0
        shiftCount = 0
    }
}