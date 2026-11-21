package com.fabio.escanerpdf.datos

import android.graphics.Bitmap
import java.io.File

data class Punto(val x: Float, val y: Float)

enum class Filtro(val etiqueta: String) {
    COLOR("Color"),
    GRIS("Gris"),
    BLANCO_NEGRO("B/N"),
}

data class PaginaEscaneada(
    val archivo: File,
    val ancho: Int,
    val alto: Int,
    val enEscalaDeGrises: Boolean,
    val miniatura: Bitmap,
)

data class DocumentoPdf(
    val archivo: File,
    val nombre: String,
    val fechaModificacion: Long,
    val tamanoBytes: Long,
)
