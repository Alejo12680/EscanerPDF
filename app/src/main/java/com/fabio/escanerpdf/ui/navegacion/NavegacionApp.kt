package com.fabio.escanerpdf.ui.navegacion

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fabio.escanerpdf.ui.camara.PantallaCamara
import com.fabio.escanerpdf.ui.inicio.PantallaInicio
import com.fabio.escanerpdf.ui.recorte.PantallaRecorte
import com.fabio.escanerpdf.ui.revision.PantallaRevision
import com.fabio.escanerpdf.viewmodel.EscanerViewModel

private object Rutas {
    const val INICIO = "inicio"
    const val CAMARA = "camara"
    const val RECORTE = "recorte"
    const val REVISION = "revision"
}

@Composable
fun NavegacionApp() {
    val navController = rememberNavController()
    val contexto = LocalContext.current

    val escaner: EscanerViewModel = viewModel()

    val error = escaner.mensajeError
    LaunchedEffect(error) {
        if (error != null) {
            Toast.makeText(contexto, error, Toast.LENGTH_LONG).show()
            escaner.mensajeErrorMostrado()
        }
    }

    fun irARevision() {
        navController.navigate(Rutas.REVISION) {
            popUpTo(Rutas.INICIO)
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = Rutas.INICIO) {
        composable(Rutas.INICIO) {
            PantallaInicio(
                pdfResaltado = escaner.pdfRecienCreado,
                alEscanear = {
                    escaner.nuevoDocumento()
                    navController.navigate(Rutas.CAMARA)
                },
            )
        }
        composable(Rutas.CAMARA) {
            PantallaCamara(
                cantidadPaginas = escaner.paginas.size,
                alCapturar = { foto ->
                    escaner.cargarFoto(foto)
                    navController.navigate(Rutas.RECORTE)
                },
                alIrARevision = { irARevision() },
                alVolver = {
                    if (escaner.paginas.isEmpty()) navController.popBackStack() else irARevision()
                },
            )
        }
        composable(Rutas.RECORTE) {
            PantallaRecorte(
                escaner = escaner,
                alRepetir = {
                    escaner.descartarFoto()
                    navController.popBackStack()
                },
                alOtraPagina = { navController.popBackStack() },
                alTerminar = { irARevision() },
            )
        }
        composable(Rutas.REVISION) {
            PantallaRevision(
                escaner = escaner,
                alAgregarPagina = { navController.navigate(Rutas.CAMARA) },
                alPdfCreado = {
                    navController.navigate(Rutas.INICIO) {
                        popUpTo(Rutas.INICIO) { inclusive = true }
                    }
                },
                alDescartar = {
                    escaner.descartarDocumento()
                    navController.popBackStack(Rutas.INICIO, inclusive = false)
                },
            )
        }
    }
}
