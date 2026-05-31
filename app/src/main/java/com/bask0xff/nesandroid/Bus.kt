package com.bask0xff.nesandroid

import CPU

class Bus(val cartridge: Cartridge) {

    val cpu: CPU
    val ppu: PPU
    val apu: APU
    val ram = ByteArray(2048) // 2KB internal RAM
    val input = Input()

    init {
        apu = APU()
        cpu = CPU(this)
        ppu = PPU(this)
        println("Bus initialized")
    }

    fun read(address: UShort): UByte {
        return when {
            address == 0x4016u.toUShort() -> {
                input.read4016()
            }
            address == 0x4017u.toUShort() -> {
                input.read4017()
            }
            address in 0x4000u..0x4017u -> {
                apu.read(address)
            }
            address in 0x2000u..0x3FFFu -> {
                val reg = (0x2000u + (address and 0x0007u)).toUShort()
                ppu.readRegister(reg)
            }
            address in 0x0000u..0x1FFFu -> {
                ram[(address and 0x07FFu).toInt()].toUByte()
            }
            address in 0x6000u..0xFFFFu -> {
                cartridge.cpuRead(address)
            }
            else -> 0u
        }
    }

    fun write(address: UShort, value: UByte) {
        when {
            address == 0x4016u.toUShort() -> {
                input.write4016(value)
            }
            address == 0x4014u.toUShort() -> {
                ppu.writeOAMDMA(value)
            }
            address in 0x4000u..0x4017u -> {
                apu.write(address, value)
            }
            address in 0x2000u..0x3FFFu -> {
                val reg = (0x2000u + (address and 0x0007u)).toUShort()
                ppu.writeRegister(reg, value)
            }
            address in 0x0000u..0x1FFFu -> {
                ram[(address and 0x07FFu).toInt()] = value.toByte()
            }
            address in 0x6000u..0xFFFFu -> {
                cartridge.cpuWrite(address, value)
            }
        }
    }
}