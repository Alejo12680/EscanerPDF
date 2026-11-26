package com.fabio.escanerpdf.procesamiento

import android.content.Context
import com.fabio.escanerpdf.datos.PaginaEscaneada
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.io.IOException

object GeneradorPdf {

    private const val ANCHO_PAGINA = 595.28f

    private const val MEMORIA_MAXIMA = 16L * 1024 * 1024

    @Throws(IOException::class)
    fun crear(contexto: Context, paginas: List<PaginaEscaneada>, destino: File) {
        require(paginas.isNotEmpty()) { "El documento no tiene páginas" }
        PDFBoxResourceLoader.init(contexto)
        val memoria = MemoryUsageSetting.setupMixed(MEMORIA_MAXIMA).setTempDir(contexto.cacheDir)
        try {
            PDDocument(memoria).use { documento ->
                for (pagina in paginas) {
                    agregarPagina(documento, pagina)
                }
                documento.save(destino)
            }
        } catch (e: Exception) {
            destino.delete()
            throw e
        }
    }

    private fun agregarPagina(documento: PDDocument, pagina: PaginaEscaneada) {
        val altoPagina = ANCHO_PAGINA * pagina.alto / pagina.ancho
        val pdPagina = PDPage(PDRectangle(ANCHO_PAGINA, altoPagina))
        documento.addPage(pdPagina)

        val espacioDeColor = if (pagina.enEscalaDeGrises) PDDeviceGray.INSTANCE else PDDeviceRGB.INSTANCE
        val imagen = pagina.archivo.inputStream().use { entrada ->
            PDImageXObject(documento, entrada, COSName.DCT_DECODE, pagina.ancho, pagina.alto, 8, espacioDeColor)
        }

        PDPageContentStream(documento, pdPagina).use { contenido ->
            contenido.drawImage(imagen, 0f, 0f, ANCHO_PAGINA, altoPagina)
        }
    }
}
