package com.fabio.escanerpdf.datos

import android.graphics.Bitmap
import java.io.File

data class Punto(val x: Float, val y: Float)

data class PaginaEscaneada(
    val archivo: File,
    val ancho: Int,
    val alto: Int,
    val miniatura: Bitmap,
)

data class DocumentoPdf(
    val archivo: File,
    val nombre: String,
    val fechaModificacion: Long,
    val tamanoBytes: Long,
)
