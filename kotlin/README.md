# Word to Markdown — versión Kotlin

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

## Ejecutar

```bash
./gradlew run
```

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
./gradlew packageDistributionForCurrentOS
```

Genera un instalador nativo (`.dmg` en macOS, `.msi` en Windows, `.deb` en
Linux) en `build/compose/binaries/`.

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
