package com.bask0xff.nesandroid

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var nes: NES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nes = NES(this)
        println("NES testMode = ${nes.isTestMode()}")

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NesScreen(nes = nes)
                }
            }
        }
    }
}

@Composable
fun NesScreen(nes: NES) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var frameNumber by remember { mutableIntStateOf(0) }
    var lastTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.Default) {
                val startTime = System.nanoTime()
                nes.runFrame()
                val endTime = System.nanoTime()
                val frameTime = (endTime - startTime) / 1_000_000

                bitmap = nes.getBitmap()
                frameNumber++

                val now = System.currentTimeMillis()
                if (now - lastTime >= 1000) {
                    println("FPS: $frameNumber frames per second, avg frame time: ${frameTime}ms")
                    frameNumber = 0
                    lastTime = now
                }
            }
            delay(16)
        }
    }

    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setBackgroundColor(0xFF00FF00.toInt())
            }
        },
        update = { imageView ->
            bitmap?.let {
                imageView.setImageBitmap(it)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}