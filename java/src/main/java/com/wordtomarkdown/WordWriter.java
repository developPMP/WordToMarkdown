package com.wordtomarkdown;

import com.vladsch.flexmark.ast.AutoLink;
import com.vladsch.flexmark.ast.BlockQuote;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.HtmlBlockBase;
import com.vladsch.flexmark.ast.HtmlInlineBase;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.LinkNodeBase;
import com.vladsch.flexmark.ast.ListBlock;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.MailLink;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableBody;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.ast.Node;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.IRunBody;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Escribe un documento Word a partir del árbol que flexmark obtiene de un
 * Markdown.
 *
 * <p>Cada elemento del Markdown se traduce al equivalente nativo de Word —no a
 * texto con formato imitándolo—: los encabezados llevan estilo {@code Heading N}
 * y nivel de esquema, las listas usan la numeración de Word, las tablas son
 * tablas y los enlaces internos apuntan a marcadores. Así el documento se puede
 * seguir editando con normalidad: navegar por apartados, insertar un índice
 * automático o cambiar el aspecto desde los estilos.
 *
 * <p>Lo que el Markdown no puede expresar (encabezados y pies, saltos de
 * sección, numeración automática de apartados) no se inventa: el documento sale
 * con el formato base de Word.
 */
final class WordWriter implements Closeable {

    /** Ancho útil de la página, en puntos: A4 menos los márgenes de 2,5 cm. */
    private static final int MAX_IMAGE_WIDTH_PT = 453;

    /** Alto máximo de una imagen, para que no desplace todo a la página siguiente. */
    private static final int MAX_IMAGE_HEIGHT_PT = 600;

    /** Los píxeles de una imagen se miden a 96 ppp; Word trabaja en puntos (72 ppp). */
    private static final double PX_TO_PT = 72.0 / 96.0;

    /** Medidas de página A4 y márgenes de 2,5 cm, en twips (1/20 de punto). */
    private static final int PAGE_WIDTH = 11906;
    private static final int PAGE_HEIGHT = 16838;
    private static final int PAGE_MARGIN = 1417;
    private static final int HEADER_MARGIN = 708;

    /** Ancho útil de la página en twips, para repartirlo entre las columnas. */
    private static final int USABLE_WIDTH = PAGE_WIDTH - 2 * PAGE_MARGIN;

    /** Longitud máxima que Word admite en el nombre de un marcador. */
    private static final int MAX_BOOKMARK_LENGTH = 40;

    /** Tamaño con el que se inserta una imagen cuyas dimensiones no se pueden leer. */
    private static final int UNKNOWN_IMAGE_WIDTH_PT = 400;
    private static final int UNKNOWN_IMAGE_HEIGHT_PT = 300;

    private final XWPFDocument document = new XWPFDocument();
    private final WordStyles styles = new WordStyles(document);

    /** Carpeta del .md de origen: las rutas de las imágenes son relativas a ella. */
    private final Path baseDir;

    private final List<String> warnings = new ArrayList<>();
    private final Set<String> reportedWarnings = new LinkedHashSet<>();
    private final List<String> imageErrors = new ArrayList<>();

    /** Marcador de Word correspondiente a cada destino de enlace del Markdown. */
    private final Map<String, String> bookmarksBySlug = new LinkedHashMap<>();

    /** Marcadores pendientes de colocar, en el mismo orden que los encabezados. */
    private final Deque<String> pendingBookmarks = new ArrayDeque<>();

    private int imagesEmbedded;
    private int imageCount;
    private int bookmarkId;

    WordWriter(Path baseDir) {
        this.baseDir = baseDir;
        setUpPage();
    }

    /** Imágenes insertadas con éxito. */
    int imagesEmbedded() {
        return imagesEmbedded;
    }

    /** Avisos sobre lo que no se pudo trasladar tal cual. */
    List<String> warnings() {
        return List.copyOf(warnings);
    }

    /** Imágenes que se quedaron fuera, con el motivo. */
    List<String> imageErrors() {
        return List.copyOf(imageErrors);
    }

    /** Vuelca el árbol del Markdown en el documento. */
    void write(Node markdown) {
        indexHeadings(markdown, new HashSet<>(), new HashSet<>());
        writeBlocks(markdown, Flow.body());
    }

    void saveTo(File target) throws IOException {
        try (OutputStream out = new FileOutputStream(target)) {
            document.write(out);
        }
    }

    @Override
    public void close() throws IOException {
        document.close();
    }

    /** A4 con márgenes normales; un documento creado desde cero no trae ninguno. */
    private void setUpPage() {
        CTBody body = document.getDocument().getBody();
        CTSectPr section = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        section.addNewPgSz().setW(BigInteger.valueOf(PAGE_WIDTH));
        section.getPgSz().setH(BigInteger.valueOf(PAGE_HEIGHT));
        CTPageMar margins = section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(PAGE_MARGIN));
        margins.setBottom(BigInteger.valueOf(PAGE_MARGIN));
        margins.setLeft(BigInteger.valueOf(PAGE_MARGIN));
        margins.setRight(BigInteger.valueOf(PAGE_MARGIN));
        // El formato exige estos tres aunque el documento no lleve encabezado ni pie
        margins.setHeader(BigInteger.valueOf(HEADER_MARGIN));
        margins.setFooter(BigInteger.valueOf(HEADER_MARGIN));
        margins.setGutter(BigInteger.ZERO);
    }

    // --- Bloques ---

    private void writeBlocks(Node parent, Flow flow) {
        for (Node node : parent.getChildren()) {
            writeBlock(node, flow);
        }
    }

    private void writeBlock(Node node, Flow flow) {
        if (node instanceof Heading heading) {
            writeHeading(heading, flow);
        } else if (node instanceof Paragraph paragraph) {
            writeInlines(newParagraph(flow.style(), flow), paragraph, RunStyle.PLAIN);
        } else if (node instanceof ListBlock list) {
            writeList(list, flow);
        } else if (node instanceof BlockQuote quote) {
            writeBlocks(quote, flow.quoted());
        } else if (node instanceof FencedCodeBlock code) {
            writeCode(code.getContentChars().normalizeEOL(), flow);
        } else if (node instanceof IndentedCodeBlock code) {
            writeCode(code.getContentChars().normalizeEOL(), flow);
        } else if (node instanceof TableBlock table) {
            writeTable(table);
        } else if (node instanceof ThematicBreak) {
            writeThematicBreak(flow);
        } else if (node instanceof HtmlBlockBase) {
            warn("se descartó un bloque de HTML incrustado en el Markdown");
        } else if (node.hasChildren()) {
            // Bloques sin equivalente propio: se vuelca lo que contengan. Los que
            // no llevan contenido visible (definiciones de enlaces) se ignoran.
            writeBlocks(node, flow);
        }
    }

    private void writeHeading(Heading heading, Flow flow) {
        XWPFParagraph paragraph = newParagraph(WordStyles.headingStyle(heading.getLevel()), flow);
        String bookmark = pendingBookmarks.poll();
        int id = bookmark != null ? startBookmark(paragraph, bookmark) : -1;
        writeInlines(paragraph, heading, RunStyle.PLAIN);
        if (id >= 0) {
            paragraph.getCTP().addNewBookmarkEnd().setId(BigInteger.valueOf(id));
        }
    }

    private void writeCode(String content, Flow flow) {
        XWPFParagraph paragraph = newParagraph(WordStyles.CODE_BLOCK, flow);
        XWPFRun previous = null;
        // Un párrafo de Word no admite saltos de línea en el texto: van como
        // saltos explícitos dentro del propio párrafo, para que el sombreado
        // del bloque salga de una pieza.
        for (String line : stripTrailingBlank(content).split("\n", -1)) {
            if (previous != null) {
                previous.addBreak();
            }
            previous = paragraph.createRun();
            previous.setText(line);
        }
    }

    private void writeThematicBreak(Flow flow) {
        XWPFParagraph paragraph = newParagraph(flow.style(), flow);
        paragraph.setBorderBottom(Borders.SINGLE);
    }

    // --- Listas ---

    private void writeList(ListBlock list, Flow flow) {
        BigInteger numbering = list instanceof OrderedList ordered
            ? styles.orderedNumbering(ordered.getStartNumber())
            : styles.bulletNumbering();

        for (Node child : list.getChildren()) {
            if (child instanceof ListItem item) {
                writeListItem(item, flow.inList(numbering, flow.listLevel() + 1));
            }
        }
    }

    /**
     * Un elemento de lista: su primer párrafo lleva la viñeta o el número, y lo
     * que venga detrás (más párrafos, código, listas anidadas) se sangra a su
     * altura sin volver a numerarlo.
     */
    private void writeListItem(ListItem item, Flow flow) {
        boolean numbered = true;
        boolean written = false;

        for (Node child : item.getChildren()) {
            if (child instanceof ListBlock nested) {
                writeList(nested, flow);
            } else if (child instanceof Paragraph paragraph) {
                writeInlines(newParagraph(WordStyles.NORMAL, numbered ? flow : flow.unnumbered()),
                    paragraph, RunStyle.PLAIN);
                numbered = false;
                written = true;
            } else if (child instanceof Block) {
                writeBlock(child, numbered ? flow : flow.unnumbered());
                numbered = false;
                written = true;
            }
        }

        // Elemento sin párrafo propio: su contenido cuelga directamente del ítem
        if (!written && item.hasChildren()) {
            writeInlines(newParagraph(WordStyles.NORMAL, flow), item, RunStyle.PLAIN);
        }
    }

    // --- Tablas ---

    /**
     * Las tablas se escriben siempre a nivel de documento: Word no admite una
     * tabla colgando de un elemento de lista o de una cita como sí hace Markdown.
     */
    private void writeTable(TableBlock table) {
        List<TableRow> rows = new ArrayList<>();
        for (Node section : table.getChildren()) {
            if (section instanceof TableHead || section instanceof TableBody) {
                for (Node row : section.getChildren()) {
                    if (row instanceof TableRow tableRow) {
                        rows.add(tableRow);
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            return;
        }

        int columns = rows.stream().mapToInt(this::cellCount).max().orElse(1);
        XWPFTable wordTable = document.createTable(rows.size(), columns);
        wordTable.setWidth("100%");
        defineColumns(wordTable, columns);

        for (int r = 0; r < rows.size(); r++) {
            boolean header = rows.get(r).getParent() instanceof TableHead;
            int c = 0;
            for (Node cell : rows.get(r).getChildren()) {
                if (cell instanceof TableCell tableCell && c < columns) {
                    writeCell(wordTable.getRow(r).getCell(c), tableCell, header);
                    c++;
                }
            }
        }

        // Dos tablas seguidas sin nada en medio Word las une en una sola
        document.createParagraph().setStyle(WordStyles.NORMAL);
    }

    /**
     * Reparte el ancho de la página entre las columnas. El formato exige esta
     * rejilla: sin ella Word da la tabla por dañada al abrir el documento.
     */
    private void defineColumns(XWPFTable table, int columns) {
        CTTblGrid grid = table.getCTTbl().addNewTblGrid();
        for (int column = 0; column < columns; column++) {
            grid.addNewGridCol().setW(BigInteger.valueOf(USABLE_WIDTH / columns));
        }
    }

    private int cellCount(TableRow row) {
        int cells = 0;
        for (Node cell : row.getChildren()) {
            if (cell instanceof TableCell) {
                cells++;
            }
        }
        return cells;
    }

    private void writeCell(XWPFTableCell cell, TableCell content, boolean header) {
        if (header) {
            cell.setColor("D9E2F3");
        }
        XWPFParagraph paragraph = cell.getParagraphs().isEmpty()
            ? cell.addParagraph()
            : cell.getParagraphs().get(0);
        paragraph.setStyle(WordStyles.NORMAL);
        paragraph.setSpacingAfter(0);
        alignmentOf(content).ifPresent(paragraph::setAlignment);
        writeInlines(paragraph, content, header ? RunStyle.PLAIN.withBold() : RunStyle.PLAIN);
    }

    private Optional<ParagraphAlignment> alignmentOf(TableCell cell) {
        if (cell.getAlignment() == null) {
            return Optional.empty();
        }
        return switch (cell.getAlignment()) {
            case CENTER -> Optional.of(ParagraphAlignment.CENTER);
            case RIGHT -> Optional.of(ParagraphAlignment.RIGHT);
            default -> Optional.of(ParagraphAlignment.LEFT);
        };
    }

    // --- Contenido en línea ---

    private void writeInlines(XWPFParagraph paragraph, Node parent, RunStyle style) {
        for (Node node : parent.getChildren()) {
            if (node instanceof StrongEmphasis) {
                writeInlines(paragraph, node, style.withBold());
            } else if (node instanceof Emphasis) {
                writeInlines(paragraph, node, style.withItalic());
            } else if (node instanceof Strikethrough) {
                writeInlines(paragraph, node, style.withStrikethrough());
            } else if (node instanceof Code code) {
                appendText(paragraph, code.getText().toString(), style.withCode());
            } else if (node instanceof Image image) {
                appendImage(paragraph, image);
            } else if (node instanceof Link link) {
                appendLink(paragraph, link, link.getUrl().unescape(), style);
            } else if (node instanceof AutoLink || node instanceof MailLink) {
                LinkNodeBase link = (LinkNodeBase) node;
                String url = link.getUrl().unescape();
                appendLink(paragraph, link, node instanceof MailLink ? "mailto:" + url : url, style);
            } else if (node instanceof HardLineBreak) {
                paragraph.createRun().addBreak();
            } else if (node instanceof SoftLineBreak) {
                // En Markdown un salto simple es solo un espacio
                appendText(paragraph, " ", style);
            } else if (node instanceof HtmlInlineBase) {
                warn("se descartó una etiqueta HTML dentro del texto");
            } else if (node.hasChildren()) {
                writeInlines(paragraph, node, style);
            } else {
                appendText(paragraph, node.getChars().unescape(), style);
            }
        }
    }

    /**
     * Enlace externo (hipervínculo normal) o interno (a un marcador del propio
     * documento). Si apunta a un encabezado que no existe se conserva el texto y
     * se descarta el enlace: uno roto estorba más que un texto plano.
     */
    private void appendLink(XWPFParagraph paragraph, Node link, String url, RunStyle style) {
        String text = textOf(link);

        // Imagen enlazada: pesa más la imagen que el enlace
        if (text.isEmpty() && hasImage(link)) {
            writeInlines(paragraph, link, style);
            return;
        }
        if (text.isEmpty()) {
            text = url;
        }

        if (url.startsWith("#")) {
            String bookmark = bookmarksBySlug.get(url.substring(1).toLowerCase(Locale.ROOT));
            if (bookmark == null) {
                warn("enlace interno sin destino en el documento: " + url);
                appendText(paragraph, text, style);
                return;
            }
            CTHyperlink hyperlink = paragraph.getCTP().addNewHyperlink();
            hyperlink.setAnchor(bookmark);
            XWPFRun run = new XWPFRun(hyperlink.addNewR(), (IRunBody) paragraph);
            run.setStyle(WordStyles.HYPERLINK);
            applyStyle(run, style);
            run.setText(text);
            return;
        }

        try {
            XWPFHyperlinkRun run = paragraph.createHyperlinkRun(url);
            run.setStyle(WordStyles.HYPERLINK);
            applyStyle(run, style);
            run.setText(text);
        } catch (Exception ex) {
            // Una dirección mal formada no puede tumbar el documento entero
            warn("dirección de enlace no válida, se deja como texto: " + url);
            appendText(paragraph, text, style);
        }
    }

    private void appendText(XWPFParagraph paragraph, String text, RunStyle style) {
        if (text.isEmpty()) {
            return;
        }
        XWPFRun run = paragraph.createRun();
        applyStyle(run, style);
        if (style.code()) {
            run.setStyle(WordStyles.CODE_CHAR);
        }
        run.setText(text);
    }

    /**
     * Formato directo del texto. El estilo de carácter no se toca aquí: los
     * hipervínculos ya traen el suyo y solo puede haber uno.
     */
    private void applyStyle(XWPFRun run, RunStyle style) {
        if (style.bold()) {
            run.setBold(true);
        }
        if (style.italic()) {
            run.setItalic(true);
        }
        if (style.struck()) {
            run.setStrikeThrough(true);
        }
    }

    // --- Imágenes ---

    /**
     * Inserta la imagen en el documento. A diferencia del sentido contrario, aquí
     * la imagen se incrusta: el .docx resultante es un único archivo y no depende
     * de que la carpeta de imágenes siga estando ahí.
     */
    private void appendImage(XWPFParagraph paragraph, Image image) {
        int index = ++imageCount;
        String url = image.getUrl().unescape();
        String alt = image.getText().unescape().trim();

        if (isRemote(url)) {
            failedImage(paragraph, index, url, alt, "las imágenes externas no se descargan");
            return;
        }
        Path file = resolve(url);
        if (file == null) {
            failedImage(paragraph, index, url, alt, "no se encontró el archivo");
            return;
        }
        int type = pictureTypeOf(file);
        if (type == 0) {
            failedImage(paragraph, index, url, alt, "formato de imagen no admitido por Word");
            return;
        }

        try (InputStream content = Files.newInputStream(file)) {
            int[] size = sizeOf(file);
            XWPFRun run = paragraph.createRun();
            XWPFPicture picture = run.addPicture(content, type, file.getFileName().toString(),
                Units.toEMU(size[0]), Units.toEMU(size[1]));
            if (!alt.isEmpty()) {
                picture.getCTPicture().getNvPicPr().getCNvPr().setDescr(alt);
            }
            imagesEmbedded++;
        } catch (Exception ex) {
            failedImage(paragraph, index, url, alt, messageOf(ex));
        }
    }

    /** Deja constancia en el registro y una marca visible en el propio documento. */
    private void failedImage(XWPFParagraph paragraph, int index, String url, String alt, String cause) {
        imageErrors.add("imagen " + index + " (" + url + "): " + cause);
        XWPFRun run = paragraph.createRun();
        run.setItalic(true);
        run.setColor("808080");
        run.setText("[imagen no insertada: " + (alt.isEmpty() ? url : alt) + "]");
    }

    private boolean isRemote(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:");
    }

    /** Ruta real de la imagen, resolviendo las relativas contra la carpeta del .md. */
    private Path resolve(String url) {
        String path = url.split("[#?]")[0];
        Path file = existing(path);
        if (file == null && path.indexOf('%') >= 0) {
            // Las rutas con espacios u otros caracteres llegan codificadas
            file = existing(URLDecoder.decode(path, StandardCharsets.UTF_8));
        }
        return file;
    }

    private Path existing(String path) {
        try {
            Path candidate = Path.of(path);
            Path absolute = candidate.isAbsolute() ? candidate : baseDir.resolve(candidate);
            return Files.isRegularFile(absolute) ? absolute.normalize() : null;
        } catch (InvalidPathException ex) {
            return null;
        }
    }

    private int pictureTypeOf(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return switch (dot < 0 ? "" : name.substring(dot + 1)) {
            case "png" -> XWPFDocument.PICTURE_TYPE_PNG;
            case "jpg", "jpeg" -> XWPFDocument.PICTURE_TYPE_JPEG;
            case "gif" -> XWPFDocument.PICTURE_TYPE_GIF;
            case "bmp" -> XWPFDocument.PICTURE_TYPE_BMP;
            case "tif", "tiff" -> XWPFDocument.PICTURE_TYPE_TIFF;
            case "emf" -> XWPFDocument.PICTURE_TYPE_EMF;
            case "wmf" -> XWPFDocument.PICTURE_TYPE_WMF;
            default -> 0;
        };
    }

    /**
     * Tamaño con el que insertar la imagen, en puntos: el suyo propio reducido
     * hasta caber en la página. Si no se puede leer (formatos vectoriales como
     * EMF) se usa uno razonable, que el usuario podrá ajustar en Word.
     */
    private int[] sizeOf(Path file) {
        int width = UNKNOWN_IMAGE_WIDTH_PT;
        int height = UNKNOWN_IMAGE_HEIGHT_PT;
        try (InputStream content = Files.newInputStream(file)) {
            BufferedImage image = ImageIO.read(content);
            if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                width = (int) Math.round(image.getWidth() * PX_TO_PT);
                height = (int) Math.round(image.getHeight() * PX_TO_PT);
            }
        } catch (IOException | RuntimeException ex) {
            warn("no se pudieron leer las dimensiones de " + file.getFileName() + ", se inserta a tamaño estándar");
        }

        double scale = Math.min(1.0, Math.min(
            MAX_IMAGE_WIDTH_PT / (double) width,
            MAX_IMAGE_HEIGHT_PT / (double) height));
        return new int[]{
            Math.max(1, (int) Math.round(width * scale)),
            Math.max(1, (int) Math.round(height * scale))};
    }

    // --- Marcadores ---

    /**
     * Recorre los encabezados por adelantado y reserva un marcador para cada uno.
     *
     * <p>Hace falta antes de escribir nada porque el índice del documento suele ir
     * al principio: sus enlaces apuntan a encabezados que todavía no se han
     * escrito.
     */
    private void indexHeadings(Node node, Set<String> usedSlugs, Set<String> usedNames) {
        for (Node child : node.getChildren()) {
            if (child instanceof Heading heading) {
                String slug = Slug.of(textOf(heading), usedSlugs);
                String bookmark = bookmarkName(slug, usedNames);
                bookmarksBySlug.put(slug, bookmark);
                pendingBookmarks.add(bookmark);
            }
            if (child.hasChildren()) {
                indexHeadings(child, usedSlugs, usedNames);
            }
        }
    }

    /**
     * Nombre de marcador válido para Word a partir del identificador del enlace:
     * solo letras, dígitos y guiones bajos, empezando por letra y sin pasar de 40
     * caracteres.
     */
    private String bookmarkName(String slug, Set<String> used) {
        StringBuilder name = new StringBuilder();
        for (char c : slug.toCharArray()) {
            name.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        if (name.isEmpty() || !Character.isLetter(name.charAt(0))) {
            name.insert(0, '_');
        }
        if (name.length() > MAX_BOOKMARK_LENGTH) {
            name.setLength(MAX_BOOKMARK_LENGTH);
        }

        String base = name.toString();
        String candidate = base;
        for (int repetition = 1; !used.add(candidate); repetition++) {
            String suffix = "_" + repetition;
            candidate = base.substring(0, Math.min(base.length(), MAX_BOOKMARK_LENGTH - suffix.length())) + suffix;
        }
        return candidate;
    }

    private int startBookmark(XWPFParagraph paragraph, String name) {
        int id = bookmarkId++;
        var bookmark = paragraph.getCTP().addNewBookmarkStart();
        bookmark.setId(BigInteger.valueOf(id));
        bookmark.setName(name);
        return id;
    }

    // --- Utilidades ---

    private XWPFParagraph newParagraph(String style, Flow flow) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle(style);
        flow.applyTo(paragraph);
        return paragraph;
    }

    /** Texto plano de un nodo, sin la sintaxis de Markdown ni el alt de las imágenes. */
    private String textOf(Node node) {
        StringBuilder text = new StringBuilder();
        collectText(node, text);
        return text.toString().trim();
    }

    private void collectText(Node node, StringBuilder text) {
        for (Node child : node.getChildren()) {
            if (child instanceof Image) {
                continue;
            }
            if (child instanceof Code code) {
                text.append(code.getText());
            } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                text.append(' ');
            } else if (child.hasChildren()) {
                collectText(child, text);
            } else {
                text.append(child.getChars().unescape());
            }
        }
    }

    private boolean hasImage(Node node) {
        for (Node child : node.getChildren()) {
            if (child instanceof Image || hasImage(child)) {
                return true;
            }
        }
        return false;
    }

    /** Cada aviso se anota una sola vez, por repetido que esté en el documento. */
    private void warn(String message) {
        if (reportedWarnings.add(message)) {
            warnings.add(message);
        }
    }

    private String stripTrailingBlank(String content) {
        return content.endsWith("\n") ? content.substring(0, content.length() - 1) : content;
    }

    private String messageOf(Exception ex) {
        return ex.getClass().getSimpleName() + (ex.getMessage() != null ? " - " + ex.getMessage() : "");
    }

    /**
     * Dónde se está escribiendo: dentro de una lista (con qué numeración y a qué
     * nivel) o de una cita. Es lo que distingue un párrafo suelto de uno que
     * cuelga de un elemento de lista.
     */
    private record Flow(BigInteger numbering, int listLevel, int quoteDepth) {

        static Flow body() {
            return new Flow(null, -1, 0);
        }

        Flow inList(BigInteger numbering, int level) {
            return new Flow(numbering, level, quoteDepth);
        }

        /** Mismo sitio, pero sin repetir la viñeta o el número del elemento. */
        Flow unnumbered() {
            return new Flow(null, listLevel, quoteDepth);
        }

        Flow quoted() {
            return new Flow(numbering, listLevel, quoteDepth + 1);
        }

        String style() {
            return quoteDepth > 0 ? WordStyles.QUOTE : WordStyles.NORMAL;
        }

        void applyTo(XWPFParagraph paragraph) {
            if (numbering != null) {
                paragraph.setNumID(numbering);
                paragraph.setNumILvl(BigInteger.valueOf(listLevel));
            } else if (listLevel >= 0) {
                // Continuación de un elemento de lista: se alinea con su texto
                paragraph.setIndentationLeft(720 * (listLevel + 1));
            }
            if (quoteDepth > 1) {
                paragraph.setIndentationLeft(720 * quoteDepth);
            }
        }
    }

    /** Formato acumulado del texto en curso. */
    private record RunStyle(boolean bold, boolean italic, boolean struck, boolean code) {

        static final RunStyle PLAIN = new RunStyle(false, false, false, false);

        RunStyle withBold() {
            return new RunStyle(true, italic, struck, code);
        }

        RunStyle withItalic() {
            return new RunStyle(bold, true, struck, code);
        }

        RunStyle withStrikethrough() {
            return new RunStyle(bold, italic, true, code);
        }

        RunStyle withCode() {
            return new RunStyle(bold, italic, struck, true);
        }
    }
}
