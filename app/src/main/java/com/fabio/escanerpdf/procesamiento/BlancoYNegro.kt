package com.fabio.escanerpdf.procesamiento

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

object BlancoYNegro {

    private const val FRACCION_BORDE = 0.70
    private const val CONTRASTE_MINIMO = 20.0
    private const val UMBRAL_OSCUROS = 0.70
    private const val LADO_FONDO = 256.0

    fun aplicar(gris: Mat): Mat {
        val normalizada = normalizar(gris)
        val corte = Mat()
        val contraste = Mat()
        val texto = Mat()
        val conContraste = Mat()
        val oscuros = Mat()
        val resultado = Mat()
        try {
            Imgproc.GaussianBlur(normalizada, normalizada, Size(3.0, 3.0), 0.0)
            calcularCorteLocal(normalizada, corte, contraste)

            Core.compare(normalizada, corte, texto, Core.CMP_LT)
            Core.compare(contraste, Scalar(CONTRASTE_MINIMO), conContraste, Core.CMP_GT)
            Core.bitwise_and(texto, conContraste, texto)
            Core.compare(normalizada, Scalar(255 * UMBRAL_OSCUROS), oscuros, Core.CMP_LT)

            Core.bitwise_or(texto, oscuros, resultado)
            Core.bitwise_not(resultado, resultado)
            Imgproc.medianBlur(resultado, resultado, 3)
            return resultado
        } finally {
            normalizada.release()
            corte.release()
            contraste.release()
            texto.release()
            conContraste.release()
            oscuros.release()
        }
    }

    private fun normalizar(gris: Mat): Mat {
        val escala = LADO_FONDO / max(gris.cols(), gris.rows())
        val pequena = Mat()
        val fondo = Mat()
        val normalizada = Mat()
        Imgproc.resize(gris, pequena, Size(), escala, escala, Imgproc.INTER_AREA)
        val nucleo = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        Imgproc.dilate(pequena, pequena, nucleo)
        nucleo.release()
        Imgproc.GaussianBlur(pequena, pequena, Size(0.0, 0.0), 12.0)
        Imgproc.resize(pequena, fondo, gris.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
        Core.divide(gris, fondo, normalizada, 255.0)
        pequena.release()
        fondo.release()
        return normalizada
    }

    private fun calcularCorteLocal(normalizada: Mat, corte: Mat, contraste: Mat) {
        val ladoCompleto = max(9, min(normalizada.cols(), normalizada.rows()) / 100) or 1
        val lado = (ladoCompleto / 2) or 1
        val reducida = Mat()
        val minimo = Mat()
        val maximo = Mat()
        val nucleo = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(lado.toDouble(), lado.toDouble()))
        try {
            Imgproc.resize(normalizada, reducida, Size(), 0.5, 0.5, Imgproc.INTER_AREA)
            Imgproc.erode(reducida, minimo, nucleo)
            Imgproc.dilate(reducida, maximo, nucleo)
            Core.addWeighted(minimo, 1 - FRACCION_BORDE, maximo, FRACCION_BORDE, 0.0, reducida)
            Imgproc.resize(reducida, corte, normalizada.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
            Core.subtract(maximo, minimo, reducida)
            Imgproc.resize(reducida, contraste, normalizada.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
        } finally {
            reducida.release()
            minimo.release()
            maximo.release()
            nucleo.release()
        }
    }
}
