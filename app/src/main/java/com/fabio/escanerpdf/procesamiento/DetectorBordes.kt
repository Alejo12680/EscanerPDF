package com.fabio.escanerpdf.procesamiento

import android.graphics.Bitmap
import com.fabio.escanerpdf.datos.Punto
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object DetectorBordes {

    private const val LADO_DETECCION = 600.0

    private const val AREA_MINIMA = 0.15
    private const val AREA_MAXIMA = 0.98
    private const val COSENO_MAXIMO = 0.6
    private const val SOPORTE_MINIMO = 0.6
    private const val LARGO_PARED = 0.15
    private const val DISTANCIA_LADO = 15.0
    private const val MARGEN_MARCO = 4

    fun detectar(imagen: Bitmap): List<Punto> {
        OpenCv.asegurarCargado()
        val rgba = Mat()
        val pequena = Mat()
        try {
            Utils.bitmapToMat(imagen, rgba)
            val escala = LADO_DETECCION / max(rgba.cols(), rgba.rows())
            Imgproc.resize(rgba, pequena, Size(), escala, escala, Imgproc.INTER_AREA)

            val cuadrilatero = buscarPorContornos(pequena) ?: buscarPorRegion(pequena)

            val ancho = pequena.cols().toFloat()
            val alto = pequena.rows().toFloat()
            return if (cuadrilatero == null) {
                esquinasPorDefecto()
            } else {
                ordenarEsquinas(cuadrilatero.map { Punto(it.x.toFloat() / ancho, it.y.toFloat() / alto) })
            }
        } finally {
            rgba.release()
            pequena.release()
        }
    }

    private fun buscarPorContornos(pequena: Mat): List<Point>? {
        val bordes = Mat()
        val jerarquia = Mat()
        val contornos = mutableListOf<MatOfPoint>()
        try {
            Imgproc.cvtColor(pequena, bordes, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(bordes, bordes, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(bordes, bordes, 50.0, 150.0)
            val nucleo = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(bordes, bordes, nucleo)
            nucleo.release()

            Imgproc.findContours(bordes, contornos, jerarquia, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val areaMinima = bordes.cols().toDouble() * bordes.rows() * AREA_MINIMA
            return contornos
                .sortedByDescending { Imgproc.contourArea(it) }
                .take(10)
                .firstNotNullOfOrNull { contorno -> aproximarCuadrilatero(contorno, areaMinima) }
        } finally {
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
            return if (esConvexo(puntos.toList())) puntos.toList() else null
        } finally {
            curva.release()
            aproximado.release()
        }
    }

    private fun buscarPorRegion(pequena: Mat): List<Point>? {
        val ancho = pequena.cols()
        val alto = pequena.rows()
        val bordes = calcularBordes(pequena)
        val paredes = Mat()
        val etiquetas = Mat()
        val region = Mat()
        val jerarquia = Mat()
        val contornos = mutableListOf<MatOfPoint>()
        val nucleo = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        try {
            soloBordesLargos(bordes, paredes)
            Imgproc.dilate(paredes, paredes, nucleo)
            Core.bitwise_not(paredes, paredes)
            Imgproc.connectedComponents(paredes, etiquetas, 4, CvType.CV_32S)
            val etiqueta = etiquetaDelCentro(etiquetas) ?: return null

            Core.compare(etiquetas, Scalar(etiqueta.toDouble()), region, Core.CMP_EQ)
            Imgproc.dilate(region, region, nucleo)
            Imgproc.findContours(region, contornos, jerarquia, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE)
            val contorno = contornos.maxByOrNull { Imgproc.contourArea(it) } ?: return null
            val puntos = contorno.toArray()

            val aproximado = aproximarConCasco(puntos) ?: return null
            val ajustado = ajustarLados(aproximado, puntos, ancho, alto)

            Imgproc.dilate(bordes, bordes, nucleo)
            return if (esValido(ajustado, bordes)) ajustado else null
        } finally {
            bordes.release()
            paredes.release()
            etiquetas.release()
            region.release()
            jerarquia.release()
            contornos.forEach { it.release() }
            nucleo.release()
        }
    }

    private fun calcularBordes(pequena: Mat): Mat {
        val gris = Mat()
        val rgb = Mat()
        val lab = Mat()
        val croma = Mat()
        val bordesCroma = Mat()
        val canales = mutableListOf<Mat>()
        val bordes = Mat()
        try {
            Imgproc.cvtColor(pequena, gris, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gris, gris, Size(5.0, 5.0), 0.0)
            Imgproc.cvtColor(pequena, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.GaussianBlur(rgb, rgb, Size(5.0, 5.0), 0.0)
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
            Core.split(lab, canales)
            Core.absdiff(canales[1], Scalar(128.0), canales[1])
            Core.absdiff(canales[2], Scalar(128.0), canales[2])
            Core.addWeighted(canales[1], 2.0, canales[2], 2.0, 0.0, croma)

            Imgproc.Canny(gris, bordes, 20.0, 60.0)
            Imgproc.Canny(croma, bordesCroma, 20.0, 60.0)
            Core.bitwise_or(bordes, bordesCroma, bordes)
            return bordes
        } finally {
            gris.release()
            rgb.release()
            lab.release()
            croma.release()
            bordesCroma.release()
            canales.forEach { it.release() }
        }
    }

    private fun soloBordesLargos(bordes: Mat, salida: Mat) {
        val engrosados = Mat()
        val etiquetas = Mat()
        val estadisticas = Mat()
        val centros = Mat()
        try {
            val nucleo = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(bordes, engrosados, nucleo)
            nucleo.release()
            val cantidad = Imgproc.connectedComponentsWithStats(engrosados, etiquetas, estadisticas, centros, 8, CvType.CV_32S)

            val columnas = estadisticas.cols()
            val datos = IntArray(cantidad * columnas).also { estadisticas.get(0, 0, it) }
            val largoMinimo = LARGO_PARED * min(bordes.cols(), bordes.rows())
            val esLargo = BooleanArray(cantidad) { i ->
                val anchoCaja = datos[i * columnas + Imgproc.CC_STAT_WIDTH]
                val altoCaja = datos[i * columnas + Imgproc.CC_STAT_HEIGHT]
                i > 0 && max(anchoCaja, altoCaja) >= largoMinimo
            }

            val total = bordes.cols() * bordes.rows()
            val ids = IntArray(total).also { etiquetas.get(0, 0, it) }
            val pixeles = ByteArray(total).also { bordes.get(0, 0, it) }
            for (i in 0 until total) {
                if (!esLargo[ids[i]]) pixeles[i] = 0
            }
            salida.create(bordes.rows(), bordes.cols(), CvType.CV_8U)
            salida.put(0, 0, pixeles)
        } finally {
            engrosados.release()
            etiquetas.release()
            estadisticas.release()
            centros.release()
        }
    }

    private fun etiquetaDelCentro(etiquetas: Mat): Int? {
        val ancho = etiquetas.cols()
        val alto = etiquetas.rows()
        val ids = IntArray(ancho * alto).also { etiquetas.get(0, 0, it) }
        val conteo = HashMap<Int, Int>()
        for (fila in (alto * 35 / 100) until (alto * 65 / 100)) {
            for (columna in (ancho * 35 / 100) until (ancho * 65 / 100)) {
                val id = ids[fila * ancho + columna]
                if (id > 0) conteo[id] = (conteo[id] ?: 0) + 1
            }
        }
        return conteo.maxByOrNull { it.value }?.key
    }

    private fun aproximarConCasco(puntos: Array<Point>): List<Point>? {
        val contorno = MatOfPoint(*puntos)
        val indices = MatOfInt()
        val curva = MatOfPoint2f()
        val aproximado = MatOfPoint2f()
        try {
            Imgproc.convexHull(contorno, indices)
            curva.fromList(indices.toArray().map { puntos[it] })
            val perimetro = Imgproc.arcLength(curva, true)
            var fraccion = 0.01
            while (fraccion <= 0.08) {
                Imgproc.approxPolyDP(curva, aproximado, fraccion * perimetro, true)
                val cantidad = aproximado.rows()
                if (cantidad == 4) return aproximado.toList()
                if (cantidad < 4) return null
                fraccion += 0.005
            }
            return null
        } finally {
            contorno.release()
            indices.release()
            curva.release()
            aproximado.release()
        }
    }

    private fun ajustarLados(esquinas: List<Point>, puntos: Array<Point>, ancho: Int, alto: Int): List<Point> {
        val grupos = List(4) { mutableListOf<Point>() }
        for (p in puntos) {
            var mejorLado = 0
            var mejorDistancia = Double.MAX_VALUE
            for (i in 0 until 4) {
                val d = distanciaASegmento(p, esquinas[i], esquinas[(i + 1) % 4])
                if (d < mejorDistancia) {
                    mejorDistancia = d
                    mejorLado = i
                }
            }
            if (mejorDistancia < DISTANCIA_LADO) grupos[mejorLado].add(p)
        }
        if (grupos.any { it.size < 5 }) return esquinas

        val rectas = grupos.map { ajustarRecta(it) }
        val ajustadas = (0 until 4).map { i ->
            intersectar(rectas[(i + 3) % 4], rectas[i]) ?: return esquinas
        }
        return ajustadas.map {
            Point(it.x.coerceIn(0.0, ancho - 1.0), it.y.coerceIn(0.0, alto - 1.0))
        }
    }

    private fun ajustarRecta(grupo: List<Point>): FloatArray {
        val puntos = MatOfPoint2f(*grupo.toTypedArray())
        val recta = Mat()
        try {
            Imgproc.fitLine(puntos, recta, Imgproc.DIST_HUBER, 0.0, 0.01, 0.01)
            return FloatArray(4).also { recta.get(0, 0, it) }
        } finally {
            puntos.release()
            recta.release()
        }
    }

    private fun intersectar(r1: FloatArray, r2: FloatArray): Point? {
        val (dx1, dy1, x1, y1) = r1.map { it.toDouble() }
        val (dx2, dy2, x2, y2) = r2.map { it.toDouble() }
        val determinante = dx2 * dy1 - dx1 * dy2
        if (abs(determinante) < 1e-6) return null
        val t = (dx2 * (y2 - y1) - dy2 * (x2 - x1)) / determinante
        return Point(x1 + t * dx1, y1 + t * dy1)
    }

    private fun distanciaASegmento(p: Point, a: Point, b: Point): Double {
        val abX = b.x - a.x
        val abY = b.y - a.y
        val largo2 = abX * abX + abY * abY
        val t = if (largo2 < 1e-9) 0.0 else (((p.x - a.x) * abX + (p.y - a.y) * abY) / largo2).coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + t * abX), p.y - (a.y + t * abY))
    }

    private fun esValido(esquinas: List<Point>, bordes: Mat): Boolean {
        val ancho = bordes.cols()
        val alto = bordes.rows()
        val poligono = MatOfPoint2f(*esquinas.toTypedArray())
        val area = Imgproc.contourArea(poligono) / (ancho.toDouble() * alto)
        poligono.release()
        if (area < AREA_MINIMA || area > AREA_MAXIMA) return false
        if (!esConvexo(esquinas) || cosenoMaximo(esquinas) > COSENO_MAXIMO) return false
        return soporte(esquinas, bordes) >= SOPORTE_MINIMO
    }

    private fun esConvexo(esquinas: List<Point>): Boolean {
        val poligono = MatOfPoint(*esquinas.map { Point(it.x.roundToInt().toDouble(), it.y.roundToInt().toDouble()) }.toTypedArray())
        val convexo = Imgproc.isContourConvex(poligono)
        poligono.release()
        return convexo
    }

    private fun cosenoMaximo(esquinas: List<Point>): Double = (0 until 4).maxOf { i ->
        val a = esquinas[(i + 3) % 4]
        val b = esquinas[i]
        val c = esquinas[(i + 1) % 4]
        val v1x = a.x - b.x
        val v1y = a.y - b.y
        val v2x = c.x - b.x
        val v2y = c.y - b.y
        abs(v1x * v2x + v1y * v2y) / (hypot(v1x, v1y) * hypot(v2x, v2y) + 1e-9)
    }

    private fun soporte(esquinas: List<Point>, bordes: Mat): Double {
        val ancho = bordes.cols()
        val alto = bordes.rows()
        val pixeles = ByteArray(ancho * alto).also { bordes.get(0, 0, it) }
        var total = 0
        var enMarco = 0
        var aciertos = 0
        for (i in 0 until 4) {
            val a = esquinas[i]
            val b = esquinas[(i + 1) % 4]
            val pasos = max(2, (hypot(b.x - a.x, b.y - a.y) / 2).toInt())
            for (k in 0 until pasos) {
                val t = k.toDouble() / (pasos - 1)
                val x = a.x + (b.x - a.x) * t
                val y = a.y + (b.y - a.y) * t
                total++
                if (x < MARGEN_MARCO || y < MARGEN_MARCO || x > ancho - 1 - MARGEN_MARCO || y > alto - 1 - MARGEN_MARCO) {
                    enMarco++
                    continue
                }
                if (pixeles[y.roundToInt() * ancho + x.roundToInt()].toInt() != 0) aciertos++
            }
        }
        val reales = total - enMarco
        if (reales < 0.4 * total) return 0.0
        return aciertos.toDouble() / reales
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
