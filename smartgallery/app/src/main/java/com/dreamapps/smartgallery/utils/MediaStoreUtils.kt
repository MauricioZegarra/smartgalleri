package com.dreamapps.smartgallery.utils

import android.content.Context
import android.provider.MediaStore

object MediaStoreUtils {

    fun getGalleryImages(context: Context): List<String> {
        val imagePaths = mutableListOf<String>()

        // Columna que queremos recuperar (la ruta absoluta del archivo)
        val projection = arrayOf(MediaStore.Images.Media.DATA)

        // Consultar la base de datos de medios de Android
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC" // Ordenar de más nuevas a más viejas
        )

        cursor?.use {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (it.moveToNext()) {
                val path = it.getString(columnIndex)
                imagePaths.add(path)
            }
        }

        return imagePaths
    }
}