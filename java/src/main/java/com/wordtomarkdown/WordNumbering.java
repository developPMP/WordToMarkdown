package com.wordtomarkdown;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Recupera la numeración automática de los encabezados de un documento Word
 * (la lista multinivel de "1", "1.1", "3.3.1.1"...).
 *
 * <p>Ese número no está escrito en el texto: Word solo guarda a qué lista y a qué
 * nivel pertenece cada párrafo, y lo pinta al mostrarlo. Mammoth no lo calcula,
 * así que sin esto los encabezados llegan al Markdown sin numerar.
 *
 * <p>Se recorre el documento contando los párrafos numerados —también los que no
 * son encabezados, porque comparten el contador— y se devuelve el número que le
 * toca a cada encabezado, en el orden en que aparecen.
 */
public class WordNumbering {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    /** Nombre de estilo de un encabezado, en el nombre canónico o en español. */
    private static final Pattern HEADING_STYLE =
        Pattern.compile("^(?:heading|t[íi]tulo)\\s*([1-9])$", Pattern.CASE_INSENSITIVE);

    /** Marca de nivel dentro del texto de la numeración: "%1.%2". */
    private static final Pattern LEVEL_PLACEHOLDER = Pattern.compile("%([1-9])");

    /** Profundidad máxima de una lista multinivel de Word. */
    private static final int MAX_LEVELS = 9;

    /** Encabezado numerado del documento: su texto y el número que le corresponde. */
    public record NumberedHeading(String text, String number) {
    }

    /**
     * Numeración de los encabezados del documento, en orden. Devuelve una lista
     * vacía si el documento no numera nada o si no se puede leer: la numeración
     * es un extra, nunca un motivo para que falle la conversión.
     */
    public List<NumberedHeading> readFrom(File docx) {
        try (ZipFile zip = new ZipFile(docx)) {
            Document document = parse(zip, "word/document.xml");
            if (document == null) {
                return List.of();
            }
            Map<Integer, Map<Integer, Level>> lists = readLists(parse(zip, "word/numbering.xml"));
            if (lists.isEmpty()) {
                return List.of();
            }
            return readHeadings(document, readStyles(parse(zip, "word/styles.xml")), lists);
        } catch (Exception ex) {
            return List.of();
        }
    }

    // --- Lectura de las partes del .docx ---

    /** Definición de un nivel: cómo se numera y cómo se escribe. */
    private record Level(String format, String text, int start) {
    }

    /** Lo que interesa de un estilo: su nombre y la lista a la que pertenece. */
    private record Style(String name, String basedOn, Integer numId, Integer level) {
    }

    /** Listas del documento: numId → nivel → definición. */
    private Map<Integer, Map<Integer, Level>> readLists(Document numbering) {
        if (numbering == null) {
            return Map.of();
        }
        Map<Integer, Map<Integer, Level>> abstractLists = new HashMap<>();
        for (Element abstractNum : childrenOf(numbering.getDocumentElement(), "abstractNum")) {
            Integer id = intAttribute(abstractNum, "abstractNumId");
            if (id == null) {
                continue;
            }
            Map<Integer, Level> levels = new HashMap<>();
            for (Element lvl : childrenOf(abstractNum, "lvl")) {
                Integer index = intAttribute(lvl, "ilvl");
                if (index == null) {
                    continue;
                }
                levels.put(index, new Level(
                    valueOf(lvl, "numFmt", "decimal"),
                    valueOf(lvl, "lvlText", ""),
                    intValueOf(lvl, "start", 1)));
            }
            abstractLists.put(id, levels);
        }

        // Cada lista concreta (numId) apunta a una definición abstracta
        Map<Integer, Map<Integer, Level>> lists = new HashMap<>();
        for (Element num : childrenOf(numbering.getDocumentElement(), "num")) {
            Integer numId = intAttribute(num, "numId");
            Integer abstractId = intValueOfChild(num, "abstractNumId");
            if (numId != null && abstractId != null && abstractLists.containsKey(abstractId)) {
                lists.put(numId, abstractLists.get(abstractId));
            }
        }
        return lists;
    }

    private Map<String, Style> readStyles(Document styles) {
        Map<String, Style> byId = new HashMap<>();
        if (styles == null) {
            return byId;
        }
        for (Element style : childrenOf(styles.getDocumentElement(), "style")) {
            String id = attribute(style, "styleId");
            if (id == null) {
                continue;
            }
            Element properties = firstChild(style, "pPr");
            Element numPr = properties == null ? null : firstChild(properties, "numPr");
            byId.put(id, new Style(
                valueOf(style, "name", ""),
                valueOf(style, "basedOn", null),
                numPr == null ? null : intValueOfChild(numPr, "numId"),
                numPr == null ? null : intValueOfChild(numPr, "ilvl")));
        }
        return byId;
    }

    // --- Recorrido del documento ---

    private List<NumberedHeading> readHeadings(Document document,
                                               Map<String, Style> styles,
                                               Map<Integer, Map<Integer, Level>> lists) {
        List<NumberedHeading> headings = new ArrayList<>();
        Map<Integer, int[]> counters = new HashMap<>();

        for (Element paragraph : paragraphsOf(document)) {
            String styleId = valueOfChildOf(paragraph, "pPr", "pStyle");
            Style style = styleId == null ? null : styles.get(styleId);

            int[] listRef = listOf(paragraph, style, styles);
            if (listRef == null) {
                continue;
            }
            Map<Integer, Level> levels = lists.get(listRef[0]);
            if (levels == null) {
                continue;
            }
            // El contador avanza aunque el párrafo no sea un encabezado: los
            // apartados de una misma lista comparten numeración.
            String number = advance(counters, listRef[0], listRef[1], levels);

            if (number != null && isHeading(style)) {
                headings.add(new NumberedHeading(textOf(paragraph), number));
            }
        }
        return headings;
    }

    /** Lista y nivel del párrafo: los suyos propios o los que hereda del estilo. */
    private int[] listOf(Element paragraph, Style style, Map<String, Style> styles) {
        String pPr = "pPr";
        Element properties = firstChild(paragraph, pPr);
        Element numPr = properties == null ? null : firstChild(properties, "numPr");
        if (numPr != null) {
            Integer numId = intValueOfChild(numPr, "numId");
            if (numId != null && numId != 0) {
                Integer level = intValueOfChild(numPr, "ilvl");
                return new int[] {numId, level == null ? 0 : level};
            }
        }
        // Lo habitual con la lista multinivel enlazada a títulos: la numeración
        // está en el estilo, no en el párrafo.
        Style current = style;
        for (int depth = 0; current != null && depth < MAX_LEVELS; depth++) {
            if (current.numId() != null && current.numId() != 0) {
                return new int[] {current.numId(), current.level() == null ? 0 : current.level()};
            }
            current = current.basedOn() == null ? null : styles.get(current.basedOn());
        }
        return null;
    }

    private boolean isHeading(Style style) {
        return style != null && HEADING_STYLE.matcher(style.name().trim()).matches();
    }

    /**
     * Cuenta un párrafo de la lista y devuelve su número ya escrito, o null si el
     * nivel no se numera (viñetas) o usa un formato que no se sabe representar.
     */
    private String advance(Map<Integer, int[]> counters, int numId, int level, Map<Integer, Level> levels) {
        Level definition = levels.get(level);
        if (definition == null || level >= MAX_LEVELS) {
            return null;
        }
        int[] counter = counters.computeIfAbsent(numId, id -> startValues(levels));
        counter[level]++;
        // Al avanzar un nivel, los inferiores vuelven a empezar
        for (int deeper = level + 1; deeper < MAX_LEVELS; deeper++) {
            Level deeperLevel = levels.get(deeper);
            counter[deeper] = (deeperLevel == null ? 1 : deeperLevel.start()) - 1;
        }
        return format(definition.text(), counter, levels);
    }

    private int[] startValues(Map<Integer, Level> levels) {
        int[] counter = new int[MAX_LEVELS];
        for (int level = 0; level < MAX_LEVELS; level++) {
            Level definition = levels.get(level);
            counter[level] = (definition == null ? 1 : definition.start()) - 1;
        }
        return counter;
    }

    /** Sustituye "%1.%2" por los contadores, con el formato de cada nivel. */
    private String format(String pattern, int[] counter, Map<Integer, Level> levels) {
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        Matcher placeholder = LEVEL_PLACEHOLDER.matcher(pattern);
        StringBuilder number = new StringBuilder();
        while (placeholder.find()) {
            int level = Integer.parseInt(placeholder.group(1)) - 1;
            Level definition = levels.get(level);
            String value = write(counter[level], definition == null ? "decimal" : definition.format());
            if (value == null) {
                return null; // viñeta o formato desconocido: mejor no inventar
            }
            placeholder.appendReplacement(number, Matcher.quoteReplacement(value));
        }
        placeholder.appendTail(number);

        String result = number.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private String write(int value, String format) {
        if (value <= 0) {
            return null;
        }
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "decimal", "decimalzero" -> String.valueOf(value);
            case "upperletter" -> letters(value).toUpperCase(Locale.ROOT);
            case "lowerletter" -> letters(value);
            case "upperroman" -> roman(value);
            case "lowerroman" -> roman(value).toLowerCase(Locale.ROOT);
            default -> null;
        };
    }

    /** 1 → a, 2 → b, ... 27 → aa (como numera Word). */
    private String letters(int value) {
        StringBuilder text = new StringBuilder();
        char letter = (char) ('a' + (value - 1) % 26);
        text.append(String.valueOf(letter).repeat((value - 1) / 26 + 1));
        return text.toString();
    }

    private String roman(int value) {
        int[] amounts = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder text = new StringBuilder();
        int pending = value;
        for (int i = 0; i < amounts.length; i++) {
            while (pending >= amounts[i]) {
                text.append(symbols[i]);
                pending -= amounts[i];
            }
        }
        return text.toString();
    }

    // --- Utilidades de XML ---

    private Document parse(ZipFile zip, String entryName) throws Exception {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) {
            return null;
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // El .docx viene de fuera: sin entidades externas ni DTD
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream content = zip.getInputStream(entry)) {
            return builder.parse(content);
        }
    }

    /** Todos los párrafos del documento, en el orden en que aparecen. */
    private List<Element> paragraphsOf(Document document) {
        List<Element> paragraphs = new ArrayList<>();
        NodeList found = document.getElementsByTagNameNS(W, "p");
        for (int i = 0; i < found.getLength(); i++) {
            paragraphs.add((Element) found.item(i));
        }
        return paragraphs;
    }

    private List<Element> childrenOf(Element parent, String name) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && W.equals(element.getNamespaceURI())
                && name.equals(element.getLocalName())) {
                children.add(element);
            }
        }
        return children;
    }

    private Element firstChild(Element parent, String name) {
        List<Element> children = childrenOf(parent, name);
        return children.isEmpty() ? null : children.get(0);
    }

    private String attribute(Element element, String name) {
        String value = element.getAttributeNS(W, name);
        return value == null || value.isEmpty() ? null : value;
    }

    private Integer intAttribute(Element element, String name) {
        return toInt(attribute(element, name));
    }

    /** Valor del atributo w:val de un hijo: {@code <w:numFmt w:val="decimal"/>}. */
    private String valueOf(Element parent, String childName, String fallback) {
        Element child = firstChild(parent, childName);
        String value = child == null ? null : attribute(child, "val");
        return value == null ? fallback : value;
    }

    private int intValueOf(Element parent, String childName, int fallback) {
        Integer value = toInt(valueOf(parent, childName, null));
        return value == null ? fallback : value;
    }

    private Integer intValueOfChild(Element parent, String childName) {
        return toInt(valueOf(parent, childName, null));
    }

    private String valueOfChildOf(Element parent, String childName, String grandChildName) {
        Element child = firstChild(parent, childName);
        return child == null ? null : valueOf(child, grandChildName, null);
    }

    private Integer toInt(String value) {
        try {
            return value == null ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Texto visible de un párrafo, juntando todos sus fragmentos. */
    private String textOf(Element paragraph) {
        StringBuilder text = new StringBuilder();
        NodeList runs = paragraph.getElementsByTagNameNS(W, "t");
        for (int i = 0; i < runs.getLength(); i++) {
            text.append(runs.item(i).getTextContent());
        }
        return text.toString().trim();
    }
}
