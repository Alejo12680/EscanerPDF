package com.fabio.escanerpdf.ui.camara

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fabio.escanerpdf.R
import com.fabio.escanerpdf.datos.Carpetas
import java.io.File

@Composable
fun PantallaCamara(
    cantidadPaginas: Int,
    alCapturar: (File) -> Unit,
    alIrARevision: () -> Unit,
    alVolver: () -> Unit,
) {
    val contexto = LocalContext.current
    var tienePermiso by remember { mutableStateOf(tienePermisoCamara(contexto)) }
    var permisoRechazado by rememberSaveable { mutableStateOf(false) }

    val lanzadorPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        tienePermiso = concedido
        permisoRechazado = !concedido
    }

    LaunchedEffect(Unit) {
        if (!tienePermiso) lanzadorPermiso.launch(Manifest.permission.CAMERA)
    }
    LifecycleResumeEffect(Unit) {
        tienePermiso = tienePermisoCamara(contexto)
        onPauseOrDispose { }
    }

    BackHandler { alVolver() }

    if (tienePermiso) {
        VistaCamara(cantidadPaginas, alCapturar, alIrARevision, alVolver)
    } else if (permisoRechazado) {
        SinPermisoCamara(
            alPedirDeNuevo = { lanzadorPermiso.launch(Manifest.permission.CAMERA) },
            alVolver = alVolver,
        )
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black))
    }
}

@Composable
private fun VistaCamara(
    cantidadPaginas: Int,
    alCapturar: (File) -> Unit,
    alIrARevision: () -> Unit,
    alVolver: () -> Unit,
) {
    val contexto = LocalContext.current
    val cicloDeVida = LocalLifecycleOwner.current
    val controlador = remember { crearControladorCamara(contexto) }
    var capturando by remember { mutableStateOf(false) }

    DisposableEffect(cicloDeVida) {
        controlador.bindToLifecycle(cicloDeVida)
        onDispose { controlador.unbind() }
    }

    val actividad = LocalActivity.current
    val vista = LocalView.current
    DisposableEffect(actividad) {
        val barras = actividad?.let { WindowCompat.getInsetsController(it.window, vista) }
        val eranOscuros = barras?.isAppearanceLightStatusBars ?: false
        barras?.isAppearanceLightStatusBars = false
        onDispose { barras?.isAppearanceLightStatusBars = eranOscuros }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    controller = controlador
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = alVolver) {
                Icon(painterResource(R.drawable.ic_cerrar), contentDescription = "Cerrar", tint = Color.White)
            }
            Text("Página ${cantidadPaginas + 1}", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            BotonDisparador(
                habilitado = !capturando,
                modifier = Modifier.align(Alignment.Center),
                alTocar = {
                    capturando = true
                    tomarFoto(
                        contexto = contexto,
                        controlador = controlador,
                        alGuardar = { foto ->
                            capturando = false
                            alCapturar(foto)
                        },
                        alFallar = { mensaje ->
                            capturando = false
                            Toast.makeText(contexto, "No se pudo tomar la foto: $mensaje", Toast.LENGTH_LONG).show()
                        },
                    )
                },
            )
            if (cantidadPaginas > 0) {
                FilledTonalButton(onClick = alIrARevision, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Listo ($cantidadPaginas)")
                }
            }
        }

        if (capturando) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}

@Composable
private fun BotonDisparador(habilitado: Boolean, alTocar: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (habilitado) Color.White else Color.Gray)
            .clickable(enabled = habilitado, onClickLabel = "Tomar foto", role = Role.Button, onClick = alTocar),
    )
}

@Composable
private fun SinPermisoCamara(alPedirDeNuevo: () -> Unit, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Se necesita la cámara", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Para escanear documentos, EscanerPDF necesita permiso para usar la cámara. " +
                "Las fotos nunca salen de tu celular.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = alPedirDeNuevo) { Text("Dar permiso") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexto.packageName, null))
            contexto.startActivity(intent)
        }) { Text("Abrir ajustes") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = alVolver) { Text("Volver") }
    }
}

private fun tienePermisoCamara(contexto: Context): Boolean =
    ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun crearControladorCamara(contexto: Context): LifecycleCameraController =
    LifecycleCameraController(contexto).apply {
        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        isAutoRotationEnabled = false
        setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        imageCaptureResolutionSelector = ResolutionSelector.Builder()
            .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .build()
        previewResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }

private fun tomarFoto(
    contexto: Context,
    controlador: LifecycleCameraController,
    alGuardar: (File) -> Unit,
    alFallar: (String) -> Unit,
) {
    val archivo = File(Carpetas.capturas(contexto), "foto_${System.currentTimeMillis()}.jpg")
    val opciones = ImageCapture.OutputFileOptions.Builder(archivo).build()
    controlador.takePicture(
        opciones,
        ContextCompat.getMainExecutor(contexto),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(resultado: ImageCapture.OutputFileResults) = alGuardar(archivo)
            override fun onError(error: ImageCaptureException) = alFallar(error.message ?: "error desconocido")
        },
    )
}
