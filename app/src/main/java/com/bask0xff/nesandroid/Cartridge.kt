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

        // Копируем PRG ROM
        val prgSize = prgBanks * 16 * 1024
        prgROM = ByteArray(prgSize)
        System.arraycopy(rom, 16, prgROM, 0, prgSize)

        // Копируем CHR ROM
        val chrSize = chrBanks * 8 * 1024
        val chrOffset = 16 + prgSize

        chrROM = if (chrSize > 0) {
            ByteArray(chrSize).also {
                System.arraycopy(rom, chrOffset, it, 0, chrSize)
            }
        } else {
            // Если нет CHR-ROM, создаём CHR-RAM с тестовым паттерном
            println("No CHR-ROM found, creating test pattern in CHR-RAM")
            ByteArray(8 * 1024).also {
                // Создаём простой узор: шахматная доска
                for (i in 0 until minOf(8192, it.size) step 16) {
                    if (i + 15 < it.size) {
                        // Плоскость 0 (биты 0)
                        it[i] = 0x55.toByte()     // 01010101
                        it[i + 1] = 0x55.toByte()
                        it[i + 2] = 0x55.toByte()
                        it[i + 3] = 0x55.toByte()
                        it[i + 4] = 0x55.toByte()
                        it[i + 5] = 0x55.toByte()
                        it[i + 6] = 0x55.toByte()
                        it[i + 7] = 0x55.toByte()
                        // Плоскость 1 (биты 1)
                        it[i + 8] = 0xAA.toByte()  // 10101010
                        it[i + 9] = 0xAA.toByte()
                        it[i + 10] = 0xAA.toByte()
                        it[i + 11] = 0xAA.toByte()
                        it[i + 12] = 0xAA.toByte()
                        it[i + 13] = 0xAA.toByte()
                        it[i + 14] = 0xAA.toByte()
                        it[i + 15] = 0xAA.toByte()
                    }
                }
            }
        }

        // Инициализация маппера
        mapper = when (mapperID) {
            0 -> Mapper0(this)
            1 -> Mapper1(this)
            2 -> Mapper2(this)
            4 -> Mapper4(this)
            else -> throw NotImplementedError("Mapper $mapperID is not supported yet")
        }

        mapper.reset()

        println("Cartridge loaded: Mapper $mapperID, PRG-ROM ${prgBanks * 16}KB, ${if (chrBanks > 0) "${chrBanks * 8}KB CHR-ROM" else "CHR-RAM with test pattern"}")
    }

    fun cpuRead(address: UShort): UByte = mapper.cpuRead(address)
    fun cpuWrite(address: UShort, value: UByte) = mapper.cpuWrite(address, value)

    fun ppuRead(address: UShort): UByte {
        val addr = address.toInt() and 0x3FFF
        return when {
            addr < chrROM.size -> {
                chrROM[addr].toUByte()
            }
            addr < chrRAM.size -> {
                chrRAM[addr].toUByte()
            }
            else -> 0u
        }
    }

    fun ppuWrite(address: UShort, value: UByte) {
        val addr = address.toInt() and 0x3FFF
        when {
            addr < chrRAM.size -> {
                chrRAM[addr] = value.toByte()
            }
            else -> mapper.ppuWrite(address, value)
        }
    }

    fun setMirroring(mode: Mirroring) {
        mirroringMode = mode
    }
}