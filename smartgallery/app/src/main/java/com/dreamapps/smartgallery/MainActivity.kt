package com.dreamapps.smartgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dreamapps.smartgallery.data.local.ObjectBoxManager
import com.dreamapps.smartgallery.data.ml.Vectorizator
import com.dreamapps.smartgallery.data.repository.GalleryRepository
import com.dreamapps.smartgallery.ui.gallery.GalleryScreen
import com.dreamapps.smartgallery.ui.gallery.GalleryViewModel
import com.dreamapps.smartgallery.ui.theme.SmartGalleryTheme

class MainActivity : ComponentActivity() {
    // 1. Inyección de Dependencias Manual (Factory)
    // Como nuestro ViewModel necesita el Repository (y este necesita el Vectorizator),
    // debemos enseñarle a Android cómo construirlos en cascada.
    private val viewModel: GalleryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // Creamos las instancias pasándole el contexto de la aplicación
                val vectorizator = Vectorizator(applicationContext)
                val repository = GalleryRepository(vectorizator)
                return GalleryViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Inicializar la Base de Datos Vectorial
        // ¡CRÍTICO! Debe iniciarse antes de que la pantalla intente buscar o guardar datos
        ObjectBoxManager.init(this)

        enableEdgeToEdge()
        setContent {
            SmartGalleryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 3. Montar la interfaz real (Jetpack Compose)
                    // Reemplazamos el "Greeting" por tu pantalla de Galería
                    GalleryScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}