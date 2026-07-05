package com.dreamapps.smartgallery.data.local.entities
// clase que guarda el vector y la ruta
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.HnswIndex

@Entity
data class ImageEntity(
    @Id var id: Long = 0,
    val filePath: String,
    // El índice HNSW permite la búsqueda semántica rápida
    @HnswIndex(dimensions = 512)
    val embedding: FloatArray? = null
)