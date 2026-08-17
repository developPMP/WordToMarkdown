package com.wordtomarkdown;

import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.NumberingDocument;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.StylesDocument;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Prepara un documento Word recién creado: sus estilos y sus listas numeradas.
 *
 * <p>Un {@code .docx} generado desde cero no trae nada de esto, y sin estilos con
 * nombre el resultado sería texto con formato "a mano": los encabezados no
 * aparecerían en el panel de navegación, no se podría insertar un índice
 * automático y cambiar el aspecto del documento obligaría a repasarlo párrafo a
 * párrafo. Por eso se definen los estilos propios de Word ({@code Heading 1…6},
 * {@code Quote}, código) y se aplican por nombre.
 *
 * <p>Las definiciones se escriben como XML de WordprocessingML y se convierten a
 * objetos POI. Construirlas con la API de esquemas requeriría varias líneas por
 * atributo y se leería mucho peor.
 */
final class WordStyles {

    /** Espacio de nombres de WordprocessingML, que declara el elemento raíz. */
    private static final String W_NS =
        "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";

    static final String NORMAL = "Normal";
    static final String QUOTE = "Quote";
    static final String CODE_BLOCK = "CodeBlock";
    static final String CODE_CHAR = "CodeChar";
    static final String HYPERLINK = "Hyperlink";

    /** Niveles de encabezado de Markdown, y por tanto de estilos {@code Heading}. */
    static final int MAX_HEADING_LEVEL = 6;

    /** Niveles de anidamiento que admite una lista de Word. */
    private static final int LIST_LEVELS = 9;

    /** Sangría (en twips) que se añade por cada nivel de lista. */
    private static final int LIST_INDENT = 720;

    /** Color de los encabezados y del título: el azul del tema por defecto de Word. */
    private static final String ACCENT = "2F5496";

    /** Tamaños de los encabezados 1 a 6, en medios puntos. */
    private static final int[] HEADING_SIZES = {32, 26, 24, 24, 22, 22};

    private final XWPFDocument document;
    private final XWPFNumbering numbering;

    /** Identificadores de lista abstracta ya usados; se asignan de forma correlativa. */
    private int nextAbstractNumId;

    /** Numeración de viñetas: una sola definición vale para todas las listas. */
    private BigInteger bulletNumId;

    WordStyles(XWPFDocument document) {
        this.document = document;
        this.numbering = document.createNumbering();
        installStyles();
    }

    /** Identificador del estilo de encabezado para un nivel de Markdown. */
    static String headingStyle(int level) {
        return "Heading" + Math.min(Math.max(level, 1), MAX_HEADING_LEVEL);
    }

    /**
     * Numeración para una lista con viñetas. Todas comparten definición: al no
     * llevar número, que continúen unas de otras no se nota.
     */
    BigInteger bulletNumbering() {
        if (bulletNumId == null) {
            bulletNumId = addNumbering(bulletDefinition(nextAbstractNumId++));
        }
        return bulletNumId;
    }

    /**
     * Numeración para una lista ordenada que empieza en {@code start}.
     *
     * <p>Cada lista estrena definición a propósito: compartiéndola, la segunda
     * lista del documento seguiría contando donde lo dejó la primera.
     */
    BigInteger orderedNumbering(int start) {
        return addNumbering(orderedDefinition(nextAbstractNumId++, start));
    }

    private BigInteger addNumbering(String abstractNumXml) {
        try {
            CTAbstractNum definition = NumberingDocument.Factory
                .parse(wrap("numbering", abstractNumXml))
                .getNumbering()
                .getAbstractNumArray(0);
            XWPFAbstractNum abstractNum = new XWPFAbstractNum(definition);
            return numbering.addNum(numbering.addAbstractNum(abstractNum));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo crear la numeración de la lista", ex);
        }
    }

    private void installStyles() {
        XWPFStyles styles = document.createStyles();
        try {
            CTStyles parsed = StylesDocument.Factory
                .parse(wrap("styles", String.join("", styleDefinitions())))
                .getStyles();
            for (CTStyle style : parsed.getStyleArray()) {
                styles.addStyle(new XWPFStyle(style));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudieron crear los estilos del documento", ex);
        }
    }

    /**
     * Envuelve las definiciones en el elemento raíz de su parte del documento.
     *
     * <p>Hay que analizarlas como parte de un documento completo y no sueltas: un
     * fragmento suelto se analiza como documento y POI lo vuelve a envolver al
     * añadirlo, con lo que el {@code styles.xml} sale con los estilos metidos
     * unos dentro de otros y Word no los reconoce.
     */
    private String wrap(String root, String content) {
        return "<w:" + root + " " + W_NS + ">" + content + "</w:" + root + ">";
    }

    private List<String> styleDefinitions() {
        List<String> definitions = new ArrayList<>();

        // Estilo por defecto de los párrafos: sin él, Word aplica los suyos y el
        // documento sale con una tipografía distinta a la que espera el usuario.
        definitions.add(paragraphStyle(NORMAL, "Normal", true,
            "<w:spacing w:after=\"160\" w:line=\"259\" w:lineRule=\"auto\"/>",
            "<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/><w:sz w:val=\"22\"/>"));

        for (int level = 1; level <= MAX_HEADING_LEVEL; level++) {
            definitions.add(headingDefinition(level));
        }

        definitions.add(paragraphStyle(QUOTE, "Quote", false,
            "<w:spacing w:before=\"120\" w:after=\"120\"/><w:ind w:left=\"720\" w:right=\"720\"/>",
            "<w:i/><w:color w:val=\"404040\"/>"));

        definitions.add(paragraphStyle(CODE_BLOCK, "Code Block", false,
            "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F2F2F2\"/>"
                + "<w:spacing w:before=\"120\" w:after=\"120\" w:line=\"240\" w:lineRule=\"auto\"/>"
                + "<w:ind w:left=\"284\"/>",
            "<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/><w:sz w:val=\"20\"/>"));

        definitions.add(characterStyle(CODE_CHAR, "Code Char",
            "<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/>"
                + "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F2F2F2\"/><w:sz w:val=\"20\"/>"));

        definitions.add(characterStyle(HYPERLINK, "Hyperlink",
            "<w:color w:val=\"0563C1\"/><w:u w:val=\"single\"/>"));

        return definitions;
    }

    /**
     * Encabezado de un nivel. El {@code outlineLvl} es lo que hace que Word lo
     * reconozca como apartado: sin él no sale en el panel de navegación ni lo
     * recoge un índice automático.
     */
    private String headingDefinition(int level) {
        String paragraph = "<w:keepNext/><w:keepLines/>"
            + "<w:spacing w:before=\"" + (level == 1 ? 360 : 240) + "\" w:after=\"120\"/>"
            + "<w:outlineLvl w:val=\"" + (level - 1) + "\"/>";
        String run = "<w:rFonts w:ascii=\"Calibri Light\" w:hAnsi=\"Calibri Light\"/>"
            + (level >= 4 ? "<w:i/>" : "")
            + "<w:color w:val=\"" + ACCENT + "\"/>"
            + "<w:sz w:val=\"" + HEADING_SIZES[level - 1] + "\"/>";
        // El nombre canónico del estilo va en minúsculas ("heading 1"): es el que
        // Word traduce a "Título 1" cuando la interfaz está en español.
        return paragraphStyle(headingStyle(level), "heading " + level, false, paragraph, run);
    }

    private String paragraphStyle(String id, String name, boolean isDefault,
                                  String paragraphProperties, String runProperties) {
        return "<w:style w:type=\"paragraph\""
            + (isDefault ? " w:default=\"1\"" : "") + " w:styleId=\"" + id + "\">"
            + "<w:name w:val=\"" + name + "\"/>"
            + (isDefault ? "" : "<w:basedOn w:val=\"" + NORMAL + "\"/><w:next w:val=\"" + NORMAL + "\"/>")
            + "<w:qFormat/>"
            + "<w:pPr>" + paragraphProperties + "</w:pPr>"
            + "<w:rPr>" + runProperties + "</w:rPr>"
            + "</w:style>";
    }

    private String characterStyle(String id, String name, String runProperties) {
        return "<w:style w:type=\"character\" w:styleId=\"" + id + "\">"
            + "<w:name w:val=\"" + name + "\"/>"
            + "<w:qFormat/>"
            + "<w:rPr>" + runProperties + "</w:rPr>"
            + "</w:style>";
    }

    /** Viñetas de Word: punto, círculo y cuadrado, alternándose por nivel. */
    private String bulletDefinition(int abstractNumId) {
        // Los caracteres son los del área privada de las fuentes Symbol y Wingdings
        String[] bullets = {"\uF0B7", "o", "\uF0A7"};
        String[] fonts = {"Symbol", "Courier New", "Wingdings"};

        StringBuilder xml = new StringBuilder(abstractNumStart(abstractNumId));
        for (int level = 0; level < LIST_LEVELS; level++) {
            int variant = level % bullets.length;
            xml.append("<w:lvl w:ilvl=\"").append(level).append("\">")
                .append("<w:start w:val=\"1\"/>")
                .append("<w:numFmt w:val=\"bullet\"/>")
                .append("<w:lvlText w:val=\"").append(bullets[variant]).append("\"/>")
                .append("<w:lvlJc w:val=\"left\"/>")
                .append(indent(level))
                .append("<w:rPr><w:rFonts w:ascii=\"").append(fonts[variant])
                .append("\" w:hAnsi=\"").append(fonts[variant]).append("\" w:hint=\"default\"/></w:rPr>")
                .append("</w:lvl>");
        }
        return xml.append("</w:abstractNum>").toString();
    }

    /** Listas ordenadas: 1. / a. / i. por nivel, como las de Word. */
    private String orderedDefinition(int abstractNumId, int start) {
        String[] formats = {"decimal", "lowerLetter", "lowerRoman"};

        StringBuilder xml = new StringBuilder(abstractNumStart(abstractNumId));
        for (int level = 0; level < LIST_LEVELS; level++) {
            xml.append("<w:lvl w:ilvl=\"").append(level).append("\">")
                // Solo el primer nivel arranca donde diga el Markdown; los interiores, en 1
                .append("<w:start w:val=\"").append(level == 0 ? Math.max(start, 1) : 1).append("\"/>")
                .append("<w:numFmt w:val=\"").append(formats[level % formats.length]).append("\"/>")
                .append("<w:lvlText w:val=\"%").append(level + 1).append(".\"/>")
                .append("<w:lvlJc w:val=\"left\"/>")
                .append(indent(level))
                .append("</w:lvl>");
        }
        return xml.append("</w:abstractNum>").toString();
    }

    private String abstractNumStart(int abstractNumId) {
        return "<w:abstractNum w:abstractNumId=\"" + abstractNumId + "\">"
            + "<w:multiLevelType w:val=\"hybridMultilevel\"/>";
    }

    /** Sangría del nivel: el texto colgado del número, como en las listas de Word. */
    private String indent(int level) {
        return "<w:pPr><w:ind w:left=\"" + (LIST_INDENT * (level + 1)) + "\" w:hanging=\"360\"/></w:pPr>";
    }
}
