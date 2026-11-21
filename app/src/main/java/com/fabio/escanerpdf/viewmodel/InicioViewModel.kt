package com.fabio.escanerpdf.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabio.escanerpdf.datos.DocumentoPdf
import com.fabio.escanerpdf.datos.RepositorioDocumentos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class InicioViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val repositorio = RepositorioDocumentos(aplicacion)

    var documentos by mutableStateOf<List<DocumentoPdf>>(emptyList())
        private set

    var mensaje by mutableStateOf<String?>(null)
        private set

    fun refrescar() {
        viewModelScope.launch {
            documentos = withContext(Dispatchers.IO) { repositorio.listar() }
        }
    }

    fun borrar(documento: DocumentoPdf) {
        viewModelScope.launch {
            val borrado = withContext(Dispatchers.IO) { repositorio.borrar(documento) }
            mensaje = if (borrado) "Se borró \"${documento.nombre}\"" else "No se pudo borrar el documento"
            refrescar()
        }
    }

    fun guardarEnDescargas(documento: DocumentoPdf) {
        viewModelScope.launch {
            mensaje = try {
                val carpeta = withContext(Dispatchers.IO) { repositorio.guardarEnDescargas(documento) }
                "Guardado en $carpeta"
            } catch (e: IOException) {
                "No se pudo guardar: ${e.message}"
            } catch (e: SecurityException) {
                "No se pudo guardar: falta el permiso de almacenamiento"
            }
        }
    }

    fun uriCompartible(documento: DocumentoPdf): Uri = repositorio.uriCompartible(documento)

    fun mostrarMensaje(texto: String) {
        mensaje = texto
    }

    fun mensajeMostrado() {
        mensaje = null
    }
}
