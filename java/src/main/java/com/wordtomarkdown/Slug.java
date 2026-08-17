package com.wordtomarkdown;

import java.util.Locale;
import java.util.Set;

/**
 * Identificador que los visores de Markdown generan para un encabezado:
 * minúsculas, sin puntuación y con guiones en lugar de espacios.
 *
 * <p>Lo usan los dos sentidos de la conversión y tiene que dar exactamente el
 * mismo resultado en ambos: al ir a Markdown para reapuntar los enlaces del
 * índice, y al volver a Word para saber a qué marcador apunta cada enlace.
 */
final class Slug {

    private Slug() {
    }

    /**
     * Identificador del texto, numerando los repetidos igual que hace GitHub
     * para no duplicar destinos.
     *
     * @param used identificadores ya emitidos; se le añade el devuelto
     */
    static String of(String text, Set<String> used) {
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
