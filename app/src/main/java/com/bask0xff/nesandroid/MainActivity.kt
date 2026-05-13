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

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.Default) {
                println("Calling runFrame #${frameNumber + 1}")
                nes.runFrame()
                val bmp = nes.getBitmap()
                bitmap = bmp
                println("runFrame completed, bitmap is ${if (bmp != null) "not null" else "null"}")
                frameNumber++
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
            println("AndroidView update called, bitmap is ${if (bitmap != null) "not null" else "null"}")
            bitmap?.let {
                imageView.setImageBitmap(it)
                println("ImageView updated with bitmap")
            } ?: println("Bitmap is null, showing green background")
        },
        modifier = Modifier.fillMaxSize()
    )
}