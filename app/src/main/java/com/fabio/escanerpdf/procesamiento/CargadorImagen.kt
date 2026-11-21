package com.fabio.escanerpdf.procesamiento

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.max

object CargadorImagen {

    fun rotacionExif(archivo: File): Int = ExifInterface(archivo).rotationDegrees

    @Throws(IOException::class)
    fun cargarReducida(archivo: File, ladoMaximo: Int): Bitmap {
        val tamano = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.path, tamano)
        val ladoMayor = max(tamano.outWidth, tamano.outHeight)
        if (ladoMayor <= 0) throw IOException("No se pudo leer la imagen ${archivo.name}")

        var muestreo = 1
        while (ladoMayor / (muestreo * 2) >= ladoMaximo) muestreo *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
        val bitmap = BitmapFactory.decodeFile(archivo.path, opciones)
            ?: throw IOException("No se pudo leer la imagen ${archivo.name}")

        val rotacion = rotacionExif(archivo)
        if (rotacion == 0) return bitmap
        val matriz = Matrix().apply { postRotate(rotacion.toFloat()) }
        val girado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
        if (girado !== bitmap) bitmap.recycle()
        return girado
    }
}
