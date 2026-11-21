# EscanerPDF

App Android para estudiantes que fotografía hojas y las convierte en PDF. Funciona 100% sin internet.

- **Lenguaje:** Kotlin + Jetpack Compose
- **Cámara:** CameraX (~12 MP)
- **Procesamiento:** OpenCV 4.12.0 (bordes, enderezado, filtros)
- **PDF:** PdfBox-Android
- **Arquitectura:** MVVM
- **Celulares objetivo:** Android 12 a 16 (Samsung, Motorola, Oppo), en vertical. `minSdk` 24.
- **Distribución:** APK (no Play Store)

---

## Índice

1. [Qué hace la app](#1-qué-hace-la-app)
2. [Decisiones técnicas y por qué](#2-decisiones-técnicas-y-por-qué)
3. [Estructura del proyecto](#3-estructura-del-proyecto)
4. [El viaje de una foto (recorrido por el código)](#4-el-viaje-de-una-foto-recorrido-por-el-código)
5. [Archivo por archivo](#5-archivo-por-archivo)
6. [Kotlin para quien viene de Java](#6-kotlin-para-quien-viene-de-java)
7. [Conceptos de Jetpack Compose usados](#7-conceptos-de-jetpack-compose-usados)
8. [Compilar, instalar y probar](#8-compilar-instalar-y-probar)
9. [Limitaciones conocidas](#9-limitaciones-conocidas)
10. [Pendientes e ideas](#10-pendientes-e-ideas)

---

## 1. Qué hace la app

```
Inicio ──Escanear──► Cámara ──foto──► Recorte ──Otra página──► Cámara ...
                                         │
                                         └──Listo──► Revisión ──Crear PDF──► Inicio
                                                        │                     (PDF resaltado)
                                                        └──Agregar──► Cámara
```

1. **Inicio:** lista de PDFs creados. Cada uno tiene **Abrir**, **Compartir**, **Descargas** (copiar a la carpeta pública) y **Borrar**.
2. **Cámara:** vista previa en 4:3 y botón disparador. Muestra qué número de página se va a tomar.
3. **Recorte:** la app propone las 4 esquinas de la hoja; el usuario las arrastra si hace falta (con lupa) y elige el filtro **Color**, **Gris** o **B/N**.
4. **Revisión:** miniaturas de las páginas, borrar páginas, agregar más y nombre del documento.
5. **Crear PDF:** se arma el PDF, se guarda en la carpeta privada y se vuelve a Inicio con el PDF nuevo resaltado.

---

## 2. Decisiones técnicas y por qué

| Decisión | Por qué |
|---|---|
| **Sin permiso de INTERNET** | Privacidad: las fotos nunca salen del celular. El manifest además *elimina* `INTERNET` y `ACCESS_NETWORK_STATE` por si alguna librería los agrega (CameraX trae `media3-common`, que agrega `ACCESS_NETWORK_STATE`). |
| **Sin ML Kit ni Google Play Services** | Funcionan con descargas de modelos y servicios de Google; queremos que todo esté dentro del APK. |
| **CameraX con ~12 MP** | Suficiente nitidez para texto sin archivos gigantes. Se pide 4000x3000; si el celular no la tiene, usa la más cercana por encima (p. ej. 4032x3024) y si no hay, por debajo. |
| **OpenCV detecta en pequeño, endereza en grande** | Detectar bordes en 600 px es rápido y basta (el usuario puede corregir). El enderezado se hace sobre la foto original completa para no perder nitidez. |
| **OpenCV 4.12.0** | Desde la 4.12.0 sus librerías nativas están alineadas a páginas de 16 KB. Con la 4.10.0, un celular con Android 15 y páginas de 16 KB no podría cargar OpenCV, y Android 16 la correría en "modo compatibilidad". |
| **PdfBox-Android con JPEG calidad 95 incrustado tal cual** | El JPEG se mete en el PDF sin descomprimir ni recomprimir (filtro `DCTDecode`): no se pierde calidad y el PDF pesa casi lo mismo que las fotos. Ideal para WhatsApp. |
| **El PDF se guarda primero en la carpeta privada** | No hace falta ningún permiso. Solo cuando el usuario toca "Descargas" se copia a `Download/EscanerPDF`. |
| **FileProvider para Abrir y Compartir** | Android prohíbe pasar rutas `file://` a otras apps. El FileProvider entrega una URI `content://` con permiso temporal de lectura. |
| **MVVM** | Las pantallas (Compose) solo dibujan; el estado vive en los ViewModel; el trabajo pesado está en `procesamiento/` y los archivos en `datos/`. |
| **Íconos como Vector Assets** | Un XML pequeño por ícono, en vez de la librería `material-icons-extended`, que pesa varios MB. |
| **Solo librerías ARM (`abiFilters`)** | Los celulares reales son ARM. Quitar las versiones x86 de OpenCV reduce mucho el APK. |
| **`useLegacyPackaging = true`** | Comprime las `.so` dentro del APK: archivo más chico para pasarlo por WhatsApp o Drive (se descomprimen al instalar). |
| **`compileSdk 37`** | `androidx.core` 1.19 y `material3` 1.5 exigen compilar contra Android 37. Se usa 37.0 (no 37.1) porque es lo máximo que soporta AGP 9.1.1. |
| **`targetSdk 36`** | Ver [sección 9](#targetsdk-36). Coincide con Android 16, el más nuevo de los celulares objetivo. |
| **Orientación vertical fija** | Simplifica la interfaz. CameraX igual detecta si el celular está acostado y guarda la foto derecha (rotación automática, activada por defecto en `CameraController`). |
| **`largeHeap`** | Margen extra de memoria para fotos de 12 MP en celulares básicos. |

---

## 3. Estructura del proyecto

```
app/src/main/
├── AndroidManifest.xml
├── res/
│   ├── drawable/ic_*.xml              Íconos vectoriales (ver tabla en 5.8)
│   └── xml/file_paths.xml             Carpetas que el FileProvider puede compartir
└── java/com/fabio/escanerpdf/
    ├── MainActivity.kt                Única Activity: carga el tema y la navegación
    ├── datos/
    │   ├── Modelos.kt                 Punto, Filtro, PaginaEscaneada, DocumentoPdf
    │   ├── Carpetas.kt                Carpetas privadas de la app
    │   └── RepositorioDocumentos.kt   Listar, nombrar, borrar, compartir y copiar PDFs
    ├── procesamiento/
    │   ├── OpenCv.kt                  Carga la librería nativa una sola vez
    │   ├── CargadorImagen.kt          Lee fotos reducidas y derechas (EXIF)
    │   ├── DetectorBordes.kt          Encuentra las 4 esquinas de la hoja
    │   ├── Filtros.kt                 Color, Gris, Blanco y negro
    │   ├── ProcesadorPagina.kt        Endereza en resolución completa y guarda JPEG 95
    │   └── GeneradorPdf.kt            Arma el PDF con PdfBox
    ├── viewmodel/
    │   ├── EscanerViewModel.kt        Estado del documento que se está escaneando
    │   └── InicioViewModel.kt         Lista de PDFs y sus acciones
    └── ui/
        ├── navegacion/NavegacionApp.kt   Rutas y pila de pantallas
        ├── inicio/PantallaInicio.kt      Lista de PDFs
        ├── inicio/AccionesPdf.kt         Intents de Abrir y Compartir
        ├── camara/PantallaCamara.kt      Permiso, vista previa y captura
        ├── recorte/PantallaRecorte.kt    Filtros y botones
        ├── recorte/EditorEsquinas.kt     Foto con 4 esquinas arrastrables y lupa
        ├── revision/PantallaRevision.kt  Miniaturas, nombre y Crear PDF
        └── theme/                        Colores y tipografía (de la plantilla)

app/src/test/java/com/fabio/escanerpdf/LogicaPuraTest.kt   Pruebas que corren en la PC
```

**Regla de dependencias:** `ui` → `viewmodel` → `procesamiento` / `datos`. Nunca al revés: ni `procesamiento` ni `datos` saben que existe Compose.

---

## 4. El viaje de una foto (recorrido por el código)

Seguir este recorrido con el código abierto es la mejor forma de entender la app.

1. **Inicio → Cámara.** En `PantallaInicio` se toca "Escanear". `NavegacionApp` llama a `escaner.nuevoDocumento()` (vacía la sesión anterior) y navega a `camara`.
2. **Permiso.** `PantallaCamara` revisa el permiso de cámara; si falta, lo pide con `rememberLauncherForActivityResult`.
3. **Captura.** `tomarFoto()` le pide a CameraX que guarde un JPEG en `cache/capturas/foto_<hora>.jpg`. La cámara **no gira los píxeles**: guarda la foto "acostada" y anota en el EXIF cuántos grados hay que girarla.
4. **Cámara → Recorte.** `alCapturar(foto)` llama a `escaner.cargarFoto(foto)` y navega a `recorte`.
5. **Vista previa y detección** (`EscanerViewModel.cargarFoto`, en un hilo de fondo):
   - `CargadorImagen.cargarReducida(foto, 1600)` → Bitmap pequeño (lado mayor entre 1600 y 3200 px) ya girado según el EXIF.
   - `DetectorBordes.detectar(bitmap)` → 4 esquinas **normalizadas** (0..1).
   - `Filtros.previsualizar(bitmap, filtro)` → la imagen con el filtro elegido, para mostrarla.
6. **Ajuste manual.** `EditorEsquinas` dibuja la foto y las esquinas. Cada arrastre llama a `escaner.moverEsquina(indice, punto)`.
7. **Listo u Otra página** → `escaner.aceptarPagina()` → `ProcesadorPagina.procesar(foto, esquinas, filtro, destino)`:
   - `Imgcodecs.imread` lee la foto **original completa** (12 MP) sin aplicar EXIF, y `girar()` aplica la **misma** rotación EXIF que usó la vista previa. Así las esquinas normalizadas coinciden exactamente.
   - Las esquinas se multiplican por el tamaño real → `warpPerspective` "aplana" la hoja.
   - Se aplica el filtro y se guarda `files/sesion/pagina_<hora>.jpg` con calidad 95.
   - Se crea una miniatura de 400 px para Revisión y se borra la foto original de `cache/capturas`.
8. **Revisión.** `PantallaRevision` muestra `escaner.paginas`. Se pueden borrar páginas o cambiar el nombre.
9. **Crear PDF** → `escaner.crearPdf()`:
   - `RepositorioDocumentos.archivoParaNuevoPdf(nombre)` limpia el nombre y evita repetidos (`Nombre (2).pdf`).
   - `GeneradorPdf.crear()` mete cada JPEG en una página.
   - Se vacía la sesión y se guarda `pdfRecienCreado` para resaltarlo.
10. **De vuelta en Inicio.** `PantallaInicio` recarga la lista desde `files/pdfs` y resalta el PDF nuevo.

### ¿Por qué coordenadas normalizadas?

La vista previa mide ~1600 px y la original ~4000 px. Si la esquina se guardara en píxeles de la vista previa, habría que convertirla. Guardándola como fracción (por ejemplo `x = 0.25` significa "a un cuarto del ancho"), sirve para cualquier tamaño: en pantalla se multiplica por el ancho dibujado, y para enderezar, por el ancho real de la foto.

---

## 5. Archivo por archivo

### 5.1 Configuración

**`gradle/libs.versions.toml`** — catálogo de versiones: cada librería se declara una vez con su versión y en `build.gradle.kts` se usa como `libs.nombre`. Para actualizar una librería se cambia solo aquí.

**`app/build.gradle.kts`**

| Bloque | Qué hace |
|---|---|
| `compileSdk { version = release(37) }` | Versión de Android contra la que se compila (APIs disponibles). |
| `minSdk = 24` | Android mínimo en el que se instala. |
| `targetSdk = 36` | Versión cuyos comportamientos acepta la app (ver sección 9). |
| `ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }` | Va **dentro de `defaultConfig`**. Solo empaqueta las `.so` de procesadores ARM. |
| `packaging { jniLibs { useLegacyPackaging = true } }` | Comprime las `.so` dentro del APK. |
| `dependencies` | Compose, Navigation, Lifecycle/ViewModel, CameraX, ExifInterface, OpenCV y PdfBox. |

### 5.2 Manifest y FileProvider

**`AndroidManifest.xml`**
- `uses-feature camera required=true`: la app necesita cámara.
- `CAMERA`: se pide al entrar a la cámara.
- `WRITE_EXTERNAL_STORAGE maxSdkVersion=28`: solo existe para Android 9 o menos. En Android 10+ se usa MediaStore, que no requiere permiso. Con celulares Android 12+ ese camino nunca se ejecuta, pero no estorba.
- `INTERNET` y `ACCESS_NETWORK_STATE` con `tools:node="remove"`: borran esos permisos si una librería los trae. Gradle avisa *"INTERNET was tagged ... but no other declaration present"*: es buena señal, significa que ninguna librería pidió internet.
- `screenOrientation="portrait"`: vertical fija. `tools:ignore` silencia el aviso de lint sobre orientación bloqueada.
- `largeHeap="true"`: más memoria para fotos grandes.
- `<provider FileProvider>`: autoridad `${applicationId}.fileprovider`, apunta a `@xml/file_paths`.

**`res/xml/file_paths.xml`** — el FileProvider solo puede compartir `files/pdfs/`. Las fotos y páginas en proceso nunca quedan expuestas.

### 5.3 `datos/`

**`Modelos.kt`**
- `Punto(x, y)`: coordenadas normalizadas 0..1.
- `Filtro`: `COLOR`, `GRIS`, `BLANCO_NEGRO`, cada uno con el texto que se muestra (`etiqueta`).
- `PaginaEscaneada`: archivo JPEG + `ancho`, `alto`, `enEscalaDeGrises` (el PDF necesita esos datos) + `miniatura`.
- `DocumentoPdf`: archivo + nombre, fecha y tamaño **leídos una vez** al listar (si se leyeran del disco en cada redibujo de Compose, la lista sería lenta).

**`Carpetas.kt`** — rutas privadas (no necesitan permisos ni las ven otras apps):

| Carpeta | Contenido |
|---|---|
| `cache/capturas` | Fotos recién tomadas; se borran al procesarlas. |
| `files/sesion` | Páginas ya procesadas del documento en curso. |
| `files/pdfs` | PDFs terminados (la única carpeta compartible). |

**`RepositorioDocumentos.kt`**
- `listar()`: PDFs de `files/pdfs`, del más nuevo al más viejo.
- `archivoParaNuevoPdf(nombre)`: usa `limpiarNombre` (cambia `\ / : * ? " < > |` por `_`, recorta a 80 caracteres, usa "Documento" si queda vacío) y `archivoSinRepetir` (agrega ` (2)`, ` (3)`...).
- `uriCompartible(doc)`: URI `content://` del FileProvider.
- `guardarEnDescargas(doc)`:
  - **Android 10+** (`guardarConMediaStore`, marcada con `@RequiresApi(Q)` porque `MediaStore.Downloads` no existe antes): se inserta un registro en MediaStore con `RELATIVE_PATH = Download/EscanerPDF` e `IS_PENDING = 1` (oculto mientras se escribe), se copian los bytes y se pone `IS_PENDING = 0`. Si falla, se borra el registro.
  - **Android 9 o menos** (`guardarConArchivoDirecto`): copia directa a la carpeta pública y `MediaScannerConnection.scanFile` para que aparezca enseguida en las apps de archivos.

### 5.4 `procesamiento/`

**`OpenCv.kt`** — `OpenCVLoader.initLocal()` carga `libopencv_java4.so`. Con `by lazy` se ejecuta una sola vez, la primera vez que se usa, y es seguro entre hilos.

**`CargadorImagen.kt`**
- `rotacionExif(archivo)`: 0, 90, 180 o 270 grados, leídos del EXIF con `ExifInterface`.
- `cargarReducida(archivo, ladoMaximo)`:
  1. Lee solo el tamaño (`inJustDecodeBounds = true`), sin cargar píxeles.
  2. Calcula `inSampleSize` (potencia de 2): con 2 se carga 1 de cada 2 píxeles por lado, o sea 4 veces menos memoria. Una foto de 12 MP completa ocupa ~48 MB como Bitmap.
  3. Gira con una `Matrix` según el EXIF.

**`DetectorBordes.kt`** — busca la hoja en una copia de **600 px**:
1. `Utils.bitmapToMat` → `Mat` RGBA.
2. `resize` a 600 px (`INTER_AREA`, el mejor método para reducir).
3. Gris → `GaussianBlur 5x5` (quita ruido y textura del papel) → `Canny(50, 150)` (bordes) → `dilate 3x3` (une líneas cortadas).
4. `findContours` (todos los contornos).
5. De los **10 más grandes**, `approxPolyDP` con tolerancia del 2% del perímetro. Se acepta el primero que quede con **4 vértices**, sea **convexo** y ocupe **al menos el 15%** de la foto.
6. Si no hay ninguno, `esquinasPorDefecto()` (rectángulo con 5% de margen).
7. `ordenarEsquinas`: ordena por ángulo alrededor del centro (sentido horario) y rota la lista para que empiece en el punto con menor `x + y` (arriba a la izquierda). Así funciona aunque la hoja esté girada (lo prueba `LogicaPuraTest`).

`Mat` vive en **memoria nativa** (C++), fuera del recolector de basura de Java; por eso cada `Mat` se libera con `release()` en un `finally`.

**`Filtros.kt`**
- `aplicar(bgr, filtro)` recibe la imagen en orden **BGR** (el de OpenCV):
  - `COLOR`: devuelve la misma `Mat`.
  - `GRIS`: `cvtColor BGR2GRAY` (1 canal).
  - `BLANCO_NEGRO`: gris + `adaptiveThreshold` gaussiano. Cada píxel se compara con el promedio de su vecindario, no con un valor fijo, así las sombras y la luz desigual no ennegrecen la hoja.
- `tamanoBloque`: el vecindario mide 1/25 del lado menor (mínimo 11, siempre impar), así la vista previa y la página final de 12 MP se ven parecidas. La constante `12.0` es cuánto más oscuro que el promedio debe ser un píxel para quedar negro.
- `previsualizar(bitmap, filtro)`: misma lógica para el Bitmap de la pantalla de recorte.

**`ProcesadorPagina.kt`**
- Todo se hace con `Mat` (memoria nativa) en vez de Bitmap, para no agotar la memoria de Java con 12 MP.
- `imread(..., IMREAD_COLOR or IMREAD_IGNORE_ORIENTATION)` + `girar()`: se gira "a mano" con la misma rotación EXIF de la vista previa (`Core.rotate`).
- `enderezar()`:
  - Esquinas × tamaño real → `Point`.
  - Ancho de la página = el mayor entre lado superior e inferior; alto = el mayor entre izquierdo y derecho.
  - `getPerspectiveTransform` (matriz 3x3) + `warpPerspective` con `INTER_CUBIC` (texto más nítido que la interpolación lineal) y `BORDER_REPLICATE`.
- `imwrite` con `IMWRITE_JPEG_QUALITY = 95`. Una imagen de 1 canal se guarda como JPEG en gris, que pesa menos.
- Devuelve `PaginaEscaneada` con `enEscalaDeGrises = channels() == 1`.

**`GeneradorPdf.kt`**
- Cada página mide el **ancho de un A4** (595,28 puntos; 1 punto = 1/72 de pulgada) y el alto proporcional a la foto: una hoja carta, un recibo o una tarjeta conservan su forma.
- **No se usa `JPEGFactory`**: en PdfBox-Android tiene un `// TODO` y marca todo JPEG como RGB. Las páginas Gris y B/N son JPEG de 1 canal y deben declararse `DeviceGray`. Por eso se crea `PDImageXObject` directamente con `COSName.DCT_DECODE` y el espacio de color correcto.
- `MemoryUsageSetting.setupMixed(16 MB)` con `cacheDir`: hasta 16 MB en memoria; si el PDF crece más, PdfBox usa archivos temporales.
- Si algo falla se borra el PDF a medio escribir.

### 5.5 `viewmodel/`

**`EscanerViewModel.kt`** — estado del documento en curso. Se crea **fuera del NavHost**, así pertenece a la Activity y Cámara, Recorte y Revisión comparten la misma instancia.

| Propiedad | Qué es |
|---|---|
| `paginas` | `mutableStateListOf`: Compose se entera cuando se agrega o quita una página. |
| `nombreDocumento` | Lo edita el campo de texto de Revisión. |
| `vistaPrevia`, `esquinas`, `filtro` | Estado de la pantalla de recorte. El filtro se conserva entre páginas. |
| `ocupado` | `true` mientras corre una tarea; la UI desactiva botones y muestra un indicador de carga. |
| `mensajeError` | Se muestra como Toast desde `NavegacionApp`. |
| `pdfRecienCreado` | Para resaltar el PDF en Inicio. |

- Las propiedades usan `by mutableStateOf(...)` con `private set`: las pantallas las leen, pero solo el ViewModel las cambia.
- `ejecutar(mensaje, trabajo)`: lanza una corrutina en `viewModelScope`, marca `ocupado` y convierte errores en `mensajeError`. Deja pasar `CancellationException`, porque la cancelación no es un error, y atrapa `OutOfMemoryError` con un mensaje claro.
- `cargarFoto` y `mostrarConFiltro` revisan al terminar si el usuario ya cambió de foto o de filtro; si es así, descartan el resultado viejo.
- `moverEsquina` crea una **lista nueva** (`toMutableList().also { ... }`) en vez de modificar la actual, para que Compose detecte el cambio.

**`InicioViewModel.kt`** — `documentos`, `mensaje` (Snackbar), `refrescar`, `borrar`, `guardarEnDescargas` (atrapa `IOException` y `SecurityException`) y `uriCompartible`.

### 5.6 `ui/`

**`navegacion/NavegacionApp.kt`** — `NavHost` con 4 rutas: `inicio`, `camara`, `recorte` y `revision`. La pila funciona como la de las Activities:

| Acción | Pila resultante |
|---|---|
| Escanear | `inicio, camara` |
| Tomar foto | `inicio, camara, recorte` |
| Repetir / Otra página | `popBackStack()` → `inicio, camara` |
| Listo (`irARevision`) | `navigate(revision) { popUpTo(inicio) }` → `inicio, revision` |
| Agregar desde Revisión | `inicio, revision, camara` |
| Atrás en Cámara con páginas | va a Revisión (para no perderlas) |
| Crear PDF | `navigate(inicio) { popUpTo(inicio) { inclusive = true } }` → un `inicio` nuevo que recarga la lista |
| Descartar en Revisión | `popBackStack(inicio)` |

**`inicio/PantallaInicio.kt`**
- `LaunchedEffect(Unit) { refrescar() }`: recarga la lista cada vez que se entra.
- `LaunchedEffect(mensaje)`: muestra el Snackbar cuando hay mensaje nuevo.
- Permiso de almacenamiento solo si `SDK_INT <= P` (Android 9); se recuerda qué documento se quería guardar (`documentoPorGuardar`) para continuar cuando el usuario responde.
- Diálogo de confirmación antes de borrar.
- `BotonAccion`: ícono + texto, con `semantics { contentDescription }` para TalkBack.

**`inicio/AccionesPdf.kt`**
- `abrirPdf`: `ACTION_VIEW` + `FLAG_GRANT_READ_URI_PERMISSION`. Si no hay visor de PDF, atrapa `ActivityNotFoundException` y avisa.
- `compartirPdf`: `ACTION_SEND` con `EXTRA_STREAM`, `EXTRA_SUBJECT` (asunto en Gmail) y `ClipData` (hace llegar el permiso a la app elegida y muestra la vista previa del archivo en Android 10+), dentro de `Intent.createChooser`.

**`camara/PantallaCamara.kt`**
- Permiso: `LaunchedEffect` lo pide al entrar; `LifecycleResumeEffect` lo revisa otra vez al volver de Ajustes; si fue rechazado, se muestran "Dar permiso", "Abrir ajustes" y "Volver".
- `crearControladorCamara()` configura `LifecycleCameraController`:
  - Cámara trasera, solo `IMAGE_CAPTURE`.
  - `CAPTURE_MODE_MAXIMIZE_QUALITY`.
  - `ResolutionStrategy(Size(4000, 3000), FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)` + relación 4:3.
  - Enfoque automático continuo y tocar para enfocar vienen activados por defecto.
- `DisposableEffect` hace `bindToLifecycle` al entrar y `unbind` al salir. CameraX apaga la cámara sola si la app pasa a segundo plano.
- `PreviewView` (vista clásica) dentro de `AndroidView`, con `FIT_CENTER` para ver la foto completa sin recortes.
- Otro `DisposableEffect` pone claros los íconos de la barra de estado (fondo negro) y los restaura al salir.
- `tomarFoto()`: `takePicture` con `OutputFileOptions` y un `OnImageSavedCallback`.

**`recorte/PantallaRecorte.kt`**
- Tres estados: imagen cargada (editor), cargando (indicador de carga) y "La foto ya no está disponible" (falló la carga o Android cerró la app en segundo plano).
- `BackHandler` ignora "atrás" mientras se procesa, para no perder la página.
- `FilterChip` por cada `Filtro.entries`.

**`recorte/EditorEsquinas.kt`**
- **Truco del `aspectRatio`:** la caja tiene la misma proporción que la foto, así la imagen la llena exactamente y las coordenadas de la caja coinciden con las de la foto.
- **`rememberUpdatedState`:** `pointerInput(Unit)` se configura una sola vez y sus lambdas recuerdan los valores de ese momento. `rememberUpdatedState` les da siempre el valor más reciente de `esquinas`, `alMoverEsquina` y `habilitado`.
- **Gesto propio en vez de `detectDragGestures`:** `detectDragGestures` espera a que el dedo recorra unos milímetros (touch slop) y avisa desde *ese* punto, no desde donde se tocó; la esquina quedaba siempre un poco atrás del dedo. Con `awaitEachGesture` + `awaitFirstDown` + `drag`:
  1. Al tocar se elige la esquina más cercana (radio de 48 dp).
  2. Se guarda el `desfase` entre el dedo y la esquina.
  3. En cada movimiento: `esquina = posición del dedo + desfase`, limitada a 0..1. Usar la posición absoluta (en vez de sumar desplazamientos) evita errores acumulados.
- **Lupa:** `Modifier.magnifier` (Android 9+) con zoom 2,5x, arriba del dedo (o abajo si el dedo está muy arriba). Con `Offset.Unspecified` se oculta.
- `Canvas` dibuja el cuadrilátero (relleno translúcido + borde) y los 4 círculos; el que se arrastra se agranda.

**`revision/PantallaRevision.kt`**
- `LazyVerticalGrid` de 2 columnas con `TarjetaPagina` (miniatura + botón borrar).
- Confirmación antes de borrar una página y antes de descartar el documento ("atrás" con páginas).
- Capa semitransparente "Creando PDF…" mientras `ocupado`.
- El botón dice "Agregar" porque "Agregar página" no cabe en una línea en celulares angostos.

### 5.7 `MainActivity.kt`

Con Compose no hay un layout XML por pantalla: la Activity llama a `enableEdgeToEdge()` (dibujar detrás de las barras del sistema; cada pantalla deja su margen) y `setContent { EscanerPDFTheme { NavegacionApp() } }`.

### 5.8 Íconos (`res/drawable`)

Trazados copiados de Material Icons (los mismos que genera *Vector Asset* en Android Studio):

| Archivo | Material Icon |
|---|---|
| `ic_camara` | photo_camera |
| `ic_compartir` | share |
| `ic_borrar` | delete |
| `ic_descargar` | file_download |
| `ic_abrir` | open_in_new |
| `ic_agregar` | add |
| `ic_check` | check |
| `ic_atras` | arrow_back |
| `ic_cerrar` | close |
| `ic_repetir` | refresh |
| `ic_pdf` | picture_as_pdf |

### 5.9 Pruebas

**`LogicaPuraTest.kt`** — corre en la PC con JUnit (no necesita celular):
- `ordenarEsquinas` con puntos desordenados y con una hoja girada 30°.
- `limpiarNombre` con caracteres prohibidos y con nombre vacío.

---

## 6. Kotlin para quien viene de Java

| Concepto | Kotlin | En Java sería | Dónde verlo |
|---|---|---|---|
| Inmutable / mutable | `val x = 1` / `var y = 2` | `final int x = 1;` / `int y = 2;` | Todo el proyecto |
| Clase de datos | `data class Punto(val x: Float, val y: Float)` | Clase con campos, constructor, `equals`, `hashCode`, `toString` (o un `record`) | `Modelos.kt` |
| Singleton | `object Carpetas { ... }` | Clase con métodos `static` o patrón singleton | `Carpetas.kt`, `procesamiento/*` |
| Miembros estáticos | `companion object { const val MIME_PDF = "..." }` | `static final String MIME_PDF` | `RepositorioDocumentos.kt` |
| Constructor primario | `class Repo(private val contexto: Context)` | Campo + constructor + asignación (3 líneas) | `RepositorioDocumentos.kt` |
| Tipos nulos | `File?` puede ser null; `File` nunca | Cualquier objeto puede ser null | `fotoActual: File?` |
| Llamada segura | `filtrada?.release()` | `if (filtrada != null) filtrada.release();` | `Filtros.kt` |
| Elvis | `val o = vistaPreviaOriginal ?: return` | `if (o == null) return;` | `EscanerViewModel.kt` |
| Bloque si no es null | `documentoPorBorrar?.let { doc -> ... }` | `if (d != null) { ... }` | `PantallaInicio.kt` |
| `when` | `when (filtro) { COLOR -> ...; GRIS -> ... }` | `switch`, pero devuelve valor y obliga a cubrir todos los casos | `Filtros.kt` |
| `if`/`try` como expresión | `mensaje = try { ... } catch (...) { "error" }` | Variable + asignar en cada rama | `InicioViewModel.kt` |
| Plantillas de texto | `"Página ${indice + 1}"` | `"Página " + (indice + 1)` | UI |
| Lambdas y tipos función | `alCapturar: (File) -> Unit` | Interfaz funcional (`Consumer<File>`) | Parámetros de las pantallas |
| Lambda al final | `ejecutar("msg") { ... }` | `ejecutar("msg", () -> { ... })` | `EscanerViewModel.kt` |
| Referencia a función | `escaner::moverEsquina` | `escaner::moverEsquina` (igual que Java 8) | `PantallaRecorte.kt` |
| Desestructuración | `val (supIzq, supDer, infDer, infIzq) = lista` | 4 líneas con `lista.get(i)` | `ProcesadorPagina.kt` |
| Pares | `imagen to esquinas` → `Pair` | Clase propia o `Map.Entry` | `EscanerViewModel.cargarFoto` |
| Funciones de alcance | `apply { }`, `also { }`, `let { }` | Varias líneas repitiendo la variable | `Carpetas.kt`, `CargadorImagen.kt` |
| `use` | `stream.use { ... }` | try-with-resources | `RepositorioDocumentos.kt`, `GeneradorPdf.kt` |
| Propiedades delegadas | `val cargado by lazy { ... }`, `var x by mutableStateOf(...)` | Getter con inicialización perezosa / observable | `OpenCv.kt`, ViewModels |
| `private set` | `var ocupado ... private set` | Getter público sin setter público | ViewModels |
| Clase anónima | `object : ImageCapture.OnImageSavedCallback { ... }` | `new OnImageSavedCallback() { ... }` | `PantallaCamara.kt` |
| Spread de vararg | `MatOfPoint2f(*arreglo)` | Pasar el arreglo directamente | `DetectorBordes.kt` |
| Igualdad | `==` compara contenido, `===` compara referencia | `equals()` / `==` | `vistaPreviaOriginal === original` |
| Sin conversión numérica implícita | `Float * Double` da `Double`; hay que convertir a mano | `float` se convierte a `double` solo | `DetectorBordes.kt` (`areaMinima: Double`) |
| Valores del enum | `Filtro.entries` | `Filtro.values()` | `PantallaRecorte.kt` |
| Parámetro sin usar | `{ _, pagina -> ... }` | Darle un nombre cualquiera | `PantallaRevision.kt` |
| Retorno con etiqueta | `return@ejecutar` | Sale solo de la lambda, no de la función | `EscanerViewModel.kt` |
| Funciones de nivel superior | `fun abrirPdf(...)` fuera de una clase | Método `static` en una clase utilitaria | `AccionesPdf.kt` |
| `internal` | Visible solo dentro del módulo | Más amplio que package-private | `OpenCv.kt` |

### Corrutinas (tareas asíncronas)

| Pieza | Qué hace |
|---|---|
| `suspend fun` | Función que puede pausarse sin bloquear el hilo. Solo se llama desde otra corrutina. |
| `viewModelScope.launch { }` | Lanza una corrutina que se cancela sola si el ViewModel se destruye. |
| `withContext(Dispatchers.Default) { }` | Ejecuta el bloque en un hilo para cálculos pesados (OpenCV) y vuelve con el resultado. |
| `Dispatchers.IO` | Hilos para leer y escribir archivos. |
| `CancellationException` | Así se cancela una corrutina. Nunca hay que "tragársela" en un `catch (e: Exception)`. |

En Java esto sería un `ExecutorService` + `Handler(Looper.getMainLooper())` para volver al hilo principal.

---

## 7. Conceptos de Jetpack Compose usados

| Concepto | Para qué | Dónde |
|---|---|---|
| `@Composable` | Función que describe cómo se ve la pantalla según el estado. Cuando el estado cambia, Compose la vuelve a ejecutar (recomposición). | Todas las pantallas |
| `mutableStateOf` / `mutableStateListOf` | Estado observable: al cambiar, se redibuja lo que lo lee. | ViewModels, pantallas |
| `remember { }` | Conserva un valor entre recomposiciones. | `estadoSnackbar`, diálogos |
| `rememberSaveable` | Como `remember`, pero sobrevive a que Android recree la Activity. | `permisoRechazado` |
| `LaunchedEffect(clave)` | Corrutina que corre al entrar a la pantalla o cuando cambia la clave. | Refrescar lista, Snackbar, pedir permiso |
| `DisposableEffect` | Código al entrar y limpieza (`onDispose`) al salir. | Encender y apagar la cámara, barra de estado |
| `LifecycleResumeEffect` | Código cada vez que la pantalla vuelve a primer plano. | Revisar el permiso al volver de Ajustes |
| `rememberUpdatedState` | Valor siempre actualizado dentro de lambdas de larga vida. | `EditorEsquinas` |
| `rememberLauncherForActivityResult` | Pedir permisos (reemplaza `onRequestPermissionsResult`). | Cámara, Descargas |
| `BackHandler` | Interceptar el botón "atrás". | Cámara, Recorte, Revisión |
| `Modifier` | Cadena de tamaño, márgenes, clics, etc. **El orden importa** (`padding` antes o después de `background` da resultados distintos). | Todo |
| `Scaffold` | Esqueleto con barra superior, botón flotante, barra inferior y Snackbar. | Inicio, Recorte, Revisión |
| `LazyColumn` / `LazyVerticalGrid` | Listas que solo dibujan lo visible (como `RecyclerView`). | Inicio, Revisión |
| `AndroidView` | Insertar una vista clásica (`PreviewView`) en Compose. | Cámara |
| `Canvas` + `pointerInput` | Dibujo libre y gestos táctiles. | `EditorEsquinas` |
| `viewModel()` | Obtiene el ViewModel del dueño actual (Activity o destino de navegación). | `NavegacionApp`, `PantallaInicio` |

---

## 8. Compilar, instalar y probar

Desde Android Studio basta con **Run**. Desde la terminal (PowerShell o Git Bash) hay que usar el Java que trae Android Studio:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"   # Git Bash
./gradlew assembleDebug        # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # pruebas de LogicaPuraTest
./gradlew lintDebug            # reporte en app/build/reports/lint-results-debug.html
```

En PowerShell: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` y luego `.\gradlew assembleDebug`.

Si el APK sale más grande de lo normal (~44 MB), conviene `./gradlew clean assembleDebug`: el empaquetado incremental puede dejar espacio libre al reemplazar librerías.

### Probar en un celular (por ejemplo Motorola)

1. **Ajustes → Acerca del teléfono →** tocar 7 veces **Número de compilación** (activa las Opciones de desarrollador).
2. **Ajustes → Sistema → Opciones de desarrollador → Depuración por USB:** activar.
3. Conectar por USB y aceptar el diálogo "¿Permitir depuración USB?".
4. Comprobar la conexión y luego instalar y abrir:
   ```bash
   adb devices
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.fabio.escanerpdf/.MainActivity
   ```
5. Errores en vivo: `adb logcat -b crash` o, en Android Studio, la ventana **Logcat** filtrando por `com.fabio.escanerpdf`.

`adb` está en `C:\Users\<usuario>\AppData\Local\Android\Sdk\platform-tools\`.

**Qué revisar en el celular real:**
- La foto sale de ~12 MP: en Logcat buscar `ImageCapture` → `resolution=`.
- Tiempo de "Listo" (enderezar 12 MP) en el celular más básico.
- Detección de bordes con una hoja sobre una mesa oscura.
- Hoja horizontal con el celular acostado: debe salir derecha.
- Compartir por WhatsApp y abrir en el visor de PDF.

---

## 9. Limitaciones conocidas

### targetSdk 36

`targetSdk` le dice a Android "esta app conoce y acepta los cambios de comportamiento hasta esta versión". Con 36 (Android 16), en un celular con Android 17 el sistema aplica modos de compatibilidad para los cambios que trajo la 17. Como los celulares objetivo llegan hasta Android 16, 36 es el valor correcto. Lint avisa *"Not targeting the latest versions"* solo porque ya existe el SDK 37; subirlo exigiría revisar los cambios de Android 17 y probar en un celular con esa versión.

### B/N con zonas negras grandes

El umbral adaptativo compara cada píxel con el promedio de su vecindario (un cuadrado de ~1/25 del lado de la hoja). Con texto, el vecindario es casi todo papel blanco, así que las letras quedan negras. Pero dentro de una zona negra **más grande que ese cuadrado** (una foto, un gráfico relleno, un tablero), el promedio también es negro: el centro "no es más oscuro que lo que lo rodea" y queda blanco. El resultado es que solo se ven los contornos y la zona se ve hueca. **B/N es para texto; para fotos o gráficos conviene Gris.** Ver idea de mejora en la sección 10.

### Otras

- **Detección automática:** necesita que la hoja contraste con el fondo y ocupe al menos el 15% de la foto. Si no, aparece el rectángulo por defecto y se ajusta a mano.
- **La sesión vive en memoria:** si Android cierra la app en segundo plano a mitad de un escaneo, las páginas no terminadas se pierden (Recorte muestra "La foto ya no está disponible").
- **Emulador:** su cámara virtual da solo 1280x960 y OpenCV corre traducido de ARM a x86 (lento). Sirve para probar el flujo, no la calidad.
- **Tamaño del APK (~44 MB):** OpenCV ocupa ~17 MB comprimido (dos arquitecturas). PdfBox trae BouncyCastle (para PDFs cifrados, que no usamos), que suma varios MB.
- **Avisos de lint que quedan, y por qué no importan:**

  | Aviso | Por qué |
  |---|---|
  | Versiones nuevas de librerías | Informativo. |
  | `TrustAllX509TrustManager` | Viene de BouncyCastle, y la app no tiene internet. |
  | `ChromeOsAbiSupport` | No apuntamos a Chromebooks. |
  | Colores sin usar en `colors.xml` | Vienen de la plantilla. |

---

## 10. Pendientes e ideas

- [ ] **Ícono de la app:** Android Studio → clic derecho en `res` → *New → Image Asset*.
- [ ] **Probar en Motorola** (sección 8).
- [ ] **APK firmado para repartir:** el APK *debug* sirve para pruebas. Para distribuir: *Build → Generate Signed App Bundle / APK → APK*, crear un keystore y **guardarlo bien** (sin él no se pueden publicar actualizaciones que se instalen encima).
- [ ] **B/N mejorado:** combinar el umbral adaptativo con un umbral global (píxel negro si el adaptativo lo marca negro **o** si es muy oscuro en términos absolutos). Así se rellenan las zonas negras grandes.
- [ ] Girar una página 90° desde Revisión.
- [ ] Reordenar páginas.
- [ ] Botón de flash o linterna para aulas con poca luz.
- [ ] Release con R8 (`isMinifyEnabled = true`) para reducir el APK (requiere reglas `-keep` para OpenCV y PdfBox).
