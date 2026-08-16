package com.wordtomarkdown;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Líneas de registro de una conversión")
class ConversionReporterTest {

    private final ConversionReporter reporter = new ConversionReporter();

    private static final File ORIGEN = new File("/docs/Informe.docx");
    private static final File SALIDA = new File("/docs/Informe.md");
    private static final Path IMAGENES = Path.of("/docs/Informe_images");

    private ConversionResult exito(int imagenes, List<String> erroresImagen, List<String> avisos) {
        return new ConversionResult(ORIGEN, SALIDA, true, null, avisos, imagenes, erroresImagen, IMAGENES);
    }

    @Test
    @DisplayName("una conversión limpia muestra origen, salida y OK")
    void conversionLimpia() {
        List<String> lineas = reporter.describe(exito(0, List.of(), List.of()));

        assertEquals(List.of(
            "Convirtiendo: Informe.docx",
            "  Salida: " + SALIDA.getAbsolutePath(),
            "  OK: Informe.md"
        ), lineas);
    }

    @Test
    @DisplayName("informa del número de imágenes extraídas y su carpeta")
    void informaImagenes() {
        List<String> lineas = reporter.describe(exito(3, List.of(), List.of()));

        assertTrue(lineas.contains("  Imágenes extraídas: 3 → Informe_images/"), lineas.toString());
    }

    @Test
    @DisplayName("detalla las imágenes fallidas y lo resume en la línea de resultado")
    void detallaImagenesFallidas() {
        List<String> lineas = reporter.describe(
            exito(1, List.of("imagen 2: IOException - disco lleno"), List.of()));

        assertTrue(lineas.contains("  No se pudieron extraer 1 imagen(es):"), lineas.toString());
        assertTrue(lineas.contains("    [!] imagen 2: IOException - disco lleno"), lineas.toString());
        assertTrue(lineas.contains("  OK: Informe.md (con 1 imagen(es) sin extraer)"), lineas.toString());
    }

    @Test
    @DisplayName("muestra las advertencias de Mammoth")
    void muestraAdvertencias() {
        List<String> lineas = reporter.describe(exito(0, List.of(), List.of("estilo desconocido")));

        assertTrue(lineas.contains("  Advertencias durante la conversión:"), lineas.toString());
        assertTrue(lineas.contains("    [!] estilo desconocido"), lineas.toString());
    }

    @Test
    @DisplayName("un documento fallido muestra el error y ningún OK")
    void documentoFallido() {
        ConversionResult fallo = ConversionResult.failure(ORIGEN, SALIDA, IMAGENES, "ZipException - roto");

        List<String> lineas = reporter.describe(fallo);

        assertTrue(lineas.contains("  ERROR: ZipException - roto"), lineas.toString());
        assertFalse(lineas.stream().anyMatch(l -> l.startsWith("  OK:")), lineas.toString());
    }

    @Test
    @DisplayName("el resumen del lote cuenta convertidos y fallidos")
    void resumenDelLote() {
        assertTrue(reporter.summarize(3, 1).contains("Resumen: 3 convertido(s), 1 con error."));
    }
}
