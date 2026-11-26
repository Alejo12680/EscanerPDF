package com.fabio.escanerpdf.ui.recorte

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.magnifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fabio.escanerpdf.datos.Punto
import com.fabio.escanerpdf.ui.theme.Naranja

@Composable
fun EditorEsquinas(
    imagen: Bitmap,
    esquinas: List<Punto>,
    alMoverEsquina: (indice: Int, punto: Punto) -> Unit,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
) {
    val imagenCompose = remember(imagen) { imagen.asImageBitmap() }

    val esquinasActuales by rememberUpdatedState(esquinas)
    val moverEsquina by rememberUpdatedState(alMoverEsquina)
    val habilitadoActual by rememberUpdatedState(habilitado)

    var indiceArrastrado by remember { mutableIntStateOf(-1) }
    var posicionDedo by remember { mutableStateOf(Offset.Unspecified) }

    Box(modifier.aspectRatio(imagen.width.toFloat() / imagen.height)) {
        Image(
            bitmap = imagenCompose,
            contentDescription = "Foto a recortar",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .magnifier(
                    sourceCenter = { posicionDedo },
                    magnifierCenter = {
                        val separacion = 90.dp.toPx()
                        if (posicionDedo.y > separacion * 1.5f) posicionDedo - Offset(0f, separacion)
                        else posicionDedo + Offset(0f, separacion)
                    },
                    zoom = 2.5f,
                    size = DpSize(110.dp, 110.dp),
                    cornerRadius = 55.dp,
                )
                .pointerInput(Unit) {
                    val radioToque = 48.dp.toPx()
                    awaitEachGesture {
                        val toque = awaitFirstDown()
                        val indice = if (habilitadoActual) {
                            esquinaMasCercana(toque.position, esquinasActuales, size, radioToque)
                        } else {
                            -1
                        }
                        if (indice >= 0) {
                            toque.consume()
                            val desfase = enPixeles(esquinasActuales[indice], size) - toque.position
                            indiceArrastrado = indice
                            posicionDedo = toque.position + desfase
                            try {
                                drag(toque.id) { cambio ->
                                    cambio.consume()
                                    val destino = cambio.position + desfase
                                    val nuevo = Punto(
                                        (destino.x / size.width).coerceIn(0f, 1f),
                                        (destino.y / size.height).coerceIn(0f, 1f),
                                    )
                                    moverEsquina(indice, nuevo)
                                    posicionDedo = enPixeles(nuevo, size)
                                }
                            } finally {
                                indiceArrastrado = -1
                                posicionDedo = Offset.Unspecified
                            }
                        }
                    }
                },
        ) {
            val puntos = esquinas.map { Offset(it.x * size.width, it.y * size.height) }
            val contorno = Path().apply {
                moveTo(puntos[0].x, puntos[0].y)
                for (punto in puntos.drop(1)) lineTo(punto.x, punto.y)
                close()
            }
            drawPath(contorno, color = Naranja.copy(alpha = 0.18f))
            drawPath(contorno, color = Naranja, style = Stroke(width = 2.dp.toPx()))

            puntos.forEachIndexed { indice, punto ->
                val radio = if (indice == indiceArrastrado) 11.dp.toPx() else 8.dp.toPx()
                drawCircle(Color.White, radius = radio, center = punto)
                drawCircle(Naranja, radius = radio, center = punto, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
    }
}

private fun enPixeles(punto: Punto, tamano: IntSize): Offset =
    Offset(punto.x * tamano.width, punto.y * tamano.height)

private fun esquinaMasCercana(toque: Offset, esquinas: List<Punto>, tamano: IntSize, radio: Float): Int {
    val distancias = esquinas.map { (enPixeles(it, tamano) - toque).getDistance() }
    val masCercana = distancias.indices.minBy { distancias[it] }
    return if (distancias[masCercana] <= radio) masCercana else -1
}
