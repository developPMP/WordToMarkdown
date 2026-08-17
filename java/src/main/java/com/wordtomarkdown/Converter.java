package com.wordtomarkdown;

import java.io.File;
import java.util.List;

/**
 * Conversión de documentos en un sentido concreto ({@link ConversionDirection}).
 *
 * <p>La ventana trabaja siempre contra esta interfaz, de modo que cambiar de
 * sentido es cambiar de implementación: no hay condicionales repartidos por la
 * interfaz gráfica.
 */
public interface Converter {

    /** Sentido en el que convierte. */
    ConversionDirection direction();

    /** true si el archivo sirve como entrada para este sentido. */
    boolean isInput(File file);

    /**
     * Archivos convertibles directamente contenidos en la carpeta, ordenados por
     * nombre. No se recorren las subcarpetas.
     */
    List<File> findInputFiles(File folder);

    /**
     * Convierte un archivo y escribe el resultado junto al original. Nunca lanza:
     * los fallos se describen en el {@link ConversionResult} devuelto.
     */
    ConversionResult convert(File input);
}
