package com.bask0xff.nesandroid

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nes = NES(this)

        setContent {
            NesScreen(nes)
        }
    }
}

@Composable
fun NesScreen(nes: NES) {
    var frameCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.Default) {
                nes.runFrame()
            }
            frameCount++
        }
    }

    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
            }
        },
        update = { imageView ->
            imageView.setImageBitmap(nes.getBitmap())
        },
        modifier = Modifier.fillMaxSize()
    )
}