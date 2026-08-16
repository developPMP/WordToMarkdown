package com.wordtomarkdown;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Selección de documentos en una carpeta")
class FindDocxFilesTest {

    private final ConversionService service = new ConversionService();

    @Test
    @DisplayName("devuelve los .docx ordenados por nombre sin distinguir mayúsculas")
    void devuelveDocxOrdenados(@TempDir Path folder) throws IOException {
        DocxFixtures.simpleDocument(folder, "beta.docx", "b");
        DocxFixtures.simpleDocument(folder, "Alpha.docx", "a");
        DocxFixtures.simpleDocument(folder, "Gamma.DOCX", "g");

        List<String> nombres = service.findDocxFiles(folder.toFile()).stream().map(File::getName).toList();

        assertEquals(List.of("Alpha.docx", "beta.docx", "Gamma.DOCX"), nombres);
    }

    @Test
    @DisplayName("ignora los archivos temporales que deja Word abierto (~$)")
    void ignoraTemporalesDeWord(@TempDir Path folder) throws IOException {
        DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido");
        Files.writeString(folder.resolve("~$Informe.docx"), "lock", StandardCharsets.UTF_8);

        List<File> encontrados = service.findDocxFiles(folder.toFile());

        assertEquals(1, encontrados.size());
        assertEquals("Informe.docx", encontrados.get(0).getName());
    }

    @Test
    @DisplayName("ignora archivos que no son .docx")
    void ignoraOtrasExtensiones(@TempDir Path folder) throws IOException {
        DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido");
        Files.writeString(folder.resolve("notas.txt"), "hola", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("hoja.xlsx"), "datos", StandardCharsets.UTF_8);

        assertEquals(1, service.findDocxFiles(folder.toFile()).size());
    }

    @Test
    @DisplayName("no recorre las subcarpetas")
    void noEsRecursivo(@TempDir Path folder) throws IOException {
        DocxFixtures.simpleDocument(folder, "Raiz.docx", "raiz");
        Path sub = Files.createDirectory(folder.resolve("sub"));
        DocxFixtures.simpleDocument(sub, "Anidado.docx", "anidado");

        List<File> encontrados = service.findDocxFiles(folder.toFile());

        assertEquals(1, encontrados.size());
        assertEquals("Raiz.docx", encontrados.get(0).getName());
    }

    @Test
    @DisplayName("devuelve lista vacía si la carpeta no tiene documentos o no existe")
    void carpetaSinDocumentos(@TempDir Path folder) {
        assertTrue(service.findDocxFiles(folder.toFile()).isEmpty());
        assertTrue(service.findDocxFiles(folder.resolve("no-existe").toFile()).isEmpty());
        assertTrue(service.findDocxFiles(null).isEmpty());
    }

    @Test
    @DisplayName("isDocx acepta documentos y rechaza temporales, carpetas y otras extensiones")
    void reconoceDocumentos(@TempDir Path folder) throws IOException {
        Path documento = DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido");
        Path temporal = Files.writeString(folder.resolve("~$Informe.docx"), "lock", StandardCharsets.UTF_8);
        Path texto = Files.writeString(folder.resolve("notas.txt"), "hola", StandardCharsets.UTF_8);

        assertTrue(service.isDocx(documento.toFile()));
        assertFalse(service.isDocx(temporal.toFile()));
        assertFalse(service.isDocx(texto.toFile()));
        assertFalse(service.isDocx(folder.toFile()));
        assertFalse(service.isDocx(null));
    }
}
