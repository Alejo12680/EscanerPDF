package com.fabio.escanerpdf.ui.revision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fabio.escanerpdf.R
import com.fabio.escanerpdf.datos.PaginaEscaneada
import com.fabio.escanerpdf.viewmodel.EscanerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaRevision(
    escaner: EscanerViewModel,
    alAgregarPagina: () -> Unit,
    alPdfCreado: () -> Unit,
    alDescartar: () -> Unit,
) {
    val ocupado = escaner.ocupado
    val paginas = escaner.paginas
    var confirmarDescarte by remember { mutableStateOf(false) }
    var paginaPorBorrar by remember { mutableStateOf<PaginaEscaneada?>(null) }

    fun intentarSalir() {
        if (ocupado) return
        if (paginas.isEmpty()) alDescartar() else confirmarDescarte = true
    }
    BackHandler { intentarSalir() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Revisar documento") },
                navigationIcon = {
                    IconButton(onClick = { intentarSalir() }, enabled = !ocupado) {
                        Icon(painterResource(R.drawable.ic_atras), contentDescription = "Volver al inicio")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = alAgregarPagina, enabled = !ocupado, modifier = Modifier.weight(1f)) {
                        Icon(painterResource(R.drawable.ic_agregar), contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar")
                    }
                    Button(
                        onClick = { escaner.crearPdf(alTerminar = alPdfCreado) },
                        enabled = !ocupado && paginas.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(painterResource(R.drawable.ic_pdf), contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Crear PDF")
                    }
                }
            }
        },
    ) { relleno ->
        Box(Modifier.padding(relleno).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = escaner.nombreDocumento,
                    onValueChange = { escaner.nombreDocumento = it },
                    label = { Text("Nombre del documento") },
                    singleLine = true,
                    enabled = !ocupado,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (paginas.isEmpty()) {
                    Text(
                        "No hay páginas. Toca «Agregar página» para escanear.",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        if (paginas.size == 1) "1 página" else "${paginas.size} páginas",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(paginas, key = { _, pagina -> pagina.archivo.path }) { indice, pagina ->
                            TarjetaPagina(
                                numero = indice + 1,
                                pagina = pagina,
                                habilitado = !ocupado,
                                alBorrar = { paginaPorBorrar = pagina },
                            )
                        }
                    }
                }
            }

            if (ocupado) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Creando PDF…")
                }
            }
        }
    }

    paginaPorBorrar?.let { pagina ->
        AlertDialog(
            onDismissRequest = { paginaPorBorrar = null },
            title = { Text("¿Borrar página ${paginas.indexOf(pagina) + 1}?") },
            confirmButton = {
                TextButton(onClick = {
                    escaner.borrarPagina(pagina)
                    paginaPorBorrar = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { paginaPorBorrar = null }) { Text("Cancelar") } },
        )
    }

    if (confirmarDescarte) {
        AlertDialog(
            onDismissRequest = { confirmarDescarte = false },
            title = { Text("¿Descartar documento?") },
            text = { Text("Se perderán las páginas escaneadas que aún no están en un PDF.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarDescarte = false
                    alDescartar()
                }) { Text("Descartar") }
            },
            dismissButton = { TextButton(onClick = { confirmarDescarte = false }) { Text("Seguir editando") } },
        )
    }
}

@Composable
private fun TarjetaPagina(numero: Int, pagina: PaginaEscaneada, habilitado: Boolean, alBorrar: () -> Unit) {
    val miniatura = remember(pagina) { pagina.miniatura.asImageBitmap() }
    Card {
        Box {
            Image(
                bitmap = miniatura,
                contentDescription = "Página $numero",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(pagina.ancho.toFloat() / pagina.alto),
                contentScale = ContentScale.Fit,
            )
            IconButton(
                onClick = alBorrar,
                enabled = habilitado,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape),
            ) {
                Icon(painterResource(R.drawable.ic_borrar), contentDescription = "Borrar página $numero")
            }
        }
        Text(
            "Página $numero",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
