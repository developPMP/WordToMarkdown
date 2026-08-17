package com.wordtomarkdown;

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Conversión de archivos Markdown a documentos Word, el sentido inverso de
 * {@link ConversionService}. No depende de la interfaz gráfica, de modo que
 * puede usarse y probarse por separado.
 *
 * <p>El flujo es {@code .md → [flexmark] → árbol → [Apache POI] → .docx}: se
 * analiza el Markdown y se escribe el documento elemento a elemento, en lugar de
 * pasar por HTML. Cuesta más código, pero es lo que permite generar estilos de
 * Word de verdad ({@code Heading 1}, listas numeradas, tablas, marcadores) y no
 * un documento con el formato imitado a base de negritas y tamaños.
 *
 * <p>El viaje de ida y vuelta no es idéntico al original: el Markdown no guarda
 * encabezados y pies, saltos de sección ni la numeración automática de los
 * apartados, así que el documento generado sale con el formato base de Word.
 */
public class MarkdownToWordService implements Converter {

    private static final ConversionDirection DIRECTION = ConversionDirection.MARKDOWN_TO_WORD;

    /** Bytes que se leen del principio del archivo para reconocer su formato real. */
    private static final int FORMAT_SAMPLE_BYTES = 1024;

    /**
     * Codificación de reserva para los .md que no están en UTF-8, que es lo que
     * suele escribir el Bloc de notas en Windows.
     */
    private static final Charset FALLBACK_CHARSET = Charset.forName("windows-1252");

    /**
     * Tablas y tachado no forman parte del Markdown básico, pero sí de lo que
     * genera este mismo programa al convertir en el otro sentido.
     */
    private static final DataHolder PARSER_OPTIONS = new MutableDataSet()
        .set(Parser.EXTENSIONS, List.of(TablesExtension.create(), StrikethroughExtension.create()))
        .toImmutable();

    private final Parser parser = Parser.builder(PARSER_OPTIONS).build();

    @Override
    public ConversionDirection direction() {
        return DIRECTION;
    }

    @Override
    public boolean isInput(File file) {
        return isMarkdown(file);
    }

    /** true si el archivo es un Markdown convertible. */
    public boolean isMarkdown(File file) {
        return file != null
            && file.isFile()
            && DIRECTION.matchesInput(file.getName())
            && !file.getName().startsWith(".");
    }

    /**
     * Archivos Markdown directamente contenidos en la carpeta, ordenados por
     * nombre. No se recorren las subcarpetas.
     */
    @Override
    public List<File> findInputFiles(File folder) {
        if (folder == null || !folder.isDirectory()) {
            return List.of();
        }
        File[] files = folder.listFiles(this::isMarkdown);
        if (files == null) {
            return List.of();
        }
        return Arrays.stream(files)
            .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /** Nombre del archivo sin su extensión de Markdown. */
    public String baseNameOf(File markdown) {
        String name = markdown.getName();
        for (String extension : DIRECTION.inputExtensions()) {
            if (name.toLowerCase().endsWith(extension)) {
                return name.substring(0, name.length() - extension.length());
            }
        }
        return name;
    }

    /**
     * Convierte un Markdown y escribe el .docx junto al original. Nunca lanza:
     * los fallos se describen en el {@link ConversionResult} devuelto.
     */
    @Override
    public ConversionResult convert(File markdown) {
        File output = outputFor(markdown);
        List<String> warnings = new ArrayList<>();

        // Se comprueba antes de leer: cualquier binario se puede interpretar como
        // texto en la codificación de reserva, y se convertiría en un documento
        // lleno de basura en lugar de avisar de que el archivo no es Markdown.
        String otherFormat = detectFormat(markdown);
        if (otherFormat != null) {
            return ConversionResult.failure(DIRECTION, markdown, output, null,
                "No es un archivo de texto Markdown: " + otherFormat + ".");
        }

        try {
            String text = readText(markdown, warnings);
            if (text.isBlank()) {
                warnings.add("el archivo no tiene contenido: el documento se genera vacío");
            }

            try (WordWriter writer = new WordWriter(markdown.getAbsoluteFile().getParentFile().toPath())) {
                writer.write(parser.parse(text));
                writer.saveTo(output);

                warnings.addAll(writer.warnings());
                return new ConversionResult(
                    DIRECTION, markdown, output, true, null,
                    warnings,
                    writer.imagesEmbedded(),
                    writer.imageErrors(),
                    null
                );
            }

        } catch (Exception ex) {
            return ConversionResult.failure(DIRECTION, markdown, output, null, messageOf(ex));
        }
    }

    /**
     * Archivo .docx a generar, buscando un nombre libre.
     *
     * <p>Lo habitual es que el .md se haya obtenido de un .docx que está en esa
     * misma carpeta y con el mismo nombre —así lo deja la conversión de ida—, de
     * modo que escribir sin mirar destruiría el documento original. Cuando el
     * nombre está ocupado se numera el nuevo ({@code Informe (2).docx}); el
     * registro indica siempre cuál se ha escrito.
     *
     * <p>Visible para las pruebas.
     */
    File outputFor(File markdown) {
        File parent = markdown.getAbsoluteFile().getParentFile();
        String baseName = baseNameOf(markdown);

        File candidate = new File(parent, baseName + DIRECTION.outputExtension());
        for (int copy = 2; candidate.exists(); copy++) {
            candidate = new File(parent, baseName + " (" + copy + ")" + DIRECTION.outputExtension());
        }
        return candidate;
    }

    /**
     * Contenido del archivo. Se espera UTF-8 (es lo que escribe la conversión de
     * ida), pero un .md editado a mano en Windows puede venir en la codificación
     * del sistema: antes que fallar, se lee con ella y se avisa.
     */
    private String readText(File markdown, List<String> warnings) throws IOException {
        try {
            return withoutBom(Files.readString(markdown.toPath(), StandardCharsets.UTF_8));
        } catch (CharacterCodingException ex) {
            warnings.add("el archivo no está codificado en UTF-8; se ha leído como "
                + FALLBACK_CHARSET.displayName() + " y algún carácter puede no coincidir");
            return withoutBom(new String(Files.readAllBytes(markdown.toPath()), FALLBACK_CHARSET));
        }
    }

    /** La marca de orden de bytes que añaden algunos editores no es texto. */
    private String withoutBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    /**
     * Describe el formato real del archivo si no es texto, o null si sí lo
     * parece. Un archivo vacío se da por bueno: es texto, solo que no tiene nada.
     */
    private String detectFormat(File file) {
        byte[] head = firstBytes(file, FORMAT_SAMPLE_BYTES);
        if (startsWith(head, 'P', 'K')) {
            return "es un archivo comprimido, probablemente un .docx con la extensión cambiada";
        }
        if (startsWith(head, '%', 'P', 'D', 'F')) {
            return "es un PDF con la extensión cambiada";
        }
        if (startsWith(head, (char) 0xD0, (char) 0xCF, (char) 0x11, (char) 0xE0)) {
            return "es un documento de Word anterior a 2007 (.doc)";
        }
        for (byte b : head) {
            if (b == 0) {
                return "es un archivo binario, no texto";
            }
        }
        return null;
    }

    private byte[] firstBytes(File file, int count) {
        try (InputStream is = new FileInputStream(file)) {
            return is.readNBytes(count);
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    private boolean startsWith(byte[] content, char... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private String messageOf(Exception ex) {
        return ex.getClass().getSimpleName()
            + (ex.getMessage() != null ? " - " + ex.getMessage() : "");
    }
}
