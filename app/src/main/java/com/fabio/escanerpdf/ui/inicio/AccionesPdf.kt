package com.fabio.escanerpdf.ui.inicio

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fabio.escanerpdf.datos.RepositorioDocumentos.Companion.MIME_PDF

fun abrirPdf(contexto: Context, uri: Uri): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, MIME_PDF)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        contexto.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

fun compartirPdf(contexto: Context, uri: Uri, nombre: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = MIME_PDF
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, nombre)
        clipData = ClipData.newRawUri(nombre, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    contexto.startActivity(Intent.createChooser(intent, "Compartir PDF"))
}
