package com.wordtomarkdown

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConversionServiceTest {

    private val service = ConversionService()
    private lateinit var folder: File

    @BeforeTest
    fun crearCarpetaTemporal() {
        folder = Files.createTempDirectory("wtm-test").toFile()
    }

    @AfterTest
    fun limpiar() {
        folder.deleteRecursively()
    }

    @Test
    fun `escribe el md junto al documento de origen`() {
        val docx = DocxFixtures.simpleDocument(folder, "Informe.docx", "Primer parrafo")

        val result = service.convert(docx)

        assertTrue(result.success, "esperaba éxito, error: ${result.errorMessage}")
        assertEquals(File(folder, "Informe.md"), result.output)

        val markdown = result.output.readText()
        assertTrue("Primer parrafo" in markdown, markdown)
        assertTrue("**Texto en negrita**" in markdown, markdown)
    }

    @Test
    fun `respeta la extension en mayusculas al nombrar la salida`() {
        val docx = DocxFixtures.simpleDocument(folder, "Gamma.DOCX", "contenido")

        val result = service.convert(docx)

        assertTrue(result.success)
        assertEquals("Gamma.md", result.output.name)
    }

    @Test
    fun `extrae las imagenes y las enlaza con rutas relativas`() {
        val docx = DocxFixtures.documentWithImages(folder, "ConImagenes.docx")

        val result = service.convert(docx)

        assertTrue(result.success, "error: ${result.errorMessage}")
        assertEquals(2, result.imagesExtracted)
        assertFalse(result.hasImageErrors)
        assertTrue(File(folder, "ConImagenes_images/image1.png").isFile)
        assertTrue(File(folder, "ConImagenes_images/image2.png").isFile)

        // La barra del enlace es siempre "/" aunque el separador del sistema sea "\"
        assertTrue("(ConImagenes_images/image1.png)" in result.output.readText())
    }

    @Test
    fun `conserva el alt text real y descarta el aviso generado por la IA de Word`() {
        val docx = DocxFixtures.documentWithImages(folder, "ConImagenes.docx")

        val markdown = service.convert(docx).output.readText()

        assertTrue("![Diagrama de arquitectura]" in markdown, markdown)
        assertFalse("generado por ia" in markdown.lowercase(), markdown)
        assertTrue("![imagen 2]" in markdown, markdown)
    }

    @Test
    fun `si una imagen no se puede extraer lo reporta y la marca en el Markdown`() {
        val docx = DocxFixtures.documentWithImages(folder, "ConImagenes.docx")
        // Un archivo normal con el nombre de la carpeta de imágenes impide crearla
        File(folder, "ConImagenes_images").writeText("bloqueado")

        val result = service.convert(docx)

        assertTrue(result.success, "el documento debe convertirse aunque falten imágenes")
        assertEquals(0, result.imagesExtracted, "ninguna imagen debió extraerse")
        assertEquals(2, result.imageErrors.size)

        // La numeración es correlativa: sin doble incremento del contador
        assertTrue(result.imageErrors[0].startsWith("imagen 1"), result.imageErrors.toString())
        assertTrue(result.imageErrors[1].startsWith("imagen 2"), result.imageErrors.toString())
        // El mensaje identifica el tipo de fallo, no solo la ruta
        assertTrue("Exception" in result.imageErrors[0], result.imageErrors.toString())

        assertTrue("imagen 1 (no se pudo extraer)" in result.output.readText())
    }

    @Test
    fun `un documento ilegible falla sin dejar Markdown a medias`() {
        val docx = DocxFixtures.corruptDocument(folder, "Corrupto.docx")

        val result = service.convert(docx)

        assertFalse(result.success)
        assertNotNull(result.errorMessage)
        assertFalse(result.output.exists(), "no debe quedar un .md incompleto")
    }

    @Test
    fun `un documento ilegible no altera a los demas de la carpeta`() {
        DocxFixtures.simpleDocument(folder, "Alpha.docx", "alpha")
        DocxFixtures.corruptDocument(folder, "Corrupto.docx")
        DocxFixtures.simpleDocument(folder, "Beta.docx", "beta")

        val resultados = service.findDocxFiles(folder).map { service.convert(it) }

        assertEquals(2, resultados.count { it.success })
        assertEquals(1, resultados.count { !it.success })
        assertTrue(File(folder, "Alpha.md").isFile)
        assertTrue(File(folder, "Beta.md").isFile)
        assertFalse(File(folder, "Corrupto.md").exists())
    }

    @Test
    fun `baseNameOf quita la extension sin distinguir mayusculas`() {
        assertEquals("Informe", service.baseNameOf(File(folder, "Informe.docx")))
        assertEquals("Informe", service.baseNameOf(File(folder, "Informe.DOCX")))
        assertEquals("Informe.txt", service.baseNameOf(File(folder, "Informe.txt")))
    }
}
