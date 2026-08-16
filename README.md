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
    └── main/
        └── java/
            └── com/wordtomarkdown/
                └── App.java
```

## Requisitos previos

- Java 21 o superior
- Maven 3.6 o superior

## Compilar

```bash
mvn clean package
```

El JAR ejecutable se genera en:

```
target/word-to-markdown.jar
```

## Ejecutar

```bash
java -jar target/word-to-markdown.jar
```

## Uso

1. Ejecutar la aplicación con el comando anterior.
2. Elegir el **tipo de selección** con los botones de opción superiores:
   - **Archivo** (valor por defecto): convierte un único documento.
   - **Carpeta**: convierte por lote todos los `.docx` de la carpeta elegida.
3. Hacer clic en **Seleccionar...** y elegir el archivo o la carpeta.
4. Hacer clic en **Convertir a Markdown**.
5. Cada archivo `.md` se genera automáticamente junto a su `.docx` de origen.

### Modo carpeta

- Se procesan los `.docx` **directamente contenidos** en la carpeta; las subcarpetas no se recorren.
- Se ignoran los archivos temporales que Word crea al tener un documento abierto (`~$nombre.docx`), que no son documentos válidos.
- Si un documento falla, el lote continúa con los siguientes y al final se muestra un resumen con el total de conversiones correctas y con error.

## Notas

- La conversión se ejecuta en un hilo secundario (`SwingWorker`) para no bloquear la interfaz; durante el proceso los controles quedan deshabilitados.
- Cualquier advertencia generada por Mammoth durante la conversión se muestra en el panel de registro de la ventana.
- Las imágenes del documento se extraen como archivos independientes en una carpeta `{nombre}_images/` junto al `.md`. Las referencias quedan como rutas relativas en el Markdown.
- El texto alternativo (alt text) generado automáticamente por la IA de Microsoft Word (e.g. *"el contenido generado por IA puede ser incorrecto"*) es ignorado; en su lugar se usa un texto genérico (`imagen N`).
