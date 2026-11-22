package com.fabio.escanerpdf.procesamiento

import com.fabio.escanerpdf.datos.PaginaEscaneada
import com.fabio.escanerpdf.datos.Punto
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.IOException
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

object ProcesadorPagina {

    const val CALIDAD_JPEG = 95
    private const val LADO_MINIATURA = 400

    @Throws(IOException::class)
    fun procesar(foto: File, esquinas: List<Punto>, destino: File): PaginaEscaneada {
        OpenCv.asegurarCargado()
        require(esquinas.size == 4) { "Se necesitan 4 esquinas" }

        var original = Imgcodecs.imread(foto.path, Imgcodecs.IMREAD_GRAYSCALE or Imgcodecs.IMREAD_IGNORE_ORIENTATION)
        if (original.empty()) throw IOException("No se pudo leer la foto")
        original = girar(original, CargadorImagen.rotacionExif(foto))

        val enderezada = Mat()
        var blancoYNegro: Mat? = null
        try {
            enderezar(original, esquinas, enderezada)
            original.release()

            blancoYNegro = BlancoYNegro.aplicar(enderezada)
            val guardado = Imgcodecs.imwrite(
                destino.path,
                blancoYNegro,
                MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, CALIDAD_JPEG),
            )
            if (!guardado) throw IOException("No se pudo guardar la página")

            return PaginaEscaneada(
                archivo = destino,
                ancho = blancoYNegro.cols(),
                alto = blancoYNegro.rows(),
                miniatura = CargadorImagen.cargarReducida(destino, LADO_MINIATURA),
            )
        } finally {
            original.release()
            enderezada.release()
            blancoYNegro?.release()
        }
    }

    private fun enderezar(original: Mat, esquinas: List<Punto>, salida: Mat) {
        val ancho = original.cols().toDouble()
        val alto = original.rows().toDouble()
        val (supIzq, supDer, infDer, infIzq) = esquinas.map { Point(it.x * ancho, it.y * alto) }

        val anchoPagina = max(distancia(supIzq, supDer), distancia(infIzq, infDer)).roundToInt().coerceAtLeast(1)
        val altoPagina = max(distancia(supIzq, infIzq), distancia(supDer, infDer)).roundToInt().coerceAtLeast(1)

        val origen = MatOfPoint2f(supIzq, supDer, infDer, infIzq)
        val destino = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(anchoPagina - 1.0, 0.0),
            Point(anchoPagina - 1.0, altoPagina - 1.0),
            Point(0.0, altoPagina - 1.0),
        )
        val transformacion = Imgproc.getPerspectiveTransform(origen, destino)
        Imgproc.warpPerspective(
            original, salida, transformacion,
            Size(anchoPagina.toDouble(), altoPagina.toDouble()),
            Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE,
        )
        origen.release()
        destino.release()
        transformacion.release()
    }

    private fun girar(imagen: Mat, grados: Int): Mat {
        val codigo = when (grados) {
            90 -> Core.ROTATE_90_CLOCKWISE
            180 -> Core.ROTATE_180
            270 -> Core.ROTATE_90_COUNTERCLOCKWISE
            else -> return imagen
        }
        val girada = Mat()
        Core.rotate(imagen, girada, codigo)
        imagen.release()
        return girada
    }

    private fun distancia(a: Point, b: Point): Double = hypot(a.x - b.x, a.y - b.y)
}
