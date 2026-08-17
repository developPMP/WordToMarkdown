package com.wordtomarkdown;

import java.util.ArrayList;
import java.util.List;

/**
 * Traduce un {@link ConversionResult} a las líneas que se muestran en el registro.
 * Al no depender de Swing puede probarse sin abrir la ventana.
 */
public class ConversionReporter {

    /** Líneas correspondientes a un documento convertido (o fallido). */
    public List<String> describe(ConversionResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("Convirtiendo: " + result.source().getName());
        lines.add("  Salida: " + result.output().getAbsolutePath());

        if (!result.success()) {
            lines.add("  ERROR: " + result.errorMessage());
            return lines;
        }

        if (!result.warnings().isEmpty()) {
            lines.add("  Advertencias durante la conversión:");
            result.warnings().forEach(w -> lines.add("    [!] " + w));
        }
        if (result.imagesProcessed() > 0) {
            lines.add("  " + imagesDone(result) + ": " + result.imagesProcessed()
                + (result.imageDir() != null ? " → " + result.imageDir().getFileName() + "/" : ""));
        }
        if (result.hasImageErrors()) {
            lines.add("  No se pudieron " + imageVerb(result) + " " + result.imageErrors().size() + " imagen(es):");
            result.imageErrors().forEach(m -> lines.add("    [!] " + m));
        }

        lines.add("  OK: " + result.output().getName()
            + (result.hasImageErrors()
                ? " (con " + result.imageErrors().size() + " imagen(es) sin " + imageVerb(result) + ")"
                : ""));
        return lines;
    }

    /** Las imágenes se extraen del .docx en un sentido y se insertan en él en el otro. */
    private String imagesDone(ConversionResult result) {
        return result.direction() == ConversionDirection.MARKDOWN_TO_WORD
            ? "Imágenes insertadas"
            : "Imágenes extraídas";
    }

    private String imageVerb(ConversionResult result) {
        return result.direction() == ConversionDirection.MARKDOWN_TO_WORD ? "insertar" : "extraer";
    }

    /** Línea final de un lote de varios documentos. */
    public List<String> summarize(int converted, int failed) {
        return List.of(
            "----------------------------------------",
            "Resumen: " + converted + " convertido(s), " + failed + " con error.");
    }
}
