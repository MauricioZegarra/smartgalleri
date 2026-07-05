package com.dreamapps.smartgallery.ui.gallery
// Maneja el estado y las búsquedas

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamapps.smartgallery.data.local.entities.ImageEntity
import com.dreamapps.smartgallery.data.repository.GalleryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val images: List<ImageEntity> = emptyList(),
    val isIndexing: Boolean = false
)

class GalleryViewModel(
    private val repository: GalleryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }

        // Si el usuario borra todo el texto, volvemos a mostrar la galería completa
        if (newQuery.isBlank()) {
            loadAllImages()
        }
    }

    fun searchImages() {
        val query = _uiState.value.searchQuery

        if (query.isBlank()) {
            loadAllImages()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val results = repository.search(query, topK = 20)
            _uiState.update { it.copy(isLoading = false, images = results) }
        }
    }

    fun indexGallery(imagePaths: List<String>, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isIndexing = true) }

            // 1. Procesa las imágenes nuevas o forza el refresco
            repository.indexImages(imagePaths, forceRefresh)

            // 2. ¡LA CLAVE VISUAL! Al terminar, recuperamos toda la galería
            val allSavedImages = repository.getAllImages()

            Log.d("SmartGallery", "📱 UI: Cargando ${allSavedImages.size} imágenes en pantalla.")

            _uiState.update { it.copy(
                isIndexing = false,
                images = allSavedImages // Poblamos la cuadrícula
            )}
        }
    }

    // Función auxiliar para cargar todo sin buscar
    private fun loadAllImages() {
        viewModelScope.launch {
            val all = repository.getAllImages()
            _uiState.update { it.copy(images = all, isLoading = false) }
        }
    }
}
