package com.wordtomarkdown

import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Genera documentos .docx mínimos pero válidos para las pruebas, de modo que no
 * haga falta versionar archivos binarios en el repositorio.
 */
object DocxFixtures {

    /** PNG de 1x1 píxel. */
    private val PNG: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    )

    private val CONTENT_TYPES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
        <Default Extension="xml" ContentType="application/xml"/>
        <Default Extension="png" ContentType="image/png"/>
        <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
        </Types>
    """.trimIndent()

    private val ROOT_RELS = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
    """.trimIndent()

    /** Documento con un párrafo de texto y otro en negrita. */
    fun simpleDocument(folder: File, fileName: String, text: String): File {
        val body = """
            |<w:p><w:r><w:t>$text</w:t></w:r></w:p>
            |<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Texto en negrita</w:t></w:r></w:p>
        """.trimMargin()
        return write(File(folder, fileName), document(body), null)
    }

    /**
     * Documento con dos imágenes: la primera con alt text real, la segunda con el
     * aviso que añade automáticamente la IA de Word.
     */
    fun documentWithImages(folder: File, fileName: String): File {
        val body = """
            |<w:p><w:r><w:t>Documento con imagenes</w:t></w:r></w:p>
            |${drawing(5, "Diagrama de arquitectura")}
            |${drawing(6, "el contenido generado por IA puede ser incorrecto")}
        """.trimMargin()

        val documentRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
            <Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image2.png"/>
            </Relationships>
        """.trimIndent()

        return write(File(folder, fileName), document(body), documentRels)
    }

    /** Archivo con extensión .docx pero contenido que no es un paquete OOXML. */
    fun corruptDocument(folder: File, fileName: String): File =
        File(folder, fileName).apply { writeText("esto no es un zip") }

    private fun document(body: String) = """
        |<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        |<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        | xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
        |<w:body>
        |$body
        |</w:body></w:document>
    """.trimMargin()

    private fun drawing(relationshipId: Int, description: String) = """
        |<w:p><w:r><w:drawing>
        |<wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">
        |<wp:extent cx="914400" cy="914400"/><wp:docPr id="$relationshipId" name="Picture $relationshipId" descr="$description"/>
        |<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
        |<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
        |<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
        |<pic:nvPicPr><pic:cNvPr id="$relationshipId" name="Picture $relationshipId" descr="$description"/><pic:cNvPicPr/></pic:nvPicPr>
        |<pic:blipFill><a:blip r:embed="rId$relationshipId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
        |<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="914400" cy="914400"/></a:xfrm>
        |<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
        |</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
    """.trimMargin()

    private fun write(target: File, documentXml: String, documentRels: String?): File {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.entry("[Content_Types].xml", CONTENT_TYPES.toByteArray())
            zip.entry("_rels/.rels", ROOT_RELS.toByteArray())
            zip.entry("word/document.xml", documentXml.toByteArray())
            if (documentRels != null) {
                zip.entry("word/_rels/document.xml.rels", documentRels.toByteArray())
                zip.entry("word/media/image1.png", PNG)
                zip.entry("word/media/image2.png", PNG)
            }
        }
        return target
    }

    private fun ZipOutputStream.entry(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }
}
