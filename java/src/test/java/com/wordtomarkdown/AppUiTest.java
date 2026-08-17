package com.wordtomarkdown;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Comprobaciones sobre la ventana. Requieren entorno gráfico: si no lo hay
 * (por ejemplo en un servidor de integración headless) las pruebas se omiten
 * en lugar de fallar.
 */
@DisplayName("Ventana principal")
class AppUiTest {

    @BeforeAll
    static void requiereEntornoGrafico() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "sin entorno gráfico disponible");
    }

    /** Crea la ventana en el hilo de eventos, como hace la aplicación real. */
    private App nuevaVentana() throws InterruptedException, InvocationTargetException {
        AtomicReference<App> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> ref.set(new App()));
        return ref.get();
    }

    @Test
    @DisplayName("arranca en modo Archivo y con la conversión deshabilitada")
    void estadoInicial() throws Exception {
        App app = nuevaVentana();

        assertFalse(app.isFolderMode(), "el modo por defecto debe ser Archivo");
        assertEquals("Archivo .docx:", app.pathLabel());
        assertEquals("", app.selectedPath());
        assertFalse(app.isConvertEnabled());
    }

    @Test
    @DisplayName("arranca convirtiendo de Word a Markdown")
    void sentidoPorDefecto() throws Exception {
        App app = nuevaVentana();

        assertEquals(ConversionDirection.WORD_TO_MARKDOWN, app.direction());
        assertEquals("Convertir a Markdown", app.convertLabel());
    }

    @Test
    @DisplayName("al cambiar de sentido se ajustan las etiquetas y se suelta la selección")
    void cambioDeSentido(@TempDir Path folder) throws Exception {
        App app = nuevaVentana();
        Path docx = DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido");
        app.applyDroppedPath(docx.toFile());

        app.selectDirection(ConversionDirection.MARKDOWN_TO_WORD);

        assertEquals("Archivo .md:", app.pathLabel());
        assertEquals("Convertir a Word", app.convertLabel());
        assertEquals("", app.selectedPath(), "el .docx elegido ya no sirve en este sentido");
        assertFalse(app.isConvertEnabled());
    }

    @Test
    @DisplayName("en sentido Markdown a Word se acepta el .md y se rechaza el .docx")
    void soltarEnSentidoInverso(@TempDir Path folder) throws Exception {
        App app = nuevaVentana();
        app.selectDirection(ConversionDirection.MARKDOWN_TO_WORD);
        Path md = Files.writeString(folder.resolve("Informe.md"), "# Hola", StandardCharsets.UTF_8);
        Path docx = DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido");

        assertTrue(app.applyDroppedPath(md.toFile()));
        assertEquals(md.toFile().getAbsolutePath(), app.selectedPath());

        assertFalse(app.applyDroppedPath(docx.toFile()));
        assertTrue(app.logArea().getText().contains("Ignorado (no es un .md): Informe.docx"),
            app.logArea().getText());
    }

    @Test
    @DisplayName("el área de registro acepta que se le suelten archivos")
    void areaDeRegistroPreparadaParaSoltar() throws Exception {
        App app = nuevaVentana();

        assertNotNull(app.logArea().getTransferHandler(), "falta el TransferHandler propio");
        assertNotNull(app.logArea().getDropTarget(), "el área no admite drops");
        assertTrue(app.logArea().getDropTarget().isActive());
    }

    @Test
    @DisplayName("soltar un .docx pasa a modo Archivo y rellena la ruta")
    void soltarDocumento(@TempDir Path folder) throws Exception {
        App app = nuevaVentana();
        Path docx = DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido");

        // Estando en modo Carpeta, soltar un documento debe devolver a modo Archivo
        assertTrue(app.applyDroppedPath(folder.toFile()));
        assertTrue(app.isFolderMode());

        assertTrue(app.applyDroppedPath(docx.toFile()));

        assertFalse(app.isFolderMode());
        assertEquals("Archivo .docx:", app.pathLabel());
        assertEquals(docx.toFile().getAbsolutePath(), app.selectedPath());
        assertTrue(app.isConvertEnabled());
        assertTrue(app.logArea().getText().contains("Archivo arrastrado: Informe.docx"));
    }

    @Test
    @DisplayName("soltar una carpeta pasa a modo Carpeta")
    void soltarCarpeta(@TempDir Path folder) throws Exception {
        App app = nuevaVentana();
        DocxFixtures.simpleDocument(folder, "Alpha.docx", "a");

        assertTrue(app.applyDroppedPath(folder.toFile()));

        assertTrue(app.isFolderMode());
        assertEquals("Carpeta:", app.pathLabel());
        assertEquals(folder.toFile().getAbsolutePath(), app.selectedPath());
        assertTrue(app.isConvertEnabled());
    }

    @Test
    @DisplayName("soltar algo que no es .docx no altera la selección anterior")
    void soltarAlgoInvalido(@TempDir Path folder) throws Exception {
        App app = nuevaVentana();
        Path docx = DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido");
        Path texto = Files.writeString(folder.resolve("notas.txt"), "hola", StandardCharsets.UTF_8);

        app.applyDroppedPath(docx.toFile());
        String rutaPrevia = app.selectedPath();

        assertFalse(app.applyDroppedPath(texto.toFile()));

        assertEquals(rutaPrevia, app.selectedPath(), "la ruta previa debe conservarse");
        assertTrue(app.isConvertEnabled(), "la selección válida anterior sigue vigente");
        assertTrue(app.logArea().getText().contains("Ignorado (no es un .docx): notas.txt"));
    }

    @Test
    @DisplayName("el registro se puede seleccionar y copiar al portapapeles")
    void registroCopiable(@TempDir Path folder) throws Exception {
        App app = nuevaVentana();
        Path docx = DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido");
        app.applyDroppedPath(docx.toFile());

        // Portapapeles propio: las pruebas no deben pisar el del usuario
        Clipboard clipboard = new Clipboard("pruebas");

        assertTrue(app.copyLog(clipboard), "el registro tiene contenido que copiar");
        assertEquals(app.logArea().getText(),
            clipboard.getData(DataFlavor.stringFlavor), "debe copiarse el registro entero");
    }

    @Test
    @DisplayName("el TransferHandler del registro admite copiar la selección")
    void copiarSeleccion() throws Exception {
        App app = nuevaVentana();
        JTextArea log = app.logArea();
        SwingUtilities.invokeAndWait(() -> {
            log.setText("Convirtiendo: Informe.docx\nMarkdown generado: Informe.md\n");
            log.select(0, "Convirtiendo: Informe.docx".length());
        });

        Clipboard clipboard = new Clipboard("pruebas");
        log.getTransferHandler().exportToClipboard(log, clipboard, TransferHandler.COPY);

        assertEquals("Convirtiendo: Informe.docx", clipboard.getData(DataFlavor.stringFlavor));
    }

    @Test
    @DisplayName("el registro ofrece menú contextual y botón de copia")
    void controlesDeCopia() throws Exception {
        App app = nuevaVentana();

        assertNotNull(app.logArea().getComponentPopupMenu(), "falta el menú del botón derecho");
        assertFalse(app.isCopyLogEnabled(), "sin registro no hay nada que copiar");

        app.applyDroppedPath(new java.io.File("inexistente.txt"));

        assertTrue(app.isCopyLogEnabled(), "con registro el botón debe habilitarse");
    }

    @Test
    @DisplayName("el botón Limpiar deja la ventana como recién abierta")
    void limpiarRegistroYSeleccion(@TempDir Path folder) throws Exception {
        App app = nuevaVentana();
        Path docx = DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido");
        app.applyDroppedPath(docx.toFile());

        assertTrue(app.isClearLogEnabled(), "con registro debe poder limpiarse");

        app.clearAll();

        assertEquals("", app.logArea().getText());
        assertEquals("", app.selectedPath(), "también se limpia la ruta seleccionada");
        assertFalse(app.isConvertEnabled(), "sin selección no se puede convertir");
        assertFalse(app.isClearLogEnabled(), "ya no queda nada que limpiar");
        assertFalse(app.isCopyLogEnabled(), "ni nada que copiar");
    }

    @Test
    @DisplayName("cargar otro archivo limpia el registro anterior")
    void cargarOtroArchivoLimpiaElRegistro(@TempDir Path folder) throws Exception {
        App app = nuevaVentana();
        Path primero = DocxFixtures.simpleDocument(folder, "Primero.docx", "uno");
        Path segundo = DocxFixtures.simpleDocument(folder, "Segundo.docx", "dos");

        app.applyDroppedPath(primero.toFile());
        app.applyDroppedPath(segundo.toFile());

        String registro = app.logArea().getText();
        assertTrue(registro.contains("Segundo.docx"), registro);
        assertFalse(registro.contains("Primero.docx"), "el registro anterior debe borrarse: " + registro);
        assertEquals(segundo.toFile().getAbsolutePath(), app.selectedPath(),
            "la ruta recién cargada se conserva");
    }

    @Test
    @DisplayName("soltar algo inválido no borra el registro que ya había")
    void soltarAlgoInvalidoNoLimpia(@TempDir Path folder) throws Exception {
        App app = nuevaVentana();
        Path docx = DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido");
        Path texto = Files.writeString(folder.resolve("notas.txt"), "hola", StandardCharsets.UTF_8);

        app.applyDroppedPath(docx.toFile());
        app.applyDroppedPath(texto.toFile());

        String registro = app.logArea().getText();
        assertTrue(registro.contains("Informe.docx"), registro);
        assertTrue(registro.contains("Ignorado (no es un .docx): notas.txt"), registro);
    }

    @Test
    @DisplayName("los fixtures generan documentos que la aplicación sabe convertir")
    void integracionConElServicio(@TempDir Path folder) throws IOException {
        Path docx = DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido");

        ConversionResult result = new ConversionService().convert(docx.toFile());

        assertTrue(result.success());
    }
}
