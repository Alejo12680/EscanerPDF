package com.fabio.escanerpdf.procesamiento

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.fabio.escanerpdf.datos.Filtro
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

object Filtros {

    fun aplicar(bgr: Mat, filtro: Filtro): Mat = when (filtro) {
        Filtro.COLOR -> bgr
        Filtro.GRIS -> Mat().also { Imgproc.cvtColor(bgr, it, Imgproc.COLOR_BGR2GRAY) }
        Filtro.BLANCO_NEGRO -> {
            val gris = Mat()
            Imgproc.cvtColor(bgr, gris, Imgproc.COLOR_BGR2GRAY)
            Imgproc.adaptiveThreshold(
                gris, gris, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY,
                tamanoBloque(gris), 12.0,
            )
            gris
        }
    }

    private fun tamanoBloque(imagen: Mat): Int =
        max(11, min(imagen.cols(), imagen.rows()) / 25) or 1

    fun previsualizar(imagen: Bitmap, filtro: Filtro): Bitmap {
        if (filtro == Filtro.COLOR) return imagen
        OpenCv.asegurarCargado()
        val rgba = Mat()
        val bgr = Mat()
        var filtrada: Mat? = null
        try {
            Utils.bitmapToMat(imagen, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            filtrada = aplicar(bgr, filtro)
            Imgproc.cvtColor(filtrada, rgba, Imgproc.COLOR_GRAY2RGBA)
            val resultado = createBitmap(imagen.width, imagen.height)
            Utils.matToBitmap(rgba, resultado)
            return resultado
        } finally {
            rgba.release()
            bgr.release()
            filtrada?.release()
        }
    }
}
