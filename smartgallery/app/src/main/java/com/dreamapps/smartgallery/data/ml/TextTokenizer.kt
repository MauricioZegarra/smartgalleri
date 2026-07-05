package com.dreamapps.smartgallery.data.ml

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer

class TextTokenizer(context: Context) {

    // Diccionario que cargará los tokens en la memoria RAM
    private val vocab = mutableMapOf<String, Long>()

    private val contextLength = 77
    private val sotId = 49406L
    private val eotId = 49407L

    init {
        // 1. Leer el archivo JSON directamente desde Edge (Sin librerías C++)
        val jsonString = context.assets.open("tokenizer.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)

        // 2. Extraer solo el nodo "vocab" que contiene el mapeo de palabras a números
        val vocabObj = jsonObject.getJSONObject("model").getJSONObject("vocab")

        // 3. Cargar el vocabulario al mapa de Kotlin
        val keys = vocabObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            vocab[key] = vocabObj.getLong(key)
        }
    }

    private fun normText(text: String): String {
        var t = text.lowercase().trim()
        t = Normalizer.normalize(t, Normalizer.Form.NFKD)
        t = t.replace("\\p{Mn}+".toRegex(), "")
        t = t.replace("[_\\-]+".toRegex(), " ")
        t = t.replace("\\s+".toRegex(), " ")
        return t.trim()
    }

    /**
     * Algoritmo de tokenización Word-Level optimizado para Edge.
     */
    fun tokenize(query: String): LongArray {
        val cleanText = normText(query)
        val words = cleanText.split(" ")

        val finalIds = mutableListOf<Long>()

        // Etiqueta obligatoria: Start of Text
        finalIds.add(sotId)

        // Mapeo léxico
        for (word in words) {
            // El modelo OpenCLIP suele guardar las palabras completas con el sufijo </w>
            val exactMatch = vocab["$word</w>"]
            val subwordMatch = vocab[word]

            val token = exactMatch ?: subwordMatch

            if (token != null) {
                finalIds.add(token)
            }
            // Nota para el artículo: Las palabras fuera del vocabulario (Out-Of-Vocabulary)
            // son ignoradas en este MVP heurístico para ahorrar procesamiento.
        }

        // Etiqueta obligatoria: End of Text
        finalIds.add(eotId)

        // Truncar si es muy largo
        val truncated = finalIds.take(contextLength).toMutableList()

        // Padding (rellenar con ceros hasta llegar a 77)
        while (truncated.size < contextLength) {
            truncated.add(0L)
        }

        return truncated.toLongArray()
    }
}