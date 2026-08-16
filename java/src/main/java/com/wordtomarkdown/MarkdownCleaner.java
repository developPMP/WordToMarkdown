package com.wordtomarkdown;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Retoques sobre el Markdown que produce flexmark, para que el documento se vea
 * igual de bien en cualquier visor (GitHub, VS Code, etc.).
 *
 * <p>Corrige cuatro herencias del formato de Word:
 * <ul>
 *   <li>las tablas sin fila de encabezado marcada salen con el separador
 *       ({@code |---|---|}) en primera posición, lo que impide que el visor las
 *       reconozca y no se ve el cuadro;</li>
 *   <li>los marcadores internos de Word quedan como anclas sueltas en el texto
 *       ({@code {#_Toc12345}});</li>
 *   <li>el índice queda como párrafos con el número de página pegado, y sus
 *       enlaces apuntan a esas anclas;</li>
 *   <li>sobran líneas en blanco y espacios antes de la puntuación.</li>
 * </ul>
 */
public class MarkdownCleaner {

    /** Encabezado ATX: "## Título". */
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");

    /** Ancla que flexmark emite a partir de un marcador de Word: "{#_Toc12345}". */
    private static final Pattern ANCHOR = Pattern.compile("\\{#([^}\\s]+)}");

    /** Enlace a un destino del propio documento: "[texto](#destino)". */
    private static final Pattern INTERNAL_LINK = Pattern.compile("\\[([^\\]]*)]\\(#([^)\\s]*)\\)");

    /** Fila separadora de una tabla: "|---|:---:|". */
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|[\\s:|-]*-[\\s:|-]*\\|$");

    /**
     * Entrada de índice enlazada: un enlace interno, con o sin viñeta, seguido de
     * los puntos guía y el número de página que Word arrastra del documento.
     */
    private static final Pattern LINKED_INDEX_ENTRY =
        Pattern.compile("^([ \\t]*)(?:[*+-][ \\t]+)?(\\[[^\\]]*]\\(#[^)\\s]*\\))[ \\t.]*(\\d*)[ \\t]*$");

    /** Entrada de índice sin enlace: "3.3.1 Configuración ...... 12". */
    private static final Pattern NUMBERED_INDEX_ENTRY =
        Pattern.compile("^([ \\t]*)(?:[*+-][ \\t]+)?(\\d+(?:\\.\\d+)*\\.?[ \\t]+\\S.*?)[ \\t.]+(\\d+)[ \\t]*$");

    /** Numeración multinivel al principio del texto: "3", "3.1", "3.3.1.1". */
    private static final Pattern OUTLINE_NUMBER = Pattern.compile("^\\[?\\s*(\\d+(?:\\.\\d+)*)\\.?\\s");

    /** Entradas enlazadas que bastan para dar un bloque por índice. */
    private static final int MIN_LINKED_ENTRIES = 2;

    /**
     * Entradas sin enlace que hacen falta para dar un bloque por índice. Se pide
     * más evidencia porque un párrafo numerado normal se le parece bastante.
     */
    private static final int MIN_NUMBERED_ENTRIES = 3;

    /** Nivel máximo de sangría del índice, para no anidar sin fin. */
    private static final int MAX_INDEX_LEVEL = 6;

    /** Sangría que flexmark usa por cada nivel de lista. */
    private static final int SPACES_PER_LEVEL = 2;

    /** Espacios repetidos en medio del texto (la sangría del principio se respeta). */
    private static final Pattern INNER_SPACES = Pattern.compile("(?<=\\S)[ \\t]{2,}(?=\\S)");

    /** Espacio sobrante delante de un signo de puntuación. */
    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile("(?<=\\S)[ \\t]+(?=[,;:.](?:\\s|$))");

    /** Puntos guía del índice ("Introducción . . . . 5"), que no deben tocarse como puntuación. */
    private static final Pattern DOT_LEADERS = Pattern.compile("\\.[ \\t]+\\.");

    /** Espacios de Word que no son el espacio normal (duro, fino, de cifra). */
    private static final String WORD_SPACES = "[\\u00A0\\u2007\\u202F]";

    /** Limpia el Markdown recién generado. Nunca devuelve {@code null}. */
    public String clean(String markdown) {
        return clean(markdown, List.of());
    }

    /**
     * Limpia el Markdown y devuelve a los encabezados la numeración automática
     * del documento Word, que Mammoth no llega a escribir.
     *
     * @param headingNumbers numeración de los encabezados, en orden de aparición
     */
    public String clean(String markdown, List<WordNumbering.NumberedHeading> headingNumbers) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        List<String> lines = new ArrayList<>(List.of(
            markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1)));

        Map<String, String> numbersBySlug = new LinkedHashMap<>();
        Map<String, String> targets = stripAnchors(lines, new HeadingNumbers(headingNumbers), numbersBySlug);
        retargetInternalLinks(lines, targets);
        repairTables(lines);
        List<String> compacted = compactIndex(lines, numbersBySlug);
        return normalizeSpacing(compacted);
    }

    /**
     * Quita las anclas heredadas de Word y devuelve, para cada una, el destino
     * equivalente en Markdown: el identificador que los visores generan a partir
     * del texto del encabezado. Así el índice sigue siendo navegable sin ellas.
     *
     * <p>Modifica la lista recibida.
     */
    private Map<String, String> stripAnchors(List<String> lines,
                                             HeadingNumbers numbers,
                                             Map<String, String> numbersBySlug) {
        Map<String, String> targets = new LinkedHashMap<>();
        Set<String> usedSlugs = new HashSet<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                String text = ANCHOR.matcher(heading.group(2)).replaceAll("").trim();

                // La numeración va delante del texto y forma parte del destino
                String number = numbers.numberFor(text);
                if (number != null) {
                    text = number + " " + text;
                }
                String slug = uniqueSlugOf(text, usedSlugs);
                if (number != null) {
                    numbersBySlug.put(slug, number);
                }
                Matcher anchors = ANCHOR.matcher(heading.group(2));
                while (anchors.find()) {
                    targets.put(anchors.group(1), slug);
                }
                lines.set(i, text.isEmpty() ? "" : heading.group(1) + " " + text);
            } else {
                // Fuera de los encabezados solo se quitan los marcadores internos
                // de Word (empiezan por "_"): un "{#...}" escrito a propósito se respeta.
                lines.set(i, line.replaceAll("\\{#_[^}\\s]*}", ""));
            }
        }
        return targets;
    }

    /**
     * Va repartiendo la numeración del documento entre los encabezados del
     * Markdown, emparejándolos por texto y en orden.
     *
     * <p>No todos los encabezados están numerados, así que no vale con ir por
     * posición. Se admite saltarse unos pocos por si alguno no casa (Mammoth
     * puede reescribir el texto), pero no más: si no, un título repetido más
     * adelante se quedaría con el número que no le toca.
     */
    private static final class HeadingNumbers {

        /** Cuántos encabezados numerados se pueden saltar buscando el que casa. */
        private static final int LOOKAHEAD = 3;

        /** Numeración ya escrita en el texto del documento: "3.1 Alcance". */
        private static final Pattern ALREADY_NUMBERED = Pattern.compile("^\\d+(?:\\.\\d+)*[.)]?\\s");

        private final List<WordNumbering.NumberedHeading> pending;
        private int position;

        HeadingNumbers(List<WordNumbering.NumberedHeading> pending) {
            this.pending = pending == null ? List.of() : pending;
        }

        /** Número que corresponde al encabezado, o null si no le toca ninguno. */
        String numberFor(String headingText) {
            if (position >= pending.size() || ALREADY_NUMBERED.matcher(headingText).find()) {
                return null;
            }
            String wanted = comparable(headingText);
            int limit = Math.min(pending.size(), position + LOOKAHEAD);
            for (int i = position; i < limit; i++) {
                if (comparable(pending.get(i).text()).equals(wanted)) {
                    position = i + 1;
                    return pending.get(i).number();
                }
            }
            return null;
        }

        /** Texto comparable: sin el formato que añade Markdown ni espacios de más. */
        private String comparable(String text) {
            return text.replaceAll("[*_`\\\\]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Reapunta los enlaces internos al encabezado correspondiente. Si el ancla
     * era de Word y ya no existe destino, se conserva el texto pero se retira el
     * enlace: un enlace roto estorba más que un texto plano.
     *
     * <p>Modifica la lista recibida.
     */
    private void retargetInternalLinks(List<String> lines, Map<String, String> targets) {
        for (int i = 0; i < lines.size(); i++) {
            Matcher link = INTERNAL_LINK.matcher(lines.get(i));
            StringBuilder rebuilt = new StringBuilder();
            while (link.find()) {
                String text = link.group(1);
                String anchor = link.group(2);
                String slug = targets.get(anchor);
                String replacement;
                if (slug != null) {
                    replacement = "[" + text + "](#" + slug + ")";
                } else if (anchor.startsWith("_")) {
                    replacement = text;
                } else {
                    replacement = link.group();
                }
                link.appendReplacement(rebuilt, Matcher.quoteReplacement(replacement));
            }
            link.appendTail(rebuilt);
            lines.set(i, rebuilt.toString());
        }
    }

    /**
     * Coloca el separador de cada tabla en su sitio. flexmark lo escribe el
     * primero cuando el documento no marcó ninguna fila como encabezado (lo
     * habitual en Word), y entonces el visor no reconoce la tabla y no se ve
     * nada: basta con dejar la primera fila como encabezado.
     *
     * <p>Modifica la lista recibida.
     */
    private void repairTables(List<String> lines) {
        int i = 0;
        while (i < lines.size()) {
            if (!isTableRow(lines.get(i))) {
                i++;
                continue;
            }
            // Solo se toca el separador si abre la tabla: si va después de una
            // fila, el documento sí marcaba encabezado y está donde debe.
            if (isSeparator(lines, i) && i + 1 < lines.size()
                && isTableRow(lines.get(i + 1)) && !isSeparator(lines, i + 1)) {
                lines.set(i, lines.set(i + 1, lines.get(i)));
            }
            while (i < lines.size() && isTableRow(lines.get(i))) {
                i++;
            }
        }
    }

    private boolean isSeparator(List<String> lines, int i) {
        return TABLE_SEPARATOR.matcher(lines.get(i)).matches();
    }

    /**
     * Deja el índice como una lista compacta: viñeta uniforme, una entrada por
     * línea, sin el número de página del documento original y con la sangría que
     * corresponde a su numeración (3 → 3.1 → 3.1.1 ...).
     */
    private List<String> compactIndex(List<String> lines, Map<String, String> numbersBySlug) {
        List<String> result = new ArrayList<>(lines.size());
        int i = 0;
        while (i < lines.size()) {
            IndexBlock block = readIndexBlock(lines, i, numbersBySlug);
            if (block.isEmpty()) {
                result.add(lines.get(i));
                i++;
            } else {
                block.entries().forEach(entry -> result.add(entry.asListItem()));
                i = block.end();
            }
        }
        return result;
    }

    /**
     * Lee el índice que empieza en {@code from}, saltando las líneas en blanco
     * que Word deja entre entradas. Devuelve un bloque vacío si lo que hay ahí
     * no reúne evidencia suficiente de ser un índice.
     */
    private IndexBlock readIndexBlock(List<String> lines, int from, Map<String, String> numbersBySlug) {
        List<IndexEntry> entries = new ArrayList<>();
        int linked = 0;
        int numbered = 0;
        int lastEntry = -1;

        for (int i = from; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                if (entries.isEmpty()) {
                    break;
                }
                continue;
            }
            Matcher link = LINKED_INDEX_ENTRY.matcher(lines.get(i));
            Matcher numeral = NUMBERED_INDEX_ENTRY.matcher(lines.get(i));
            IndexEntry entry;
            if (link.matches()) {
                entry = entryOf(link.group(1), numbered(link.group(2), numbersBySlug), link.group(3));
                linked++;
            } else if (numeral.matches()) {
                entry = entryOf(numeral.group(1), numeral.group(2), numeral.group(3));
                numbered++;
            } else {
                break;
            }
            entries.add(entry);
            lastEntry = i;
        }

        boolean isIndex = linked >= MIN_LINKED_ENTRIES
            || (numbered >= MIN_NUMBERED_ENTRIES && linked == 0 && pagesInOrder(entries));
        return isIndex ? new IndexBlock(withStepwiseLevels(entries), lastEntry + 1) : IndexBlock.none(from);
    }

    /**
     * La sangría solo puede bajar un nivel cada vez: un salto de "3.3" a
     * "3.3.1.1" dejaría una lista mal anidada en el visor.
     */
    private List<IndexEntry> withStepwiseLevels(List<IndexEntry> entries) {
        List<IndexEntry> stepwise = new ArrayList<>(entries.size());
        int previous = 0;
        for (IndexEntry entry : entries) {
            int level = Math.min(entry.level(), previous + 1);
            stepwise.add(new IndexEntry(level, entry.text(), entry.page()));
            previous = level;
        }
        return stepwise;
    }

    /**
     * Añade a la entrada del índice la numeración del encabezado al que enlaza,
     * si el documento no la traía ya escrita en el texto.
     */
    private String numbered(String entry, Map<String, String> numbersBySlug) {
        Matcher link = INTERNAL_LINK.matcher(entry);
        if (numbersBySlug.isEmpty() || !link.matches()) {
            return entry;
        }
        String number = numbersBySlug.get(link.group(2));
        String text = link.group(1);
        // Si el índice de Word ya traía su numeración escrita, se respeta la suya
        if (number == null || HeadingNumbers.ALREADY_NUMBERED.matcher(text).find()) {
            return entry;
        }
        return "[" + number + " " + text + "](#" + link.group(2) + ")";
    }

    private IndexEntry entryOf(String indent, String content, String page) {
        String text = content.strip();
        Matcher number = OUTLINE_NUMBER.matcher(text);
        int level = number.find()
            ? countOf(number.group(1), '.') + 1
            : indentLevelOf(indent);
        return new IndexEntry(Math.min(level, MAX_INDEX_LEVEL), text, pageOf(page));
    }

    /**
     * Un índice de verdad no retrocede de página. Es la comprobación que separa
     * un índice sin enlaces de una simple lista de apartados numerados.
     */
    private boolean pagesInOrder(List<IndexEntry> entries) {
        int previous = 0;
        for (IndexEntry entry : entries) {
            if (entry.page() < previous) {
                return false;
            }
            previous = entry.page();
        }
        return true;
    }

    private int pageOf(String page) {
        try {
            return page == null || page.isEmpty() ? 0 : Integer.parseInt(page);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** Nivel deducido de la sangría que traía la línea, si no hay numeración. */
    private int indentLevelOf(String indent) {
        int spaces = indent.replace("\t", " ".repeat(SPACES_PER_LEVEL)).length();
        return Math.min(spaces / SPACES_PER_LEVEL + 1, MAX_INDEX_LEVEL);
    }

    private int countOf(String text, char c) {
        return (int) text.chars().filter(ch -> ch == c).count();
    }

    /** Entrada del índice ya normalizada. */
    private record IndexEntry(int level, String text, int page) {
        String asListItem() {
            return " ".repeat(SPACES_PER_LEVEL * (level - 1)) + "- " + text;
        }
    }

    /** Índice localizado en el documento y primera línea que ya no le pertenece. */
    private record IndexBlock(List<IndexEntry> entries, int end) {
        static IndexBlock none(int position) {
            return new IndexBlock(List.of(), position);
        }

        boolean isEmpty() {
            return entries.isEmpty();
        }
    }

    /**
     * Espaciado final: sin espacios al final de línea (salvo el salto forzado de
     * dos espacios), sin espacio antes de la puntuación, como mucho una línea en
     * blanco seguida y un único salto al final del archivo. El contenido de los
     * bloques de código se deja intacto.
     */
    private String normalizeSpacing(List<String> lines) {
        StringBuilder result = new StringBuilder();
        boolean insideCode = false;
        boolean pendingBlank = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.stripLeading().startsWith("```") || line.stripLeading().startsWith("~~~")) {
                insideCode = !insideCode;
            }
            // El salto forzado (dos espacios) solo tiene sentido si el párrafo continúa
            boolean paragraphContinues = i + 1 < lines.size() && !lines.get(i + 1).isBlank();
            String clean = insideCode ? line : tidy(line, paragraphContinues);
            if (clean.isBlank() && !insideCode) {
                pendingBlank = !result.isEmpty();
                continue;
            }
            if (pendingBlank) {
                result.append('\n');
                pendingBlank = false;
            }
            result.append(clean).append('\n');
        }
        return result.toString();
    }

    /** Retoques de una línea suelta de texto. */
    private String tidy(String line, boolean paragraphContinues) {
        String clean = line.replaceAll(WORD_SPACES, " ");
        boolean hardBreak = paragraphContinues && clean.endsWith("  ") && !clean.isBlank();
        clean = clean.stripTrailing();

        // La separación entre columnas de una tabla es significativa: no se toca
        if (!isTableRow(clean)) {
            clean = INNER_SPACES.matcher(clean).replaceAll(" ");
            if (!DOT_LEADERS.matcher(clean).find()) {
                clean = SPACE_BEFORE_PUNCTUATION.matcher(clean).replaceAll("");
            }
        }
        return hardBreak ? clean + "  " : clean;
    }

    private boolean isTableRow(String line) {
        return line.startsWith("|");
    }

    /**
     * Identificador que los visores de Markdown generan para un encabezado:
     * minúsculas, sin puntuación y con guiones en lugar de espacios. Se numeran
     * los repetidos igual que hace GitHub, para no duplicar destinos.
     */
    private String uniqueSlugOf(String headingText, Set<String> used) {
        String text = INTERNAL_LINK.matcher(headingText).replaceAll("$1").replace("\\", "");
        StringBuilder slug = new StringBuilder();
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-') {
                slug.append(c);
            } else if (Character.isWhitespace(c) && !slug.isEmpty() && slug.charAt(slug.length() - 1) != '-') {
                slug.append('-');
            }
        }
        while (!slug.isEmpty() && slug.charAt(slug.length() - 1) == '-') {
            slug.deleteCharAt(slug.length() - 1);
        }

        String base = slug.toString();
        String candidate = base;
        for (int repetition = 1; !used.add(candidate); repetition++) {
            candidate = base + "-" + repetition;
        }
        return candidate;
    }
}
