package com.fabio.escanerpdf.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabio.escanerpdf.datos.Carpetas
import com.fabio.escanerpdf.datos.PaginaEscaneada
import com.fabio.escanerpdf.datos.Punto
import com.fabio.escanerpdf.datos.RepositorioDocumentos
import com.fabio.escanerpdf.procesamiento.CargadorImagen
import com.fabio.escanerpdf.procesamiento.DetectorBordes
import com.fabio.escanerpdf.procesamiento.GeneradorPdf
import com.fabio.escanerpdf.procesamiento.ProcesadorPagina
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EscanerViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val contexto: Context get() = getApplication()
    private val repositorio = RepositorioDocumentos(aplicacion)

    val paginas = mutableStateListOf<PaginaEscaneada>()

    var nombreDocumento by mutableStateOf(nombrePorDefecto())

    private var fotoActual: File? = null

    var vistaPrevia by mutableStateOf<Bitmap?>(null)
        private set
    var esquinas by mutableStateOf(DetectorBordes.esquinasPorDefecto())
        private set

    var ocupado by mutableStateOf(false)
        private set
    var mensajeError by mutableStateOf<String?>(null)
        private set

    var pdfRecienCreado by mutableStateOf<File?>(null)
        private set

    fun nuevoDocumento() {
        descartarDocumento()
        nombreDocumento = nombrePorDefecto()
        pdfRecienCreado = null
    }

    fun descartarDocumento() {
        paginas.clear()
        fotoActual = null
        vistaPrevia = null
        Carpetas.vaciar(Carpetas.sesion(contexto))
        Carpetas.vaciar(Carpetas.capturas(contexto))
    }

    fun cargarFoto(foto: File) {
        fotoActual = foto
        vistaPrevia = null
        ejecutar("No se pudo abrir la foto") {
            val (imagen, detectadas) = withContext(Dispatchers.Default) {
                val imagen = CargadorImagen.cargarReducida(foto, LADO_VISTA_PREVIA)
                imagen to DetectorBordes.detectar(imagen)
            }
            if (fotoActual != foto) return@ejecutar
            esquinas = detectadas
            vistaPrevia = imagen
        }
    }

    fun moverEsquina(indice: Int, punto: Punto) {
        esquinas = esquinas.toMutableList().also { it[indice] = punto }
    }

    fun descartarFoto() {
        fotoActual?.delete()
        fotoActual = null
    }

    fun aceptarPagina(alTerminar: () -> Unit) {
        val foto = fotoActual ?: return
        val esquinasElegidas = esquinas
        ejecutar("No se pudo procesar la página") {
            val destino = File(Carpetas.sesion(contexto), "pagina_${System.currentTimeMillis()}.jpg")
            val pagina = withContext(Dispatchers.Default) {
                ProcesadorPagina.procesar(foto, esquinasElegidas, destino)
            }
            paginas.add(pagina)
            foto.delete()
            fotoActual = null
            alTerminar()
        }
    }

    suspend fun cargarParaVer(pagina: PaginaEscaneada): Bitmap =
        withContext(Dispatchers.IO) { CargadorImagen.cargarReducida(pagina.archivo, LADO_VISOR) }

    fun borrarPagina(pagina: PaginaEscaneada) {
        paginas.remove(pagina)
        pagina.archivo.delete()
    }

    fun crearPdf(alTerminar: () -> Unit) {
        if (paginas.isEmpty()) return
        val paginasDelPdf = paginas.toList()
        val nombre = nombreDocumento
        ejecutar("No se pudo crear el PDF") {
            val archivo = withContext(Dispatchers.IO) {
                repositorio.archivoParaNuevoPdf(nombre).also { GeneradorPdf.crear(contexto, paginasDelPdf, it) }
            }
            descartarDocumento()
            pdfRecienCreado = archivo
            alTerminar()
        }
    }

    fun mensajeErrorMostrado() {
        mensajeError = null
    }

    private fun ejecutar(mensajeSiFalla: String, trabajo: suspend () -> Unit) {
        viewModelScope.launch {
            ocupado = true
            try {
                trabajo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mensajeError = "$mensajeSiFalla: ${e.message}"
            } catch (e: OutOfMemoryError) {
                mensajeError = "$mensajeSiFalla: el celular se quedó sin memoria"
            } finally {
                ocupado = false
            }
        }
    }

    private companion object {
        const val LADO_VISTA_PREVIA = 1600
        const val LADO_VISOR = 1500

        fun nombrePorDefecto(): String =
            "Documento " + SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault()).format(Date())
    }
}
