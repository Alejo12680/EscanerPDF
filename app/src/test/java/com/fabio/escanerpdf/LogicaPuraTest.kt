package com.fabio.escanerpdf

import com.fabio.escanerpdf.datos.Punto
import com.fabio.escanerpdf.datos.RepositorioDocumentos
import com.fabio.escanerpdf.procesamiento.DetectorBordes
import org.junit.Assert.assertEquals
import org.junit.Test

class LogicaPuraTest {

    @Test
    fun ordenarEsquinas_puntosDesordenados_quedanEnSentidoHorarioDesdeArribaIzquierda() {
        val supIzq = Punto(0.10f, 0.10f)
        val supDer = Punto(0.88f, 0.12f)
        val infDer = Punto(0.90f, 0.85f)
        val infIzq = Punto(0.15f, 0.90f)

        val ordenadas = DetectorBordes.ordenarEsquinas(listOf(infDer, supIzq, infIzq, supDer))

        assertEquals(listOf(supIzq, supDer, infDer, infIzq), ordenadas)
    }

    @Test
    fun ordenarEsquinas_hojaGirada30Grados_conservaElOrden() {
        val supIzq = Punto(0.390f, 0.090f)
        val supDer = Punto(0.910f, 0.390f)
        val infDer = Punto(0.610f, 0.910f)
        val infIzq = Punto(0.090f, 0.610f)

        val ordenadas = DetectorBordes.ordenarEsquinas(listOf(infIzq, infDer, supDer, supIzq))

        assertEquals(listOf(supIzq, supDer, infDer, infIzq), ordenadas)
    }

    @Test
    fun limpiarNombre_reemplazaCaracteresProhibidos() {
        assertEquals("Tarea_ 1_2", RepositorioDocumentos.limpiarNombre("  Tarea: 1/2 "))
    }

    @Test
    fun limpiarNombre_vacio_usaNombrePorDefecto() {
        assertEquals("Documento", RepositorioDocumentos.limpiarNombre("   "))
    }
}
