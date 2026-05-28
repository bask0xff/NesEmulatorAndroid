package com.bask0xff.nesandroid

import android.graphics.Bitmap

class PPU(private val bus: Bus) {

    private val SCREEN_WIDTH = 256
    private val SCREEN_HEIGHT = 240
    private val TOTAL_SCANLINES = 262

    // PPU Memory
    private val vram = ByteArray(2048)
    private val paletteRAM = ByteArray(32)
    private val oam = ByteArray(256)

    // PPU Registers (stored as Int internally to avoid UByte/UInt promotion issues)
    var ppuctrl: Int = 0      // $2000
    var ppumask: Int = 0      // $2001
    var ppustatus: Int = 0    // $2002
    var oamaddr: Int = 0      // $2003
    var ppuscrollX: Int = 0
    var ppuscrollY: Int = 0

    private var addrLatch = false
    private var scrollLatch = false
    private var ppuDataBuffer: Int = 0
    private var fineX: Int = 0

    private var v: Int = 0    // Current VRAM address (15-bit)
    private var t: Int = 0    // Temporary VRAM address (15-bit)

    private var scanline = 0
    private var scanlineCycle = 0

    // Frame rendering
    private val frameBuffer = IntArray(SCREEN_WIDTH * SCREEN_HEIGHT)
    private val scanlineBuffer = IntArray(SCREEN_WIDTH)
    private val bgMask = BooleanArray(SCREEN_WIDTH)

    private var debugFrameCount = 0
    private var debugPpumaskLogged = false

    var debugSkipSprite0Hit = false

    val bitmap: Bitmap = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)

    init {
        reset()
        println("PPU initialized")
    }

    // Добавьте этот метод в класс PPU
    fun drawTestPattern() {
        println("Drawing test pattern")
        // Заливаем фон синим
        val bgColor = 0xFF0000FF.toInt()
        for (i in frameBuffer.indices) {
            frameBuffer[i] = bgColor
        }

        // Рисуем цветные полосы
        val colors = intArrayOf(
            0xFFFF0000.toInt(), // Красный
            0xFF00FF00.toInt(), // Зелёный
            0xFFFFFF00.toInt(), // Жёлтый
            0xFFFF00FF.toInt(), // Пурпурный
            0xFF00FFFF.toInt(), // Голубой
            0xFFFFFFFF.toInt()  // Белый
        )

        val barHeight = SCREEN_HEIGHT / colors.size
        for (i in colors.indices) {
            val startY = i * barHeight
            val endY = if (i == colors.size - 1) SCREEN_HEIGHT else startY + barHeight
            for (y in startY until endY) {
                for (x in 0 until SCREEN_WIDTH) {
                    frameBuffer[y * SCREEN_WIDTH + x] = colors[i]
                }
            }
        }

        // Рисуем рамку
        for (x in 0 until SCREEN_WIDTH) {
            frameBuffer[x] = 0xFFFFFFFF.toInt() // Верхняя
            frameBuffer[(SCREEN_HEIGHT - 1) * SCREEN_WIDTH + x] = 0xFFFFFFFF.toInt() // Нижняя
        }
        for (y in 0 until SCREEN_HEIGHT) {
            frameBuffer[y * SCREEN_WIDTH] = 0xFFFFFFFF.toInt() // Левая
            frameBuffer[y * SCREEN_WIDTH + SCREEN_WIDTH - 1] = 0xFFFFFFFF.toInt() // Правая
        }

        println("Test pattern drawn, frameBuffer[0]=${frameBuffer[0].toString(16)}")
    }

    fun reset() {
        ppuctrl = 0
        ppumask = 0
        ppustatus = 0
        oamaddr = 0
        v = 0; t = 0; fineX = 0
        addrLatch = false
        scrollLatch = false
        scanline = 0
        scanlineCycle = 0
        ppuDataBuffer = 0
        frameBuffer.fill(0xFF000000.toInt())

        // Инициализируем всю палитру нулями, потом игра сама запишет
        for (i in 0 until 32) {
            paletteRAM[i] = 0
        }

        // Устанавливаем базовый цвет фона (чтобы не было чёрного)
        paletteRAM[0] = 0x0F.toByte()  // Белый/серый цвет
/*
        // ВАЖНО: Инициализация палитры правильными значениями
        // Стандартная палитра NES для фона
        paletteRAM[0x00] = 0x0F.toByte()  // Universal background color
        paletteRAM[0x01] = 0x00.toByte()
        paletteRAM[0x02] = 0x10.toByte()
        paletteRAM[0x03] = 0x20.toByte()
        paletteRAM[0x04] = 0x0F.toByte()
        paletteRAM[0x05] = 0x16.toByte()
        paletteRAM[0x06] = 0x26.toByte()
        paletteRAM[0x07] = 0x36.toByte()
        paletteRAM[0x08] = 0x0F.toByte()
        paletteRAM[0x09] = 0x12.toByte()
        paletteRAM[0x0A] = 0x22.toByte()
        paletteRAM[0x0B] = 0x32.toByte()
        paletteRAM[0x0C] = 0x0F.toByte()
        paletteRAM[0x0D] = 0x14.toByte()
        paletteRAM[0x0E] = 0x24.toByte()
        paletteRAM[0x0F] = 0x34.toByte()

        // Sprite palette
        paletteRAM[0x10] = 0x0F.toByte()
        paletteRAM[0x11] = 0x01.toByte()
        paletteRAM[0x12] = 0x11.toByte()
        paletteRAM[0x13] = 0x21.toByte()
        paletteRAM[0x14] = 0x0F.toByte()
        paletteRAM[0x15] = 0x06.toByte()
        paletteRAM[0x16] = 0x16.toByte()
        paletteRAM[0x17] = 0x26.toByte()
        paletteRAM[0x18] = 0x0F.toByte()
        paletteRAM[0x19] = 0x08.toByte()
        paletteRAM[0x1A] = 0x18.toByte()
        paletteRAM[0x1B] = 0x28.toByte()
        paletteRAM[0x1C] = 0x0F.toByte()
        paletteRAM[0x1D] = 0x0A.toByte()
        paletteRAM[0x1E] = 0x1A.toByte()
        paletteRAM[0x1F] = 0x2A.toByte() */
    }

    fun step(elapsedCycles: Int) {
        repeat(elapsedCycles) {
            if (scanline == 0 && scanlineCycle == 0) {
                ppustatus = ppustatus and 0x3F
            }

            // MMC3 IRQ support
            if (scanline in 0..239 && scanlineCycle == 260) {
                if ((ppumask and 0x18) != 0 && bus.cartridge.mapper is Mapper4) {
                    val mmc3 = bus.cartridge.mapper as Mapper4
                    mmc3.runScanlineIRQ()
                    if (mmc3.irqAsserted) {
                        bus.cpu.requestIRQ()
                        mmc3.irqAsserted = false
                    }
                }
            }
            scanlineCycle++

            if (scanlineCycle >= 341) {
                scanlineCycle = 0

                if (scanline in 0..239) {
                    copyXFromTToV()  // убрать условие if (scanline == 0)
                    renderScanline(scanline)
                    incrementY()
                }

                if (scanline == 241) {
                    ppustatus = ppustatus or 0x80
                    if ((ppuctrl and 0x80) != 0) {
                        bus.cpu.requestNMI()
                    }
                }

                if (scanline == 261) {
                    v = t
                }

                scanline++
                if (scanline >= TOTAL_SCANLINES) {
                    scanline = 0
                }
            }
        }
    }

    private var debugFramesShown = 0

    private fun renderScanline(scanline: Int) {
        bgMask.fill(false)
        scanlineBuffer.fill(0)
        renderBackground()
        renderSprites()

        // Отладка: проверяем scanlineBuffer перед копированием
        if (scanline == 8 && debugFramesShown < 2) {
            var nonZeroCount = 0
            for (x in 0 until SCREEN_WIDTH) {
                if (scanlineBuffer[x] != 0) {
                    nonZeroCount++
                    if (nonZeroCount <= 3) {
                        println("scanlineBuffer[$x] = 0x${scanlineBuffer[x].toString(16)}")
                    }
                }
            }
            println("scanlineBuffer has $nonZeroCount non-zero pixels")
        }

        // Копируем в frameBuffer
        val startIndex = scanline * SCREEN_WIDTH
        System.arraycopy(scanlineBuffer, 0, frameBuffer, startIndex, SCREEN_WIDTH)

        // Отладка: проверяем frameBuffer после копирования
        if (scanline == 8 && debugFramesShown < 2) {
            println("frameBuffer[$startIndex] = 0x${frameBuffer[startIndex].toString(16)}")
            debugFramesShown++
        }
    }

    private fun renderBackground() {
        if ((ppumask and 0x08) == 0) return

        val bgColor = nesPalette[paletteRAM[0].toInt() and 0x3F]
        scanlineBuffer.fill(bgColor)

        var renderV = v
        for (tile in 0 until 33) {
            val coarseX = renderV and 0x001F
            val coarseY = (renderV shr 5) and 0x001F
            val nameTable = (renderV shr 10) and 0x0003

            val baseNTAddr = 0x2000 + (nameTable * 0x400)
            val tileAddr = baseNTAddr + (coarseY * 32) + coarseX
            val tileIndex = read(tileAddr.toUShort()).toInt()
            val fineY = (renderV shr 12) and 0x7

            val patternTable = if ((ppuctrl and 0x10) != 0) 0x1000 else 0x0000
            val patternAddr = patternTable + (tileIndex * 16) + fineY

            val plane0 = read(patternAddr.toUShort()).toInt()
            val plane1 = read((patternAddr + 8).toUShort()).toInt()

            val attrAddr = baseNTAddr + 0x3C0 + (coarseY / 4) * 8 + (coarseX / 4)
            val attrByte = read(attrAddr.toUShort()).toInt()
            val attrShift = ((coarseY and 0x02) shl 1) or (coarseX and 0x02)  // ← исправлено
            val paletteIndex = (attrByte shr attrShift) and 0x03               // ← исправлено

            for (i in 0 until 8) {
                val pixel = tile * 8 + i - fineX
                if (pixel < 0 || pixel >= SCREEN_WIDTH) continue

                val bitIndex = 7 - i
                val bit0 = (plane0 shr bitIndex) and 1
                val bit1 = (plane1 shr bitIndex) and 1
                val colorIndex = bit0 or (bit1 shl 1)

                if (colorIndex != 0) bgMask[pixel] = true
                scanlineBuffer[pixel] = getColorFromPalette(colorIndex, paletteIndex)
            }

            renderV = incrementX(renderV)
        }
    }

    private fun debugVRAM() {
        if (debugFramesShown < 1) {
            println("=== VRAM DEBUG ===")
            // Проверяем чтение из паттерн таблицы
            val patternAddr = 0x0000
            for (i in 0..4) {
                val b0 = read((patternAddr + i).toUShort()).toInt()
                val b1 = read((patternAddr + i + 8).toUShort()).toInt()
                println("Pattern[$i]: b0=0x${b0.toString(16)}, b1=0x${b1.toString(16)}")
            }

            // Проверяем чтение из неймтейбла
            val nameTableAddr = 0x2000
            for (i in 0..4) {
                val tile = read((nameTableAddr + i).toUShort()).toInt()
                println("NameTable[$i]: tile=0x${tile.toString(16)}")
            }
            debugFramesShown++
        }
    }

    private fun renderSprites() {
        if ((ppumask and 0x10) == 0) return

        val isSprite8x16 = (ppuctrl and 0x20) != 0
        val spritePixelDrawn = BooleanArray(SCREEN_WIDTH)

        for (i in 0 until 64) {
            val offset = i * 4
            val spriteY     = oam[offset].toInt() and 0xFF
            val tileIndex   = oam[offset + 1].toInt() and 0xFF
            val attributes  = oam[offset + 2].toInt() and 0xFF
            val spriteX     = oam[offset + 3].toInt() and 0xFF

            val paletteIndex = attributes and 0b11
            val flipX = (attributes and 0x40) != 0
            val flipY = (attributes and 0x80) != 0
            val priority = (attributes and 0x20) == 0

            val tileHeight = if (isSprite8x16) 16 else 8

            if (scanline < spriteY || scanline >= spriteY + tileHeight) continue

            var subY = scanline - spriteY
            if (flipY) subY = tileHeight - 1 - subY

            val subTileIndex = if (isSprite8x16) {
                (tileIndex and 0xFE) + (subY / 8)
            } else {
                tileIndex
            }

            val patternTable = if (isSprite8x16) {
                if ((tileIndex and 1) != 0) 0x1000 else 0x0000
            } else {
                if ((ppuctrl and 0x08) != 0) 0x1000 else 0x0000
            }

            val baseAddr = patternTable + subTileIndex * 16
            val plane0 = read((baseAddr + (subY % 8)).toUShort()).toInt()
            val plane1 = read((baseAddr + (subY % 8) + 8).toUShort()).toInt()

            for (x in 0 until 8) {
                val bit = if (flipX) x else 7 - x
                val bit0 = (plane0 shr bit) and 1
                val bit1 = (plane1 shr bit) and 1
                val color = bit0 or (bit1 shl 1)

                if (color == 0) continue

                val px = spriteX + x
                if (px < 0 || px >= SCREEN_WIDTH) continue

                // Sprite 0 Hit
                if (i == 0 && bgMask[px]) {
                    ppustatus = ppustatus or 0x40
                }

                if (spritePixelDrawn[px]) continue

                if (priority || !bgMask[px]) {
                    scanlineBuffer[px] = getSpriteColor(color, paletteIndex)
                    spritePixelDrawn[px] = true
                }
            }
        }
    }


    // ====================== Регистры ======================

    fun writeRegister(address: UShort, value: UByte) {
        val v8 = value.toInt() and 0xFF
        when (address.toInt()) {
            0x2000 -> {
                ppuctrl = v8
                t = (t and 0xF3FF) or ((v8 and 0x03) shl 10)
            }
            0x2001 -> ppumask = v8
            0x2002 -> {
                ppustatus = ppustatus and 0x7F
                scrollLatch = false
            }
            0x2003 -> oamaddr = v8
            0x2004 -> {
                oam[oamaddr] = value.toByte()
                oamaddr = (oamaddr + 1) and 0xFF
            }
            0x2005 -> {
                if (!scrollLatch) {
                    ppuscrollX = v8
                    fineX = v8 and 0x07
                    t = (t and 0xFFE0) or (v8 shr 3)
                } else {
                    ppuscrollY = v8
                    t = (t and 0x8FFF) or ((v8 and 0x07) shl 12)
                    t = (t and 0xFC1F) or ((v8 and 0xF8) shl 2)
                }
                scrollLatch = !scrollLatch
            }
            0x2006 -> {
                if (!addrLatch) {
                    t = (t and 0x00FF) or ((v8 and 0x3F) shl 8)
                } else {
                    t = (t and 0xFF00) or v8
                    this.v = t
                }
                addrLatch = !addrLatch
            }
            0x2007 -> {
                write(this.v.toUShort(), value)
                this.v = (this.v + if ((ppuctrl and 0x04) != 0) 32 else 1) and 0x7FFF
            }
        }
    }

    fun readRegister(address: UShort): UByte {
        return when (address.toInt()) {
            0x2002 -> {
                val result = ppustatus
                ppustatus = ppustatus and 0x3F
                addrLatch = false
                result.toUByte()
            }
            0x2004 -> oam[oamaddr].toUByte()
            0x2007 -> {
                var result = ppuDataBuffer
                ppuDataBuffer = read(v.toUShort()).toInt()
                if (v >= 0x3F00) result = ppuDataBuffer
                v = (v + if ((ppuctrl and 0x04) != 0) 32 else 1) and 0x7FFF
                result.toUByte()
            }
            else -> 0u
        }
    }

    // ====================== VRAM Access ======================

    fun read(address: UShort): UByte {
        val addr = address.toInt() and 0x3FFF
        return when {
            addr < 0x2000 -> {
                val result = bus.cartridge.ppuRead(addr.toUShort())
                if (debugFrameCount < 2) {
                    //println("PPU read from CHR-ROM: addr=0x${addr.toString(16)}, result=0x${result.toInt().toString(16)}")
                }
                result
            }
            addr <= 0x3EFF -> {
                val mirrored = mirrorVRAMAddress(addr)
                vram[mirrored].toUByte()
            }
            addr <= 0x3FFF -> {
                var mirrored = addr and 0x1F
                if (mirrored >= 0x10 && (mirrored % 4) == 0) mirrored -= 0x10
                paletteRAM[mirrored].toUByte()
            }
            else -> 0u
        }
    }

    fun write(address: UShort, value: UByte) {
        val addr = address.toInt() and 0x3FFF
        when {
            addr < 0x2000 -> bus.cartridge.ppuWrite(addr.toUShort(), value)
            addr <= 0x3EFF -> {
                val mirrored = mirrorVRAMAddress(addr)
                vram[mirrored] = value.toByte()
            }
            addr <= 0x3FFF -> {
                var mirrored = addr and 0x1F
                if (mirrored >= 0x10 && (mirrored % 4) == 0) mirrored -= 0x10
                paletteRAM[mirrored] = value.toByte()
            }
        }
    }

    private fun mirrorVRAMAddress(address: Int): Int {
        val offset = address and 0x0FFF
        val ntIndex = offset / 0x400
        val innerOffset = offset % 0x400
        return when (bus.cartridge.mirroringMode) {
            Mirroring.VERTICAL      -> (ntIndex % 2) * 0x400 + innerOffset
            Mirroring.HORIZONTAL    -> ((ntIndex / 2) * 0x400) + innerOffset
            Mirroring.SINGLE_SCREEN_A -> innerOffset
            Mirroring.SINGLE_SCREEN_B -> 0x400 + innerOffset
            Mirroring.FOUR_SCREEN   -> offset
        }
    }

    fun writeOAMDMA(page: UByte) {
        val baseAddr = (page.toInt() and 0xFF) shl 8
        for (i in 0 until 256) {
            val value = bus.read((baseAddr + i).toUShort())
            oam[oamaddr] = value.toByte()
            oamaddr = (oamaddr + 1) and 0xFF
        }
    }

    // ====================== Scrolling ======================

    private fun incrementY() {
        if ((v and 0x7000) != 0x7000) {
            v += 0x1000
        } else {
            v = v and 0x8FFF
            var y = (v and 0x03E0) shr 5
            y = when (y) {
                29   -> { v = v xor 0x0800; 0 }
                31   -> 0
                else -> y + 1
            }
            v = (v and 0xFC1F) or (y shl 5)
        }
    }

    private fun incrementX(addr: Int): Int {
        return if ((addr and 0x001F) == 31) {
            (addr and 0xFFE0) xor 0x0400
        } else {
            addr + 1
        }
    }

    private fun copyXFromTToV() {
        v = (v and 0xFBE0) or (t and 0x041F)
    }

    // ====================== Colors ======================

    private fun getSpriteColor(colorIndex: Int, paletteIndex: Int): Int {
        if (colorIndex == 0) return 0  // Прозрачный - не рисуем

        // Спрайты используют палитры 4-7 (адреса 0x10-0x1F в paletteRAM)
        val base = 0x10 + (paletteIndex * 4)
        val paletteAddr = base + colorIndex
        val colorCode = paletteRAM[paletteAddr].toInt() and 0x3F
        return nesPalette[colorCode]
    }

    private fun getColorFromPalette(colorIndex: Int, paletteIndex: Int): Int {
        if (colorIndex == 0) {
            // Цвет фона - используем palette[0]
            val colorCode = paletteRAM[0].toInt() and 0x3F
            return nesPalette[colorCode]
        } else {
            // Цвет из палитры фона (0-3)
            val base = paletteIndex * 4
            val paletteAddr = base + colorIndex
            val colorCode = paletteRAM[paletteAddr].toInt() and 0x3F
            return nesPalette[colorCode]
        }
    }

    fun updateBitmap() {
        bitmap.setPixels(frameBuffer, 0, SCREEN_WIDTH, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT)
    }

    // ====================== NES Палитра (64 цвета) ======================

    private val nesPalette = intArrayOf(
        0xFF545454.toInt(), 0xFF002E76.toInt(), 0xFF081090.toInt(), 0xFF300088.toInt(),
        0xFF440064.toInt(), 0xFF5C0030.toInt(), 0xFF540400.toInt(), 0xFF3C1800.toInt(),
        0xFF202A00.toInt(), 0xFF083A00.toInt(), 0xFF004000.toInt(), 0xFF003C00.toInt(),
        0xFF00323C.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(),

        0xFF989698.toInt(), 0xFF084CC4.toInt(), 0xFF3032EC.toInt(), 0xFF5C1EE4.toInt(),
        0xFF8814B0.toInt(), 0xFFA01464.toInt(), 0xFF982220.toInt(), 0xFF783C00.toInt(),
        0xFF545A00.toInt(), 0xFF287200.toInt(), 0xFF087C00.toInt(), 0xFF007628.toInt(),
        0xFF006678.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(),

        0xFFECECEC.toInt(), 0xFF4C9AEC.toInt(), 0xFF787CEC.toInt(), 0xFFB062EC.toInt(),
        0xFFE454EC.toInt(), 0xFFEC58B4.toInt(), 0xFFEC6A64.toInt(), 0xFFD48820.toInt(),
        0xFFA0AA00.toInt(), 0xFF74C400.toInt(), 0xFF4CD020.toInt(), 0xFF38CC6C.toInt(),
        0xFF38B4CC.toInt(), 0xFF3C3C3C.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(),

        0xFFECECEC.toInt(), 0xFFA8CCEC.toInt(), 0xFFBCBCEC.toInt(), 0xFFD4B2EC.toInt(),
        0xFFECA6EC.toInt(), 0xFFECA6D4.toInt(), 0xFFECB4B0.toInt(), 0xFFE4C490.toInt(),
        0xFFCCD278.toInt(), 0xFFB4DE78.toInt(), 0xFFA8E290.toInt(), 0xFF98E2B4.toInt(),
        0xFFA0D6E4.toInt(), 0xFFA0A2A0.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt()
    )
}