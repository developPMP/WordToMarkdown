package com.wordtomarkdown;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Limpieza del Markdown generado")
class MarkdownCleanerTest {

    private final MarkdownCleaner cleaner = new MarkdownCleaner();

    @Test
    @DisplayName("mueve el separador de la tabla detrás de la primera fila")
    void colocaElSeparadorDeLaTabla() {
        String markdown = """
            |--------|-------------|
            | Campo  | Descripcion |
            | Nombre | Texto libre |
            """;

        assertEquals("""
            | Campo  | Descripcion |
            |--------|-------------|
            | Nombre | Texto libre |
            """, cleaner.clean(markdown));
    }

    @Test
    @DisplayName("no toca la tabla que ya trae el separador en su sitio")
    void respetaLaTablaCorrecta() {
        String markdown = """
            | Campo  | Descripcion |
            |--------|-------------|
            | Nombre | Texto libre |
            | Edad   | Numero      |
            """;

        assertEquals(markdown, cleaner.clean(markdown));
    }

    @Test
    @DisplayName("corrige todas las tablas del documento, no solo la primera")
    void corrigeVariasTablas() {
        String markdown = """
            |-----|-----|
            | a   | b   |

            Texto entre tablas.

            |-----|-----|
            | c   | d   |
            """;

        assertEquals("""
            | a   | b   |
            |-----|-----|

            Texto entre tablas.

            | c   | d   |
            |-----|-----|
            """, cleaner.clean(markdown));
    }

    @Test
    @DisplayName("quita las anclas de Word y reapunta los enlaces al encabezado")
    void limpiaLasAnclasDeWord() {
        String markdown = """
            [3.3 Interfaz](#_Toc11)

            # {#_Toc11}3.3 Interfaz

            Ver el {#_Ref20}apartado anterior.
            """;

        String clean = cleaner.clean(markdown);

        assertFalse(clean.contains("_Toc11"), clean);
        assertTrue(clean.contains("# 3.3 Interfaz"), clean);
        assertTrue(clean.contains("[3.3 Interfaz](#33-interfaz)"), clean);
        assertTrue(clean.contains("Ver el apartado anterior."), clean);
    }

    @Test
    @DisplayName("un enlace a un ancla sin destino conserva el texto y pierde el enlace")
    void enlaceSinDestino() {
        String clean = cleaner.clean("Consultar el [anexo B](#_Toc99) del documento.\n");

        assertEquals("Consultar el anexo B del documento.\n", clean);
    }

    @Test
    @DisplayName("numera las anclas repetidas igual que los visores")
    void encabezadosRepetidos() {
        String clean = cleaner.clean("""
            # {#_Toc1}Alcance

            # {#_Toc2}Alcance

            [primero](#_Toc1) y [segundo](#_Toc2)
            """);

        assertTrue(clean.contains("[primero](#alcance)"), clean);
        assertTrue(clean.contains("[segundo](#alcance-1)"), clean);
    }

    @Test
    @DisplayName("deja el índice como lista, sin número de página y con sangría por numeración")
    void indiceComoLista() {
        String markdown = """
            [3 Requisitos](#3-requisitos) 8

            [3.3 Interfaz](#33-interfaz) 9

            [3.3.1 Pantalla](#331-pantalla) 10
            """;

        assertEquals("""
            - [3 Requisitos](#3-requisitos)
              - [3.3 Interfaz](#33-interfaz)
                - [3.3.1 Pantalla](#331-pantalla)
            """, cleaner.clean(markdown));
    }

    @Test
    @DisplayName("el índice sin enlaces también queda como lista")
    void indiceSinEnlaces() {
        String markdown = """
            1 Introduccion ........ 3

            1.1 Alcance ........ 4

            2 Requisitos ........ 8
            """;

        assertEquals("""
            - 1 Introduccion
              - 1.1 Alcance
            - 2 Requisitos
            """, cleaner.clean(markdown));
    }

    @Test
    @DisplayName("una lista de apartados numerados no se confunde con un índice")
    void apartadosNumeradosNoSonIndice() {
        String markdown = """
            3.1 El sistema admite hasta 10

            3.2 El plazo maximo es de 30

            3.3 Los reintentos permitidos son 3
            """;

        assertEquals(markdown, cleaner.clean(markdown));
    }

    @Test
    @DisplayName("la sangría del índice no salta niveles")
    void sangriaSinSaltos() {
        String clean = cleaner.clean("""
            [3 Requisitos](#a) 8

            [3.3.1.1 Pantalla](#b) 9
            """);

        assertEquals("""
            - [3 Requisitos](#a)
              - [3.3.1.1 Pantalla](#b)
            """, clean);
    }

    @Test
    @DisplayName("colapsa los blancos de más y cierra el archivo con un solo salto")
    void espaciadoUniforme() {
        String clean = cleaner.clean("\n\nPrimer parrafo   \n\n\n\nSegundo parrafo\n\n\n");

        assertEquals("Primer parrafo\n\nSegundo parrafo\n", clean);
    }

    @Test
    @DisplayName("conserva el salto de línea forzado (dos espacios al final)")
    void conservaElSaltoForzado() {
        String clean = cleaner.clean("linea1  \nlinea2\n");

        assertEquals("linea1  \nlinea2\n", clean);
    }

    @Test
    @DisplayName("quita el espacio anterior a la puntuación y los espacios duros de Word")
    void puntuacionYEspaciosDeWord() {
        String clean = cleaner.clean("El resultado  es correcto , seguro .\n");

        assertEquals("El resultado es correcto, seguro.\n", clean);
    }

    @Test
    @DisplayName("recompone las tildes y la eñe que vienen descompuestas")
    void acentosDescompuestos() {
        // Los mismos textos, pero con la tilde y la virgulilla como carácter aparte
        String descompuesto = "# Introduccio\u0301n y Disen\u0303o\n\nAn\u0303o.\n";

        String clean = cleaner.clean(descompuesto);

        assertEquals("# Introducción y Diseño\n\nAño.\n", clean);
        assertFalse(clean.contains("\u0301"), "no debe quedar ninguna tilde suelta");
        assertFalse(clean.contains("\u0303"), "ni ninguna virgulilla suelta");
    }

    @Test
    @DisplayName("el destino de un encabezado con tilde no depende de cómo venga escrita")
    void anclaConTildeDescompuesta() {
        String clean = cleaner.clean("# {#_Toc1}Introduccio\u0301n\n\n[ver](#_Toc1)\n");

        assertTrue(clean.contains("# Introducción"), clean);
        assertTrue(clean.contains("[ver](#introducción)"), clean);
    }

    @Test
    @DisplayName("un Markdown vacío o nulo no rompe la conversión")
    void entradaVacia() {
        assertEquals("", cleaner.clean(null));
        assertEquals("", cleaner.clean("   \n\n"));
    }
}
