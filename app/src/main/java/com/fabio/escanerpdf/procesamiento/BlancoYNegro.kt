package com.fabio.escanerpdf.procesamiento

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object BlancoYNegro {

    private const val FRACCION_BORDE = 0.70
    private const val CONTRASTE_MINIMO = 20.0
    private const val UMBRAL_OSCUROS = 0.70
    private const val LADO_FONDO = 256.0
    private const val LADO_CIERRE = 9.0
    private const val SIGMA_FONDO = 3.0
    private const val PISO_FONDO = 0.25
    private const val VECES_RUIDO = 6.0
    private const val CONTRASTE_TINTA = 60.0
    private const val PAPEL_MINIMO = 0.15f
    private const val VECES_RUIDO_NUCLEO = 24.0
    private const val DIVISOR_MARGEN_NUCLEO = 100

    fun aplicar(gris: Mat): Mat {
        val fondo = estimarFondo(gris)
        val normalizada = Mat()
        val corte = Mat()
        val contraste = Mat()
        val umbral = Mat()
        val texto = Mat()
        val conContraste = Mat()
        val oscuros = Mat()
        val resultado = Mat()
        try {
            normalizar(gris, fondo, normalizada)
            Imgproc.GaussianBlur(normalizada, normalizada, Size(3.0, 3.0), 0.0)
            val ruidoPagina = calcularCorteLocal(normalizada, fondo, corte, contraste, umbral)

            Core.compare(normalizada, corte, texto, Core.CMP_LT)
            Core.compare(contraste, umbral, conContraste, Core.CMP_GT)
            Core.bitwise_and(texto, conContraste, texto)
            Core.compare(normalizada, Scalar(255 * UMBRAL_OSCUROS), oscuros, Core.CMP_LT)

            Core.bitwise_or(texto, oscuros, texto)
            conservarConNucleo(texto, normalizada, ruidoPagina, resultado)
            Core.bitwise_not(resultado, resultado)
            Imgproc.medianBlur(resultado, resultado, 3)
            return resultado
        } finally {
            fondo.release()
            normalizada.release()
            corte.release()
            contraste.release()
            umbral.release()
            texto.release()
            conContraste.release()
            oscuros.release()
        }
    }

    private fun estimarFondo(gris: Mat): Mat {
        val escala = LADO_FONDO / max(gris.cols(), gris.rows())
        val fondo = Mat()
        Imgproc.resize(gris, fondo, Size(), escala, escala, Imgproc.INTER_AREA)
        val nucleo = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(LADO_CIERRE, LADO_CIERRE))
        Imgproc.morphologyEx(fondo, fondo, Imgproc.MORPH_CLOSE, nucleo)
        nucleo.release()
        Imgproc.GaussianBlur(fondo, fondo, Size(0.0, 0.0), SIGMA_FONDO)
        val piso = PISO_FONDO * Core.minMaxLoc(fondo).maxVal
        Core.max(fondo, Scalar(piso), fondo)
        return fondo
    }

    private fun normalizar(gris: Mat, fondo: Mat, normalizada: Mat) {
        val fondoCompleto = Mat()
        Imgproc.resize(fondo, fondoCompleto, gris.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
        Core.divide(gris, fondoCompleto, normalizada, 255.0)
        fondoCompleto.release()
    }

    private fun conservarConNucleo(negro: Mat, normalizada: Mat, ruidoPagina: Double, conservado: Mat) {
        val profundidad = max(CONTRASTE_MINIMO, VECES_RUIDO_NUCLEO * ruidoPagina)
        val nucleo = Mat()
        val jerarquia = Mat()
        val contornos = mutableListOf<MatOfPoint>()
        val mascara = Mat.zeros(negro.rows() + 2, negro.cols() + 2, CvType.CV_8U)
        val banderas = 8 or Imgproc.FLOODFILL_MASK_ONLY or (255 shl 8)
        val igual = Scalar(0.0)
        val marca = ByteArray(1)
        try {
            Core.compare(normalizada, Scalar(255 - profundidad), nucleo, Core.CMP_LT)
            Core.bitwise_and(nucleo, negro, nucleo)
            val margen = max(1, min(negro.rows(), negro.cols()) / DIVISOR_MARGEN_NUCLEO)
            val borde = Point((negro.cols() - 1).toDouble(), (negro.rows() - 1).toDouble())
            Imgproc.rectangle(nucleo, Point(0.0, 0.0), borde, Scalar(0.0), 2 * margen + 1)
            Imgproc.findContours(nucleo, contornos, jerarquia, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            for (contorno in contornos) {
                val (x, y) = contorno.get(0, 0).map { it.toInt() }
                mascara.get(y + 1, x + 1, marca)
                if (marca[0].toInt() == 0) {
                    val semilla = Point(x.toDouble(), y.toDouble())
                    Imgproc.floodFill(negro, mascara, semilla, Scalar(0.0), Rect(), igual, igual, banderas)
                }
            }
            val interior = mascara.submat(1, negro.rows() + 1, 1, negro.cols() + 1)
            interior.copyTo(conservado)
            interior.release()
        } finally {
            nucleo.release()
            jerarquia.release()
            contornos.forEach { it.release() }
            mascara.release()
        }
    }

    private fun calcularCorteLocal(normalizada: Mat, fondo: Mat, corte: Mat, contraste: Mat, umbral: Mat): Double {
        val ladoPagina = min(normalizada.cols(), normalizada.rows())
        val ladoCompleto = max(9, ladoPagina / 100) or 1
        val lado = (ladoCompleto / 2) or 1
        val reducida = Mat()
        val minimo = Mat()
        val maximo = Mat()
        val diferencia = Mat()
        val nucleo = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(lado.toDouble(), lado.toDouble()))
        try {
            Imgproc.resize(normalizada, reducida, Size(), 0.5, 0.5, Imgproc.INTER_AREA)
            Imgproc.erode(reducida, minimo, nucleo)
            Imgproc.dilate(reducida, maximo, nucleo)
            Core.subtract(maximo, minimo, diferencia)
            val ruidoPagina = calcularUmbral(reducida, diferencia, nucleo, fondo, ladoPagina, normalizada.size(), umbral)

            Core.addWeighted(minimo, 1 - FRACCION_BORDE, maximo, FRACCION_BORDE, 0.0, reducida)
            Imgproc.resize(reducida, corte, normalizada.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
            Imgproc.resize(diferencia, contraste, normalizada.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
            return ruidoPagina
        } finally {
            reducida.release()
            minimo.release()
            maximo.release()
            diferencia.release()
            nucleo.release()
        }
    }

    private fun calcularUmbral(
        reducida: Mat,
        diferencia: Mat,
        nucleo: Mat,
        fondo: Mat,
        ladoPagina: Int,
        tamanoFinal: Size,
        umbral: Mat,
    ): Double {
        val ladoBloque = max(8, ladoPagina / 80)
        val bloques = Size(
            max(1, reducida.cols() / ladoBloque).toDouble(),
            max(1, reducida.rows() / ladoBloque).toDouble(),
        )
        val papel = Mat()
        val valores = Mat()
        val producto = Mat()
        val suma0 = Mat()
        val suma1 = Mat()
        val suma2 = Mat()
        val brillo = Mat()
        val ruido = Mat()
        try {
            Core.compare(diferencia, Scalar(CONTRASTE_TINTA), papel, Core.CMP_GT)
            Imgproc.dilate(papel, papel, nucleo)
            Core.bitwise_not(papel, papel)
            papel.convertTo(papel, CvType.CV_32F, 1.0 / 255)
            reducida.convertTo(valores, CvType.CV_32F)

            Imgproc.resize(papel, suma0, bloques, 0.0, 0.0, Imgproc.INTER_AREA)
            Core.multiply(papel, valores, producto)
            Imgproc.resize(producto, suma1, bloques, 0.0, 0.0, Imgproc.INTER_AREA)
            Core.multiply(producto, valores, producto)
            Imgproc.resize(producto, suma2, bloques, 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.resize(fondo, brillo, bloques, 0.0, 0.0, Imgproc.INTER_AREA)

            val ruidoPagina = ruidoPorBloque(suma0, suma1, suma2, brillo, ruido)
            Imgproc.GaussianBlur(ruido, ruido, Size(0.0, 0.0), 1.0)
            Core.multiply(ruido, Scalar(VECES_RUIDO), ruido)
            Core.max(ruido, Scalar(CONTRASTE_MINIMO), ruido)
            ruido.convertTo(ruido, CvType.CV_8U)
            Imgproc.resize(ruido, umbral, tamanoFinal, 0.0, 0.0, Imgproc.INTER_LINEAR)
            return ruidoPagina
        } finally {
            papel.release()
            valores.release()
            producto.release()
            suma0.release()
            suma1.release()
            suma2.release()
            brillo.release()
            ruido.release()
        }
    }

    private fun ruidoPorBloque(suma0: Mat, suma1: Mat, suma2: Mat, brillo: Mat, mapa: Mat): Double {
        val total = suma0.rows() * suma0.cols()
        val s0 = FloatArray(total).also { suma0.get(0, 0, it) }
        val s1 = FloatArray(total).also { suma1.get(0, 0, it) }
        val s2 = FloatArray(total).also { suma2.get(0, 0, it) }
        val luz = ByteArray(total).also { brillo.get(0, 0, it) }
        val ganancia = FloatArray(total) { 255f / max(luz[it].toInt() and 0xFF, 1) }

        val ruido = FloatArray(total) { Float.NaN }
        val ruidoNormalizado = mutableListOf<Float>()
        for (i in 0 until total) {
            if (s0[i] > PAPEL_MINIMO) {
                val media = s1[i] / s0[i]
                val desviacion = sqrt(max(s2[i] / s0[i] - media * media, 0f))
                ruido[i] = desviacion / ganancia[i]
                ruidoNormalizado.add(desviacion)
            }
        }
        val relleno = mediana(ruido.filter { !it.isNaN() })
        for (i in 0 until total) {
            val base = if (ruido[i].isNaN()) relleno else ruido[i]
            ruido[i] = base * ganancia[i]
        }
        mapa.create(suma0.rows(), suma0.cols(), CvType.CV_32F)
        mapa.put(0, 0, ruido)
        return mediana(ruidoNormalizado).toDouble()
    }

    private fun mediana(valores: List<Float>): Float {
        val ordenados = valores.sorted()
        return if (ordenados.isEmpty()) 0f else ordenados[ordenados.size / 2]
    }
}
