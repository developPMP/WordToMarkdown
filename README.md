# Word to Markdown

Conversor de documentos Word (`.docx`) a **Markdown** (`.md`), con dos
implementaciones independientes de la misma aplicación de escritorio.

| Subproyecto | Lenguaje | Interfaz | Build | Documentación |
|---|---|---|---|---|
| [`java/`](java/) | Java 21 | Swing | Maven | [java/README.md](java/README.md) |
| [`kotlin/`](kotlin/) | Kotlin 2.3 | Compose Multiplatform (desktop) | Gradle | [kotlin/README.md](kotlin/README.md) |

Ambas comparten el mismo motor de conversión (`.docx → HTML → Markdown` con
Mammoth y flexmark), el mismo diseño en tres capas y las mismas prestaciones:

- Conversión de un archivo suelto o de todos los `.docx` de una carpeta.
- Arrastrar y soltar un documento o una carpeta sobre el área de registro.
- Extracción de imágenes a `{nombre}_images/`, ignorando el alt text que genera
  automáticamente la IA de Word.
- Registro detallado, con aviso explícito de las imágenes que no se pudieron
  extraer y resumen final del lote.

## Estructura

```
WordToMarkdown/
├── java/      Versión Java 21 + Swing (Maven)
└── kotlin/    Versión Kotlin + Compose Multiplatform (Gradle)
```

Los dos subproyectos son autónomos: se compilan y ejecutan por separado, sin
build agregador que los una.

## Arranque rápido

```bash
# Versión Java
cd java && mvn clean package && java -jar target/word-to-markdown.jar

# Versión Kotlin
cd kotlin && ./gradlew run
```

Ambas requieren **JDK 21** o superior. Consulta el README de cada subproyecto
para el detalle de requisitos, pruebas y empaquetado.
