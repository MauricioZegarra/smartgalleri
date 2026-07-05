package com.dreamapps.smartgallery.data.ml
// clase para el ONNX

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.sqrt

class Vectorizator(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var visualSession: OrtSession? = null
    private var textSession: OrtSession? = null
    private val textTokenizer = TextTokenizer(context)

    init {
        // 1. Opciones para el Modelo Visual (Aceleración por Hardware)
        // Usamos el DSP/NPU del Snapdragon para volar procesando los píxeles de las fotos
        val visualOptions = OrtSession.SessionOptions().apply {
            addNnapi()
        }

        // 2. Opciones para el Modelo de Texto (Procesamiento en CPU Clásica)
        // NO agregamos NNAPI aquí. La CPU tradicional sí soporta el operador ArgMax(13)
        // necesario para entender las palabras del usuario.
        val textOptions = OrtSession.SessionOptions()

        // 3. Copiamos los archivos y sus pesos (.data)
        val visualPath = copyAssetToInternalStorage(context, "visual.onnx")
        copyAssetToInternalStorage(context, "visual.onnx.data")

        val textPath = copyAssetToInternalStorage(context, "text.onnx")
        copyAssetToInternalStorage(context, "text.onnx.data")

        // 4. Inicializamos dividiendo las cargas de trabajo (Computación Heterogénea)
        visualSession = env.createSession(visualPath, visualOptions)
        textSession = env.createSession(textPath, textOptions)
    }

    /**
     * Extrae un archivo desde la carpeta 'assets' de solo lectura hacia
     * el directorio de archivos de la aplicación para que la librería C++ pueda leerlo.
     */
    private fun copyAssetToInternalStorage(context: Context, filename: String): String {
        val file = File(context.filesDir, filename)
        // Solo lo copia si no existe (para no demorar el inicio de la app cada vez)
        if (!file.exists()) {
            context.assets.open(filename).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return file.absolutePath
    }

    // Equivalente a `encode_image` en tu app.py
    fun encodeImage(floatBuffer: FloatBuffer): FloatArray {
        // Crear el tensor de entrada con la forma geométrica: [Batch=1, Canales=3, Alto=256, Ancho=256]
        val shape = longArrayOf(1, 3, 256, 256)
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)

        // Obtener el nombre de la capa de entrada dinámicamente (como en python: sess.get_inputs()[0].name)
        val inputName = visualSession?.inputNames?.iterator()?.next() ?: return FloatArray(0)

        // Ejecutar la inferencia
        val result = visualSession?.run(mapOf(inputName to inputTensor))

        // Extraer el vector crudo (suele ser un Array 2D de [1][512])
        val rawVector = (result?.get(0)?.value as Array<FloatArray>)[0]

        inputTensor.close()
        result.close()

        return l2Normalize(rawVector)
    }

    fun encodeText(query: String): FloatArray {
        // 1. Obtener los 77 tokens matemáticos
        val tokens = textTokenizer.tokenize(query)

        // 2. Preparar el único tensor requerido (Shape: [Batch=1, Length=77])
        val shape = longArrayOf(1, 77)
        val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), shape)

        // 3. Obtener dinámicamente el nombre de la entrada que el modelo espera
        // (Suele llamarse "input_ids" o "tokens")
        val inputName = textSession?.inputNames?.iterator()?.next() ?: return FloatArray(0)

        // ¡LA SOLUCIÓN! Pasamos exactamente 1 entrada (esperada: [1,1), encontrada: 1)
        val inputs = mapOf(inputName to inputIdsTensor)

        val result = textSession?.run(inputs)

        // 4. Extraer el vector
        val rawVector = (result?.get(0)?.value as Array<FloatArray>)[0]

        // 5. Limpiar memoria C++
        inputIdsTensor.close()
        result.close()

        // Devolvemos el vector normalizado
        return l2Normalize(rawVector)
    }

    // Equivalente a `l2_normalize` en tu app.py
    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sum = 0.0
        for (v in vector) sum += (v * v).toDouble()
        val norm = sqrt(sum).toFloat() + 1e-12f // Sumamos epsilon para evitar división por cero

        for (i in vector.indices) {
            vector[i] /= norm
        }
        return vector
    }

    fun close() {
        visualSession?.close()
        env.close()
    }
}