import com.bask0xff.nesandroid.Bus

class CPU(private val bus: Bus) {

    var A: Int = 0
    var X: Int = 0
    var Y: Int = 0
    var PC: Int = 0
    var SP: Int = 0
    var status: Int = 0

    private var irqRequested = false
    private var nmiRequested = false
    //private var lastOpcode: Int = 0
    //fun getLastOpcode(): Int = lastOpcode

    companion object {
        const val FLAG_C = 0
        const val FLAG_Z = 1
        const val FLAG_I = 2
        const val FLAG_D = 3
        const val FLAG_B = 4
        const val FLAG_U = 5
        const val FLAG_V = 6
        const val FLAG_N = 7
    }

    init {
        reset()
    }

    fun reset() {
        A = 0
        X = 0
        Y = 0
        SP = 0xFD
        status = 0x24

        val low = bus.read(0xFFFCu).toInt()
        val high = bus.read(0xFFFDu).toInt()
        PC = (high shl 8) or low
    }

    fun setFlag(bit: Int, value: Boolean) {
        if (value) {
            status = status or (1 shl bit)
        } else {
            status = status and (1 shl bit).inv()
        }
    }

    fun getFlag(bit: Int): Boolean = (status and (1 shl bit)) != 0

    private fun setZN(value: Int) {
        setFlag(FLAG_Z, (value and 0xFF) == 0)
        setFlag(FLAG_N, (value and 0x80) != 0)
    }

    fun requestIRQ() { irqRequested = true }
    fun requestNMI() { nmiRequested = true }

    private var lastOpcode: Int = 0
    fun getLastOpcode(): Int = lastOpcode

    fun executeInstruction(): Int {
        if (nmiRequested) {
            nmiRequested = false
            return nmi()
        }
        if (!getFlag(FLAG_I) && irqRequested) {
            irqRequested = false
            return irq()
        }

        val opcode = fetch()
        lastExecutedOpcode = opcode

        return when (opcode) {
            0x00 -> brk()
            0xEA -> nop()
            0x40 -> rti()
/*
            // Unofficial NOPs (просто пропускают байт/байты)
            0x80, 0x82, 0x89, 0xC2, 0xE2 -> { fetch(); 2 }           // NOP #imm (2 байта)
            0x04, 0x44, 0x64             -> { fetch(); 3 }            // NOP zp
            0x14, 0x34, 0x54, 0x74,
            0xD4, 0xF4                   -> { fetch(); 4 }            // NOP zp,X
            0x0C                         -> { fetch(); fetch(); 4 }   // NOP abs
            0x1C, 0x3C, 0x5C, 0x7C,
            0xDC, 0xFC                   -> { absoluteX(); 4 }        // NOP abs,X
            0x1A, 0x3A, 0x5A, 0x7A,
            0xDA, 0xFA                   -> 2                         // NOP implied

// ISB/ISC — increment memory then SBC
            0xF7 -> { val m = zeroPageX(); inc(m, 0); sbc(m, 6) }

// SLO — ASL then ORA
            0x03 -> { val m = indirectX(); asl(m, 0); ora(m, 8) }
            0x13 -> { val m = indirectY(); asl(m, 0); ora(m, 8) }

// RLA — ROL then AND
            0x3F -> { val m = absoluteX(); rol(m, 0); and(m, 7) }
*/
            // LDA
            0xA9 -> ldrA(immediate(), 2)
            0xA5 -> ldrA(zeroPage(), 3)
            0xB5 -> ldrA(zeroPageX(), 4)
            0xAD -> ldrA(absolute(), 4)
            0xBD -> ldrA(absoluteX(), 4)
            0xB9 -> ldrA(absoluteY(), 4)
            0xA1 -> ldrA(indirectX(), 6)
            0xB1 -> ldrA(indirectY(), 5)

            // LDX
            0xA2 -> ldrX(immediate(), 2)
            0xA6 -> ldrX(zeroPage(), 3)
            0xB6 -> ldrX(zeroPageY(), 4)
            0xAE -> ldrX(absolute(), 4)
            0xBE -> ldrX(absoluteY(), 4)

            // LDY
            0xA0 -> ldrY(immediate(), 2)
            0xA4 -> ldrY(zeroPage(), 3)
            0xB4 -> ldrY(zeroPageX(), 4)
            0xAC -> ldrY(absolute(), 4)
            0xBC -> ldrY(absoluteX(), 4)

            // STA
            0x85 -> strA(zeroPage(), 3)
            0x95 -> strA(zeroPageX(), 4)
            0x8D -> strA(absolute(), 4)
            0x9D -> strA(absoluteX(), 5)
            0x99 -> strA(absoluteY(), 5)
            0x81 -> strA(indirectX(), 6)
            0x91 -> strA(indirectY(), 6)

            // STX
            0x86 -> strX(zeroPage(), 3)
            0x96 -> strX(zeroPageY(), 4)
            0x8E -> strX(absolute(), 4)

            // STY
            0x84 -> strY(zeroPage(), 3)
            0x94 -> strY(zeroPageX(), 4)
            0x8C -> strY(absolute(), 4)

            // Transfer
            0xAA -> tax()
            0xA8 -> tay()
            0x8A -> txa()
            0x98 -> tya()

            // Stack
            0xBA -> tsx()
            0x9A -> txs()
            0x48 -> pha()
            0x08 -> php()
            0x68 -> pla()
            0x28 -> plp()

            // Logical
            0x29 -> and(immediate(), 2)
            0x25 -> and(zeroPage(), 3)
            0x35 -> and(zeroPageX(), 4)
            0x2D -> and(absolute(), 4)
            0x3D -> and(absoluteX(), 4)
            0x39 -> and(absoluteY(), 4)
            0x21 -> and(indirectX(), 6)
            0x31 -> and(indirectY(), 5)

            0x49 -> eor(immediate(), 2)
            0x45 -> eor(zeroPage(), 3)
            0x55 -> eor(zeroPageX(), 4)
            0x4D -> eor(absolute(), 4)
            0x5D -> eor(absoluteX(), 4)
            0x59 -> eor(absoluteY(), 4)
            0x41 -> eor(indirectX(), 6)
            0x51 -> eor(indirectY(), 5)

            0x09 -> ora(immediate(), 2)
            0x05 -> ora(zeroPage(), 3)
            0x15 -> ora(zeroPageX(), 4)
            0x0D -> ora(absolute(), 4)
            0x1D -> ora(absoluteX(), 4)
            0x19 -> ora(absoluteY(), 4)
            0x01 -> ora(indirectX(), 6)
            0x11 -> ora(indirectY(), 5)

            0x24 -> bit(zeroPage(), 3)
            0x2C -> bit(absolute(), 4)

            // Arithmetic
            0x69 -> adc(immediate(), 2)
            0x65 -> adc(zeroPage(), 3)
            0x75 -> adc(zeroPageX(), 4)
            0x6D -> adc(absolute(), 4)
            0x7D -> adc(absoluteX(), 4)
            0x79 -> adc(absoluteY(), 4)
            0x61 -> adc(indirectX(), 6)
            0x71 -> adc(indirectY(), 5)

            0xE9 -> sbc(immediate(), 2)
            0xE5 -> sbc(zeroPage(), 3)
            0xF5 -> sbc(zeroPageX(), 4)
            0xED -> sbc(absolute(), 4)
            0xFD -> sbc(absoluteX(), 4)
            0xF9 -> sbc(absoluteY(), 4)
            0xE1 -> sbc(indirectX(), 6)
            0xF1 -> sbc(indirectY(), 5)

            // Compare
            0xC9 -> cpr(A, immediate(), 2)
            0xC5 -> cpr(A, zeroPage(), 3)
            0xD5 -> cpr(A, zeroPageX(), 4)
            0xCD -> cpr(A, absolute(), 4)
            0xDD -> cpr(A, absoluteX(), 4)
            0xD9 -> cpr(A, absoluteY(), 4)
            0xC1 -> cpr(A, indirectX(), 6)
            0xD1 -> cpr(A, indirectY(), 5)

            0xE0 -> cpr(X, immediate(), 2)
            0xE4 -> cpr(X, zeroPage(), 3)
            0xEC -> cpr(X, absolute(), 4)

            0xC0 -> cpr(Y, immediate(), 2)
            0xC4 -> cpr(Y, zeroPage(), 3)
            0xCC -> cpr(Y, absolute(), 4)

            // Inc/Dec
            0xE6 -> inc(zeroPage(), 5)
            0xF6 -> inc(zeroPageX(), 6)
            0xEE -> inc(absolute(), 6)
            0xFE -> inc(absoluteX(), 7)
            0xE8 -> inx()
            0xC8 -> iny()
            0xC6 -> dec(zeroPage(), 5)
            0xD6 -> dec(zeroPageX(), 6)
            0xCE -> dec(absolute(), 6)
            0xDE -> dec(absoluteX(), 7)
            0xCA -> dex()
            0x88 -> dey()

            // Shifts
            0x0A -> aslAcc()
            0x06 -> asl(zeroPage(), 5)
            0x16 -> asl(zeroPageX(), 6)
            0x0E -> asl(absolute(), 6)
            0x1E -> asl(absoluteX(), 7)

            0x4A -> lsrAcc()
            0x46 -> lsr(zeroPage(), 5)
            0x56 -> lsr(zeroPageX(), 6)
            0x4E -> lsr(absolute(), 6)
            0x5E -> lsr(absoluteX(), 7)

            0x2A -> rolAcc()
            0x26 -> rol(zeroPage(), 5)
            0x36 -> rol(zeroPageX(), 6)
            0x2E -> rol(absolute(), 6)
            0x3E -> rol(absoluteX(), 7)

            0x6A -> rorAcc()
            0x66 -> ror(zeroPage(), 5)
            0x76 -> ror(zeroPageX(), 6)
            0x6E -> ror(absolute(), 6)
            0x7E -> ror(absoluteX(), 7)

            // Jump / Call
            0x4C -> jmp(absolute())
            0x6C -> jmp(indirect())
            0x20 -> jsr()
            0x60 -> rts()

            // Branches
            0x90 -> branch(!getFlag(FLAG_C))
            0xB0 -> branch(getFlag(FLAG_C))
            0xF0 -> branch(getFlag(FLAG_Z))
            0x30 -> branch(getFlag(FLAG_N))
            0xD0 -> branch(!getFlag(FLAG_Z))
            0x10 -> branch(!getFlag(FLAG_N))
            0x50 -> branch(!getFlag(FLAG_V))
            0x70 -> branch(getFlag(FLAG_V))

            // Flags
            0x18 -> { setFlag(FLAG_C, false); 2 }
            0xD8 -> { setFlag(FLAG_D, false); 2 }
            0x58 -> { setFlag(FLAG_I, false); 2 }
            0xB8 -> { setFlag(FLAG_V, false); 2 }
            0x38 -> { setFlag(FLAG_C, true); 2 }
            0xF8 -> { setFlag(FLAG_D, true); 2 }
            0x78 -> { setFlag(FLAG_I, true); 2 }

            // SRE = LSR память, потом EOR с A
            0x47 -> { val m = zeroPage();  lsr(m, 0); eor(m, 5) }
            0x4F -> { val m = absolute();  lsr(m, 0); eor(m, 6) }
            0x53 -> { val m = indirectY(); lsr(m, 0); eor(m, 8) }
            0x57 -> { val m = zeroPageX(); lsr(m, 0); eor(m, 6) }
            0x5B -> { val m = absoluteY(); lsr(m, 0); eor(m, 7) }
            0x5F -> { val m = absoluteX(); lsr(m, 0); eor(m, 7) }

            // ISB = INC память, потом SBC
            0xFB -> { val m = absoluteY(); inc(m, 0); sbc(m, 7) }
            0xE3 -> { val m = indirectX(); inc(m, 0); sbc(m, 8) }
            0xE7 -> { val m = zeroPage();  inc(m, 0); sbc(m, 5) }
            0xEF -> { val m = absolute();  inc(m, 0); sbc(m, 6) }
            0xF3 -> { val m = indirectY(); inc(m, 0); sbc(m, 8) }
            0xF7 -> { val m = zeroPageX(); inc(m, 0); sbc(m, 6) }
            0xFF -> { val m = absoluteX(); inc(m, 0); sbc(m, 7) }

            0x1A, 0x3A, 0x5A, 0x7A, 0xDA, 0xFA -> 2
            0x80, 0x82, 0x89, 0xC2, 0xE2 -> { fetch(); 2 }
            0x04, 0x44, 0x64 -> { fetch(); 3 }
            0x14, 0x34, 0x54, 0x74, 0xD4, 0xF4 -> { fetch(); 4 }
            0x1C, 0x3C, 0x5C, 0x7C, 0xDC, 0xFC -> { absoluteX(); 4 }

            else -> {
                println("Unimplemented Opcode: ${opcode.toString(16).uppercase()} at PC: ${(PC - 1).toString(16).uppercase()}")
                2  // минимум 2 цикла чтобы CPU двигался вперёд
            }
        }
    }

    private fun fetch(): Int = bus.read(PC++.toUShort()).toInt() and 0xFF

    private fun fetch16(): Int {
        val low = fetch()
        val high = fetch()
        return (high shl 8) or low
    }

    private data class AddrResult(val address: Int, val extraCycles: Int = 0)

    private fun immediate() = AddrResult(PC++)
    private fun zeroPage() = AddrResult(fetch())
    private fun zeroPageX() = AddrResult((fetch() + X) and 0xFF)
    private fun zeroPageY() = AddrResult((fetch() + Y) and 0xFF)
    private fun absolute() = AddrResult(fetch16())

    private fun absoluteX(): AddrResult {
        val base = fetch16()
        val eff = base + X
        return AddrResult(eff and 0xFFFF, if ((base and 0xFF00) != (eff and 0xFF00)) 1 else 0)
    }

    private fun absoluteY(): AddrResult {
        val base = fetch16()
        val eff = base + Y
        return AddrResult(eff and 0xFFFF, if ((base and 0xFF00) != (eff and 0xFF00)) 1 else 0)
    }

    private fun indirectX(): AddrResult {
        val zp = fetch()
        val ptr = (zp + X) and 0xFF
        val low = bus.read(ptr.toUShort()).toInt()
        val high = bus.read(((ptr + 1) and 0xFF).toUShort()).toInt()
        return AddrResult((high shl 8) or low)
    }

    private fun indirectY(): AddrResult {
        val zp = fetch()
        val baseLow = bus.read(zp.toUShort()).toInt()
        val baseHigh = bus.read(((zp + 1) and 0xFF).toUShort()).toInt()
        val base = (baseHigh shl 8) or baseLow
        val eff = base + Y
        return AddrResult(eff and 0xFFFF, if ((base and 0xFF00) != (eff and 0xFF00)) 1 else 0)
    }

    private fun indirect(): AddrResult {
        val ptr = fetch16()
        val low = bus.read(ptr.toUShort()).toInt()
        val high = if ((ptr and 0x00FF) == 0x00FF)
            bus.read((ptr and 0xFF00).toUShort()).toInt()
        else
            bus.read((ptr + 1).toUShort()).toInt()
        return AddrResult((high shl 8) or low)
    }

    private fun push(value: Int) {
        bus.write((0x0100 + SP).toUShort(), (value and 0xFF).toUByte())
        SP = (SP - 1) and 0xFF
    }

    private fun pop(): Int {
        SP = (SP + 1) and 0xFF
        return bus.read((0x0100 + SP).toUShort()).toInt()
    }

    private fun ldrA(mode: AddrResult, base: Int): Int {
        A = bus.read(mode.address.toUShort()).toInt() and 0xFF
        setZN(A)
        return base + mode.extraCycles
    }

    private fun ldrX(mode: AddrResult, base: Int): Int {
        X = bus.read(mode.address.toUShort()).toInt() and 0xFF
        setZN(X)
        return base + mode.extraCycles
    }

    private fun ldrY(mode: AddrResult, base: Int): Int {
        Y = bus.read(mode.address.toUShort()).toInt() and 0xFF
        setZN(Y)
        return base + mode.extraCycles
    }

    private fun strA(mode: AddrResult, base: Int): Int { bus.write(mode.address.toUShort(), (A and 0xFF).toUByte()); return base }
    private fun strX(mode: AddrResult, base: Int): Int { bus.write(mode.address.toUShort(), (X and 0xFF).toUByte()); return base }
    private fun strY(mode: AddrResult, base: Int): Int { bus.write(mode.address.toUShort(), (Y and 0xFF).toUByte()); return base }

    private fun tax(): Int { X = A; setZN(X); return 2 }
    private fun tay(): Int { Y = A; setZN(Y); return 2 }
    private fun txa(): Int { A = X; setZN(A); return 2 }
    private fun tya(): Int { A = Y; setZN(A); return 2 }

    private fun tsx(): Int { X = SP; setZN(X); return 2 }
    private fun txs(): Int { SP = X; return 2 }
    private fun pha(): Int { push(A); return 3 }
    private fun php(): Int { push(status or 0x30); return 3 }
    private fun pla(): Int { A = pop(); setZN(A); return 4 }
    private fun plp(): Int { status = (pop() and 0xEF) or 0x20; return 4 }
    // 0xEF = 11101111 — очищает бит 4 (FLAG_B), устанавливает бит 5 (FLAG_U)


    private fun and(mode: AddrResult, base: Int): Int { A = A and bus.read(mode.address.toUShort()).toInt(); setZN(A); return base + mode.extraCycles }
    private fun eor(mode: AddrResult, base: Int): Int { A = A xor bus.read(mode.address.toUShort()).toInt(); setZN(A); return base + mode.extraCycles }
    private fun ora(mode: AddrResult, base: Int): Int { A = A or bus.read(mode.address.toUShort()).toInt(); setZN(A); return base + mode.extraCycles }

    private fun bit(mode: AddrResult, base: Int): Int {
        val value = bus.read(mode.address.toUShort()).toInt()
        setFlag(FLAG_Z, (A and value) == 0)
        setFlag(FLAG_N, (value and 0x80) != 0)
        setFlag(FLAG_V, (value and 0x40) != 0)
        return base + mode.extraCycles
    }

    private fun adc(mode: AddrResult, base: Int): Int {
        val m = bus.read(mode.address.toUShort()).toInt()
        val sum = A + m + if (getFlag(FLAG_C)) 1 else 0
        setFlag(FLAG_C, sum > 0xFF)
        setFlag(FLAG_Z, (sum and 0xFF) == 0)
        setFlag(FLAG_N, (sum and 0x80) != 0)
        setFlag(FLAG_V, ((A xor m).inv() and (A xor sum) and 0x80) != 0)
        A = sum and 0xFF
        return base + mode.extraCycles
    }

    private fun sbc(mode: AddrResult, base: Int): Int {
        val m = (bus.read(mode.address.toUShort()).toInt() xor 0xFF)
        val sum = A + m + if (getFlag(FLAG_C)) 1 else 0
        setFlag(FLAG_C, sum > 0xFF)
        setFlag(FLAG_Z, (sum and 0xFF) == 0)
        setFlag(FLAG_N, (sum and 0x80) != 0)
        setFlag(FLAG_V, ((A xor sum) and (m xor sum) and 0x80) != 0)
        A = sum and 0xFF
        return base + mode.extraCycles
    }

    private fun cpr(reg: Int, mode: AddrResult, base: Int): Int {
        val m = bus.read(mode.address.toUShort()).toInt()
        val temp = reg - m
        setFlag(FLAG_C, reg >= m)
        setFlag(FLAG_Z, (temp and 0xFF) == 0)
        setFlag(FLAG_N, (temp and 0x80) != 0)
        return base + mode.extraCycles
    }

    private fun inc(mode: AddrResult, base: Int): Int {
        val result = (bus.read(mode.address.toUShort()).toInt() + 1) and 0xFF
        bus.write(mode.address.toUShort(), result.toUByte())
        setZN(result)
        return base
    }

    private fun dec(mode: AddrResult, base: Int): Int {
        val result = (bus.read(mode.address.toUShort()).toInt() - 1) and 0xFF
        bus.write(mode.address.toUShort(), result.toUByte())
        setZN(result)
        return base
    }

    private fun inx(): Int { X = (X + 1) and 0xFF; setZN(X); return 2 }
    private fun iny(): Int { Y = (Y + 1) and 0xFF; setZN(Y); return 2 }
    private fun dex(): Int { X = (X - 1) and 0xFF; setZN(X); return 2 }
    private fun dey(): Int { Y = (Y - 1) and 0xFF; setZN(Y); return 2 }

    private fun aslAcc(): Int {
        setFlag(FLAG_C, (A and 0x80) != 0)
        A = (A shl 1) and 0xFF
        setZN(A)
        return 2
    }

    private fun asl(mode: AddrResult, base: Int): Int {
        val value = bus.read(mode.address.toUShort()).toInt()
        setFlag(FLAG_C, (value and 0x80) != 0)
        val result = (value shl 1) and 0xFF
        bus.write(mode.address.toUShort(), result.toUByte())
        setZN(result)
        return base
    }

    private fun lsrAcc(): Int {
        setFlag(FLAG_C, (A and 0x01) != 0)
        A = A shr 1
        setZN(A)
        return 2
    }

    private fun lsr(mode: AddrResult, base: Int): Int {
        val value = bus.read(mode.address.toUShort()).toInt()
        setFlag(FLAG_C, (value and 0x01) != 0)
        val result = value shr 1
        bus.write(mode.address.toUShort(), result.toUByte())
        setZN(result)
        return base
    }

    private fun rolAcc(): Int {
        val oldC = getFlag(FLAG_C)
        setFlag(FLAG_C, (A and 0x80) != 0)
        A = ((A shl 1) or (if (oldC) 1 else 0)) and 0xFF
        setZN(A)
        return 2
    }

    private fun rol(mode: AddrResult, base: Int): Int {
        val value = bus.read(mode.address.toUShort()).toInt()
        val oldC = getFlag(FLAG_C)
        setFlag(FLAG_C, (value and 0x80) != 0)
        val result = ((value shl 1) or (if (oldC) 1 else 0)) and 0xFF
        bus.write(mode.address.toUShort(), result.toUByte())
        setZN(result)
        return base
    }

    private fun rorAcc(): Int {
        val oldC = getFlag(FLAG_C)
        setFlag(FLAG_C, (A and 0x01) != 0)
        A = ((A shr 1) or (if (oldC) 0x80 else 0)) and 0xFF
        setZN(A)
        return 2
    }

    private fun ror(mode: AddrResult, base: Int): Int {
        val value = bus.read(mode.address.toUShort()).toInt()
        val oldC = getFlag(FLAG_C)
        setFlag(FLAG_C, (value and 0x01) != 0)
        val result = ((value shr 1) or (if (oldC) 0x80 else 0)) and 0xFF
        bus.write(mode.address.toUShort(), result.toUByte())
        setZN(result)
        return base
    }

    private fun jmp(mode: AddrResult): Int { PC = mode.address; return 3 }

    private fun jsr(): Int {
        val target = fetch16()
        val ret = PC - 1
        push(ret shr 8)
        push(ret and 0xFF)
        PC = target
        return 6
    }

    private fun rts(): Int {
        val low = pop()
        val high = pop()
        PC = ((high shl 8) or low) + 1
        return 6
    }

    private fun branch(condition: Boolean): Int {
        val offset = fetch().toByte().toInt()
        if (condition) {
            val oldPC = PC
            PC = (PC + offset) and 0xFFFF
            val pageCross = if ((oldPC and 0xFF00) != (PC and 0xFF00)) 1 else 0
            return 3 + pageCross
        }
        return 2
    }

    private fun nop() = 2

    private fun brk(): Int {
        PC++
        push(PC shr 8)
        push(PC and 0xFF)
        push(status or 0x30)
        setFlag(FLAG_I, true)
        val low = bus.read(0xFFFEu).toInt()
        val high = bus.read(0xFFFFu).toInt()
        PC = (high shl 8) or low
        return 7
    }

    private fun rti(): Int {
        status = (pop() and 0xEF) or 0x20
        val low = pop()
        val high = pop()
        PC = (high shl 8) or low
        return 6
    }

    private fun irq(): Int {
        if (!getFlag(FLAG_I)) {
            push(PC shr 8)
            push(PC and 0xFF)
            push(status)
            setFlag(FLAG_I, true)
            val low = bus.read(0xFFFEu).toInt()
            val high = bus.read(0xFFFFu).toInt()
            PC = (high shl 8) or low
            return 7
        }
        return 0
    }

    private fun nmi(): Int {
        push(PC shr 8)
        push(PC and 0xFF)
        push(status)
        setFlag(FLAG_I, true)
        val low = bus.read(0xFFFAu).toInt()
        val high = bus.read(0xFFFBu).toInt()
        PC = (high shl 8) or low
        return 7
    }


    private var lastExecutedOpcode: Int = 0

    fun getLastExecutedOpcode(): Int = lastExecutedOpcode
}