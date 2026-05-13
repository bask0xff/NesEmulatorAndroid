package com.bask0xff.nesandroid

class CPU(private val bus: Bus) {

    var A: UByte = 0u
    var X: UByte = 0u
    var Y: UByte = 0u
    var PC: UShort = 0u
    var SP: UByte = 0u
    var status: UByte = 0u

    private var irqRequested = false
    private var nmiRequested = false

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
        A = 0u
        X = 0u
        Y = 0u
        SP = 0xFDu
        status = 0x24u

        val low = bus.read(0xFFFCu)
        val high = bus.read(0xFFFDu)
        PC = (high.toUShort() shl 8) or low.toUShort()
    }

    fun setFlag(bit: Int, value: Boolean) {
        if (value)
            status = status or (1u shl bit).toUByte()
        else
            status = status and (1u shl bit).inv().toUByte()
    }

    fun getFlag(bit: Int): Boolean = (status.toInt() and (1 shl bit)) != 0

    private fun setZN(value: UByte) {
        setFlag(FLAG_Z, value == 0u)
        setFlag(FLAG_N, (value.toInt() and 0x80) != 0)
    }

    fun requestIRQ() {
        irqRequested = true
    }

    fun requestNMI() {
        nmiRequested = true
    }

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

        return when (opcode.toInt()) {
            // System
            0x00 -> brk()
            0xEA -> nop()
            0x40 -> rti()

            // LDA
            0xA9 -> ldr(ref A, immediate(), 2)
            0xA5 -> ldr(ref A, zeroPage(), 3)
            0xB5 -> ldr(ref A, zeroPageX(), 4)
            0xAD -> ldr(ref A, absolute(), 4)
            0xBD -> ldr(ref A, absoluteX(), 4)
            0xB9 -> ldr(ref A, absoluteY(), 4)
            0xA1 -> ldr(ref A, indirectX(), 6)
            0xB1 -> ldr(ref A, indirectY(), 5)

            // LDX
            0xA2 -> ldr(ref X, immediate(), 2)
            0xA6 -> ldr(ref X, zeroPage(), 3)
            0xB6 -> ldr(ref X, zeroPageY(), 4)
            0xAE -> ldr(ref X, absolute(), 4)
            0xBE -> ldr(ref X, absoluteY(), 4)

            // LDY
            0xA0 -> ldr(ref Y, immediate(), 2)
            0xA4 -> ldr(ref Y, zeroPage(), 3)
            0xB4 -> ldr(ref Y, zeroPageX(), 4)
            0xAC -> ldr(ref Y, absolute(), 4)
            0xBC -> ldr(ref Y, absoluteX(), 4)

            // STA
            0x85 -> str(ref A, zeroPage(), 3)
            0x95 -> str(ref A, zeroPageX(), 4)
            0x8D -> str(ref A, absolute(), 4)
            0x9D -> str(ref A, absoluteX(), 5)
            0x99 -> str(ref A, absoluteY(), 5)
            0x81 -> str(ref A, indirectX(), 6)
            0x91 -> str(ref A, indirectY(), 6)

            // STX
            0x86 -> str(ref X, zeroPage(), 3)
            0x96 -> str(ref X, zeroPageY(), 4)
            0x8E -> str(ref X, absolute(), 4)

            // STY
            0x84 -> str(ref Y, zeroPage(), 3)
            0x94 -> str(ref Y, zeroPageX(), 4)
            0x8C -> str(ref Y, absolute(), 4)

            // Transfer
            0xAA -> trr(ref X, ref A, 2)
            0xA8 -> trr(ref Y, ref A, 2)
            0x8A -> trr(ref A, ref X, 2)
            0x98 -> trr(ref A, ref Y, 2)

            // Stack
            0xBA -> tsx(2)
            0x9A -> txs(2)
            0x48 -> pha(3)
            0x08 -> php(3)
            0x68 -> pla(4)
            0x28 -> plp(4)

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
            0xE8 -> inr(ref X, 2)
            0xC8 -> inr(ref Y, 2)

            0xC6 -> dec(zeroPage(), 5)
            0xD6 -> dec(zeroPageX(), 6)
            0xCE -> dec(absolute(), 6)
            0xDE -> dec(absoluteX(), 7)
            0xCA -> der(ref X, 2)
            0x88 -> der(ref Y, 2)

            // Shifts
            0x0A -> asl(accumulator(), 2)
            0x06 -> asl(zeroPage(), 5)
            0x16 -> asl(zeroPageX(), 6)
            0x0E -> asl(absolute(), 6)
            0x1E -> asl(absoluteX(), 7)

            0x4A -> lsr(accumulator(), 2)
            0x46 -> lsr(zeroPage(), 5)
            0x56 -> lsr(zeroPageX(), 6)
            0x4E -> lsr(absolute(), 6)
            0x5E -> lsr(absoluteX(), 7)

            0x2A -> rol(accumulator(), 2)
            0x26 -> rol(zeroPage(), 5)
            0x36 -> rol(zeroPageX(), 6)
            0x2E -> rol(absolute(), 6)
            0x3E -> rol(absoluteX(), 7)

            0x6A -> ror(accumulator(), 2)
            0x66 -> ror(zeroPage(), 5)
            0x76 -> ror(zeroPageX(), 6)
            0x6E -> ror(absolute(), 6)
            0x7E -> ror(absoluteX(), 7)

            // Jump / Subroutine
            0x4C -> jmp(absolute(), 3)
            0x6C -> jmp(indirect(), 5)
            0x20 -> jsr()
            0x60 -> rts()

            // Branches
            0x90 -> bif(!getFlag(FLAG_C), relative(), 2)
            0xB0 -> bif(getFlag(FLAG_C), relative(), 2)
            0xF0 -> bif(getFlag(FLAG_Z), relative(), 2)
            0x30 -> bif(getFlag(FLAG_N), relative(), 2)
            0xD0 -> bif(!getFlag(FLAG_Z), relative(), 2)
            0x10 -> bif(!getFlag(FLAG_N), relative(), 2)
            0x50 -> bif(!getFlag(FLAG_V), relative(), 2)
            0x70 -> bif(getFlag(FLAG_V), relative(), 2)

            // Flags
            0x18 -> fsc(FLAG_C, false, 2)
            0xD8 -> fsc(FLAG_D, false, 2)
            0x58 -> fsc(FLAG_I, false, 2)
            0xB8 -> fsc(FLAG_V, false, 2)
            0x38 -> fsc(FLAG_C, true, 2)
            0xF8 -> fsc(FLAG_D, true, 2)
            0x78 -> fsc(FLAG_I, true, 2)

            else -> {
                println("Unimplemented Opcode: ${opcode.toString(16).uppercase()} at PC: ${(PC - 1u).toString(16).uppercase()}")
                throw NotImplementedError("Opcode 0x${opcode.toString(16).uppercase()}")
            }
        }
    }

    private fun fetch(): UByte = bus.read(PC++)

    private fun fetch16(): UShort {
        val low = fetch()
        val high = fetch()
        return (high.toUShort() shl 8) or low.toUShort()
    }

    private data class AddrResult(val address: UShort, val extraCycles: Int = 0)

    private fun implied() = AddrResult(0u)
    private fun accumulator() = AddrResult(0u)
    private fun immediate() = AddrResult(PC++)
    private fun zeroPage() = AddrResult(fetch().toUShort())
    private fun zeroPageX() = AddrResult((fetch() + X).toUShort())
    private fun zeroPageY() = AddrResult((fetch() + Y).toUShort())
    private fun absolute() = AddrResult(fetch16())

    private fun absoluteX(): AddrResult {
        val base = fetch16()
        val eff = base + X.toUShort()
        return AddrResult(eff, if (hasPageCross(base, eff)) 1 else 0)
    }

    private fun absoluteY(): AddrResult {
        val base = fetch16()
        val eff = base + Y.toUShort()
        return AddrResult(eff, if (hasPageCross(base, eff)) 1 else 0)
    }

    private fun indirectX(): AddrResult {
        val zp = fetch()
        val ptr = (zp + X).toUByte()
        val low = bus.read(ptr.toUShort())
        val high = bus.read((ptr + 1u).toUShort())
        return AddrResult((high.toUShort() shl 8) or low.toUShort())
    }

    private fun indirectY(): AddrResult {
        val zp = fetch()
        val base = (bus.read((zp + 1u).toUShort()).toUShort() shl 8) or bus.read(zp.toUShort()).toUShort()
        val eff = base + Y.toUShort()
        return AddrResult(eff, if (hasPageCross(base, eff)) 1 else 0)
    }

    private fun indirect(): AddrResult {
        val ptr = fetch16()
        val low = bus.read(ptr)
        val high = if ((ptr and 0x00FFu) == 0x00FFu)
            bus.read(ptr and 0xFF00u) else bus.read(ptr + 1u)
        return AddrResult((high.toUShort() shl 8) or low.toUShort())
    }

    private fun relative(): AddrResult {
        val offset = fetch().toByte()
        val target = PC + offset.toUShort()
        return AddrResult(target, if (hasPageCross(PC - 1u, target)) 1 else 0)
    }

    private fun hasPageCross(base: UShort, eff: UShort): Boolean =
        (base and 0xFF00u) != (eff and 0xFF00u)

    private fun push(value: UByte) {
        bus.write(0x0100u + SP.toUShort(), value)
        SP = (SP - 1u) and 0xFFu
    }

    private fun pop(): UByte {
        SP = (SP + 1u) and 0xFFu
        return bus.read(0x0100u + SP.toUShort())
    }

    // ==================== Instructions ====================

    private fun ldr(reg: UByte, mode: AddrResult, baseCycles: Int): Int {
        val value = bus.read(mode.address)
        when (reg) {
            A -> A = value
            X -> X = value
            Y -> Y = value
        }
        setZN(value)
        return baseCycles + mode.extraCycles
    }

    private fun str(reg: UByte, mode: AddrResult, baseCycles: Int): Int {
        val value = when (reg) {
            A -> A
            X -> X
            Y -> Y
            else -> 0u
        }
        bus.write(mode.address, value)
        return baseCycles
    }

    private fun trr(to: UByte, from: UByte, cycles: Int): Int {
        when (to) {
            A -> A = from
            X -> X = from
            Y -> Y = from
        }
        setZN(to)
        return cycles
    }

    private fun tsx(cycles: Int): Int { X = SP; setZN(X); return cycles }
    private fun txs(cycles: Int): Int { SP = X; return cycles }
    private fun pha(cycles: Int): Int { push(A); return cycles }
    private fun php(cycles: Int): Int {
        push(status or (1u shl FLAG_B).toUByte() or (1u shl FLAG_U).toUByte())
        return cycles
    }
    private fun pla(cycles: Int): Int { A = pop(); setZN(A); return cycles }
    private fun plp(cycles: Int): Int {
        status = pop()
        setFlag(FLAG_U, true)
        setFlag(FLAG_B, false)
        return cycles
    }

    private fun and(mode: AddrResult, base: Int): Int { A = A and bus.read(mode.address); setZN(A); return base + mode.extraCycles }
    private fun eor(mode: AddrResult, base: Int): Int { A = A xor bus.read(mode.address); setZN(A); return base + mode.extraCycles }
    private fun ora(mode: AddrResult, base: Int): Int { A = A or bus.read(mode.address); setZN(A); return base + mode.extraCycles }

    private fun bit(mode: AddrResult, base: Int): Int {
        val value = bus.read(mode.address)
        setFlag(FLAG_Z, (A and value) == 0u)
        setFlag(FLAG_N, (value.toInt() and 0x80) != 0)
        setFlag(FLAG_V, (value.toInt() and 0x40) != 0)
        return base + mode.extraCycles
    }

    private fun adc(mode: AddrResult, base: Int): Int {
        val m = bus.read(mode.address).toInt()
        val sum = A.toInt() + m + if (getFlag(FLAG_C)) 1 else 0
        setFlag(FLAG_C, sum > 0xFF)
        setFlag(FLAG_Z, (sum and 0xFF) == 0)
        setFlag(FLAG_N, (sum and 0x80) != 0)
        setFlag(FLAG_V, ((A.toInt() xor m) and (A.toInt() xor sum) and 0x80) != 0)
        A = (sum and 0xFF).toUByte()
        return base + mode.extraCycles
    }

    private fun sbc(mode: AddrResult, base: Int): Int {
        val m = (bus.read(mode.address) xor 0xFFu).toInt()
        val sum = A.toInt() + m + if (getFlag(FLAG_C)) 1 else 0
        setFlag(FLAG_C, sum > 0xFF)
        setFlag(FLAG_Z, (sum and 0xFF) == 0)
        setFlag(FLAG_N, (sum and 0x80) != 0)
        setFlag(FLAG_V, ((A.toInt() xor sum) and (m xor sum) and 0x80) != 0)
        A = (sum and 0xFF).toUByte()
        return base + mode.extraCycles
    }

    private fun cpr(reg: UByte, mode: AddrResult, base: Int): Int {
        val m = bus.read(mode.address)
        val temp = reg.toInt() - m.toInt()
        setFlag(FLAG_C, reg >= m)
        setFlag(FLAG_Z, (temp and 0xFF) == 0)
        setFlag(FLAG_N, (temp and 0x80) != 0)
        return base + mode.extraCycles
    }

    private fun inc(mode: AddrResult, base: Int): Int {
        val result = (bus.read(mode.address) + 1u)
        bus.write(mode.address, result)
        setZN(result)
        return base
    }

    private fun dec(mode: AddrResult, base: Int): Int {
        val result = (bus.read(mode.address) - 1u)
        bus.write(mode.address, result)
        setZN(result)
        return base
    }

    private fun inr(reg: UByte, cycles: Int): Int {
        when (reg) { A -> A++ ; X -> X++ ; Y -> Y++ }
        setZN(reg)
        return cycles
    }

    private fun der(reg: UByte, cycles: Int): Int {
        when (reg) { A -> A-- ; X -> X-- ; Y -> Y-- }
        setZN(reg)
        return cycles
    }

    private fun asl(mode: AddrResult, base: Int): Int {
        val value = if (mode.address == 0u) A else bus.read(mode.address)
        setFlag(FLAG_C, (value.toInt() and 0x80) != 0)
        val result = (value.toInt() shl 1).toUByte()
        if (mode.address == 0u) A = result else bus.write(mode.address, result)
        setZN(result)
        return base
    }

    private fun lsr(mode: AddrResult, base: Int): Int {
        val value = if (mode.address == 0u) A else bus.read(mode.address)
        setFlag(FLAG_C, (value.toInt() and 0x01) != 0)
        val result = (value.toInt() shr 1).toUByte()
        if (mode.address == 0u) A = result else bus.write(mode.address, result)
        setZN(result)
        return base
    }

    private fun rol(mode: AddrResult, base: Int): Int {
        val value = if (mode.address == 0u) A else bus.read(mode.address)
        val oldC = getFlag(FLAG_C)
        setFlag(FLAG_C, (value.toInt() and 0x80) != 0)
        val result = ((value.toInt() shl 1) or (if (oldC) 1 else 0)).toUByte()
        if (mode.address == 0u) A = result else bus.write(mode.address, result)
        setZN(result)
        return base
    }

    private fun ror(mode: AddrResult, base: Int): Int {
        val value = if (mode.address == 0u) A else bus.read(mode.address)
        val oldC = getFlag(FLAG_C)
        setFlag(FLAG_C, (value.toInt() and 0x01) != 0)
        val result = ((value.toInt() shr 1) or (if (oldC) 0x80 else 0)).toUByte()
        if (mode.address == 0u) A = result else bus.write(mode.address, result)
        setZN(result)
        return base
    }

    private fun jmp(mode: AddrResult, cycles: Int): Int { PC = mode.address; return cycles }

    private fun jsr(): Int {
        val target = fetch16()
        val returnAddr = (PC - 1u)
        push((returnAddr shr 8).toUByte())
        push(returnAddr.toUByte())
        PC = target
        return 6
    }

    private fun rts(): Int {
        val low = pop()
        val high = pop()
        PC = ((high.toUShort() shl 8) or low.toUShort()) + 1u
        return 6
    }

    private fun bif(condition: Boolean, mode: AddrResult, base: Int): Int {
        if (condition) {
            PC = mode.address
            return base + 1 + mode.extraCycles
        }
        return base
    }

    private fun fsc(bit: Int, value: Boolean, cycles: Int): Int {
        setFlag(bit, value)
        return cycles
    }

    private fun nop() = 2

    private fun brk(): Int {
        PC++
        push((PC shr 8).toUByte())
        push(PC.toUByte())
        push(status or (1u shl FLAG_B).toUByte() or (1u shl FLAG_U).toUByte())
        setFlag(FLAG_I, true)
        val low = bus.read(0xFFFEu)
        val high = bus.read(0xFFFFu)
        PC = (high.toUShort() shl 8) or low.toUShort()
        return 7
    }

    private fun rti(): Int {
        status = pop()
        setFlag(FLAG_U, true)
        setFlag(FLAG_B, false)
        val low = pop()
        val high = pop()
        PC = (high.toUShort() shl 8) or low.toUShort()
        return 6
    }

    private fun irq(): Int {
        if (!getFlag(FLAG_I)) {
            push((PC shr 8).toUByte())
            push(PC.toUByte())
            setFlag(FLAG_B, false)
            setFlag(FLAG_U, true)
            push(status)
            setFlag(FLAG_I, true)
            val low = bus.read(0xFFFEu)
            val high = bus.read(0xFFFFu)
            PC = (high.toUShort() shl 8) or low.toUShort()
            return 7
        }
        return 0
    }

    private fun nmi(): Int {
        push((PC shr 8).toUByte())
        push(PC.toUByte())
        setFlag(FLAG_B, false)
        setFlag(FLAG_U, true)
        push(status)
        setFlag(FLAG_I, true)
        val low = bus.read(0xFFFAu)
        val high = bus.read(0xFFFBu)
        PC = (high.toUShort() shl 8) or low.toUShort()
        return 7
    }
}