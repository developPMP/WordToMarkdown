No míralos. Muchas personas no lo entienden soles de la televisión lo pasan y es algo muy fuerte, pasen a nuestro chico, en la respuesta que la matrimonio masivo que organiza la municipalidad en el parque zona del correspondiente, por ejemplo, yo me voy a casar el día a matrimonio masivo en el parque Sul Baitaca. Me dicho que no hay manera que no se va a casar en el Maita Capa, que yo le digo, no, pero es lo que corresponde. Yo soy el hijo de San Martigo Lichuch nos vamos a casar y no que yo me quiero casar en la iglesia del parque quién es o he vada y le digo que chuchando el casamento por yo te conocirvías en pocas y tú ya vamos a casa de tiraflor yo me toque la serie de baila en todo caso ya si el hogar tú lo case con el estómago que yo quiero que me pierda material parque la sábado pero he dicho majo me dice no que cuando vayas a pedirme la mano yo quiero que me pidas la mano en parido le voy a foda le digo que parís parís y le digo y todos pens la mano en un lugar icónico para nosotros no son pode# Word to Markdown — versión Kotlin

Aplicación de escritorio en **Kotlin** con interfaz **Compose Multiplatform**
que convierte documentos Word (`.docx`) a **Markdown** (`.md`).

Es la contraparte de la [versión Java + Swing](../java/): mismas prestaciones y
mismo diseño, distinta tecnología de interfaz.

## Flujo de conversión

```
documento.docx  →  [Mammoth]  →  HTML  →  [flexmark]  →  documento.md
```

1. **Mammoth** lee el `.docx` y produce HTML semántico limpio.
2. **flexmark-html2md-converter** transforma ese HTML a Markdown.

> Ambas son librerías Java, usadas aquí desde Kotlin. Mammoth no ofrece
> conversión directa a Markdown, por eso se realiza en dos pasos.

## Tecnologías

| Librería | Versión | Rol |
|---|---|---|
| Kotlin | 2.3.21 | Lenguaje |
| Kotlin Multiplatform | — | Estructura del proyecto (target `jvm`) |
| Compose Multiplatform | 1.11.1 | Interfaz de escritorio |
| Material 3 | 1.9.0 | Componentes visuales |
| [Mammoth](https://github.com/mwilliamson/java-mammoth) | 1.12.0 | Conversión `.docx` → HTML |
| [flexmark-html2md-converter](https://github.com/vsch/flexmark-java) | 0.64.8 | Conversión HTML → Markdown |
| Gradle | 9.4.1 | Build (wrapper incluido) |

## Requisitos previos

- **JDK 21** o superior.

No hace falta instalar Gradle ni Kotlin: el wrapper (`./gradlew`) descarga lo
necesario. El proyecto usa un *toolchain* de Java 21, así que compila con esa
versión aunque Gradle arranque con otra.

Todos los comandos se ejecutan desde esta carpeta (`kotlin/`). En Windows,
sustituye `./gradlew` por `gradlew.bat`.

## Compilar

```bash
./gradlew build              # compila y ejecuta los tests
./gradlew compileKotlinJvm   # solo compilar, sin tests
./gradlew clean build        # partir de cero
```

> **El JAR de `build/libs/` no es ejecutable.** A diferencia de la versión Java
> —cuyo `word-to-markdown.jar` es un *uber-jar* autónomo— aquí `build` genera un
> JAR sin dependencias ni `Main-Class`, y `java -jar` responde *"no hay ningún
> atributo de manifiesto principal"*. Para obtener algo distribuible usa las
> tareas de la sección [Empaquetar](#empaquetar).

Si un build termina en menos de un segundo, incluso tras un `clean`, es la caché
de Gradle reutilizando resultados anteriores. Para forzar que todo se rehaga:

```bash
./gradlew build --rerun-tasks
```

## Ejecutar

```bash
./gradlew run
```

Es la forma habitual durante el desarrollo: compila y abre la ventana.

## Tests

```bash
./gradlew jvmTest
```

Cubren la conversión, la selección de documentos en carpeta, el filtrado de
temporales de Word, la extracción de imágenes, el criterio de arrastrar y soltar
y el formato del registro. Los `.docx` de prueba se generan al vuelo
(`DocxFixtures`), sin versionar binarios.

## Empaquetar

```bash
./gradlew createDistributable              # aplicación lista para usar
./gradlew packageDistributionForCurrentOS  # instalador del sistema
```

| Tarea | Resultado | Ubicación |
|---|---|---|
| `createDistributable` | Aplicación ejecutable (`WordToMarkdown.app` en macOS) | `build/compose/binaries/main/app/` |
| `packageDistributionForCurrentOS` | Instalador `.dmg` (macOS), `.msi` (Windows) o `.deb` (Linux) | `build/compose/binaries/main/<formato>/` |

El resultado ronda los **135 MB** porque incluye su propio runtime de Java: a
cambio, quien lo reciba no necesita tener Java instalado.

Ambas tareas generan **solo para el sistema donde se ejecutan**: `jpackage` no
compila cruzado, así que el `.msi` requiere ejecutarse en Windows.

### JAR para Windows 11

```bash
./gradlew windowsJar
```

Genera `build/distributions/word-to-markdown-kt-1.0.0-windows-x64.jar` (~37 MB),
**autónomo y ejecutable en Windows** desde cualquier sistema, incluido macOS:

```powershell
java -jar word-to-markdown-kt-1.0.0-windows-x64.jar
```

Compose dibuja con Skia, que es código nativo, por lo que el JAR corriente de
`build/libs/` arrastra las librerías del sistema donde se compiló. La tarea
`windowsJar` resuelve `compose.desktop.windows_x64` en una configuración aparte
y empaqueta `skiko-windows-x64.dll` junto al código y al resto de dependencias.

> Requiere **JDK 21** instalado en el equipo Windows: este JAR incluye las
> librerías, pero no el runtime de Java (a diferencia del instalador `.msi`).

## Estructura

```
kotlin/
├── build.gradle.kts
├── settings.gradle.kts
└── src/
    ├── jvmMain/kotlin/com/wordtomarkdown/
    │   ├── Main.kt                 Interfaz Compose y arranque
    │   ├── ConversionService.kt    Lógica de conversión, sin dependencias de UI
    │   ├── ConversionResult.kt     Resultado de convertir un documento
    │   ├── ConversionReporter.kt   Traduce el resultado a líneas de registro
    │   └── SelectionPolicy.kt      Decide qué hacer con una ruta arrastrada
    └── jvmTest/kotlin/com/wordtomarkdown/
        ├── ConversionServiceTest.kt
        ├── SelectionAndReportTest.kt
        └── DocxFixtures.kt         Genera documentos .docx de prueba
```

## Uso

1. Elegir el **tipo de selección**: **Archivo** (por defecto) o **Carpeta**.
2. Pulsar **Seleccionar...**, o arrastrar un `.docx` (o una carpeta) sobre el
   área de **Registro**.
3. Pulsar **Convertir a Markdown**.
4. Cada `.md` se genera junto a su `.docx` de origen.

## Notas

- La conversión se ejecuta fuera del hilo de interfaz (corrutina en
  `Dispatchers.IO`), y los controles se deshabilitan mientras dura.
- En modo carpeta se procesan los `.docx` **directamente contenidos** en ella;
  las subcarpetas no se recorren. Se ignoran los temporales de Word
  (`~$nombre.docx`).
- Si un documento falla, el lote continúa y se muestra un resumen final.
- Las imágenes se extraen a `{nombre}_images/` y se enlazan con rutas relativas.
  Si alguna no se puede extraer, se detalla en el registro y queda marcada en el
  Markdown como `imagen N (no se pudo extraer)`.
- El alt text generado automáticamente por la IA de Word se descarta en favor de
  un texto genérico (`imagen N`).
