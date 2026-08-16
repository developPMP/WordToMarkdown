package com.wordtomarkdown;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Numeración automática de los encabezados de Word")
class WordNumberingTest {

    private final WordNumbering numbering = new WordNumbering();

    @Test
    @DisplayName("calcula el número de cada encabezado según su nivel")
    void calculaLaNumeracion(@TempDir Path folder) throws IOException {
        Path docx = DocxFixtures.documentWithAutomaticNumbering(folder, "Numerado.docx");

        List<WordNumbering.NumberedHeading> headings = numbering.readFrom(docx.toFile());

        assertEquals(List.of("1", "2", "2.1", "2.1.1"),
            headings.stream().map(WordNumbering.NumberedHeading::number).toList());
        assertEquals(List.of("Introduccion", "Analisis", "Alcance", "Riesgos"),
            headings.stream().map(WordNumbering.NumberedHeading::text).toList());
    }

    @Test
    @DisplayName("los encabezados sin lista asociada se quedan fuera")
    void encabezadosSinNumerar(@TempDir Path folder) throws IOException {
        Path docx = DocxFixtures.documentWithAutomaticNumbering(folder, "Numerado.docx");

        List<WordNumbering.NumberedHeading> headings = numbering.readFrom(docx.toFile());

        assertTrue(headings.stream().noneMatch(h -> h.text().equals("Anexo sin numerar")),
            headings.toString());
    }

    @Test
    @DisplayName("un documento sin numeración no aporta nada")
    void documentoSinNumeracion(@TempDir Path folder) throws IOException {
        Path docx = DocxFixtures.documentWithIndexAndTable(folder, "Manual.docx");

        assertEquals(List.of(), numbering.readFrom(docx.toFile()));
    }

    @Test
    @DisplayName("un archivo ilegible no rompe la conversión: simplemente no numera")
    void archivoIlegible(@TempDir Path folder) throws IOException {
        Path corrupto = DocxFixtures.corruptDocument(folder, "Corrupto.docx");

        assertEquals(List.of(), numbering.readFrom(corrupto.toFile()));
        assertEquals(List.of(), numbering.readFrom(folder.resolve("no-existe.docx").toFile()));
    }
}
