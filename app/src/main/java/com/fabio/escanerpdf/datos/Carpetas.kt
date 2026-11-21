package com.fabio.escanerpdf.datos

import android.content.Context
import java.io.File

object Carpetas {

    fun capturas(contexto: Context): File = File(contexto.cacheDir, "capturas").apply { mkdirs() }

    fun sesion(contexto: Context): File = File(contexto.filesDir, "sesion").apply { mkdirs() }

    fun pdfs(contexto: Context): File = File(contexto.filesDir, "pdfs").apply { mkdirs() }

    fun vaciar(carpeta: File) {
        carpeta.listFiles()?.forEach { it.deleteRecursively() }
    }
}
