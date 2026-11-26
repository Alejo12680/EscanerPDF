package com.fabio.escanerpdf.procesamiento

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.fabio.escanerpdf.datos.Filtro
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object Filtros {

    fun previsualizar(imagen: Bitmap, filtro: Filtro): Bitmap {
        if (filtro == Filtro.COLOR) return imagen
        OpenCv.asegurarCargado()
        val rgba = Mat()
        val gris = Mat()
        var blancoYNegro: Mat? = null
        try {
            Utils.bitmapToMat(imagen, rgba)
            Imgproc.cvtColor(rgba, gris, Imgproc.COLOR_RGBA2GRAY)
            val filtrada = if (filtro == Filtro.BLANCO_NEGRO) {
                BlancoYNegro.aplicar(gris).also { blancoYNegro = it }
            } else {
                gris
            }
            Imgproc.cvtColor(filtrada, rgba, Imgproc.COLOR_GRAY2RGBA)
            val resultado = createBitmap(imagen.width, imagen.height)
            Utils.matToBitmap(rgba, resultado)
            return resultado
        } finally {
            rgba.release()
            gris.release()
            blancoYNegro?.release()
        }
    }
}
