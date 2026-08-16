package com.wordtomarkdown

import java.io.File
import java.nio.file.Path

/**
 * Resultado de convertir un documento. Describe qué ocurrió en forma de datos,
 * sin decidir cómo mostrarlo: de eso se encarga la interfaz.
 *
 * @param source          documento .docx de origen
 * @param output          archivo .md destino (existe solo si [success])
 * @param success         true si el Markdown se escribió correctamente
 * @param errorMessage    causa del fallo, o null si tuvo éxito
 * @param warnings        advertencias emitidas por Mammoth durante la lectura
 * @param imagesExtracted número de imágenes escritas con éxito
 * @param imageErrors     descripción de cada imagen que no se pudo extraer
 * @param imageDir        carpeta donde se extraen las imágenes
 */
data class ConversionResult(
    val source: File,
    val output: File,
    val success: Boolean,
    val errorMessage: String? = null,
    val warnings: List<String> = emptyList(),
    val imagesExtracted: Int = 0,
    val imageErrors: List<String> = emptyList(),
    val imageDir: Path,
) {
    /** true si el documento se convirtió pero alguna de sus imágenes se quedó fuera. */
    val hasImageErrors: Boolean get() = imageErrors.isNotEmpty()

    companion object {
        fun failure(source: File, output: File, imageDir: Path, errorMessage: String) =
            ConversionResult(
                source = source,
                output = output,
                success = false,
                errorMessage = errorMessage,
                imageDir = imageDir,
            )
    }
}
