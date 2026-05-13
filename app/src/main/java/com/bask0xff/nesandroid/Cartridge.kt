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

        // Копируем PRG и CHR ROM
        val prgSize = prgBanks * 16 * 1024
        val chrSize = chrBanks * 8 * 1024

        prgROM = ByteArray(prgSize)
        System.arraycopy(rom, 16, prgROM, 0, prgSize)

        val chrOffset = 16 + prgSize
        chrROM = if (chrSize > 0) {
            ByteArray(chrSize).also {
                System.arraycopy(rom, chrOffset, it, 0, chrSize)
            }
        } else {
            ByteArray(8 * 1024) // CHR-RAM
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

        println("Cartridge loaded: Mapper $mapperID, PRG-ROM ${prgBanks * 16}KB, ${if (chrBanks > 0) "${chrBanks * 8}KB CHR-ROM" else "CHR-RAM"}")
    }

    fun cpuRead(address: UShort): UByte = mapper.cpuRead(address)
    fun cpuWrite(address: UShort, value: UByte) = mapper.cpuWrite(address, value)

    fun ppuRead(address: UShort): UByte = mapper.ppuRead(address)
    fun ppuWrite(address: UShort, value: UByte) = mapper.ppuWrite(address, value)

    fun setMirroring(mode: Mirroring) {
        mirroringMode = mode
    }
}