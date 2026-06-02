package com.bask0xff.nesandroid

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SimpleNesProgramTest {

    @Test
    fun generateSimpleDemoRom_writesFile() {
        val output = File("build/tmp/simple-demo.nes")
        output.parentFile?.mkdirs()
        SimpleNesProgram.saveSimpleDemoRom(output)

        assertTrue("ROM file should be created", output.exists())
        assertTrue("ROM file should contain iNES header", output.readBytes().size >= 0x4010)
    }
}
