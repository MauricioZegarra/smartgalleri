package com.dreamapps.smartgallery.data.repository
// Orquestador: une la IA con la DB

import android.graphics.BitmapFactory
import android.util.Log
import com.dreamapps.smartgallery.data.local.ObjectBoxManager
import com.dreamapps.smartgallery.data.local.entities.ImageEntity
import com.dreamapps.smartgallery.data.ml.ImageProcessor
import com.dreamapps.smartgallery.data.ml.Vectorizator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GalleryRepository(private val vectorizator: Vectorizator) {

    /**
     * FASE DE INGESTIÓN (Con limpieza para evitar clones)
     */
    suspend fun indexImages(imagePaths: List<String>, forceRefresh: Boolean = false) {
        withContext(Dispatchers.IO) {
            val box = ObjectBoxManager.store.boxFor(ImageEntity::class.java)

            // Si tocaste el botón de la lupa con el ícono de Refrescar, limpiamos la BD
            if (forceRefresh) {
                box.removeAll()
                Log.d("SmartGallery", "🧹 Base de datos vaciada. Adiós clones.")
            }

            val existingPaths = box.all.map { it.filePath }.toSet()

            for (path in imagePaths) {
                // Indexación inteligente: saltar si ya existe
                if (!forceRefresh && existingPaths.contains(path)) continue

                val file = File(path)
                if (!file.exists()) continue

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inSampleSize = 4
                }
                val bitmap = BitmapFactory.decodeFile(path, options) ?: continue

                val floatBuffer = ImageProcessor.bitmapToFloatBuffer(bitmap)
                val embedding = vectorizator.encodeImage(floatBuffer)

                val entity = ImageEntity(filePath = path, embedding = embedding)
                box.put(entity)
            }
        }
    }

    /**
     * FASE DE RECUPERACIÓN (Con diagnóstico dinámico de dimensiones)
     */
    suspend fun search(query: String, topK: Int = 20): List<ImageEntity> {
        return withContext(Dispatchers.IO) {
            // 1. Vectorizar texto
            val queryEmbedding = vectorizator.encodeText(query)

            // Diagnóstico inicial del texto
            if (queryEmbedding == null) {
                android.util.Log.e("SmartGallery", "🚨 ERROR: El vector de texto regresó NULO.")
                return@withContext emptyList()
            }

            val dim = queryEmbedding.size
            android.util.Log.d("SmartGallery", "📏 DIMENSIÓN TEXTO: $dim")

            // 2. Pedimos a ObjectBox una muestra cruda
            // (Esta es la línea que se había borrado y causaba los errores)
            val rawResults = ObjectBoxManager.searchSimilarImages(queryEmbedding, 50)
            if (rawResults.isEmpty()) return@withContext emptyList()

            // 3. Calculamos el producto punto dinámicamente según el tamaño real
            val scoredResults = rawResults.mapNotNull { entity ->
                val emb = entity.embedding

                if (emb != null && emb.size == dim) {
                    var dotProduct = 0f
                    for (i in 0 until dim) dotProduct += queryEmbedding[i] * emb[i]
                    Pair(entity, dotProduct)
                } else {
                    // Si el tamaño no coincide, lanzamos la alerta
                    android.util.Log.e("SmartGallery", "🚨 MISMATCH DIMENSIONAL: Texto = $dim, Imagen = ${emb?.size}")
                    null
                }
            }

            if (scoredResults.isEmpty()) return@withContext emptyList()

            // 4. Identificamos el puntaje de la MEJOR foto
            val bestScore = scoredResults.maxOf { it.second }

            // 5. UMBRAL RELATIVO (Cutoff):
            val cutoff = bestScore - 0.04f

            // 6. Aplicar filtro, ordenar y devolver al ViewModel
            scoredResults
                .filter { it.second >= cutoff }
                .sortedByDescending { it.second }
                .take(topK)
                .map { it.first }
        }
    }

    suspend fun getAllImages(): List<ImageEntity> {
        return withContext(Dispatchers.IO) {
            val box = ObjectBoxManager.store.boxFor(ImageEntity::class.java)
            box.all // Retorna la lista completa
        }
    }
}