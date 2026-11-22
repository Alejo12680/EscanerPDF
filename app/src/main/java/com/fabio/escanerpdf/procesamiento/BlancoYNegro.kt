package com.fabio.escanerpdf.procesamiento

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

object BlancoYNegro {

    private const val UMBRAL_OSCUROS = 0.5
    private const val CONTRASTE_TEXTO = 15.0
    private const val LADO_FONDO = 256.0

    fun aplicar(gris: Mat): Mat {
        val fondo = estimarFondo(gris)
        val normalizada = Mat()
        val oscuros = Mat()
        val finos = Mat()
        val resultado = Mat()
        try {
            Core.divide(gris, fondo, normalizada, 255.0)
            Imgproc.GaussianBlur(normalizada, normalizada, Size(5.0, 5.0), 0.0)
            Imgproc.threshold(normalizada, oscuros, 255 * UMBRAL_OSCUROS, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.adaptiveThreshold(
                normalizada, finos, 255.0,
                Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY,
                tamanoBloque(normalizada), CONTRASTE_TEXTO,
            )
            Core.bitwise_and(oscuros, finos, resultado)
            Imgproc.medianBlur(resultado, resultado, 3)
            return resultado
        } finally {
            fondo.release()
            normalizada.release()
            oscuros.release()
            finos.release()
        }
    }

    private fun estimarFondo(gris: Mat): Mat {
        val escala = LADO_FONDO / max(gris.cols(), gris.rows())
        val pequena = Mat()
        val fondo = Mat()
        Imgproc.resize(gris, pequena, Size(), escala, escala, Imgproc.INTER_AREA)
        val nucleo = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        Imgproc.dilate(pequena, pequena, nucleo)
        nucleo.release()
        Imgproc.GaussianBlur(pequena, pequena, Size(0.0, 0.0), 12.0)
        Imgproc.resize(pequena, fondo, gris.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
        pequena.release()
        return fondo
    }

    private fun tamanoBloque(imagen: Mat): Int =
        max(11, min(imagen.cols(), imagen.rows()) / 25) or 1
}
