package com.bask0xff.nesandroid

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class APU {

    companion object {
        private const val CPU_FREQUENCY = 1789773.0
        private const val SAMPLE_RATE = 44100
    }

    private val pulse1 = PulseChannel()
    private val pulse2 = PulseChannel()

    private val audioTrack: AudioTrack
    private val audioBuffer = ShortArray(2048)
    private var bufferPos = 0
    private var sampleAccumulator = 0.0
    private var frameCycles = 0

    init {
        val minSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(audioBuffer.size)

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minSize,
            AudioTrack.MODE_STREAM
        )
        audioTrack.play()
    }

    fun stop() {
        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.stop()
        }
        audioTrack.release()
    }

    fun read(address: UShort): UByte {
        return when (address.toInt()) {
            0x4015 -> statusRegister()
            else -> 0u
        }
    }

    fun write(address: UShort, value: UByte) {
        when (address.toInt()) {
            0x4000 -> pulse1.writeControl(value)
            0x4001 -> pulse1.writeSweep(value)
            0x4002 -> pulse1.writeTimerLow(value)
            0x4003 -> pulse1.writeTimerHigh(value)
            0x4004 -> pulse2.writeControl(value)
            0x4005 -> pulse2.writeSweep(value)
            0x4006 -> pulse2.writeTimerLow(value)
            0x4007 -> pulse2.writeTimerHigh(value)
            0x4015 -> writeStatus(value)
            0x4017 -> writeFrameCounter(value)
        }
    }

    fun step(cpuCycles: Int) {
        pulse1.step(cpuCycles)
        pulse2.step(cpuCycles)

        frameCycles += cpuCycles
        while (frameCycles >= 7457) {
            frameCycles -= 7457
            pulse1.clockLength()
            pulse2.clockLength()
        }

        sampleAccumulator += cpuCycles * SAMPLE_RATE / CPU_FREQUENCY
        while (sampleAccumulator >= 1.0) {
            sampleAccumulator -= 1.0
            audioBuffer[bufferPos++] = mixSample()
            if (bufferPos >= audioBuffer.size) {
                audioTrack.write(audioBuffer, 0, audioBuffer.size)
                bufferPos = 0
            }
        }
    }

    private fun statusRegister(): UByte {
        var status = 0
        if (pulse1.isActive()) status = status or 0x01
        if (pulse2.isActive()) status = status or 0x02
        return status.toUByte()
    }

    private fun writeStatus(value: UByte) {
        pulse1.enabled = (value.toInt() and 0x01) != 0
        pulse2.enabled = (value.toInt() and 0x02) != 0

        if (!pulse1.enabled) pulse1.lengthCounter = 0
        if (!pulse2.enabled) pulse2.lengthCounter = 0
    }

    private fun writeFrameCounter(value: UByte) {
        frameCycles = 0
    }

    private fun mixSample(): Short {
        val output = (pulse1.output() + pulse2.output()) * 0.45f
        val scaled = (output * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        return scaled.toShort()
    }

    private class PulseChannel {
        var enabled = true
        var lengthCounter = 0
        private var duty = 0
        private var sequenceIndex = 0
        private var timer = 0
        private var timerPeriod = 0
        private var constantVolume = 0
        private var envelopeLoop = false
        private var volume = 0

        private val dutyTable = arrayOf(
            intArrayOf(0, 1, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 1, 1, 0, 0, 0, 0, 0),
            intArrayOf(0, 1, 1, 1, 1, 0, 0, 0),
            intArrayOf(1, 0, 0, 1, 1, 1, 1, 1)
        )

        fun writeControl(value: UByte) {
            duty = (value.toInt() shr 6) and 0x03
            envelopeLoop = (value.toInt() and 0x20) != 0
            constantVolume = value.toInt() and 0x0F
            volume = constantVolume
        }

        fun writeSweep(value: UByte) {
            // Sweep unit is not implemented in this minimal APU.
        }

        fun writeTimerLow(value: UByte) {
            timerPeriod = (timerPeriod and 0xFF00) or (value.toInt() and 0xFF)
        }

        fun writeTimerHigh(value: UByte) {
            timerPeriod = (timerPeriod and 0x00FF) or ((value.toInt() and 0x07) shl 8)
            loadLengthCounter((value.toInt() shr 3) and 0x1F)
            timer = timerPeriod + 1
        }

        fun step(cycles: Int) {
            if (timerPeriod < 8 || lengthCounter == 0) return
            timer -= cycles
            if (timer <= 0) {
                val period = timerPeriod + 1
                val advance = ((-timer) / period) + 1
                timer += advance * period
                sequenceIndex = (sequenceIndex + advance) and 0x07
            }
        }

        fun clockLength() {
            if (lengthCounter > 0 && !envelopeLoop) {
                lengthCounter--
            }
        }

        fun isActive(): Boolean = enabled && lengthCounter > 0

        fun output(): Float {
            if (!enabled || lengthCounter == 0 || timerPeriod < 8) return 0f
            val value = dutyTable[duty][sequenceIndex]
            return if (value == 1) constantVolume / 15f else 0f
        }

        fun loadLengthCounter(index: Int) {
            lengthCounter = LENGTH_TABLE[index.coerceIn(0, LENGTH_TABLE.size - 1)]
        }

        companion object {
            private val LENGTH_TABLE = intArrayOf(
                10, 254, 20, 2, 40, 4, 80, 6,
                160, 8, 60, 10, 14, 12, 26, 14,
                12, 16, 24, 18, 48, 20, 96, 22,
                192, 24, 72, 26, 16, 28, 32, 30
            )
        }
    }
}
