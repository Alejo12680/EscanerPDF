package com.fabio.escanerpdf.datos

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

class RepositorioDocumentos(private val contexto: Context) {

    private val carpetaPdfs: File get() = Carpetas.pdfs(contexto)

    fun listar(): List<DocumentoPdf> =
        carpetaPdfs.listFiles { archivo -> archivo.extension.equals("pdf", ignoreCase = true) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { archivo ->
                DocumentoPdf(
                    archivo = archivo,
                    nombre = archivo.nameWithoutExtension,
                    fechaModificacion = archivo.lastModified(),
                    tamanoBytes = archivo.length(),
                )
            }

    fun archivoParaNuevoPdf(nombreDeseado: String): File =
        archivoSinRepetir(carpetaPdfs, limpiarNombre(nombreDeseado), "pdf")

    fun documentoDe(archivo: File): DocumentoPdf =
        DocumentoPdf(archivo, archivo.nameWithoutExtension, archivo.lastModified(), archivo.length())

    fun borrar(documento: DocumentoPdf): Boolean = documento.archivo.delete()

    fun uriCompartible(documento: DocumentoPdf): Uri =
        FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", documento.archivo)

    @Throws(IOException::class)
    fun guardarEnDescargas(documento: DocumentoPdf): String {
        val nombreArchivo = documento.archivo.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            guardarConMediaStore(documento.archivo, nombreArchivo)
        } else {
            guardarConArchivoDirecto(documento.archivo, nombreArchivo)
        }
        return "${Environment.DIRECTORY_DOWNLOADS}/$CARPETA_PUBLICA"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun guardarConMediaStore(origen: File, nombreArchivo: String) {
        val resolver = contexto.contentResolver
        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nombreArchivo)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_PDF)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$CARPETA_PUBLICA")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
            ?: throw IOException("No se pudo crear el archivo en Descargas")
        try {
            val salida = resolver.openOutputStream(uri)
                ?: throw IOException("No se pudo escribir en Descargas")
            salida.use { destino -> origen.inputStream().use { it.copyTo(destino) } }
            valores.clear()
            valores.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, valores, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun guardarConArchivoDirecto(origen: File, nombreArchivo: String) {
        @Suppress("DEPRECATION")
        val descargas = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val carpeta = File(descargas, CARPETA_PUBLICA)
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            throw IOException("No se pudo crear la carpeta Descargas/$CARPETA_PUBLICA")
        }
        val destino = archivoSinRepetir(carpeta, File(nombreArchivo).nameWithoutExtension, "pdf")
        origen.copyTo(destino)
        MediaScannerConnection.scanFile(contexto, arrayOf(destino.absolutePath), arrayOf(MIME_PDF), null)
    }

    companion object {
        const val MIME_PDF = "application/pdf"
        private const val CARPETA_PUBLICA = "EscanerPDF"
        private const val LARGO_MAXIMO_NOMBRE = 80

        fun limpiarNombre(nombre: String): String =
            nombre.trim()
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .take(LARGO_MAXIMO_NOMBRE)
                .ifBlank { "Documento" }

        private fun archivoSinRepetir(carpeta: File, base: String, extension: String): File {
            var archivo = File(carpeta, "$base.$extension")
            var numero = 2
            while (archivo.exists()) {
                archivo = File(carpeta, "$base ($numero).$extension")
                numero++
            }
            return archivo
        }
    }
}
