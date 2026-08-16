package com.wordtomarkdown

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import org.zwobble.mammoth.DocumentConverter
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Conversión de documentos Word a Markdown. No depende de la interfaz gráfica,
 * de modo que puede usarse y probarse por separado.
 *
 * El flujo es `.docx → [Mammoth] → HTML → [flexmark] → .md`, ya que la librería
 * Java de Mammoth no ofrece conversión directa a Markdown.
 */
class ConversionService {

    /** true si el archivo es un documento Word convertible. */
    fun isDocx(file: File?): Boolean =
        file != null && file.isFile && file.name.hasDocxExtension() && !file.name.isWordTempFile()

    /**
     * Documentos .docx directamente contenidos en la carpeta, ordenados por nombre.
     * No se recorren las subcarpetas.
     *
     * Se ignoran los archivos temporales que Word deja al abrir un documento
     * ("~$nombre.docx"), que no son documentos válidos.
     */
    fun findDocxFiles(folder: File?): List<File> {
        if (folder == null || !folder.isDirectory) return emptyList()
        return folder.listFiles { f: File ->
            f.isFile && f.name.hasDocxExtension() && !f.name.isWordTempFile()
        }.orEmpty().sortedBy { it.name.lowercase() }
    }

    /** Nombre del documento sin la extensión .docx. */
    fun baseNameOf(docx: File): String =
        if (docx.name.hasDocxExtension()) docx.name.dropLast(DOCX_EXTENSION.length) else docx.name

    /**
     * Convierte un documento y escribe el .md junto al original. Nunca lanza:
     * los fallos se describen en el [ConversionResult] devuelto.
     */
    fun convert(docx: File): ConversionResult {
        val baseName = baseNameOf(docx)
        val parent = docx.absoluteFile.parentFile
        val output = File(parent, "$baseName.md")
        val imageDir = parent.toPath().resolve("$baseName$IMAGE_DIR_SUFFIX")

        // Numera las imágenes encontradas (para nombres únicos) y contabiliza
        // por separado las extraídas con éxito y las que fallaron.
        var imageCount = 0
        var imagesOk = 0
        val imageErrors = mutableListOf<String>()

        return try {
            val converter = DocumentConverter().imageConverter { image ->
                val index = ++imageCount
                var imgName: String? = null
                try {
                    Files.createDirectories(imageDir)
                    imgName = "image$index.${extensionFor(image.contentType)}"
                    image.inputStream.use { input ->
                        Files.copy(input, imageDir.resolve(imgName))
                    }
                    imagesOk++
                    mapOf(
                        "src" to "$baseName$IMAGE_DIR_SUFFIX/$imgName",
                        "alt" to cleanAltText(image.altText.orElse(null), index),
                    )
                } catch (ex: Exception) {
                    // Una imagen ilegible no debe abortar el documento, pero tampoco
                    // puede pasar inadvertida: queda anotada y señalada en el Markdown.
                    imageErrors += describeImageError(index, imgName, ex)
                    mapOf(
                        "src" to "",
                        "alt" to "imagen $index (no se pudo extraer)",
                    )
                }
            }

            val htmlResult = converter.convertToHtml(docx)
            val markdown = FlexmarkHtmlConverter.builder().build().convert(htmlResult.value)
            Files.writeString(output.toPath(), markdown, Charsets.UTF_8)

            ConversionResult(
                source = docx,
                output = output,
                success = true,
                warnings = htmlResult.warnings.toList(),
                imagesExtracted = imagesOk,
                imageErrors = imageErrors.toList(),
                imageDir = imageDir,
            )
        } catch (ex: Exception) {
            ConversionResult.failure(docx, output, imageDir, messageOf(ex))
        }
    }

    /** Extensión a partir del tipo MIME, normalizando compuestos como "svg+xml". */
    private fun extensionFor(contentType: String?): String {
        if (contentType == null || "/" !in contentType) return "png"
        return contentType.substringAfter("/").substringBefore("+")
    }

    /**
     * Alt text utilizable: descarta el vacío y los avisos que añade automáticamente
     * la IA de Word, sustituyéndolos por un texto genérico.
     */
    private fun cleanAltText(altText: String?, index: Int): String {
        val trimmed = altText?.trim().orEmpty()
        val generatedByAi = AI_ALT_TEXT_MARKERS.any { it in trimmed.lowercase() }
        return if (trimmed.isNotBlank() && !generatedByAi) trimmed else "imagen $index"
    }

    private fun describeImageError(index: Int, imgName: String?, ex: Exception): String =
        "imagen $index" + (imgName?.let { " ($it)" }.orEmpty()) + ": " + messageOf(ex)

    /**
     * Algunas excepciones de E/S solo traen la ruta como mensaje
     * (e.g. FileAlreadyExistsException), así que se indica también el tipo.
     */
    private fun messageOf(ex: Exception): String =
        ex.javaClass.simpleName + (ex.message?.let { " - $it" }.orEmpty())

    private fun String.hasDocxExtension() = endsWith(DOCX_EXTENSION, ignoreCase = true)

    /** Archivos "~$nombre.docx" (temporales de Word) y ocultos. */
    private fun String.isWordTempFile() = startsWith("~$") || startsWith(".")

    private companion object {
        const val DOCX_EXTENSION = ".docx"

        /** Sufijo de la carpeta donde se extraen las imágenes de un documento. */
        const val IMAGE_DIR_SUFFIX = "_images"

        /** Fragmentos de alt text con los que Word marca las descripciones generadas por IA. */
        val AI_ALT_TEXT_MARKERS = listOf(
            "ia puede ser",
            "generated by ai",
            "generado por ia",
        )
    }
}
