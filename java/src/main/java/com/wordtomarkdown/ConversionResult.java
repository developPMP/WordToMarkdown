package com.wordtomarkdown;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Resultado de convertir un documento. Describe qué ocurrió en forma de datos,
 * sin decidir cómo mostrarlo: de eso se encarga la interfaz.
 *
 * @param direction       sentido en el que se convirtió
 * @param source          archivo de origen (.docx o .md, según el sentido)
 * @param output          archivo generado (existe solo si {@code success})
 * @param success         true si el archivo de salida se escribió correctamente
 * @param errorMessage    causa del fallo, o null si tuvo éxito
 * @param warnings        advertencias emitidas durante la lectura del origen
 * @param imagesProcessed imágenes tratadas con éxito: extraídas del .docx o
 *                        insertadas en él, según el sentido
 * @param imageErrors     descripción de cada imagen que se quedó fuera
 * @param imageDir        carpeta donde se extraen las imágenes, o null cuando se
 *                        insertan en el documento y no hace falta ninguna
 */
public record ConversionResult(
    ConversionDirection direction,
    File source,
    File output,
    boolean success,
    String errorMessage,
    List<String> warnings,
    int imagesProcessed,
    List<String> imageErrors,
    Path imageDir
) {

    public ConversionResult {
        warnings = List.copyOf(warnings);
        imageErrors = List.copyOf(imageErrors);
    }

    public static ConversionResult failure(ConversionDirection direction, File source, File output,
                                           Path imageDir, String errorMessage) {
        return new ConversionResult(direction, source, output, false, errorMessage,
            List.of(), 0, List.of(), imageDir);
    }

    /** true si el documento se convirtió pero alguna de sus imágenes se quedó fuera. */
    public boolean hasImageErrors() {
        return !imageErrors.isEmpty();
    }
}
