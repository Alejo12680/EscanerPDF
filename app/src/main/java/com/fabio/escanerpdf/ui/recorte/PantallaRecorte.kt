package com.fabio.escanerpdf.ui.recorte

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fabio.escanerpdf.R
import com.fabio.escanerpdf.viewmodel.EscanerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaRecorte(
    escaner: EscanerViewModel,
    alRepetir: () -> Unit,
    alOtraPagina: () -> Unit,
    alTerminar: () -> Unit,
) {
    val ocupado = escaner.ocupado
    val imagen = escaner.vistaPrevia

    BackHandler { if (!ocupado) alRepetir() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajusta los bordes") },
                navigationIcon = {
                    IconButton(onClick = alRepetir, enabled = !ocupado) {
                        Icon(painterResource(R.drawable.ic_atras), contentDescription = "Volver a la cámara")
                    }
                },
                actions = {
                    TextButton(onClick = alRepetir, enabled = !ocupado) {
                        Icon(painterResource(R.drawable.ic_repetir), contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Repetir")
                    }
                },
            )
        },
    ) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    imagen != null -> EditorEsquinas(
                        imagen = imagen,
                        esquinas = escaner.esquinas,
                        alMoverEsquina = escaner::moverEsquina,
                        habilitado = !ocupado,
                    )
                    !ocupado -> FotoNoDisponible(alRepetir)
                }
                if (ocupado) CircularProgressIndicator()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val puedeAceptar = !ocupado && imagen != null
                OutlinedButton(
                    onClick = { escaner.aceptarPagina(alTerminar = alOtraPagina) },
                    enabled = puedeAceptar,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(painterResource(R.drawable.ic_agregar), contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Otra página")
                }
                Button(
                    onClick = { escaner.aceptarPagina(alTerminar = alTerminar) },
                    enabled = puedeAceptar,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Listo")
                }
            }
        }
    }
}

@Composable
private fun FotoNoDisponible(alRepetir: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "La foto ya no está disponible.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = alRepetir) { Text("Tomar otra foto") }
    }
}
