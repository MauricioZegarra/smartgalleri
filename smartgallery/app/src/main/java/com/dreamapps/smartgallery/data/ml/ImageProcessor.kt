package com.dreamapps.smartgallery.data.ml
// Ajuste de imágenes

import android.graphics.Bitmap
import java.nio.FloatBuffer

object ImageProcessor {
    // MobileCLIP2-S4 utiliza resolución de 256x256 (según tu open_clip_config.json)
    private const val IMAGE_SIZE = 256

    // Medias y Desviaciones Estándar exactas de tu app.py
    private val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
    private val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

    fun bitmapToFloatBuffer(bitmap: Bitmap): FloatBuffer {
        // 1. Redimensionar (Idealmente con Center Crop, pero iniciamos con Scaled)
        val resized = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)

        // El buffer requiere: 3 canales * 256 * 256 = 196,608 floats
        val area = IMAGE_SIZE * IMAGE_SIZE
        val floatBuffer = FloatBuffer.allocate(3 * area)
        val intValues = IntArray(area)

        resized.getPixels(intValues, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

        // 2. Transposición a CHW y Normalización en un solo ciclo
        for (i in 0 until area) {
            val pixel = intValues[i]

            // Extraer RGB (0-255), pasar a (0-1) y aplicar (valor - media) / std
            val r = (((pixel shr 16) and 0xFF) / 255.0f - MEAN[0]) / STD[0]
            val g = (((pixel shr 8) and 0xFF) / 255.0f - MEAN[1]) / STD[1]
            val b = ((pixel and 0xFF) / 255.0f - MEAN[2]) / STD[2]

            // Escribir en formato CHW (Capa R, Capa G, Capa B separadas)
            floatBuffer.put(i, r)               // Canal Rojo
            floatBuffer.put(i + area, g)        // Canal Verde
            floatBuffer.put(i + area * 2, b)    // Canal Azul
        }

        floatBuffer.rewind()
        return floatBuffer
    }
}
