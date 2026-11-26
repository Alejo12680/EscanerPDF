package com.fabio.escanerpdf.ui.inicio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabio.escanerpdf.R
import com.fabio.escanerpdf.datos.DocumentoPdf
import com.fabio.escanerpdf.ui.theme.AzulClaro
import com.fabio.escanerpdf.ui.theme.AzulNocheClaro
import com.fabio.escanerpdf.ui.theme.AzulNocheOscuro
import com.fabio.escanerpdf.ui.theme.AzulOscuro
import com.fabio.escanerpdf.ui.theme.VerdeClaro
import com.fabio.escanerpdf.ui.theme.VerdeOscuro
import com.fabio.escanerpdf.viewmodel.InicioViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaInicio(
    pdfResaltado: File?,
    alEscanear: () -> Unit,
    alCerrarApp: () -> Unit,
    inicioViewModel: InicioViewModel = viewModel(),
) {
    val contexto = LocalContext.current
    val estadoSnackbar = remember { SnackbarHostState() }

    var documentoPorBorrar by remember { mutableStateOf<DocumentoPdf?>(null) }
    var documentoPorGuardar by remember { mutableStateOf<DocumentoPdf?>(null) }

    LaunchedEffect(Unit) { inicioViewModel.refrescar() }

    val mensaje = inicioViewModel.mensaje
    LaunchedEffect(mensaje) {
        if (mensaje != null) {
            estadoSnackbar.showSnackbar(mensaje)
            inicioViewModel.mensajeMostrado()
        }
    }

    val lanzadorPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        val documento = documentoPorGuardar
        documentoPorGuardar = null
        if (concedido && documento != null) {
            inicioViewModel.guardarEnDescargas(documento)
        } else if (!concedido) {
            inicioViewModel.mostrarMensaje("Sin el permiso de almacenamiento no se puede guardar en Descargas")
        }
    }

    fun guardarEnDescargas(documento: DocumentoPdf) {
        val necesitaPermiso = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (necesitaPermiso) {
            documentoPorGuardar = documento
            lanzadorPermiso.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            inicioViewModel.guardarEnDescargas(documento)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EscanerPDF") },
                actions = {
                    TextButton(onClick = alCerrarApp) {
                        Icon(painterResource(R.drawable.ic_cerrar), contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Cerrar")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = alEscanear,
                icon = { Icon(painterResource(R.drawable.ic_camara), contentDescription = null) },
                text = { Text("Escanear") },
            )
        },
        snackbarHost = { SnackbarHost(estadoSnackbar) },
    ) { relleno ->
        if (inicioViewModel.documentos.isEmpty()) {
            SinDocumentos(Modifier.padding(relleno))
        } else {
            LazyColumn(
                modifier = Modifier.padding(relleno),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(inicioViewModel.documentos, key = { it.archivo.path }) { documento ->
                    TarjetaDocumento(
                        documento = documento,
                        resaltado = documento.archivo == pdfResaltado,
                        alAbrir = {
                            val uri = inicioViewModel.uriCompartible(documento)
                            if (!abrirPdf(contexto, uri)) {
                                inicioViewModel.mostrarMensaje("No hay ninguna app para ver PDF instalada")
                            }
                        },
                        alCompartir = {
                            compartirPdf(contexto, inicioViewModel.uriCompartible(documento), documento.nombre)
                        },
                        alGuardar = { guardarEnDescargas(documento) },
                        alBorrar = { documentoPorBorrar = documento },
                    )
                }
            }
        }
    }

    documentoPorBorrar?.let { documento ->
        AlertDialog(
            onDismissRequest = { documentoPorBorrar = null },
            title = { Text("¿Borrar documento?") },
            text = {
                Text("\"${documento.nombre}\" se borrará de la app. Las copias que hayas guardado en Descargas no se borran.")
            },
            confirmButton = {
                TextButton(onClick = {
                    inicioViewModel.borrar(documento)
                    documentoPorBorrar = null
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { documentoPorBorrar = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun SinDocumentos(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_pdf),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("Aún no tienes documentos", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Toca «Escanear» para fotografiar tus hojas y convertirlas en PDF.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TarjetaDocumento(
    documento: DocumentoPdf,
    resaltado: Boolean,
    alAbrir: () -> Unit,
    alCompartir: () -> Unit,
    alGuardar: () -> Unit,
    alBorrar: () -> Unit,
) {
    val contexto = LocalContext.current
    val fecha = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(documento.fechaModificacion))
    val tamano = Formatter.formatShortFileSize(contexto, documento.tamanoBytes)
    val forma = RoundedCornerShape(16.dp)
    val sombraTexto = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 2f), 4f)
    val degradado = if (isSystemInDarkTheme()) {
        listOf(AzulNocheClaro, AzulNocheOscuro)
    } else {
        listOf(AzulClaro, AzulOscuro)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = forma)
            .background(Brush.verticalGradient(degradado), forma)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_pdf),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.White,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    documento.nombre,
                    style = MaterialTheme.typography.titleMedium.copy(shadow = sombraTexto),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (resaltado) "Recién creado · $tamano" else "$fecha · $tamano",
                    style = MaterialTheme.typography.bodySmall.copy(shadow = sombraTexto),
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonAccion(R.drawable.ic_abrir, "Abrir", "Abrir", alAbrir, Modifier.weight(1f))
            BotonAccion(R.drawable.ic_compartir, "Compartir", "Compartir", alCompartir, Modifier.weight(1f))
            BotonAccion(R.drawable.ic_descargar, "Descargas", "Guardar en Descargas", alGuardar, Modifier.weight(1f))
            BotonAccion(R.drawable.ic_borrar, "Borrar", "Borrar", alBorrar, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BotonAccion(
    @DrawableRes icono: Int,
    texto: String,
    descripcion: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val forma = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .shadow(elevation = 3.dp, shape = forma)
            .clip(forma)
            .background(Brush.verticalGradient(listOf(VerdeClaro, VerdeOscuro)))
            .clickable(onClick = alTocar)
            .semantics { contentDescription = descripcion }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icono), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
        )
    }
}
