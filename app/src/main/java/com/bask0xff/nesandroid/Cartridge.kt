package com.bask0xff.nesandroid

class Cartridge(romData: ByteArray) {

    val rom: ByteArray = romData
    lateinit var prgROM: ByteArray
    lateinit var chrROM: ByteArray

    val prgBanks: Int
    val chrBanks: Int
    val mapperID: Int

    var mirroringMode: Mirroring = Mirroring.HORIZONTAL
    val hasBattery: Boolean

    val prgRAM = ByteArray(8 * 1024)
    val chrRAM = ByteArray(8 * 1024)

    lateinit var mapper: Mapper

    init {
        // Проверка iNES заголовка
        if (rom.size < 16 ||
            rom[0] != 'N'.code.toByte() ||
            rom[1] != 'E'.code.toByte() ||
            rom[2] != 'S'.code.toByte() ||
            rom[3] != 0x1A.toByte()
        ) {
            throw IllegalArgumentException("Invalid iNES Header!")
        }

        prgBanks = rom[4].toInt() and 0xFF
        chrBanks = rom[5].toInt() and 0xFF

        val flag6 = rom[6].toInt() and 0xFF
        val flag7 = rom[7].toInt() and 0xFF

        val hasTrainer = (flag6 and 0x04) != 0
        val mirrorVertical = (flag6 and 0x01) != 0
        hasBattery = (flag6 and 0x02) != 0

        mirroringMode = if ((flag6 and 0x08) != 0) {
            Mirroring.FOUR_SCREEN
        } else if (mirrorVertical) {
            Mirroring.VERTICAL
        } else {
            Mirroring.HORIZONTAL
        }

        mapperID = (flag6 shr 4) or ((flag7 shr 4) shl 4)

        val trainerSize = if (hasTrainer) 512 else 0
        val prgStart = 16 + trainerSize
        val prgSize = prgBanks * 16 * 1024
        val chrSize = chrBanks * 8 * 1024

        // PRG ROM
        prgROM = ByteArray(prgSize)
        System.arraycopy(rom, prgStart, prgROM, 0, prgSize)

        // CHR ROM — ИСПРАВЛЕННЫЙ БЛОК
        val chrOffset = prgStart + prgSize
        chrROM = if (chrSize > 0 && chrOffset + chrSize <= rom.size) {
            ByteArray(chrSize).also {
                System.arraycopy(rom, chrOffset, it, 0, chrSize)
            }
        } else {
            println("WARNING: CHR copy failed or no CHR-ROM. chrSize=$chrSize, chrOffset=$chrOffset, rom.size=${rom.size}")
            ByteArray(8 * 1024).also { ram ->
                // Тестовый паттерн на случай ошибки
                for (i in 0 until ram.size step 16) {
                    if (i + 15 < ram.size) {
                        ram[i] = 0x55.toByte()
                        ram[i+1] = 0x55.toByte()
                        ram[i+2] = 0x55.toByte()
                        ram[i+3] = 0x55.toByte()
                        ram[i+4] = 0x55.toByte()
                        ram[i+5] = 0x55.toByte()
                        ram[i+6] = 0x55.toByte()
                        ram[i+7] = 0x55.toByte()
                        ram[i+8] = 0xAA.toByte()
                        ram[i+9] = 0xAA.toByte()
                        ram[i+10] = 0xAA.toByte()
                        ram[i+11] = 0xAA.toByte()
                        ram[i+12] = 0xAA.toByte()
                        ram[i+13] = 0xAA.toByte()
                        ram[i+14] = 0xAA.toByte()
                        ram[i+15] = 0xAA.toByte()
                    }
                }
            }
        }

        mapper = when (mapperID) {
            0 -> Mapper0(this)
            1 -> Mapper1(this)
            2 -> Mapper2(this)
            3 -> Mapper3(this)
            4 -> Mapper4(this)
            else -> throw NotImplementedError("Mapper $mapperID is not supported yet")
        }

        mapper.reset()

        println("Cartridge loaded: ${getDescription()}")
        println("DEBUG CHR: chrBanks=$chrBanks, chrSize=$chrSize, chrOffset=$chrOffset, rom.size=${rom.size}")
        if (chrROM.isNotEmpty()) {
            println("First 16 bytes of CHR: ${chrROM.take(16).joinToString { it.toUByte().toString(16) }}")
        }
    }

    private fun getDescription(): String {
        val chrDesc = if (chrBanks > 0) "${chrBanks * 8}KB CHR-ROM" else "CHR-RAM"
        return "Mapper $mapperID, ${if ((rom[7].toInt() shr 2) and 0x03 == 2) "NES 2.0" else "iNES"}, ${mirroringMode.name.lowercase().replace('_', ' ')}, ${prgBanks * 16}KB PRG-ROM, $chrDesc${if (hasBattery) ", Battery" else ""}"
    }

    fun cpuRead(address: UShort): UByte = mapper.cpuRead(address)
    fun cpuWrite(address: UShort, value: UByte) = mapper.cpuWrite(address, value)

    fun ppuRead(address: UShort): UByte = mapper.ppuRead(address)
    fun ppuWrite(address: UShort, value: UByte) = mapper.ppuWrite(address, value)

    fun setMirroring(mode: Mirroring) {
        mirroringMode = mode
    }
}