package com.bask0xff.nesandroid

class Input {

    private var controller1State: UByte = 0u
    private var controller2State: UByte = 0u
    private var strobe = false
    private var buttonIndex1 = 0
    private var buttonIndex2 = 0

    // A B Select Start Up Down Left Right
    fun setControllerState1(state: UByte) {
        controller1State = state
    }

    fun setControllerState2(state: UByte) {
        controller2State = state
    }

    fun read4016(): UByte {
        // read controller 1
        if (strobe) {
            buttonIndex1 = 0
            return (controller1State and 1u)
        }

        val bit = (controller1State.toInt() shr buttonIndex1) and 1
        buttonIndex1 = (buttonIndex1 + 1) % 8
        return bit.toUByte()
    }

    fun read4017(): UByte {
        // read controller 2
        if (strobe) {
            buttonIndex2 = 0
            return (controller2State and 1u)
        }

        val bit = (controller2State.toInt() shr buttonIndex2) and 1
        buttonIndex2 = (buttonIndex2 + 1) % 8
        return bit.toUByte()
    }

    fun write4016(value: UByte) {
        val newStrobe = (value.toInt() and 1) != 0
        strobe = newStrobe
        if (strobe) {
            buttonIndex1 = 0
            buttonIndex2 = 0
        }
    }
}