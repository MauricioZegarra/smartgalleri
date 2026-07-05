package com.dreamapps.smartgallery.data.local
// configuracion de la DB vectorial

import android.content.Context
import io.objectbox.BoxStore
import com.dreamapps.smartgallery.data.local.entities.MyObjectBox
import com.dreamapps.smartgallery.data.local.entities.ImageEntity
import com.dreamapps.smartgallery.data.local.entities.ImageEntity_

object ObjectBoxManager {
    lateinit var store: BoxStore
        private set

    fun init(context: Context) {
        // MyObjectBox es una clase generada automáticamente tras compilar
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
    }

    fun searchSimilarImages(queryEmbedding: FloatArray, maxResults: Int = 5): List<ImageEntity> {
        val box = store.boxFor(ImageEntity::class.java)

        // Ejecuta la búsqueda de vecinos más cercanos (K-NN) en el espacio vectorial
        return box.query(
            ImageEntity_.embedding.nearestNeighbors(queryEmbedding, maxResults)
        ).build().find()
    }
}