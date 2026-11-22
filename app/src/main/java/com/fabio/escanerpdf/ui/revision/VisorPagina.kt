package com.fabio.escanerpdf.ui.revision

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fabio.escanerpdf.R

private const val ESCALA_MAXIMA = 6f
private const val ESCALA_DOBLE_TOQUE = 3f

@Composable
fun VisorPagina(titulo: String, cargar: suspend () -> Bitmap, alCerrar: () -> Unit) {
    val imagen by produceState<ImageBitmap?>(initialValue = null) {
        value = cargar().asImageBitmap()
    }

    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val actual = imagen
            if (actual == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            } else {
                ImagenConZoom(actual, Modifier.fillMaxSize())
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = alCerrar) {
                    Icon(painterResource(R.drawable.ic_cerrar), contentDescription = "Cerrar", tint = Color.White)
                }
                Text(titulo, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }

            Text(
                "Pellizca o toca dos veces para acercar",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun ImagenConZoom(imagen: ImageBitmap, modifier: Modifier = Modifier) {
    var escala by remember { mutableFloatStateOf(1f) }
    var desplazamiento by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { centro, paneo, zoom, _ ->
                    val escalaAnterior = escala
                    val escalaNueva = (escala * zoom).coerceIn(1f, ESCALA_MAXIMA)
                    val nuevo = (desplazamiento + centro / escalaAnterior) - (centro / escalaNueva + paneo / escalaAnterior)
                    desplazamiento = limitar(nuevo, escalaNueva, size)
                    escala = escalaNueva
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { punto ->
                    if (escala > 1f) {
                        escala = 1f
                        desplazamiento = Offset.Zero
                    } else {
                        desplazamiento = limitar(punto - punto / ESCALA_DOBLE_TOQUE, ESCALA_DOBLE_TOQUE, size)
                        escala = ESCALA_DOBLE_TOQUE
                    }
                })
            },
    ) {
        Image(
            bitmap = imagen,
            contentDescription = "Página ampliada",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = escala
                    scaleY = escala
                    translationX = -desplazamiento.x * escala
                    translationY = -desplazamiento.y * escala
                },
        )
    }
}

private fun limitar(desplazamiento: Offset, escala: Float, tamano: IntSize): Offset {
    val maximoX = tamano.width * (1f - 1f / escala)
    val maximoY = tamano.height * (1f - 1f / escala)
    return Offset(desplazamiento.x.coerceIn(0f, maximoX), desplazamiento.y.coerceIn(0f, maximoY))
}
