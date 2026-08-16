package com.wordtomarkdown

import java.io.File

/**
 * Decide qué hacer con una ruta que el usuario suelta sobre la ventana.
 *
 * Separa el criterio (¿se acepta?, ¿archivo o carpeta?, ¿qué se anota en el
 * registro?) de su aplicación a la interfaz, de modo que pueda probarse sin
 * necesidad de una pantalla.
 */
class SelectionPolicy(private val service: ConversionService) {

    /**
     * Resultado de evaluar una ruta soltada.
     *
     * @param accepted   true si la ruta sirve como selección
     * @param folderMode true si debe activarse el modo carpeta
     * @param path       ruta aceptada, o null si se rechazó
     * @param messages   líneas a volcar en el registro
     */
    data class Decision(
        val accepted: Boolean,
        val folderMode: Boolean,
        val path: File?,
        val messages: List<String>,
    ) {
        companion object {
            fun rejected(message: String) = Decision(false, false, null, listOf(message))
        }
    }

    fun decideDrop(dropped: File?): Decision {
        if (dropped == null || !dropped.exists()) {
            return Decision.rejected("Ignorado (no existe): ${dropped?.name ?: "-"}")
        }

        if (dropped.isDirectory) {
            return Decision(
                accepted = true,
                folderMode = true,
                path = dropped,
                messages = listOf(
                    "Carpeta arrastrada: ${dropped.absolutePath}",
                    "Documentos .docx encontrados: ${service.findDocxFiles(dropped).size}",
                ),
            )
        }

        if (!service.isDocx(dropped)) {
            return Decision.rejected("Ignorado (no es un .docx): ${dropped.name}")
        }

        return Decision(
            accepted = true,
            folderMode = false,
            path = dropped,
            messages = listOf("Archivo arrastrado: ${dropped.name}"),
        )
    }
}
