package com.bask0xff.nesandroid

class Input {

    private var controllerState: UByte = 0u
    private var strobe = false
    private var buttonIndex = 0

    // A B Select Start Up Down Left Right
    fun setControllerState(state: UByte) {
        controllerState = state
    }

    fun read4016(): UByte {
        if (strobe) {
            buttonIndex = 0
            return (controllerState and 1u)
        }

        val bit = (controllerState.toInt() shr buttonIndex) and 1
        buttonIndex = (buttonIndex + 1) % 8
        return bit.toUByte()
    }

    fun write4016(value: UByte) {
        strobe = (value.toInt() and 1) != 0
        if (strobe) {
            buttonIndex = 0
        }
    }
}