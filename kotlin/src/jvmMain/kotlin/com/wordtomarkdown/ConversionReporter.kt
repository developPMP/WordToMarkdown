package com.wordtomarkdown

/**
 * Traduce un [ConversionResult] a las líneas que se muestran en el registro.
 * Al no depender de la interfaz puede probarse sin abrir la ventana.
 */
class ConversionReporter {

    /** Líneas correspondientes a un documento convertido (o fallido). */
    fun describe(result: ConversionResult): List<String> = buildList {
        add("Convirtiendo: ${result.source.name}")
        add("  Salida: ${result.output.absolutePath}")

        if (!result.success) {
            add("  ERROR: ${result.errorMessage}")
            return@buildList
        }

        if (result.warnings.isNotEmpty()) {
            add("  Advertencias durante la conversión:")
            result.warnings.forEach { add("    [!] $it") }
        }
        if (result.imagesExtracted > 0) {
            add("  Imágenes extraídas: ${result.imagesExtracted} → ${result.imageDir.fileName}/")
        }
        if (result.hasImageErrors) {
            add("  No se pudieron extraer ${result.imageErrors.size} imagen(es):")
            result.imageErrors.forEach { add("    [!] $it") }
        }

        val sufijo = if (result.hasImageErrors) {
            " (con ${result.imageErrors.size} imagen(es) sin extraer)"
        } else {
            ""
        }
        add("  OK: ${result.output.name}$sufijo")
    }

    /** Línea final de un lote de varios documentos. */
    fun summarize(converted: Int, failed: Int): List<String> = listOf(
        "----------------------------------------",
        "Resumen: $converted convertido(s), $failed con error.",
    )
}
