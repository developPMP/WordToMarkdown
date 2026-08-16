# Word to Markdown

Aplicación de escritorio en **Java 21** con interfaz **Swing** que convierte documentos Word (`.docx`) a formato **Markdown** (`.md`).

## Descripción

El usuario elige, mediante un diálogo, un archivo `.docx` individual o una carpeta completa. La aplicación genera un archivo `.md` con el mismo nombre en la misma carpeta de cada documento de origen.

### Flujo de conversión

```
documento.docx  →  [Mammoth]  →  HTML  →  [flexmark]  →  documento.md
```

1. **Mammoth** lee el `.docx` y produce HTML semántico limpio.
2. **flexmark-html2md-converter** transforma ese HTML a Markdown.

> La librería Java de Mammoth no incluye conversión directa a Markdown (es una _missing feature_ documentada), por eso se realiza en dos pasos.

## Tecnologías

| Librería | Versión | Rol |
|---|---|---|
| Java | 21 | Lenguaje / plataforma |
| Swing | JDK | Interfaz gráfica |
| [Mammoth](https://github.com/mwilliamson/java-mammoth) | 1.12.0 | Conversión `.docx` → HTML |
| [flexmark-html2md-converter](https://github.com/vsch/flexmark-java) | 0.64.8 | Conversión HTML → Markdown |
| Maven | 3.x | Gestión de dependencias y build |

## Estructura del proyecto

```
WordToMarkdown/
├── pom.xml
└── src/
    ├── main/java/com/wordtomarkdown/
    │   ├── App.java                 Interfaz Swing (ventana, selección, registro)
    │   ├── ConversionService.java   Lógica de conversión, sin dependencias de UI
    │   ├── ConversionResult.java    Resultado de convertir un documento
    │   ├── ConversionReporter.java  Traduce el resultado a líneas de registro
    │   └── SelectionPolicy.java     Decide qué hacer con una ruta arrastrada
    └── test/java/com/wordtomarkdown/
        ├── ConversionServiceTest.java
        ├── FindDocxFilesTest.java
        ├── ConversionReporterTest.java
        ├── SelectionPolicyTest.java
        ├── AppUiTest.java           Ventana (se omite sin entorno gráfico)
        └── DocxFixtures.java        Genera documentos .docx de prueba
```

La lógica de conversión vive en `ConversionService`, separada de la ventana: no
depende de Swing, devuelve el resultado como datos (`ConversionResult`) y es `App`
quien decide cómo mostrarlo. Así puede probarse sin abrir la interfaz.

## Requisitos previos

- Java 21 o superior
- Maven 3.6 o superior

## Compilar

Antes de compilar, asegúrate de que Maven use Java 21.

### macOS (zsh / bash)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package
```

### Git Bash (Windows)

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package
```

### PowerShell (Windows)

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\\bin;$env:Path"
mvn clean package
```

Si tu instalación de Java 21 está en otra ruta, sustituye el valor de `JAVA_HOME` por el correspondiente.

Si Maven arranca con un JDK anterior, el build se detiene en la fase `validate`
(mediante `maven-enforcer-plugin`) con un mensaje explícito indicando que hace falta
JDK 21, en lugar del críptico `invalid target release: 21` del compilador.

El JAR ejecutable se genera en:

```
target/word-to-markdown.jar
```

## Ejecutar

```bash
java -jar target/word-to-markdown.jar
```

## Tests

```bash
mvn test
```

Las pruebas (JUnit 5) cubren la selección de documentos en carpeta, el filtrado de
temporales de Word, la conversión a Markdown, la extracción de imágenes, el
comportamiento ante documentos ilegibles, el criterio al arrastrar y soltar, el
formato del registro y el estado de la ventana. Los `.docx` de prueba se generan al
vuelo en carpetas temporales (`DocxFixtures`), por lo que no se versiona ningún
binario.

Las pruebas de `AppUiTest` necesitan entorno gráfico para crear la ventana. En un
entorno headless (por ejemplo integración continua) **se omiten** en lugar de
fallar, y el resto de la batería se ejecuta con normalidad:

```bash
mvn test -DargLine="-Djava.awt.headless=true"
```

## Uso

1. Ejecutar la aplicación con el comando anterior.
2. Elegir el **tipo de selección** con los botones de opción superiores:
   - **Archivo** (valor por defecto): convierte un único documento.
   - **Carpeta**: convierte por lote todos los `.docx` de la carpeta elegida.
3. Hacer clic en **Seleccionar...** y elegir el archivo o la carpeta.
4. Hacer clic en **Convertir a Markdown**.
5. Cada archivo `.md` se genera automáticamente junto a su `.docx` de origen.

### Arrastrar y soltar

Como alternativa al diálogo, se puede arrastrar un `.docx` (o una carpeta) sobre el
área de **Registro**: equivale a seleccionarlo, ajustando además el tipo de selección
al contenido soltado —**Archivo** si es un documento, **Carpeta** si es un directorio—
y rellenando la ruta. Lo que no sea un `.docx` se ignora sin alterar la selección
previa, y no se admiten arrastres mientras hay una conversión en curso.

### Modo carpeta

- Se procesan los `.docx` **directamente contenidos** en la carpeta; las subcarpetas no se recorren.
- Se ignoran los archivos temporales que Word crea al tener un documento abierto (`~$nombre.docx`), que no son documentos válidos.
- Si un documento falla, el lote continúa con los siguientes y al final se muestra un resumen con el total de conversiones correctas y con error.

## Notas

- La conversión se ejecuta en un hilo secundario (`SwingWorker`) para no bloquear la interfaz; durante el proceso los controles quedan deshabilitados.
- Cualquier advertencia generada por Mammoth durante la conversión se muestra en el panel de registro de la ventana.
- Las imágenes del documento se extraen como archivos independientes en una carpeta `{nombre}_images/` junto al `.md`. Las referencias quedan como rutas relativas en el Markdown.
- Si una imagen concreta no se puede extraer, la conversión del documento continúa, pero el fallo **no pasa inadvertido**: se detalla en el registro (con número de imagen y causa), se resume en la línea de resultado del documento y en el Markdown queda marcada como `imagen N (no se pudo extraer)`.
- El texto alternativo (alt text) generado automáticamente por la IA de Microsoft Word (e.g. *"el contenido generado por IA puede ser incorrecto"*) es ignorado; en su lugar se usa un texto genérico (`imagen N`).
