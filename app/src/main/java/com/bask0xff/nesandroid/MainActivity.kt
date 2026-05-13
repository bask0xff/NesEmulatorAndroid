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

    // Запускаем эмуляцию в отдельном потоке
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.Default) {
                nes.runFrame()  // Вызываем runFrame у NES
                bitmap = nes.getBitmap()
            }
            delay(16) // ~60 FPS
        }
    }

    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setBackgroundColor(0xFF00FF00.toInt()) // Зелёный фон для проверки
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