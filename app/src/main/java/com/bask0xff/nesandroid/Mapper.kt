package com.bask0xff.nesandroid

interface Mapper {
    fun reset()
    fun cpuRead(address: UShort): UByte
    fun cpuWrite(address: UShort, value: UByte)
    fun ppuRead(address: UShort): UByte
    fun ppuWrite(address: UShort, value: UByte)
}