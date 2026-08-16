package com.wordtomarkdown;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Resultado de convertir un documento. Describe qué ocurrió en forma de datos,
 * sin decidir cómo mostrarlo: de eso se encarga la interfaz.
 *
 * @param source          documento .docx de origen
 * @param output          archivo .md destino (existe solo si {@code success})
 * @param success         true si el Markdown se escribió correctamente
 * @param errorMessage    causa del fallo, o null si tuvo éxito
 * @param warnings        advertencias emitidas por Mammoth durante la lectura
 * @param imagesExtracted número de imágenes escritas con éxito
 * @param imageErrors     descripción de cada imagen que no se pudo extraer
 * @param imageDir        carpeta donde se extraen las imágenes
 */
public record ConversionResult(
    File source,
    File output,
    boolean success,
    String errorMessage,
    List<String> warnings,
    int imagesExtracted,
    List<String> imageErrors,
    Path imageDir
) {

    public ConversionResult {
        warnings = List.copyOf(warnings);
        imageErrors = List.copyOf(imageErrors);
    }

    public static ConversionResult failure(File source, File output, Path imageDir, String errorMessage) {
        return new ConversionResult(source, output, false, errorMessage, List.of(), 0, List.of(), imageDir);
    }

    /** true si el documento se convirtió pero alguna de sus imágenes se quedó fuera. */
    public boolean hasImageErrors() {
        return !imageErrors.isEmpty();
    }
}
