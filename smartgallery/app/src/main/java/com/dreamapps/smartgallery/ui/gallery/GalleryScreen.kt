package com.dreamapps.smartgallery.ui.gallery
// // Pantalla principal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.dreamapps.smartgallery.utils.MediaStoreUtils
import androidx.compose.material.icons.filled.Refresh
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier
) {
    // Observamos el estado del ViewModel
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val context = LocalContext.current

// 1. Determinar qué permiso pedir según la versión de Android
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES // Android 13+
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE // Android 12 y anteriores
    }

// 2. Crear el lanzador para solicitar el permiso
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // ¡Permiso concedido por primera vez!
            val paths = MediaStoreUtils.getGalleryImages(context)
            viewModel.indexGallery(paths) // Indexación suave (solo nuevos)
            println("Se encontraron ${paths.size} imágenes en la galería.")
        } else {
            println("Permiso denegado por el usuario.")
        }
    }

// 3. Lógica robusta de inicialización
    LaunchedEffect(Unit) {
        // Verificamos si Funtouch OS ya nos dio el permiso antes
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            permissionToRequest
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            // Si ya tenemos permiso, leemos las fotos y hacemos indexación suave
            val paths = MediaStoreUtils.getGalleryImages(context)
            viewModel.indexGallery(paths)
        } else {
            // Si no tenemos permiso, lanzamos la ventana al usuario
            permissionLauncher.launch(permissionToRequest)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {

        // 1. Barra de Búsqueda
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Buscar en la galería (ej. playa, gato)") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },

            // ¡NUEVO BOTÓN DE ACTUALIZAR!
            trailingIcon = {
                IconButton(onClick = {
                    val paths = MediaStoreUtils.getGalleryImages(context)
                    // Aquí SÍ forzamos el borrado total de la base de datos
                    viewModel.indexGallery(paths, forceRefresh = true)
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Forzar indexación profunda")
                }
            },

            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    viewModel.searchImages()
                    keyboardController?.hide()
                }
            ),
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )

        // 2. Indicadores de Carga
        if (uiState.isLoading || uiState.isIndexing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (uiState.isIndexing) {
                Text(
                    text = "Indexando galería con IA... Esto puede tomar un momento.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // 3. Cuadrícula de Resultados
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), // 3 columnas como una galería típica
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(uiState.images) { imageEntity ->
                // Utilizamos Coil para cargar la foto desde su ruta local
                AsyncImage(
                    model = imageEntity.filePath, // La ruta que guardaste en ObjectBox
                    contentDescription = "Imagen de la galería",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}