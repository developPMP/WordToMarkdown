# Word to Markdown

Aplicación de escritorio en **Java 21** con interfaz **Swing** que convierte documentos Word (`.docx`) a formato **Markdown** (`.md`).

## Descripción

El usuario selecciona un archivo `.docx` mediante un diálogo de archivos. La aplicación genera un archivo `.md` con el mismo nombre en la misma carpeta del archivo de origen.

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
2. Hacer clic en **Seleccionar...** y elegir el archivo `.docx`.
3. Hacer clic en **Convertir a Markdown**.
4. El archivo `.md` se genera automáticamente en la misma carpeta que el `.docx`.

## Notas

- La conversión se ejecuta en un hilo secundario (`SwingWorker`) para no bloquear la interfaz.
- Cualquier advertencia generada por Mammoth durante la conversión se muestra en el panel de registro de la ventana.
- Las imágenes embebidas en el documento se convierten a base64 inline (comportamiento por defecto de Mammoth).
