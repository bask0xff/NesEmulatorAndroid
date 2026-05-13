package com.bask0xff.nesandroid

class Mapper4(private val cartridge: Cartridge) : Mapper {

    private val registers = IntArray(8)
    private var bankSelect: Int = 0

    private var prgBankOffset0: Int = 0
    private var prgBankOffset1: Int = 0
    private var prgBankOffset2: Int = 0
    private var prgBankOffset3: Int = 0

    private val chrBankOffsets = IntArray(8)

    private var irqLatch: Int = 0
    private var irqCounter: Int = 0
    private var irqEnable: Boolean = false
    private var irqReloadPending: Boolean = false
    var irqAsserted: Boolean = false

    override fun reset() {
        bankSelect = 0
        registers.fill(0)
        irqLatch = 0
        irqCounter = 0
        irqEnable = false
        irqReloadPending = false
        irqAsserted = false
        applyBanks()
    }

    override fun cpuRead(address: UShort): UByte {
        val addr = address.toInt()
        return when {
            addr in 0x6000..0x7FFF -> cartridge.prgRAM[addr - 0x6000].toUByte()
            addr in 0x8000..0x9FFF -> cartridge.prgROM[prgBankOffset0 + (addr - 0x8000)].toUByte()
            addr in 0xA000..0xBFFF -> cartridge.prgROM[prgBankOffset1 + (addr - 0xA000)].toUByte()
            addr in 0xC000..0xDFFF -> cartridge.prgROM[prgBankOffset2 + (addr - 0xC000)].toUByte()
            addr in 0xE000..0xFFFF -> cartridge.prgROM[prgBankOffset3 + (addr - 0xE000)].toUByte()
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

        val even = (addr and 1) == 0
        when {
            addr in 0x8000..0x9FFF -> if (even) {
                bankSelect = v
            } else {
                registers[bankSelect and 0x07] = v
                applyBanks()
            }
            addr in 0xA000..0xBFFF -> if (even) {
                applyMirroring(v)
            }
            addr in 0xC000..0xDFFF -> if (even) {
                irqLatch = v
            } else {
                irqCounter = 0
                irqReloadPending = true
            }
            addr in 0xE000..0xFFFF -> {
                irqEnable = !even
                if (!irqEnable) irqAsserted = false
            }
        }
    }

    override fun ppuRead(address: UShort): UByte {
        val addr = address.toInt()
        if (addr >= 0x2000) return 0u

        if (cartridge.chrBanks == 0) {
            return cartridge.chrRAM[addr].toUByte()
        }

        val index = when {
            addr < 0x0400 -> chrBankOffsets[0] + addr
            addr < 0x0800 -> chrBankOffsets[1] + (addr - 0x0400)
            addr < 0x0C00 -> chrBankOffsets[2] + (addr - 0x0800)
            addr < 0x1000 -> chrBankOffsets[3] + (addr - 0x0C00)
            addr < 0x1400 -> chrBankOffsets[4] + (addr - 0x1000)
            addr < 0x1800 -> chrBankOffsets[5] + (addr - 0x1400)
            addr < 0x1C00 -> chrBankOffsets[6] + (addr - 0x1800)
            else          -> chrBankOffsets[7] + (addr - 0x1C00)
        }
        return cartridge.chrROM[index % cartridge.chrROM.size].toUByte()
    }

    override fun ppuWrite(address: UShort, value: UByte) {
        val addr = address.toInt()
        if (addr < 0x2000 && cartridge.chrBanks == 0) {
            cartridge.chrRAM[addr] = value.toByte()
        }
    }

    fun runScanlineIRQ() {
        if (irqCounter == 0) {
            irqCounter = irqLatch
        } else {
            irqCounter--
            if (irqCounter == 0 && irqEnable) {
                irqAsserted = true
            }
        }
        if (irqReloadPending) {
            irqCounter = irqLatch
            irqReloadPending = false
        }
    }

    private fun applyMirroring(v: Int) {
        cartridge.setMirroring(
            if ((v and 0x01) != 0) Mirroring.HORIZONTAL else Mirroring.VERTICAL
        )
    }

    private fun applyBanks() {
        val prgMode = (bankSelect shr 6) and 1
        val chrMode = (bankSelect shr 7) and 1
        val prgBankCount = cartridge.prgROM.size / 0x2000

        // PRG banks
        val r6 = registers[6] % maxOf(1, prgBankCount)
        val r7 = registers[7] % maxOf(1, prgBankCount)
        val secondLast = (prgBankCount - 2) * 0x2000
        val last = (prgBankCount - 1) * 0x2000

        if (prgMode == 0) {
            prgBankOffset0 = r6 * 0x2000
            prgBankOffset1 = r7 * 0x2000
            prgBankOffset2 = secondLast
            prgBankOffset3 = last
        } else {
            prgBankOffset0 = secondLast
            prgBankOffset1 = r7 * 0x2000
            prgBankOffset2 = r6 * 0x2000
            prgBankOffset3 = last
        }

        // CHR banks (each slot is 0x400 = 1KB)
        if (cartridge.chrBanks == 0) return
        val chrBankCount = cartridge.chrROM.size / 0x400

        fun chrOffset(reg: Int) = (registers[reg] % maxOf(1, chrBankCount)) * 0x400

        if (chrMode == 0) {
            // R0/R1 are 2KB slots mapped to first 4KB; R2-R5 fill the rest
            chrBankOffsets[0] = (registers[0] and 0xFE) % maxOf(1, chrBankCount) * 0x400
            chrBankOffsets[1] = (registers[0] or 0x01) % maxOf(1, chrBankCount) * 0x400
            chrBankOffsets[2] = (registers[1] and 0xFE) % maxOf(1, chrBankCount) * 0x400
            chrBankOffsets[3] = (registers[1] or 0x01) % maxOf(1, chrBankCount) * 0x400
            chrBankOffsets[4] = chrOffset(2)
            chrBankOffsets[5] = chrOffset(3)
            chrBankOffsets[6] = chrOffset(4)
            chrBankOffsets[7] = chrOffset(5)
        } else {
            chrBankOffsets[0] = chrOffset(2)
            chrBankOffsets[1] = chrOffset(3)
            chrBankOffsets[2] = chrOffset(4)
            chrBankOffsets[3] = chrOffset(5)
            chrBankOffsets[4] = (registers[0] and 0xFE) % maxOf(1, chrBankCount) * 0x400
            chrBankOffsets[5] = (registers[0] or 0x01) % maxOf(1, chrBankCount) * 0x400
            chrBankOffsets[6] = (registers[1] and 0xFE) % maxOf(1, chrBankCount) * 0x400
            chrBankOffsets[7] = (registers[1] or 0x01) % maxOf(1, chrBankCount) * 0x400
        }
    }
}