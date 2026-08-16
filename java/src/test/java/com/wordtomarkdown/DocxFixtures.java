package com.wordtomarkdown;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Genera documentos .docx mínimos pero válidos para las pruebas, de modo que no
 * haga falta versionar archivos binarios en el repositorio.
 */
final class DocxFixtures {

    /** PNG de 1x1 píxel. */
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static final String CONTENT_TYPES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
        <Default Extension="xml" ContentType="application/xml"/>
        <Default Extension="png" ContentType="image/png"/>
        <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
        </Types>""";

    private static final String ROOT_RELS = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>""";

    private DocxFixtures() {
    }

    /** Documento con un párrafo de texto y otro en negrita. */
    static Path simpleDocument(Path folder, String fileName, String text) throws IOException {
        String body = """
            <w:p><w:r><w:t>%s</w:t></w:r></w:p>
            <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Texto en negrita</w:t></w:r></w:p>
            """.formatted(text);
        return write(folder.resolve(fileName), document(body), null);
    }

    /**
     * Documento con dos imágenes: la primera con alt text real, la segunda con el
     * aviso que añade automáticamente la IA de Word.
     */
    static Path documentWithImages(Path folder, String fileName) throws IOException {
        String body = """
            <w:p><w:r><w:t>Documento con imagenes</w:t></w:r></w:p>
            %s
            %s
            """.formatted(
            drawing(5, "Diagrama de arquitectura"),
            drawing(6, "el contenido generado por IA puede ser incorrecto"));

        String documentRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
            <Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image2.png"/>
            </Relationships>""";

        return write(folder.resolve(fileName), document(body), documentRels);
    }

    /** Archivo con extensión .docx pero contenido que no es un paquete OOXML. */
    static Path corruptDocument(Path folder, String fileName) throws IOException {
        Path path = folder.resolve(fileName);
        Files.writeString(path, "esto no es un zip", StandardCharsets.UTF_8);
        return path;
    }

    private static String document(String body) {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
             xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
            <w:body>
            %s
            </w:body></w:document>""".formatted(body);
    }

    private static String drawing(int relationshipId, String description) {
        return """
            <w:p><w:r><w:drawing>
            <wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">
            <wp:extent cx="914400" cy="914400"/><wp:docPr id="%1$d" name="Picture %1$d" descr="%2$s"/>
            <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
            <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
            <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
            <pic:nvPicPr><pic:cNvPr id="%1$d" name="Picture %1$d" descr="%2$s"/><pic:cNvPicPr/></pic:nvPicPr>
            <pic:blipFill><a:blip r:embed="rId%1$d"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
            <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="914400" cy="914400"/></a:xfrm>
            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
            </pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"""
            .formatted(relationshipId, description);
    }

    private static Path write(Path target, String documentXml, String documentRels) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            entry(zip, "[Content_Types].xml", CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
            entry(zip, "_rels/.rels", ROOT_RELS.getBytes(StandardCharsets.UTF_8));
            entry(zip, "word/document.xml", documentXml.getBytes(StandardCharsets.UTF_8));
            if (documentRels != null) {
                entry(zip, "word/_rels/document.xml.rels", documentRels.getBytes(StandardCharsets.UTF_8));
                entry(zip, "word/media/image1.png", PNG);
                entry(zip, "word/media/image2.png", PNG);
            }
        }
        return target;
    }

    private static void entry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        OutputStream out = zip;
        out.write(content);
        zip.closeEntry();
    }
}
