package com.bask0xff.nesandroid

class Mapper1(private val cartridge: Cartridge) : Mapper {

    private var shiftRegister: Int = 0x10
    private var control: Int = 0x0C
    private var chrBank0: Int = 0
    private var chrBank1: Int = 0
    private var prgBank: Int = 0
    private var shiftCount: Int = 0

    private var prgBankOffset0: Int = 0
    private var prgBankOffset1: Int = 0
    private var chrBankOffset0: Int = 0
    private var chrBankOffset1: Int = 0

    override fun reset() {
        shiftRegister = 0x10
        control = 0x0C
        chrBank0 = 0; chrBank1 = 0; prgBank = 0
        shiftCount = 0
        applyMirroring()
        applyBanks()
    }

    override fun cpuRead(address: UShort): UByte {
        val addr = address.toInt()
        return when {
            addr in 0x6000..0x7FFF -> cartridge.prgRAM[addr - 0x6000].toUByte()
            addr in 0x8000..0xBFFF -> cartridge.prgROM[prgBankOffset0 + (addr - 0x8000)].toUByte()
            addr in 0xC000..0xFFFF -> cartridge.prgROM[prgBankOffset1 + (addr - 0xC000)].toUByte()
            else -> 0u
        }
    }

    override fun cpuWrite(address: UShort, value: UByte) {
        val addr = address.toInt()
        val v = value.toInt()

        if (addr in 0x6000..0x7FFF) {
            cartridge.prgRAM[addr - 0x6000] = value.toByte()
            return
        }
        if (addr < 0x8000) return

        if ((v and 0x80) != 0) {
            shiftRegister = 0x10
            control = control or 0x0C
            shiftCount = 0
            applyBanks()
            return
        }

        shiftRegister = (shiftRegister shr 1) or ((v and 1) shl 4)
        shiftCount++

        if (shiftCount == 5) {
            when ((addr shr 13) and 0x03) {
                0 -> { control = shiftRegister and 0x1F; applyMirroring() }
                1 -> { chrBank0 = shiftRegister and 0x1F; applyMirroring() }
                2 -> { chrBank1 = shiftRegister and 0x1F }
                3 -> { prgBank = shiftRegister and 0x0F }
            }
            shiftRegister = 0x10
            shiftCount = 0
            applyBanks()
        }
    }

    override fun ppuRead(address: UShort): UByte {
        val addr = address.toInt()
        if (addr < 0x2000) {
            if (cartridge.chrBanks == 0) return cartridge.chrRAM[addr].toUByte()
            return if ((control shr 4) and 1 == 0) {
                cartridge.chrROM[(addr + (chrBank0 and 0x1E) * 0x1000) % cartridge.chrROM.size].toUByte()
            } else {
                if (addr < 0x1000)
                    cartridge.chrROM[(addr + chrBankOffset0) % cartridge.chrROM.size].toUByte()
                else
                    cartridge.chrROM[(addr - 0x1000 + chrBankOffset1) % cartridge.chrROM.size].toUByte()
            }
        }
        return 0u
    }

    override fun ppuWrite(address: UShort, value: UByte) {
        val addr = address.toInt()
        if (addr < 0x2000 && cartridge.chrBanks == 0) {
            cartridge.chrRAM[addr] = value.toByte()
        }
    }

    private fun applyMirroring() {
        cartridge.setMirroring(when (control and 0x03) {
            0 -> Mirroring.SINGLE_SCREEN_A
            1 -> Mirroring.SINGLE_SCREEN_B
            2 -> Mirroring.VERTICAL
            else -> Mirroring.HORIZONTAL
        })
    }

    private fun applyBanks() {
        val chrMode = (control shr 4) and 1
        if (chrMode == 0) {
            chrBankOffset0 = (chrBank0 and 0x1E) * 0x1000
            chrBankOffset1 = chrBankOffset0 + 0x1000
        } else {
            chrBankOffset0 = chrBank0 * 0x1000
            chrBankOffset1 = chrBank1 * 0x1000
        }
        if (cartridge.chrBanks > 0) {
            chrBankOffset0 %= cartridge.chrROM.size
            chrBankOffset1 %= cartridge.chrROM.size
        }

        val prgBankCount = cartridge.prgROM.size / 0x4000
        when ((control shr 2) and 0x03) {
            0, 1 -> {
                val bank = (prgBank and 0x0E) % maxOf(1, prgBankCount)
                prgBankOffset0 = bank * 0x4000
                prgBankOffset1 = prgBankOffset0 + 0x4000
            }
            2 -> {
                prgBankOffset0 = 0
                prgBankOffset1 = (prgBank % maxOf(1, prgBankCount)) * 0x4000
            }
            else -> {
                prgBankOffset0 = (prgBank % maxOf(1, prgBankCount)) * 0x4000
                prgBankOffset1 = (prgBankCount - 1) * 0x4000
            }
        }
        prgBankOffset0 %= cartridge.prgROM.size
        prgBankOffset1 %= cartridge.prgROM.size
    }
}
