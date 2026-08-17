package com.wordtomarkdown;

import java.util.List;

/**
 * Sentido en el que se convierte: de Word a Markdown o al revés.
 *
 * <p>Reúne lo que cambia entre los dos sentidos —extensiones y cómo se nombra
 * cada cosa— para que ni la ventana ni el registro tengan que ir preguntando
 * "¿en qué modo estamos?" en cada línea de texto.
 */
public enum ConversionDirection {

    WORD_TO_MARKDOWN(
        List.of(".docx"), ".md",
        "un .docx", "Documentos .docx",
        "Archivo .docx:", "Convertir a Markdown",
        "Documentos Word (*.docx)", "docx"),

    MARKDOWN_TO_WORD(
        // .markdown es la otra extensión habitual del formato
        List.of(".md", ".markdown"), ".docx",
        "un .md", "Archivos .md",
        "Archivo .md:", "Convertir a Word",
        "Archivos Markdown (*.md, *.markdown)", "md", "markdown");

    private final List<String> inputExtensions;
    private final String outputExtension;
    private final String inputKind;
    private final String inputKindPlural;
    private final String pathLabel;
    private final String actionLabel;
    private final String fileFilterDescription;
    private final String[] fileFilterExtensions;

    ConversionDirection(List<String> inputExtensions, String outputExtension,
                        String inputKind, String inputKindPlural,
                        String pathLabel, String actionLabel,
                        String fileFilterDescription, String... fileFilterExtensions) {
        this.inputExtensions = List.copyOf(inputExtensions);
        this.outputExtension = outputExtension;
        this.inputKind = inputKind;
        this.inputKindPlural = inputKindPlural;
        this.pathLabel = pathLabel;
        this.actionLabel = actionLabel;
        this.fileFilterDescription = fileFilterDescription;
        this.fileFilterExtensions = fileFilterExtensions;
    }

    /** Extensiones que acepta como entrada, en minúsculas y con el punto. */
    public List<String> inputExtensions() {
        return inputExtensions;
    }

    /** Extensión del archivo que se genera. */
    public String outputExtension() {
        return outputExtension;
    }

    /** Cómo se nombra un archivo de entrada: "un .docx". */
    public String inputKind() {
        return inputKind;
    }

    /** Cómo se nombra el conjunto: "Documentos .docx". */
    public String inputKindPlural() {
        return inputKindPlural;
    }

    /** Etiqueta del campo de ruta cuando se elige un archivo suelto. */
    public String pathLabel() {
        return pathLabel;
    }

    /** Texto del botón que lanza la conversión. */
    public String actionLabel() {
        return actionLabel;
    }

    /** Descripción del filtro del diálogo de selección. */
    public String fileFilterDescription() {
        return fileFilterDescription;
    }

    /** Extensiones del filtro, sin punto, como las espera Swing. */
    public String[] fileFilterExtensions() {
        return fileFilterExtensions.clone();
    }

    /** true si el nombre termina en alguna de las extensiones de entrada. */
    public boolean matchesInput(String fileName) {
        String lower = fileName.toLowerCase();
        return inputExtensions.stream().anyMatch(lower::endsWith);
    }
}
