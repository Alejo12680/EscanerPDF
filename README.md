# EscanerPDF

App Android para estudiantes que fotografía hojas y las convierte en PDF. Funciona 100% sin internet.

- **Lenguaje:** Kotlin + Jetpack Compose
- **Cámara:** CameraX (~12 MP)
- **Procesamiento:** OpenCV 4.12.0 (bordes, enderezado, blanco y negro)
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

1. **Inicio:** lista de PDFs creados. Cada uno tiene **Abrir**, **Compartir**, **Descargas** (copiar a la carpeta pública) y **Borrar**. Arriba, **Cerrar** cierra la app por completo.
2. **Cámara:** vista previa en 4:3 y botón disparador. Muestra qué número de página se va a tomar.
3. **Recorte:** la app propone las 4 esquinas de la hoja y el usuario las arrastra si hace falta (con lupa). Todas las páginas salen en **blanco y negro**.
4. **Revisión:** miniaturas de las páginas (tocar una la abre en pantalla completa con zoom), borrar páginas, agregar más y nombre del documento.
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
| **Orientación vertical fija, también en la foto** | La app y la foto siempre son verticales, igual que la pantalla. La rotación automática de CameraX está **desactivada**: con el celular acostado sobre la hoja (mirando hacia abajo), el sensor de gravedad no sabe dónde es "arriba" y la foto salía de lado. |
| **Solo blanco y negro** | Es lo que usan los estudiantes para apuntes y fotocopias: texto nítido y archivos livianos (JPEG de 1 canal). Quitar Color y Gris también simplifica la pantalla de recorte. |
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
    │   ├── Modelos.kt                 Punto, PaginaEscaneada, DocumentoPdf
    │   ├── Carpetas.kt                Carpetas privadas de la app
    │   └── RepositorioDocumentos.kt   Listar, nombrar, borrar, compartir y copiar PDFs
    ├── procesamiento/
    │   ├── OpenCv.kt                  Carga la librería nativa una sola vez
    │   ├── CargadorImagen.kt          Lee fotos reducidas y derechas (EXIF)
    │   ├── DetectorBordes.kt          Encuentra las 4 esquinas de la hoja
    │   ├── BlancoYNegro.kt            Convierte la página a blanco y negro
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
        ├── recorte/PantallaRecorte.kt    Botones Repetir, Otra página y Listo
        ├── recorte/EditorEsquinas.kt     Foto con 4 esquinas arrastrables y lupa
        ├── revision/PantallaRevision.kt  Miniaturas, nombre y Crear PDF
        ├── revision/VisorPagina.kt       Página en pantalla completa con zoom
        └── theme/                        Colores y tipografía (de la plantilla)

app/src/test/java/com/fabio/escanerpdf/LogicaPuraTest.kt   Pruebas que corren en la PC
```

**Regla de dependencias:** `ui` → `viewmodel` → `procesamiento` / `datos`. Nunca al revés: ni `procesamiento` ni `datos` saben que existe Compose.

---

## 4. El viaje de una foto (recorrido por el código)

Seguir este recorrido con el código abierto es la mejor forma de entender la app.

1. **Inicio → Cámara.** En `PantallaInicio` se toca "Escanear". `NavegacionApp` llama a `escaner.nuevoDocumento()` (vacía la sesión anterior) y navega a `camara`.
2. **Permiso.** `PantallaCamara` revisa el permiso de cámara; si falta, lo pide con `rememberLauncherForActivityResult`.
3. **Captura.** `tomarFoto()` le pide a CameraX que guarde un JPEG en `cache/capturas/foto_<hora>.jpg`, siempre en vertical. Según el celular, el giro se aplica de dos formas: algunos guardan la foto "acostada" y anotan en el EXIF cuántos grados hay que girarla; otros (como el Moto g22) ya giran los píxeles y dejan el EXIF en 0. La app funciona con ambos porque siempre lee el EXIF.
4. **Cámara → Recorte.** `alCapturar(foto)` llama a `escaner.cargarFoto(foto)` y navega a `recorte`.
5. **Vista previa y detección** (`EscanerViewModel.cargarFoto`, en un hilo de fondo):
   - `CargadorImagen.cargarReducida(foto, 1600)` → Bitmap pequeño (lado mayor entre 1600 y 3200 px) ya girado según el EXIF.
   - `DetectorBordes.detectar(bitmap)` → 4 esquinas **normalizadas** (0..1).
   - La pantalla de recorte muestra la foto original (en color), que es más fácil para ubicar los bordes.
6. **Ajuste manual.** `EditorEsquinas` dibuja la foto y las esquinas. Cada arrastre llama a `escaner.moverEsquina(indice, punto)`.
7. **Listo u Otra página** → `escaner.aceptarPagina()` → `ProcesadorPagina.procesar(foto, esquinas, destino)`:
   - `Imgcodecs.imread` lee la foto **original completa** (12 MP) directamente **en gris** y sin aplicar EXIF, y `girar()` aplica la **misma** rotación EXIF que usó la vista previa. Así las esquinas normalizadas coinciden exactamente.
   - Las esquinas se multiplican por el tamaño real → `warpPerspective` "aplana" la hoja.
   - `BlancoYNegro.aplicar()` y se guarda `files/sesion/pagina_<hora>.jpg` con calidad 95.
   - Se crea una miniatura de 400 px para Revisión y se borra la foto original de `cache/capturas`.
8. **Revisión.** `PantallaRevision` muestra `escaner.paginas`. Se pueden borrar páginas, cambiar el nombre o tocar una página para revisarla con zoom (`VisorPagina`).
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
- `PaginaEscaneada`: archivo JPEG + `ancho` y `alto` (el PDF los necesita para declarar la imagen) + `miniatura`.
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

**`DetectorBordes.kt`** — busca la hoja en una copia de **600 px** (`Utils.bitmapToMat` → `Mat` RGBA → `resize` con `INTER_AREA`, el mejor método para reducir). Prueba dos métodos, en orden:

**Método 1, `buscarPorContornos`** (el original; funciona cuando los cuatro bordes se ven completos):
1. Gris → `GaussianBlur 5x5` (quita ruido y textura del papel) → `Canny(50, 150)` (bordes) → `dilate 3x3` (une líneas cortadas).
2. `findContours` (todos los contornos).
3. De los **10 más grandes**, `approxPolyDP` con tolerancia del 2% del perímetro. Se acepta el primero que quede con **4 vértices**, sea **convexo** y ocupe **al menos el 15%** de la foto.

**Método 2, `buscarPorRegion`** (se agregó después de probar con un OPPO A60: papel reciclado sobre una tela rosada y una hoja que se salía de la foto). El método 1 falla cuando un borde casi no se ve o cuando la hoja toca el marco de la foto, porque el contorno queda abierto.
1. `calcularBordes`: `Canny(20, 60)` (más sensible) sobre el gris **y** sobre el "croma", que mide qué tan lejos del gris neutro está cada color (canales a y b del espacio Lab). Así aparece el borde entre un papel beige y una tela rosada aunque tengan el mismo brillo.
2. `soloBordesLargos`: con `connectedComponentsWithStats` se descartan los bordes cortos, como las letras, y quedan las líneas largas (≥15% del lado menor): bordes de la hoja, de la mesa, etc.
3. Esas líneas, engrosadas, funcionan como **paredes**. `connectedComponents` encuentra las zonas que quedan encerradas, y se elige la que más ocupa el **centro** de la foto (la hoja casi siempre está ahí).
4. `findContours` de esa zona → `convexHull` → `approxPolyDP`, subiendo la tolerancia de a poco hasta que queden 4 puntos (`aproximarConCasco`).
5. `ajustarLados`: la aproximación traza cuerdas que quedan unos píxeles por dentro del borde real. Por eso cada punto del contorno se asigna al lado más cercano, se ajusta una **recta** a cada lado (`fitLine` con `DIST_HUBER`, que ignora puntos raros) y las esquinas son los **cruces** de rectas vecinas.
6. `esValido`: el área debe estar entre el 15% y el 98%, la figura debe ser convexa, los ángulos razonables (`cosenoMaximo` ≤ 0,6) y debe tener **soporte**: al menos el 60% del perímetro tiene que caer sobre bordes reales (los tramos pegados al marco de la foto no cuentan). Así no se acepta un cuadrilátero inventado.

Si ninguno sirve, se usa `esquinasPorDefecto()` (rectángulo con 5% de margen). Al final, `ordenarEsquinas` ordena por ángulo alrededor del centro (sentido horario) y rota la lista para que empiece en el punto con menor `x + y` (arriba a la izquierda). Así funciona aunque la hoja esté girada (lo prueba `LogicaPuraTest`).

Con 6 fotos reales, el método 1 encontraba la hoja en 1. Con el método 2 también se encuentran la firma y la lista del OPPO. Las 3 hojas sobre la tabla de apoyo, que se salen de la foto por arriba y por los lados, siguen con el rectángulo por defecto, igual que antes.

`Mat` vive en **memoria nativa** (C++), fuera del recolector de basura de Java; por eso cada `Mat` se libera con `release()` en un `finally`.

**`BlancoYNegro.kt`** — convierte la página (en gris) a blanco y negro puro. Se ajustó con fotos reales del Moto g22 (papel blanco) y del OPPO A60 (papel reciclado, sombras y luz de noche):

1. **`estimarFondo`:** reduce la página a 256 px y aplica un **cierre morfológico 9x9** (`MORPH_CLOSE`, que es `dilate` y luego `erode`). El texto desaparece porque es más delgado que 9 px, pero el borde de una sombra queda en su sitio. Después aplica un `GaussianBlur` suave (σ=3). Además, el fondo nunca baja del 25% del brillo máximo (`PISO_FONDO`), para que una zona muy oscura, como la mesa o un gancho metálico, no se "aclare" hasta parecer papel.
2. **`normalizar`:** `Core.divide(gris, fondo, 255)` divide cada píxel por su fondo, así el papel queda en ~255 en toda la hoja aunque tenga sombras.
3. **`GaussianBlur 3x3`:** suaviza un poco la textura del papel.
4. **`calcularCorteLocal`** (a media resolución, para que sea rápido):
   - `erode` → la tinta más oscura alrededor de cada punto (`minimo`).
   - `dilate` → el papel más claro alrededor (`maximo`).
   - **corte** = 70% del camino entre la tinta y el papel (`FRACCION_BORDE`).
   - **contraste** = `maximo - minimo`.
5. **`calcularUmbral`:** decide cuánto contraste se exige en cada zona.
   - Divide la página en bloques de ~1/40 del lado.
   - En cada bloque mide la **desviación estándar del brillo del papel**, sin contar lo que está cerca de tinta (`CONTRASTE_TINTA` = 60). Se calcula con sumas: `resize` con `INTER_AREA` promedia, y con el promedio de x y el de x² sale la varianza.
   - `ruidoPorBloque` divide ese valor por la **ganancia** (cuánto se aclaró el bloque al normalizar) y luego la vuelve a aplicar. Así una zona en sombra, que al aclararse multiplicó su grano, exige más contraste. Los bloques sin papel visible, como los párrafos densos, usan la mediana de los demás.
   - **umbral** = el mayor entre 20 (`CONTRASTE_MINIMO`) y 6 × ruido (`VECES_RUIDO`).
6. Un píxel es **texto** si es más oscuro que su corte **y** el contraste de la zona supera su umbral.
7. Un píxel es **zona oscura** si está por debajo del 70% del brillo del papel (`UMBRAL_OSCUROS`). Rellena barras, logos y grises medios.
8. Negro = texto **o** zona oscura. Al final, `medianBlur 3x3` quita puntos sueltos.

**¿Por qué un corte local y no un umbral fijo?** Cada letra se corta a la misma fracción entre *su* tinta y *su* papel. Una letra negra (tinta ~20% del brillo del papel) y un título gris claro (tinta ~70%) quedan los dos con su grosor real. Con un umbral único había que elegir: o el título gris se borraba, o el texto negro salía grueso.

**¿Por qué un umbral que depende del ruido?** En papel blanco con buena luz el ruido es bajo y el umbral queda en 20, como antes, así que el título gris claro se conserva. En papel reciclado (fibras y manchas) o en una sombra, las manchas tienen contraste de 20 a 30 y se volvían puntos negros por toda la hoja. Ahí el ruido medido es alto y el umbral sube a 40-60, mientras la tinta (contraste de 130 o más) pasa sin problema.

Historia de los ajustes (probados siempre con las mismas fotos reales):

| Versión | Resultado |
|---|---|
| `adaptiveThreshold` gaussiano, C=15 | Texto bien, pero el título gris claro se borraba, las barras grises salían huecas (solo el borde) y tardaba **16,4 s por página** en el Moto g22 (vecindario de 113x113 px). |
| `adaptiveThreshold` por promedio, C=10 | 0,9 s por página, título completo y barras rellenas, pero **todo el texto salía grueso**: los bordes suaves de las letras también se volvían negros. |
| Corte local al 70% | Grosor normal, título completo, barras rellenas y velocidad similar. Pero en el OPPO, con papel reciclado y de noche, la hoja se llenaba de puntos negros. |
| Corte al 50% | Demasiado fino: se cortaban letras como la "y". |
| Contraste mínimo fijo 30-40 | Se volvía a borrar parte del título. |
| Fondo con `dilate` y desenfoque grande (σ=12) | Una sombra con borde marcado, como la del celular, quedaba como franja gris llena de puntos. Se reemplazó por el cierre morfológico. |
| **Umbral según el ruido, 6 veces (actual)** | Papel reciclado y sombras limpios. En papel blanco, el mismo grosor que antes (negro del texto 10,53% contra 10,60%) y el título se conserva. |
| Umbral 8 veces el ruido | Se borraba parte del título gris. |
| Exigir bordes nítidos | Borraba "PROTOC", lo más tenue del título, y el ruido de las sombras también tiene bordes nítidos. Descartado. |
| Borrar manchas pequeñas por tamaño | Borraba los puntos de las íes y los puntos finales. Descartado. |
| Suavizar más antes del corte | Engrosaba el texto ~12%. Descartado. |

**`ProcesadorPagina.kt`**
- Todo se hace con `Mat` (memoria nativa) en vez de Bitmap, para no agotar la memoria de Java con 12 MP.
- `imread(..., IMREAD_GRAYSCALE or IMREAD_IGNORE_ORIENTATION)` + `girar()`: se lee directamente en gris (3 veces menos datos que en color) y se gira "a mano" con la misma rotación EXIF de la vista previa (`Core.rotate`).
- `enderezar()`:
  - Esquinas × tamaño real → `Point`.
  - Ancho de la página = el mayor entre lado superior e inferior; alto = el mayor entre izquierdo y derecho.
  - `getPerspectiveTransform` (matriz 3x3) + `warpPerspective` con `INTER_LINEAR` y `BORDER_REPLICATE`. La interpolación cúbica es 3,6 veces más lenta y, después de pasar a blanco y negro, la diferencia no se ve.
- `BlancoYNegro.aplicar()` y `imwrite` con `IMWRITE_JPEG_QUALITY = 95`. Una imagen de 1 canal se guarda como JPEG en gris, que pesa menos.

**`GeneradorPdf.kt`**
- Cada página mide el **ancho de un A4** (595,28 puntos; 1 punto = 1/72 de pulgada) y el alto proporcional a la foto: una hoja carta, un recibo o una tarjeta conservan su forma.
- **No se usa `JPEGFactory`**: en PdfBox-Android tiene un `// TODO` y marca todo JPEG como RGB. Las páginas son JPEG de 1 canal y deben declararse `DeviceGray`. Por eso se crea `PDImageXObject` directamente con `COSName.DCT_DECODE` y `PDDeviceGray`.
- `MemoryUsageSetting.setupMixed(16 MB)` con `cacheDir`: hasta 16 MB en memoria; si el PDF crece más, PdfBox usa archivos temporales.
- Si algo falla se borra el PDF a medio escribir.

### 5.5 `viewmodel/`

**`EscanerViewModel.kt`** — estado del documento en curso. Se crea **fuera del NavHost**, así pertenece a la Activity y Cámara, Recorte y Revisión comparten la misma instancia.

| Propiedad | Qué es |
|---|---|
| `paginas` | `mutableStateListOf`: Compose se entera cuando se agrega o quita una página. |
| `nombreDocumento` | Lo edita el campo de texto de Revisión. |
| `vistaPrevia`, `esquinas` | Estado de la pantalla de recorte (foto reducida y las 4 esquinas). |
| `ocupado` | `true` mientras corre una tarea; la UI desactiva botones y muestra un indicador de carga. |
| `mensajeError` | Se muestra como Toast desde `NavegacionApp`. |
| `pdfRecienCreado` | Para resaltar el PDF en Inicio. |

- Las propiedades usan `by mutableStateOf(...)` con `private set`: las pantallas las leen, pero solo el ViewModel las cambia.
- `ejecutar(mensaje, trabajo)`: lanza una corrutina en `viewModelScope`, marca `ocupado` y convierte errores en `mensajeError`. Deja pasar `CancellationException`, porque la cancelación no es un error, y atrapa `OutOfMemoryError` con un mensaje claro.
- `cargarFoto` revisa al terminar si el usuario ya descartó esa foto; si es así, ignora el resultado.
- `cargarParaVer(pagina)`: función `suspend` que carga la página a ~1500-3000 px para el visor con zoom (en un hilo de E/S).
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

- **Botón Cerrar** (`alCerrarApp`): `finishAndRemoveTask()` cierra la Activity y la quita de las apps recientes; luego `Process.killProcess(Process.myPid())` termina el proceso. Normalmente Android prefiere manejar el cierre él mismo, pero aquí se pidió que la app no quede en segundo plano.

**`inicio/PantallaInicio.kt`**
- `TopAppBar` con el botón **Cerrar** en `actions`.
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
  - `isAutoRotationEnabled = false`: la foto siempre sale vertical como la pantalla. Con la rotación automática (activada por defecto), el celular acostado sobre la hoja producía fotos de lado.
  - `CAPTURE_MODE_MAXIMIZE_QUALITY`.
  - `ResolutionStrategy(Size(4000, 3000), FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)` + relación 4:3.
  - `setAllowedResolutionMode(PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)`: en celulares con la capacidad `BURST_CAPTURE`, Android esconde las resoluciones que no alcanzan 20 fotos por segundo en una lista aparte (`getHighResolutionOutputSizes`). CameraX solo la usa si se le pide con este modo. **Caso real:** en el Moto g22, 4080x3072 tarda 66 ms por cuadro (15 fps); sin este modo, la app tomaba fotos de 3264x2448 (8 MP).
  - Enfoque automático continuo y tocar para enfocar vienen activados por defecto.
- `DisposableEffect` hace `bindToLifecycle` al entrar y `unbind` al salir. CameraX apaga la cámara sola si la app pasa a segundo plano.
- `PreviewView` (vista clásica) dentro de `AndroidView`, con `FIT_CENTER` para ver la foto completa sin recortes.
- Otro `DisposableEffect` pone claros los íconos de la barra de estado (fondo negro) y los restaura al salir.
- `tomarFoto()`: `takePicture` con `OutputFileOptions` y un `OnImageSavedCallback`.

**`recorte/PantallaRecorte.kt`**
- Tres estados: imagen cargada (editor), cargando (indicador de carga) y "La foto ya no está disponible" (falló la carga o Android cerró la app en segundo plano).
- `BackHandler` ignora "atrás" mientras se procesa, para no perder la página.
- Muestra la foto original en color: es más fácil ubicar los bordes de la hoja que en blanco y negro.

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
- `LazyVerticalGrid` de 2 columnas con `TarjetaPagina` (miniatura + botón borrar). La tarjeta es un `Card(onClick = ...)`: al tocarla abre `VisorPagina`.
- Confirmación antes de borrar una página y antes de descartar el documento ("atrás" con páginas).
- Capa semitransparente "Creando PDF…" mientras `ocupado`.
- El botón dice "Agregar" porque "Agregar página" no cabe en una línea en celulares angostos.

**`revision/VisorPagina.kt`** — la página en pantalla completa para revisar si quedó bien:
- `Dialog` con `usePlatformDefaultWidth = false` (ocupa toda la pantalla); "atrás" lo cierra.
- `produceState`: carga la imagen en segundo plano (`escaner.cargarParaVer`) y mientras tanto muestra un indicador de carga.
- **Zoom:** `detectTransformGestures` entrega el centro del pellizco, el desplazamiento y el factor de zoom. La imagen se dibuja con `graphicsLayer` (`scaleX/scaleY` + `translationX/Y`, origen arriba a la izquierda). La fórmula `desplazamiento = (desp + centro / escalaAnterior) - (centro / escalaNueva + paneo / escalaAnterior)` mantiene fijo el punto bajo los dedos.
- **Doble toque** (`detectTapGestures`): acerca 3x en el punto tocado o vuelve a 1x.
- `limitar()` impide arrastrar la imagen fuera de la pantalla. Zoom máximo: 6x.

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
| Llamada segura | `blancoYNegro?.release()` | `if (blancoYNegro != null) blancoYNegro.release();` | `ProcesadorPagina.kt` |
| Elvis | `val o = vistaPreviaOriginal ?: return` | `if (o == null) return;` | `EscanerViewModel.kt` |
| Bloque si no es null | `documentoPorBorrar?.let { doc -> ... }` | `if (d != null) { ... }` | `PantallaInicio.kt` |
| `when` | `when (grados) { 90 -> ...; 180 -> ...; else -> ... }` | `switch`, pero devuelve un valor | `ProcesadorPagina.girar` |
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
| `produceState` | Estado que se llena con una corrutina (por ejemplo, cargar una imagen). | `VisorPagina` |
| `Dialog` | Ventana encima de la pantalla actual; con `usePlatformDefaultWidth = false` ocupa toda la pantalla. | `VisorPagina` |
| `detectTransformGestures` / `detectTapGestures` | Pellizcar, arrastrar y doble toque. | `VisorPagina` |
| `graphicsLayer` | Escala y mueve lo dibujado sin volver a medir la pantalla (rápido para zoom). | `VisorPagina` |

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

### Blanco y negro: equilibrios

El corte local compara cada píxel con la tinta y el papel de su alrededor. Con texto funciona muy bien, pero dentro de una zona oscura **más grande que ese alrededor** no hay papel cerca, el contraste es bajo y el centro quedaría blanco (solo se verían los contornos). Por eso `BlancoYNegro` agrega una regla para "zonas oscuras" (sección 5.4). Lo que todavía puede pasar:

- **Grises decorativos** (curvas, sombreados) pueden salir rellenos con algunos puntitos blancos, o con manchas si quedan detrás del texto.
- **Texto muy claro** (lápiz suave, impresión gris muy tenue) puede salir con trazos cortados. Bajar `CONTRASTE_MINIMO` lo recupera a cambio de más ruido; subir `FRACCION_BORDE` engruesa el texto.
- **Papel reciclado con sombra muy fuerte:** en la parte más oscura pueden quedar algunos puntos. Subir `VECES_RUIDO` limpia más, pero desde 8 empieza a borrar texto gris claro.
- **Fotos o dibujos** dentro de la hoja se ven como alto contraste, no como fotografía.

### Velocidad en el Moto g22

Procesar una página de 12 MP en el Moto g22 (gama baja) tardaba 16,4 s con el umbral gaussiano y la interpolación cúbica. Con el umbral por promedio y la interpolación lineal bajó a 0,9 s; el corte local tiene un costo parecido, y el umbral según el ruido agrega poco porque se calcula a media resolución y por bloques (ver sección 5.4).

### Otras

- **Detección automática:** necesita que la hoja se distinga del fondo, en brillo o en color, y ocupe al menos el 15% de la foto. Si la hoja se sale de la foto por varios lados, aparece el rectángulo por defecto y se ajusta a mano.
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
- [x] **Probar en Motorola** (Moto g22, Android 12): foto vertical, 12 MP, B/N y zoom.
- [ ] **APK firmado para repartir:** el APK *debug* sirve para pruebas. Para distribuir: *Build → Generate Signed App Bundle / APK → APK*, crear un keystore y **guardarlo bien** (sin él no se pueden publicar actualizaciones que se instalen encima).
- [x] **B/N mejorado:** normalización del fondo + corte local entre tinta y papel + umbral para zonas oscuras.
- [x] **Papel reciclado, sombras y poca luz** (OPPO A60): umbral según el ruido y detección de bordes por regiones.
- [ ] Girar una página 90° desde Revisión.
- [ ] Reordenar páginas.
- [ ] Botón de flash o linterna para aulas con poca luz.
- [ ] Fondo oscuro al abrir la app cuando el celular está en modo oscuro (hoy aparece un instante en blanco).
- [ ] Release con R8 (`isMinifyEnabled = true`) para reducir el APK (requiere reglas `-keep` para OpenCV y PdfBox).
