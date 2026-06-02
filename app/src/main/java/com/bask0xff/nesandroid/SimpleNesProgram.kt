package com.bask0xff.nesandroid

import java.io.File

object SimpleNesProgram {

    /**
     * Создает минимальную iNES ROM с 16KB PRG и 0 KB CHR (используется CHR-RAM).
     * Программа рисует простой фон, используя CHR-RAM и таблицы PPU.
     */
    fun createSimpleDemoRom(): ByteArray {
        val header = ByteArray(16)
        header[0] = 'N'.code.toByte()
        header[1] = 'E'.code.toByte()
        header[2] = 'S'.code.toByte()
        header[3] = 0x1A.toByte()
        header[4] = 1.toByte() // 16KB PRG
        header[5] = 0.toByte() // 0 KB CHR => CHR-RAM
        header[6] = 0.toByte() // mapper 0, horizontal mirroring, no battery, no trainer
        header[7] = 0.toByte()

        val prg = ByteArray(0x4000)
        val program = byteArrayOf(
            0x78.toByte(),               // SEI
            0xD8.toByte(),               // CLD
            0xA9.toByte(), 0x00.toByte(), 0x8D.toByte(), 0x00.toByte(), 0x20.toByte(), // STA $2000
            0xA9.toByte(), 0x08.toByte(), 0x8D.toByte(), 0x01.toByte(), 0x20.toByte(), // STA $2001

            // Set palette at $3F00: universal background + one bg palette
            0xA9.toByte(), 0x3F.toByte(), 0x8D.toByte(), 0x06.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x00.toByte(), 0x8D.toByte(), 0x06.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x0F.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x01.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x11.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x21.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),

            // Set CHR RAM write address to $0000
            0xA9.toByte(), 0x00.toByte(), 0x8D.toByte(), 0x06.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x00.toByte(), 0x8D.toByte(), 0x06.toByte(), 0x20.toByte(),

            // Write one full-color 8x8 tile to CHR RAM (plane0=0xFF, plane1=0x00)
            0xA9.toByte(), 0xFF.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0xFF.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0xFF.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0xFF.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0xFF.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0xFF.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0xFF.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0xFF.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),

            0xA9.toByte(), 0x00.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x00.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x00.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x00.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x00.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x00.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x00.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x00.toByte(), 0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),

            // Set name table address to $2000 and fill first 32 tiles with tile 0
            0xA9.toByte(), 0x20.toByte(), 0x8D.toByte(), 0x06.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x00.toByte(), 0x8D.toByte(), 0x06.toByte(), 0x20.toByte(),
            0xA9.toByte(), 0x00.toByte(), 0xA2.toByte(), 0x20.toByte(),
            0x8D.toByte(), 0x07.toByte(), 0x20.toByte(),
            0xCA.toByte(),
            0xD0.toByte(), 0xFB.toByte(),

            // Infinite loop
            0x4C.toByte(), 0x00.toByte(), 0x80.toByte()
        )

        System.arraycopy(program, 0, prg, 0, program.size)
        prg[0x7FFC] = 0x00.toByte()
        prg[0x7FFD] = 0x80.toByte()

        // Concatenate header and PRG
        val result = ByteArray(header.size + prg.size)
        System.arraycopy(header, 0, result, 0, header.size)
        System.arraycopy(prg, 0, result, header.size, prg.size)
        return result
    }

    /**
     * Сохраняет сгенерированную ROM в указанный файл.
     */
    fun saveSimpleDemoRom(outputFile: File): File {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(createSimpleDemoRom())
        return outputFile
    }

    /**
     * Сохраняет сгенерированную ROM во внутреннее хранилище приложения.
     */
    fun saveSimpleDemoRom(context: android.content.Context, filename: String = "simple-demo.nes"): File {
        val outputFile = File(context.filesDir, filename)
        return saveSimpleDemoRom(outputFile)
    }

    /**
     * Создает NES-инстанс прямо из сгенерированных байтов ROM.
     */
    fun createDemoNes(context: android.content.Context): NES {
        return NES(createSimpleDemoRom())
    }
}
