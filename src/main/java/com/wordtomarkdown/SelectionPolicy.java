package com.wordtomarkdown;

import java.io.File;
import java.util.List;

/**
 * Decide qué hacer con una ruta que el usuario suelta sobre la ventana.
 *
 * <p>Separa el criterio (¿se acepta?, ¿archivo o carpeta?, ¿qué se anota en el
 * registro?) de su aplicación a los componentes Swing, de modo que pueda probarse
 * sin necesidad de una pantalla.
 */
public class SelectionPolicy {

    private final ConversionService service;

    public SelectionPolicy(ConversionService service) {
        this.service = service;
    }

    /**
     * Resultado de evaluar una ruta soltada.
     *
     * @param accepted   true si la ruta sirve como selección
     * @param folderMode true si debe activarse el modo carpeta
     * @param path       ruta aceptada, o null si se rechazó
     * @param messages   líneas a volcar en el registro
     */
    public record Decision(boolean accepted, boolean folderMode, File path, List<String> messages) {

        public Decision {
            messages = List.copyOf(messages);
        }

        static Decision rejected(String message) {
            return new Decision(false, false, null, List.of(message));
        }
    }

    public Decision decideDrop(File dropped) {
        if (dropped == null || !dropped.exists()) {
            return Decision.rejected("Ignorado (no existe): " + (dropped == null ? "-" : dropped.getName()));
        }

        if (dropped.isDirectory()) {
            return new Decision(true, true, dropped, List.of(
                "Carpeta arrastrada: " + dropped.getAbsolutePath(),
                "Documentos .docx encontrados: " + service.findDocxFiles(dropped).size()));
        }

        if (!service.isDocx(dropped)) {
            return Decision.rejected("Ignorado (no es un .docx): " + dropped.getName());
        }

        return new Decision(true, false, dropped, List.of("Archivo arrastrado: " + dropped.getName()));
    }
}
