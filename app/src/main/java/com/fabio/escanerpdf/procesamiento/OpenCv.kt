package com.fabio.escanerpdf.procesamiento

import org.opencv.android.OpenCVLoader

internal object OpenCv {

    private val cargado: Boolean by lazy { OpenCVLoader.initLocal() }

    fun asegurarCargado() {
        check(cargado) { "No se pudo cargar OpenCV en este dispositivo" }
    }
}
