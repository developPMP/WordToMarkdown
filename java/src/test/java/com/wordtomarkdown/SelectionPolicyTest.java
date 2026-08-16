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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Criterio al arrastrar y soltar una ruta")
class SelectionPolicyTest {

    private final SelectionPolicy policy = new SelectionPolicy(new ConversionService());

    @Test
    @DisplayName("un .docx se acepta en modo Archivo")
    void aceptaDocumento(@TempDir Path folder) throws IOException {
        Path docx = DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido");

        SelectionPolicy.Decision decision = policy.decideDrop(docx.toFile());

        assertTrue(decision.accepted());
        assertFalse(decision.folderMode(), "un documento no debe activar el modo carpeta");
        assertEquals(docx.toFile(), decision.path());
        assertEquals(java.util.List.of("Archivo arrastrado: Informe.docx"), decision.messages());
    }

    @Test
    @DisplayName("una carpeta se acepta en modo Carpeta e informa de cuántos .docx contiene")
    void aceptaCarpeta(@TempDir Path folder) throws IOException {
        DocxFixtures.simpleDocument(folder, "Alpha.docx", "a");
        DocxFixtures.simpleDocument(folder, "Beta.docx", "b");
        Files.writeString(folder.resolve("~$Alpha.docx"), "lock", StandardCharsets.UTF_8);

        SelectionPolicy.Decision decision = policy.decideDrop(folder.toFile());

        assertTrue(decision.accepted());
        assertTrue(decision.folderMode());
        assertEquals(folder.toFile(), decision.path());
        // El recuento ignora el temporal de Word
        assertTrue(decision.messages().contains("Documentos .docx encontrados: 2"),
            decision.messages().toString());
    }

    @Test
    @DisplayName("un archivo que no es .docx se rechaza")
    void rechazaOtrasExtensiones(@TempDir Path folder) throws IOException {
        Path texto = Files.writeString(folder.resolve("notas.txt"), "hola", StandardCharsets.UTF_8);

        SelectionPolicy.Decision decision = policy.decideDrop(texto.toFile());

        assertFalse(decision.accepted());
        assertNull(decision.path());
        assertEquals(java.util.List.of("Ignorado (no es un .docx): notas.txt"), decision.messages());
    }

    @Test
    @DisplayName("un temporal de Word se rechaza aunque acabe en .docx")
    void rechazaTemporalDeWord(@TempDir Path folder) throws IOException {
        Path temporal = Files.writeString(folder.resolve("~$Informe.docx"), "lock", StandardCharsets.UTF_8);

        SelectionPolicy.Decision decision = policy.decideDrop(temporal.toFile());

        assertFalse(decision.accepted());
    }

    @Test
    @DisplayName("una ruta inexistente o nula se rechaza sin fallar")
    void rechazaRutasInvalidas(@TempDir Path folder) {
        assertFalse(policy.decideDrop(folder.resolve("no-existe.docx").toFile()).accepted());
        assertFalse(policy.decideDrop(null).accepted());
    }
}
