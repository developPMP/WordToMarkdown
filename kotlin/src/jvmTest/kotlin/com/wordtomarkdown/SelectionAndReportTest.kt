package com.wordtomarkdown

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindDocxFilesTest {

    private val service = ConversionService()
    private lateinit var folder: File

    @BeforeTest
    fun crearCarpetaTemporal() {
        folder = Files.createTempDirectory("wtm-find").toFile()
    }

    @AfterTest
    fun limpiar() {
        folder.deleteRecursively()
    }

    @Test
    fun `devuelve los docx ordenados por nombre sin distinguir mayusculas`() {
        DocxFixtures.simpleDocument(folder, "beta.docx", "b")
        DocxFixtures.simpleDocument(folder, "Alpha.docx", "a")
        DocxFixtures.simpleDocument(folder, "Gamma.DOCX", "g")

        assertEquals(
            listOf("Alpha.docx", "beta.docx", "Gamma.DOCX"),
            service.findDocxFiles(folder).map { it.name },
        )
    }

    @Test
    fun `ignora los temporales de Word`() {
        DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido")
        File(folder, "~\$Informe.docx").writeText("lock")

        val encontrados = service.findDocxFiles(folder)

        assertEquals(listOf("Informe.docx"), encontrados.map { it.name })
    }

    @Test
    fun `ignora archivos que no son docx`() {
        DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido")
        File(folder, "notas.txt").writeText("hola")
        File(folder, "hoja.xlsx").writeText("datos")

        assertEquals(1, service.findDocxFiles(folder).size)
    }

    @Test
    fun `no recorre las subcarpetas`() {
        DocxFixtures.simpleDocument(folder, "Raiz.docx", "raiz")
        val sub = File(folder, "sub").apply { mkdir() }
        DocxFixtures.simpleDocument(sub, "Anidado.docx", "anidado")

        val encontrados = service.findDocxFiles(folder)

        assertEquals(1, encontrados.size)
        assertEquals("Raiz.docx", encontrados[0].name)
    }

    @Test
    fun `devuelve lista vacia si la carpeta no tiene documentos o no existe`() {
        assertTrue(service.findDocxFiles(folder).isEmpty())
        assertTrue(service.findDocxFiles(File(folder, "no-existe")).isEmpty())
        assertTrue(service.findDocxFiles(null).isEmpty())
    }

    @Test
    fun `isDocx acepta documentos y rechaza el resto`() {
        val documento = DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido")
        val temporal = File(folder, "~\$Informe.docx").apply { writeText("lock") }
        val texto = File(folder, "notas.txt").apply { writeText("hola") }

        assertTrue(service.isDocx(documento))
        assertFalse(service.isDocx(temporal))
        assertFalse(service.isDocx(texto))
        assertFalse(service.isDocx(folder))
        assertFalse(service.isDocx(null))
    }
}

class SelectionPolicyTest {

    private val policy = SelectionPolicy(ConversionService())
    private lateinit var folder: File

    @BeforeTest
    fun crearCarpetaTemporal() {
        folder = Files.createTempDirectory("wtm-policy").toFile()
    }

    @AfterTest
    fun limpiar() {
        folder.deleteRecursively()
    }

    @Test
    fun `un docx se acepta en modo Archivo`() {
        val docx = DocxFixtures.simpleDocument(folder, "Informe.docx", "contenido")

        val decision = policy.decideDrop(docx)

        assertTrue(decision.accepted)
        assertFalse(decision.folderMode, "un documento no debe activar el modo carpeta")
        assertEquals(docx, decision.path)
        assertEquals(listOf("Archivo arrastrado: Informe.docx"), decision.messages)
    }

    @Test
    fun `una carpeta se acepta en modo Carpeta e informa de cuantos docx contiene`() {
        DocxFixtures.simpleDocument(folder, "Alpha.docx", "a")
        DocxFixtures.simpleDocument(folder, "Beta.docx", "b")
        File(folder, "~\$Alpha.docx").writeText("lock")

        val decision = policy.decideDrop(folder)

        assertTrue(decision.accepted)
        assertTrue(decision.folderMode)
        assertEquals(folder, decision.path)
        // El recuento ignora el temporal de Word
        assertTrue("Documentos .docx encontrados: 2" in decision.messages, decision.messages.toString())
    }

    @Test
    fun `un archivo que no es docx se rechaza`() {
        val texto = File(folder, "notas.txt").apply { writeText("hola") }

        val decision = policy.decideDrop(texto)

        assertFalse(decision.accepted)
        assertNull(decision.path)
        assertEquals(listOf("Ignorado (no es un .docx): notas.txt"), decision.messages)
    }

    @Test
    fun `una ruta inexistente o nula se rechaza sin fallar`() {
        assertFalse(policy.decideDrop(File(folder, "no-existe.docx")).accepted)
        assertFalse(policy.decideDrop(null).accepted)
    }
}

class ConversionReporterTest {

    private val reporter = ConversionReporter()
    private val origen = File("/docs/Informe.docx")
    private val salida = File("/docs/Informe.md")
    private val imagenes: Path = Path.of("/docs/Informe_images")

    private fun exito(
        imagenesExtraidas: Int = 0,
        erroresImagen: List<String> = emptyList(),
        avisos: List<String> = emptyList(),
    ) = ConversionResult(
        source = origen,
        output = salida,
        success = true,
        warnings = avisos,
        imagesExtracted = imagenesExtraidas,
        imageErrors = erroresImagen,
        imageDir = imagenes,
    )

    @Test
    fun `una conversion limpia muestra origen salida y OK`() {
        assertEquals(
            listOf(
                "Convirtiendo: Informe.docx",
                "  Salida: ${salida.absolutePath}",
                "  OK: Informe.md",
            ),
            reporter.describe(exito()),
        )
    }

    @Test
    fun `informa del numero de imagenes extraidas y su carpeta`() {
        assertTrue("  Imágenes extraídas: 3 → Informe_images/" in reporter.describe(exito(imagenesExtraidas = 3)))
    }

    @Test
    fun `detalla las imagenes fallidas y lo resume en la linea de resultado`() {
        val lineas = reporter.describe(
            exito(imagenesExtraidas = 1, erroresImagen = listOf("imagen 2: IOException - disco lleno")),
        )

        assertTrue("  No se pudieron extraer 1 imagen(es):" in lineas, lineas.toString())
        assertTrue("    [!] imagen 2: IOException - disco lleno" in lineas, lineas.toString())
        assertTrue("  OK: Informe.md (con 1 imagen(es) sin extraer)" in lineas, lineas.toString())
    }

    @Test
    fun `muestra las advertencias de Mammoth`() {
        val lineas = reporter.describe(exito(avisos = listOf("estilo desconocido")))

        assertTrue("  Advertencias durante la conversión:" in lineas, lineas.toString())
        assertTrue("    [!] estilo desconocido" in lineas, lineas.toString())
    }

    @Test
    fun `un documento fallido muestra el error y ningun OK`() {
        val fallo = ConversionResult.failure(origen, salida, imagenes, "ZipException - roto")

        val lineas = reporter.describe(fallo)

        assertTrue("  ERROR: ZipException - roto" in lineas, lineas.toString())
        assertFalse(lineas.any { it.startsWith("  OK:") }, lineas.toString())
    }

    @Test
    fun `el resumen del lote cuenta convertidos y fallidos`() {
        assertTrue("Resumen: 3 convertido(s), 1 con error." in reporter.summarize(3, 1))
    }
}
