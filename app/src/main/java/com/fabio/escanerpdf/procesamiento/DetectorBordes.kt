package com.fabio.escanerpdf.procesamiento

import android.graphics.Bitmap
import com.fabio.escanerpdf.datos.Punto
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.atan2
import kotlin.math.max

object DetectorBordes {

    private const val LADO_DETECCION = 600.0

    private const val AREA_MINIMA = 0.15

    fun detectar(imagen: Bitmap): List<Punto> {
        OpenCv.asegurarCargado()
        val rgba = Mat()
        val pequena = Mat()
        val bordes = Mat()
        val jerarquia = Mat()
        val contornos = mutableListOf<MatOfPoint>()
        try {
            Utils.bitmapToMat(imagen, rgba)
            val escala = LADO_DETECCION / max(rgba.cols(), rgba.rows())
            Imgproc.resize(rgba, pequena, Size(), escala, escala, Imgproc.INTER_AREA)

            Imgproc.cvtColor(pequena, bordes, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(bordes, bordes, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(bordes, bordes, 50.0, 150.0)
            val nucleo = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(bordes, bordes, nucleo)
            nucleo.release()

            Imgproc.findContours(bordes, contornos, jerarquia, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val ancho = bordes.cols().toFloat()
            val alto = bordes.rows().toFloat()
            val areaMinima = ancho * alto * AREA_MINIMA

            val cuadrilatero = contornos
                .sortedByDescending { Imgproc.contourArea(it) }
                .take(10)
                .firstNotNullOfOrNull { contorno -> aproximarCuadrilatero(contorno, areaMinima) }

            return if (cuadrilatero == null) {
                esquinasPorDefecto()
            } else {
                ordenarEsquinas(cuadrilatero.map { Punto(it.x.toFloat() / ancho, it.y.toFloat() / alto) })
            }
        } finally {
            rgba.release()
            pequena.release()
            bordes.release()
            jerarquia.release()
            contornos.forEach { it.release() }
        }
    }

    private fun aproximarCuadrilatero(contorno: MatOfPoint, areaMinima: Double): List<Point>? {
        val curva = MatOfPoint2f(*contorno.toArray())
        val aproximado = MatOfPoint2f()
        try {
            val perimetro = Imgproc.arcLength(curva, true)
            Imgproc.approxPolyDP(curva, aproximado, 0.02 * perimetro, true)
            val puntos = aproximado.toArray()
            if (puntos.size != 4) return null
            if (Imgproc.contourArea(aproximado) < areaMinima) return null
            val poligono = MatOfPoint(*puntos)
            val esConvexo = Imgproc.isContourConvex(poligono)
            poligono.release()
            return if (esConvexo) puntos.toList() else null
        } finally {
            curva.release()
            aproximado.release()
        }
    }

    fun esquinasPorDefecto(): List<Punto> = listOf(
        Punto(0.05f, 0.05f),
        Punto(0.95f, 0.05f),
        Punto(0.95f, 0.95f),
        Punto(0.05f, 0.95f),
    )

    fun ordenarEsquinas(puntos: List<Punto>): List<Punto> {
        val centroX = puntos.map { it.x }.average()
        val centroY = puntos.map { it.y }.average()
        val horario = puntos.sortedBy { atan2(it.y - centroY, it.x - centroX) }
        val inicio = horario.indices.minBy { horario[it].x + horario[it].y }
        return List(4) { i -> horario[(inicio + i) % 4] }
    }
}
