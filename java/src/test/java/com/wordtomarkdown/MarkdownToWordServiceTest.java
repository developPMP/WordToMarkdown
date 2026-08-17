package com.wordtomarkdown;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Conversión de Markdown a documentos Word")
class MarkdownToWordServiceTest {

    /** PNG de 8x8 píxeles, para comprobar que la imagen se incrusta. */
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAFUlEQVR42mNk"
            + "+M9AGWA0gGGA0QAGAJvGAwXFVvKZAAAAAElFTkSuQmCC");

    private final MarkdownToWordService service = new MarkdownToWordService();

    private Path markdown(Path folder, String fileName, String content) throws IOException {
        return Files.writeString(folder.resolve(fileName), content, StandardCharsets.UTF_8);
    }

    private XWPFDocument open(java.io.File docx) throws IOException {
        try (InputStream content = Files.newInputStream(docx.toPath())) {
            return new XWPFDocument(content);
        }
    }

    /** Todo el texto del documento, párrafo a párrafo. */
    private List<String> paragraphsOf(XWPFDocument document) {
        return document.getParagraphs().stream().map(XWPFParagraph::getText).toList();
    }

    @Test
    @DisplayName("escribe el .docx junto al Markdown de origen")
    void generaDocumentoJuntoAlOrigen(@TempDir Path folder) throws IOException {
        Path md = markdown(folder, "Informe.md", "# Título\n\nPrimer párrafo.\n");

        ConversionResult result = service.convert(md.toFile());

        assertTrue(result.success(), () -> "esperaba éxito, error: " + result.errorMessage());
        assertEquals(folder.resolve("Informe.docx").toFile(), result.output());
        assertTrue(result.output().isFile());
        assertEquals(ConversionDirection.MARKDOWN_TO_WORD, result.direction());

        try (XWPFDocument document = open(result.output())) {
            assertTrue(paragraphsOf(document).contains("Título"), paragraphsOf(document).toString());
            assertTrue(paragraphsOf(document).contains("Primer párrafo."), paragraphsOf(document).toString());
        }
    }

    @Test
    @DisplayName("los encabezados llevan el estilo de Word, no negritas imitándolo")
    void encabezadosConEstiloDeWord(@TempDir Path folder) throws IOException {
        Path md = markdown(folder, "Guia.md", "# Uno\n\n## Dos\n\n### Tres\n\nTexto normal.\n");

        ConversionResult result = service.convert(md.toFile());

        try (XWPFDocument document = open(result.output())) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            assertEquals("Heading1", paragraphs.get(0).getStyle());
            assertEquals("Heading2", paragraphs.get(1).getStyle());
            assertEquals("Heading3", paragraphs.get(2).getStyle());
            assertEquals("Normal", paragraphs.get(3).getStyle());

            // El estilo tiene que existir en el documento para que Word lo aplique
            assertNotNull(document.getStyles().getStyle("Heading1"));
        }
    }

    @Test
    @DisplayName("no sobrescribe un .docx que ya exista: numera el nuevo")
    void noPisaElDocumentoOriginal(@TempDir Path folder) throws IOException {
        Path original = DocxFixtures.simpleDocument(folder, "Informe.docx", "documento original");
        byte[] antes = Files.readAllBytes(original);
        Path md = markdown(folder, "Informe.md", "# Otra cosa\n");

        ConversionResult result = service.convert(md.toFile());

        assertTrue(result.success());
        assertEquals(folder.resolve("Informe (2).docx").toFile(), result.output());
        assertArrayEqualsMessage(antes, Files.readAllBytes(original));
    }

    private void assertArrayEqualsMessage(byte[] expected, byte[] actual) {
        assertTrue(java.util.Arrays.equals(expected, actual), "el documento original no debe tocarse");
    }

    @Test
    @DisplayName("las listas usan la numeración de Word y respetan el anidamiento")
    void listasConNumeracionDeWord(@TempDir Path folder) throws IOException {
        Path md = markdown(folder, "Lista.md", """
            - Primero
            - Segundo
              - Anidado

            1. Uno
            2. Dos
            """);

        ConversionResult result = service.convert(md.toFile());

        try (XWPFDocument document = open(result.output())) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            assertTrue(paragraphs.stream().allMatch(p -> p.getNumID() != null),
                "todos los párrafos deberían pertenecer a una lista");

            XWPFParagraph anidado = paragraphs.stream()
                .filter(p -> "Anidado".equals(p.getText())).findFirst().orElseThrow();
            assertEquals(1, anidado.getNumIlvl().intValue(), "el nivel de anidamiento no se conserva");

            // La lista ordenada estrena numeración para no seguir contando desde la anterior
            XWPFParagraph uno = paragraphs.stream()
                .filter(p -> "Uno".equals(p.getText())).findFirst().orElseThrow();
            XWPFParagraph primero = paragraphs.get(0);
            assertFalse(uno.getNumID().equals(primero.getNumID()));
        }
    }

    @Test
    @DisplayName("una tabla de Markdown se convierte en una tabla de Word")
    void tablaDeVerdad(@TempDir Path folder) throws IOException {
        Path md = markdown(folder, "Tabla.md", """
            | Columna | Valor |
            |---|---|
            | Alfa | 1 |
            | Beta | 2 |
            """);

        ConversionResult result = service.convert(md.toFile());

        try (XWPFDocument document = open(result.output())) {
            assertEquals(1, document.getTables().size());
            XWPFTable table = document.getTables().get(0);
            assertEquals(3, table.getNumberOfRows(), "encabezado más dos filas");
            assertEquals("Columna", table.getRow(0).getCell(0).getText());
            assertEquals("2", table.getRow(2).getCell(1).getText());
        }
    }

    @Test
    @DisplayName("las imágenes se incrustan en el documento")
    void incrustaImagenes(@TempDir Path folder) throws IOException {
        Files.createDirectory(folder.resolve("Informe_images"));
        Files.write(folder.resolve("Informe_images/image1.png"), PNG);
        Path md = markdown(folder, "Informe.md", "![imagen 1](Informe_images/image1.png)\n");

        ConversionResult result = service.convert(md.toFile());

        assertTrue(result.success());
        assertEquals(1, result.imagesProcessed());
        assertFalse(result.hasImageErrors());

        try (XWPFDocument document = open(result.output())) {
            assertEquals(1, document.getAllPictures().size());
        }
    }

    @Test
    @DisplayName("una imagen que falta no aborta el documento, pero se detalla")
    void imagenQueFaltaNoAbortaElDocumento(@TempDir Path folder) throws IOException {
        Path md = markdown(folder, "Informe.md",
            "Texto.\n\n![diagrama](Informe_images/no-esta.png)\n");

        ConversionResult result = service.convert(md.toFile());

        assertTrue(result.success());
        assertEquals(0, result.imagesProcessed());
        assertTrue(result.hasImageErrors());
        assertTrue(result.imageErrors().get(0).contains("no se encontró el archivo"),
            result.imageErrors().toString());

        try (XWPFDocument document = open(result.output())) {
            assertTrue(paragraphsOf(document).stream().anyMatch(p -> p.contains("[imagen no insertada: diagrama]")),
                paragraphsOf(document).toString());
        }
    }

    @Test
    @DisplayName("los enlaces del índice apuntan a marcadores de los encabezados")
    void enlacesInternosNavegables(@TempDir Path folder) throws IOException {
        Path md = markdown(folder, "Manual.md", """
            - [Introducción](#introducción)

            # Introducción

            Contenido.
            """);

        ConversionResult result = service.convert(md.toFile());

        try (XWPFDocument document = open(result.output())) {
            String anchor = document.getParagraphs().stream()
                .flatMap(p -> p.getCTP().getHyperlinkList().stream())
                .map(h -> h.getAnchor())
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
            assertNotNull(anchor, "el enlace interno no se convirtió en referencia a un marcador");

            String bookmark = document.getParagraphs().stream()
                .flatMap(p -> p.getCTP().getBookmarkStartList().stream())
                .map(b -> b.getName())
                .findFirst().orElse(null);
            assertEquals(bookmark, anchor, "el enlace no apunta al marcador del encabezado");
        }
    }

    @Test
    @DisplayName("un enlace externo se convierte en hipervínculo")
    void enlaceExterno(@TempDir Path folder) throws IOException {
        Path md = markdown(folder, "Enlaces.md", "Ver [la web](https://ejemplo.test/doc).\n");

        ConversionResult result = service.convert(md.toFile());

        try (XWPFDocument document = open(result.output())) {
            assertTrue(java.util.Arrays.stream(document.getHyperlinks())
                    .anyMatch(h -> "https://ejemplo.test/doc".equals(h.getURL())),
                "no se registró el hipervínculo");
        }
    }

    @Test
    @DisplayName("negrita, cursiva y código llegan como formato de Word")
    void formatoDelTexto(@TempDir Path folder) throws IOException {
        Path md = markdown(folder, "Formato.md", "Esto es **negrita**, *cursiva* y `código`.\n");

        ConversionResult result = service.convert(md.toFile());

        try (XWPFDocument document = open(result.output())) {
            XWPFParagraph paragraph = document.getParagraphs().get(0);
            assertEquals("Esto es negrita, cursiva y código.", paragraph.getText());
            assertTrue(paragraph.getRuns().stream().anyMatch(r -> "negrita".equals(r.text()) && r.isBold()));
            assertTrue(paragraph.getRuns().stream().anyMatch(r -> "cursiva".equals(r.text()) && r.isItalic()));
            assertTrue(paragraph.getRuns().stream()
                    .anyMatch(r -> "código".equals(r.text()) && "CodeChar".equals(r.getStyle())),
                "el código en línea debería llevar su estilo de carácter");
        }
    }

    @Test
    @DisplayName("una cita y un bloque de código conservan su forma")
    void citaYBloqueDeCodigo(@TempDir Path folder) throws IOException {
        Path md = markdown(folder, "Bloques.md", """
            > Una cita.

            ```java
            int a = 1;
            int b = 2;
            ```
            """);

        ConversionResult result = service.convert(md.toFile());

        try (XWPFDocument document = open(result.output())) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            assertEquals("Quote", paragraphs.get(0).getStyle());

            XWPFParagraph code = paragraphs.get(1);
            assertEquals("CodeBlock", code.getStyle());
            // Las dos líneas van en un solo párrafo, separadas por un salto
            assertTrue(code.getText().contains("int a = 1;"), code.getText());
            assertTrue(code.getText().contains("int b = 2;"), code.getText());
        }
    }

    @Test
    @DisplayName("un .docx renombrado a .md falla diciendo qué es en realidad")
    void archivoQueNoEsTexto(@TempDir Path folder) throws IOException {
        Path docx = DocxFixtures.simpleDocument(folder, "real.docx", "contenido");
        Path falso = folder.resolve("Informe.md");
        Files.write(falso, Files.readAllBytes(docx));

        ConversionResult result = service.convert(falso.toFile());

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("extensión cambiada"), result.errorMessage());
    }

    @Test
    @DisplayName("ida y vuelta: lo convertido a Markdown se puede devolver a Word")
    void idaYVuelta(@TempDir Path folder) throws IOException {
        Path docx = DocxFixtures.documentWithIndexAndTable(folder, "Manual.docx");

        ConversionResult ida = new ConversionService().convert(docx.toFile());
        assertTrue(ida.success(), () -> "la ida falló: " + ida.errorMessage());

        ConversionResult vuelta = service.convert(ida.output());

        assertTrue(vuelta.success(), () -> "la vuelta falló: " + vuelta.errorMessage());
        // El .docx de partida sigue donde estaba: el nuevo se numera
        assertEquals(folder.resolve("Manual (2).docx").toFile(), vuelta.output());

        try (XWPFDocument document = open(vuelta.output())) {
            assertFalse(document.getParagraphs().isEmpty());
            assertTrue(document.getParagraphs().stream()
                    .anyMatch(p -> p.getStyle() != null && p.getStyle().startsWith("Heading")),
                "los encabezados deberían volver con su estilo");
        }
    }

    @Test
    @DisplayName("en una carpeta encuentra los .md y .markdown, ordenados y sin ocultos")
    void buscaMarkdownEnLaCarpeta(@TempDir Path folder) throws IOException {
        markdown(folder, "Beta.md", "b");
        markdown(folder, "alfa.markdown", "a");
        markdown(folder, ".oculto.md", "x");
        markdown(folder, "notas.txt", "no");

        List<java.io.File> found = service.findInputFiles(folder.toFile());

        assertEquals(List.of("alfa.markdown", "Beta.md"),
            found.stream().map(java.io.File::getName).toList());
    }

    @Test
    @DisplayName("el HTML incrustado se descarta con aviso, sin perder el resto")
    void avisaDelHtmlIncrustado(@TempDir Path folder) throws IOException {
        Path md = markdown(folder, "Mixto.md", "<div>algo</div>\n\nTexto que sí vale.\n");

        ConversionResult result = service.convert(md.toFile());

        assertTrue(result.success());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("HTML")), result.warnings().toString());

        try (XWPFDocument document = open(result.output())) {
            assertTrue(paragraphsOf(document).contains("Texto que sí vale."));
        }
    }
}
