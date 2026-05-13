package com.bask0xff.nesandroid

abstract class Mapper {
    abstract fun cpuRead(address: UShort): UByte
    abstract fun cpuWrite(address: UShort, value: UByte)
    abstract fun ppuRead(address: UShort): UByte
    abstract fun ppuWrite(address: UShort, value: UByte)
    abstract fun reset()
}