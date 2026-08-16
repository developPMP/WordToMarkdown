package com.wordtomarkdown;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Conversión de documentos a Markdown")
class ConversionServiceTest {

    private final ConversionService service = new ConversionService();

    @Test
    @DisplayName("escribe el .md junto al documento de origen")
    void generaMarkdownJuntoAlOrigen(@TempDir Path folder) throws IOException {
        Path docx = DocxFixtures.simpleDocument(folder, "Informe.docx", "Primer parrafo");

        ConversionResult result = service.convert(docx.toFile());

        assertTrue(result.success(), () -> "esperaba éxito, error: " + result.errorMessage());
        assertEquals(folder.resolve("Informe.md").toFile(), result.output());
        assertTrue(result.output().isFile());

        String markdown = Files.readString(result.output().toPath(), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("Primer parrafo"), markdown);
        assertTrue(markdown.contains("**Texto en negrita**"), markdown);
    }

    @Test
    @DisplayName("respeta la extensión en mayúsculas al nombrar la salida")
    void aceptaExtensionEnMayusculas(@TempDir Path folder) throws IOException {
        Path docx = DocxFixtures.simpleDocument(folder, "Gamma.DOCX", "contenido");

        ConversionResult result = service.convert(docx.toFile());

        assertTrue(result.success());
        assertEquals("Gamma.md", result.output().getName());
    }

    @Test
    @DisplayName("extrae las imágenes a {nombre}_images y las enlaza con rutas relativas")
    void extraeImagenes(@TempDir Path folder) throws IOException {
        Path docx = DocxFixtures.documentWithImages(folder, "ConImagenes.docx");

        ConversionResult result = service.convert(docx.toFile());

        assertTrue(result.success(), () -> "error: " + result.errorMessage());
        assertEquals(2, result.imagesExtracted());
        assertFalse(result.hasImageErrors());
        assertTrue(Files.isRegularFile(folder.resolve("ConImagenes_images/image1.png")));
        assertTrue(Files.isRegularFile(folder.resolve("ConImagenes_images/image2.png")));

        String markdown = Files.readString(result.output().toPath(), StandardCharsets.UTF_8);
        // La barra del enlace es siempre "/" aunque el separador del sistema sea "\"
        assertTrue(markdown.contains("(ConImagenes_images/image1.png)"), markdown);
    }

    @Test
    @DisplayName("conserva el alt text real y descarta el aviso generado por la IA de Word")
    void limpiaElAltTextDeLaIa(@TempDir Path folder) throws IOException {
        Path docx = DocxFixtures.documentWithImages(folder, "ConImagenes.docx");

        ConversionResult result = service.convert(docx.toFile());
        String markdown = Files.readString(result.output().toPath(), StandardCharsets.UTF_8);

        assertTrue(markdown.contains("![Diagrama de arquitectura]"), markdown);
        assertFalse(markdown.toLowerCase().contains("generado por ia"), markdown);
        assertTrue(markdown.contains("![imagen 2]"), markdown);
    }

    @Test
    @DisplayName("si una imagen no se puede extraer, lo reporta y la marca en el Markdown")
    void reportaImagenesQueFallan(@TempDir Path folder) throws IOException {
        Path docx = DocxFixtures.documentWithImages(folder, "ConImagenes.docx");
        // Un archivo normal con el nombre de la carpeta de imágenes impide crearla
        Files.writeString(folder.resolve("ConImagenes_images"), "bloqueado", StandardCharsets.UTF_8);

        ConversionResult result = service.convert(docx.toFile());

        assertTrue(result.success(), "el documento debe convertirse aunque falten imágenes");
        assertEquals(0, result.imagesExtracted(), "ninguna imagen debió extraerse");
        assertEquals(2, result.imageErrors().size());

        // La numeración es correlativa: sin doble incremento del contador
        assertTrue(result.imageErrors().get(0).startsWith("imagen 1"), result.imageErrors().toString());
        assertTrue(result.imageErrors().get(1).startsWith("imagen 2"), result.imageErrors().toString());
        // El mensaje identifica el tipo de fallo, no solo la ruta
        assertTrue(result.imageErrors().get(0).contains("Exception"), result.imageErrors().toString());

        String markdown = Files.readString(result.output().toPath(), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("imagen 1 (no se pudo extraer)"), markdown);
    }

    @Test
    @DisplayName("un documento ilegible falla sin dejar Markdown a medias")
    void documentoCorrupto(@TempDir Path folder) throws IOException {
        Path docx = DocxFixtures.corruptDocument(folder, "Corrupto.docx");

        ConversionResult result = service.convert(docx.toFile());

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
        assertFalse(result.output().exists(), "no debe quedar un .md incompleto");
    }

    @Test
    @DisplayName("un documento no altera a los demás de la carpeta")
    void loteConDocumentoCorrupto(@TempDir Path folder) throws IOException {
        DocxFixtures.simpleDocument(folder, "Alpha.docx", "alpha");
        DocxFixtures.corruptDocument(folder, "Corrupto.docx");
        DocxFixtures.simpleDocument(folder, "Beta.docx", "beta");

        int convertidos = 0;
        int fallidos = 0;
        for (var docx : service.findDocxFiles(folder.toFile())) {
            if (service.convert(docx).success()) {
                convertidos++;
            } else {
                fallidos++;
            }
        }

        assertEquals(2, convertidos);
        assertEquals(1, fallidos);
        assertTrue(Files.isRegularFile(folder.resolve("Alpha.md")));
        assertTrue(Files.isRegularFile(folder.resolve("Beta.md")));
        assertFalse(Files.exists(folder.resolve("Corrupto.md")));
    }

    @Test
    @DisplayName("baseNameOf quita la extensión sin distinguir mayúsculas")
    void nombreBase(@TempDir Path folder) {
        assertEquals("Informe", service.baseNameOf(folder.resolve("Informe.docx").toFile()));
        assertEquals("Informe", service.baseNameOf(folder.resolve("Informe.DOCX").toFile()));
        assertEquals("Informe.txt", service.baseNameOf(folder.resolve("Informe.txt").toFile()));
    }
}
