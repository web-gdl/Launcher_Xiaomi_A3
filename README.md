# Inicio: launcher ligero y privado para Android

[![Compilar APK](https://github.com/web-gdl/Launcher_Xiaomi_A3/actions/workflows/main.yml/badge.svg)](https://github.com/web-gdl/Launcher_Xiaomi_A3/actions/workflows/main.yml)
[![Licencia: dominio público](https://img.shields.io/badge/licencia-dominio%20p%C3%BAblico-brightgreen)](LICENSE)

Pantalla de inicio propia para el Xiaomi 24044RN32L (Android 16 / HyperOS). Funciona en cualquier Android 8.0 o superior (API 26+).
Sustituye al GoLauncher de fábrica sin tocarlo: se instala como una app normal y se elige como pantalla de inicio.

- **Paquete:** `com.webgdl.launcher` · **Versión:** 0.1.2
- **Tamaño del APK:** ~130 KB (el GoLauncher de fábrica ocupa 8,5 MB).
- **Sin permiso de internet:** no se conecta a nada. No tiene telemetría, anuncios ni SDKs de terceros.
- **Solo APIs públicas de Android:** no necesita permisos de sistema, root ni ajustes como `hidden_api_policy`.
- Java puro, sin Gradle, sin AndroidX y sin Kotlin. Se compila con `aapt2 → javac → D8 → apksig`.

> **Estado:** compila, se firma y pasó dos revisiones de código. Se instala en el Xiaomi 24044RN32L; **falta probar su uso en el teléfono**.
> Si algo falla, el registro se saca con `adb logcat -d -b crash -b main > log.txt`.

## Funciones

| Función | Cómo se usa |
|---|---|
| Escritorio con varias páginas | Desliza a izquierda y derecha. Los puntos de abajo indican la página. |
| Búsqueda | Barra «Buscar apps» arriba del escritorio: abre el cajón con el teclado listo. El cajón tiene el mismo campo arriba. |
| Dock (4 apps fijas) | Fila inferior. Arrastra apps dentro y fuera. |
| Cajón de todas las apps | **Desliza hacia arriba** en cualquier parte (escritorio, dock o puntos de página). Se cierra deslizando hacia abajo con la lista arriba del todo, con Atrás o con Inicio. |
| Mover iconos y widgets | **Mantén pulsado** y arrastra. Cerca del borde cambia de página; en la última página crea una nueva. |
| Quitar del inicio / Info / Desinstalar | Al arrastrar, la barra de búsqueda se convierte en estas tres opciones: suelta encima de una. Quitar no desinstala: la app sigue en el cajón. |
| Carpetas | Suelta un icono sobre otro (también en el dock). Toca la carpeta para abrirla y cambiarle el nombre. Mantén pulsada una app dentro para sacarla. |
| Añadir una app al inicio | En el cajón, mantén pulsada la app → «Añadir al inicio». |
| Atajos de las apps | En el cajón, mantén pulsada la app: aparecen sus atajos (p. ej. «Nuevo mensaje»). |
| Widgets | Mantén pulsada una **zona vacía** del escritorio → «Widgets». |
| Fondo de pantalla | Zona vacía → «Fondo de pantalla» (abre el selector del sistema). |
| Insignias de notificación | Solo en la variante «insignias» (ver abajo): Ajustes del inicio → «Activar insignias» y concede el acceso a notificaciones. |
| Ajustes | Zona vacía → «Ajustes del inicio»: columnas, filas, tamaño de iconos, nombres y restablecer. |
| Barra de notificaciones | Desliza hacia abajo en el escritorio o el dock. Usa una función no documentada de Android; si el sistema la bloquea, no hace nada. |

Las apps que instales después aparecen solas al final del escritorio. Las que quites del inicio no vuelven a aparecer.
Si reduces columnas o filas, los iconos que ya no caben se recolocan en el primer hueco libre: no se pierden.

## Descargar

| Archivo | Para qué sirve |
|---|---|
| **[launcher-movil.apk](https://github.com/web-gdl/Launcher_Xiaomi_A3/releases/latest/download/launcher-movil.apk)** | **Empieza por aquí.** Se instala desde el gestor de archivos. No tiene insignias de notificación. |
| [launcher-movil-insignias.apk](https://github.com/web-gdl/Launcher_Xiaomi_A3/releases/latest/download/launcher-movil-insignias.apk) | Igual, con insignias. Se instala con `adb install -r` (ver abajo por qué). |
| [SHA256SUMS.txt](https://github.com/web-gdl/Launcher_Xiaomi_A3/releases/latest/download/SHA256SUMS.txt) | Sumas de verificación. |

Todas las versiones están en [Releases](https://github.com/web-gdl/Launcher_Xiaomi_A3/releases). Los APK de Releases van firmados con la clave del autor,
así que cada versión se instala encima de la anterior sin perder la disposición del escritorio.

## Instalar

Hay dos variantes con el mismo paquete y la misma firma (se puede pasar de una a otra instalando encima):

| APK | Insignias | Cómo instalarla |
|---|---|---|
| `launcher-movil.apk` | No | Desde el gestor de archivos o con adb. |
| `launcher-movil-insignias.apk` | Sí | Con `adb install -r`. Google Play Protect bloquea la instalación desde navegador, mensajería o gestor de archivos de las apps que piden acceso a notificaciones (protección contra fraude). |

1. Copia `launcher-movil.apk` al teléfono y ábrelo, o usa `adb install -r launcher-movil.apk`.
2. Ve a **Ajustes → Apps → Apps predeterminadas → Pantalla de inicio** y elige **«Inicio»**.
3. En el primer arranque coloca todas tus apps en el escritorio por orden alfabético. Las de teléfono, mensajes, navegador y cámara van al dock.

**No desactives el launcher de fábrica.** HyperOS lo sigue usando para la pantalla de Recientes y los gestos de navegación,
como con cualquier launcher que no venga de fábrica.

## Volver al launcher de fábrica

Ajustes → Apps → Apps predeterminadas → Pantalla de inicio → el de fábrica. Para quitarlo del todo:
```
adb uninstall com.webgdl.launcher
```

## Privacidad y permisos

Solo pide tres permisos, ninguno de red:

| Permiso | Para qué |
|---|---|
| `QUERY_ALL_PACKAGES` | Ver las apps instaladas (imprescindible para un launcher). |
| `EXPAND_STATUS_BAR` | Bajar la barra de notificaciones al deslizar hacia abajo. |
| `REQUEST_DELETE_PACKAGES` | Abrir el diálogo de desinstalación del sistema. Siempre confirmas tú. |

- Sin el permiso `INTERNET`, Android le impide abrir cualquier conexión.
- La disposición del escritorio se guarda solo en el teléfono (`layout.json` en los datos privados de la app).
- Las insignias usan el acceso a notificaciones solo para **contar** cuántas tiene cada app. No lee su contenido ni guarda nada.
  Solo funcionan si concedes ese acceso, y puedes retirarlo cuando quieras.

## Límites conocidos (v0.1.2)

- Recientes y gestos de navegación los gestiona el sistema, no este launcher.
- Los atajos de las apps solo aparecen cuando «Inicio» es la pantalla de inicio predeterminada; Android no deja consultarlos a otro launcher.
- Las apps no pueden fijar accesos directos en el inicio (p. ej. «Añadir a la pantalla de inicio» desde Chrome).
- Las apps del cajón no se arrastran directamente al escritorio: se usa «Añadir al inicio».
- Los widgets no se pueden redimensionar; ocupan el tamaño que pide cada widget.
- Si un widget no se puede cargar (p. ej. tras actualizar su app), aparece un recuadro «Widget no disponible»: se vuelve a intentar al recargar y se puede quitar arrastrándolo.
- La variante normal no tiene insignias de notificación (ver «Instalar»).

## Compilar

Requisitos: JDK 17 o superior, `curl`, `unzip` y `zip`.

```bash
./tools/get-tools.sh     # descarga android-36.jar, aapt2 y jadx (D8 + apksig) en tools/
./build.sh                       # -> out/launcher-movil.apk (sin insignias)
./build.sh --variant insignias   # -> out/launcher-movil-insignias.apk
./build.sh --variant ambas       # las dos
```

Si no existe, la compilación crea una clave de firma en `keystore/release.p12` (alias `launcher`; contraseña: la variable `KSPASS`,
o la de `build.sh` si no la defines). **Guárdala:** sin ella no podrás instalar versiones nuevas encima de la instalada.
Si compilas tu propia versión, usa tu propia clave: los APK de Releases van firmados con la del autor.

## Publicar versiones (mantenimiento)

Cada push compila el proyecto en GitHub Actions ([`main.yml`](.github/workflows/main.yml)). Esos APK de prueba usan una clave temporal:
**no** se pueden instalar encima de una versión de Releases.

Para publicar una versión ([`release.yml`](.github/workflows/release.yml)):

1. Una sola vez, en *Settings → Secrets and variables → Actions*, crea dos secretos:
   - `KEYSTORE_BASE64`: el resultado de `base64 -w0 keystore/release.p12`
   - `KEYSTORE_PASSWORD`: la contraseña del almacén
2. Sube `android:versionCode` y `android:versionName` en `AndroidManifest.xml`, pon la misma versión en `VERSION` y anota los cambios en `CHANGELOG.md`.
3. Haz push a `main`. Se crea la etiqueta `vX.Y.Z` y una Release con los dos APK y `SHA256SUMS.txt`.

Si faltan los secretos, el flujo termina sin publicar y lo avisa: así nunca sube APK firmados con otra clave.
La clave (`keystore/`) no se sube nunca al repositorio.

## Estructura

```
AndroidManifest.xml        Actividad HOME, ajustes y servicio de insignias. Sin INTERNET.
res/                       Colores, textos, iconos vectoriales, fondos y temas.
src/com/webgdl/launcher/
  HomeActivity.java        Pantalla de inicio: escritorio, dock, cajón, carpetas, widgets, arrastre, carga y guardado.
  PagedView.java           Páginas del escritorio (deslizar a los lados) y pulsación larga en zona vacía.
  CellLayout.java          Cuadrícula de celdas.
  BubbleTextView.java      Icono + nombre + insignia.
  FolderIcon.java          Icono de carpeta con miniaturas 2×2.
  Folder.java              Carpeta abierta.
  AllAppsContainer.java    Cajón con búsqueda.
  OptionsPopup.java        Menú al mantener pulsada una app del cajón.
  DragController.java      Copia flotante durante el arrastre.
  DragLayer.java           Contenedor raíz: arrastre y deslizamientos arriba/abajo desde cualquier parte.
  LauncherWidgetHost.java  Widgets, con su propia detección de pulsación larga.
  LauncherModel.java       Lista de apps (LauncherApps) y guardado de la disposición en JSON (un único hilo de disco).
  IconCache.java           Carga de iconos en segundo plano con caché LRU.
  BadgeNotificationListener.java + BadgeCache.java   Insignias.
  SettingsActivity.java + Prefs.java                 Ajustes.
  Utilities.java           Medidas, iconos y márgenes de las barras del sistema.
```

## Licencia

Dominio público ([The Unlicense](LICENSE)).
