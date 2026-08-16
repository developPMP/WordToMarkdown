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
        <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
        <Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
        </Types>""";

    /**
     * Lista multinivel "1 / 1.1 / 1.1.1", como la que Word enlaza a los estilos
     * de título. El número no aparece en el texto: se calcula al mostrarlo.
     */
    private static final String NUMBERING = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
        <w:abstractNum w:abstractNumId="7">
        <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1"/></w:lvl>
        <w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1.%2"/></w:lvl>
        <w:lvl w:ilvl="2"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1.%2.%3"/></w:lvl>
        </w:abstractNum>
        <w:num w:numId="3"><w:abstractNumId w:val="7"/></w:num>
        </w:numbering>""";

    /**
     * Estilos con el nombre canónico que guarda el .docx (en inglés aunque Word
     * esté en español) y el identificador que genera la versión española.
     */
    private static final String STYLES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
        <w:style w:type="paragraph" w:styleId="Ttulo"><w:name w:val="Title"/></w:style>
        <w:style w:type="paragraph" w:styleId="Ttulo1"><w:name w:val="heading 1"/></w:style>
        <w:style w:type="paragraph" w:styleId="Ttulo2"><w:name w:val="heading 2"/></w:style>
        <w:style w:type="paragraph" w:styleId="TDC1"><w:name w:val="toc 1"/></w:style>
        <w:style w:type="paragraph" w:styleId="TDC2"><w:name w:val="toc 2"/></w:style>
        <w:style w:type="paragraph" w:styleId="TDC3"><w:name w:val="toc 3"/></w:style>
        </w:styles>""";

    /**
     * Los mismos estilos, pero con los títulos enlazados a la lista multinivel,
     * que es como Word numera los apartados de un documento.
     */
    private static final String NUMBERED_STYLES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
        <w:style w:type="paragraph" w:styleId="Ttulo"><w:name w:val="Title"/></w:style>
        <w:style w:type="paragraph" w:styleId="TDC1"><w:name w:val="toc 1"/></w:style>
        <w:style w:type="paragraph" w:styleId="TDC2"><w:name w:val="toc 2"/></w:style>
        <w:style w:type="paragraph" w:styleId="TDC3"><w:name w:val="toc 3"/></w:style>
        <w:style w:type="paragraph" w:styleId="Ttulo1"><w:name w:val="heading 1"/>
          <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="3"/></w:numPr></w:pPr></w:style>
        <w:style w:type="paragraph" w:styleId="Ttulo2"><w:name w:val="heading 2"/>
          <w:pPr><w:numPr><w:ilvl w:val="1"/><w:numId w:val="3"/></w:numPr></w:pPr></w:style>
        <w:style w:type="paragraph" w:styleId="Ttulo3"><w:name w:val="heading 3"/>
          <w:pPr><w:numPr><w:ilvl w:val="2"/><w:numId w:val="3"/></w:numPr></w:pPr></w:style>
        <w:style w:type="paragraph" w:styleId="Ttulo4"><w:name w:val="heading 4"/></w:style>
        </w:styles>""";

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

    /**
     * Documento como los que genera Word en la práctica: título propio, índice
     * con enlaces a marcadores internos, encabezados numerados y una tabla que
     * no marca fila de encabezado (el caso que rompe la tabla en Markdown).
     */
    static Path documentWithIndexAndTable(Path folder, String fileName) throws IOException {
        String body = """
            <w:p><w:pPr><w:pStyle w:val="Ttulo"/></w:pPr><w:r><w:t>Manual de Usuario</w:t></w:r></w:p>
            %s
            %s
            %s
            <w:p><w:pPr><w:pStyle w:val="Ttulo1"/></w:pPr>
              <w:bookmarkStart w:id="1" w:name="_Toc10"/><w:r><w:t>3 Requisitos</w:t></w:r><w:bookmarkEnd w:id="1"/></w:p>
            <w:p><w:pPr><w:pStyle w:val="Ttulo2"/></w:pPr>
              <w:bookmarkStart w:id="2" w:name="_Toc11"/><w:r><w:t>3.3 Interfaz</w:t></w:r><w:bookmarkEnd w:id="2"/></w:p>
            <w:tbl>
            <w:tr><w:tc><w:p><w:r><w:t>Campo</w:t></w:r></w:p></w:tc>
            <w:tc><w:p><w:r><w:t>Descripcion</w:t></w:r></w:p></w:tc></w:tr>
            <w:tr><w:tc><w:p><w:r><w:t>Nombre</w:t></w:r></w:p></w:tc>
            <w:tc><w:p><w:r><w:t>Texto libre</w:t></w:r></w:p></w:tc></w:tr>
            </w:tbl>
            """.formatted(
            indexEntry(1, "_Toc10", "3 Requisitos", 8),
            indexEntry(2, "_Toc11", "3.3 Interfaz", 9),
            indexEntry(3, "_Toc12", "3.3.1.1 Pantalla de acceso", 10));

        return write(folder.resolve(fileName), document(body), null);
    }

    /** Entrada de índice: hipervínculo al marcador y número de página tras un tabulador. */
    private static String indexEntry(int level, String bookmark, String text, int page) {
        return """
            <w:p><w:pPr><w:pStyle w:val="TDC%d"/></w:pPr>
            <w:hyperlink w:anchor="%s"><w:r><w:t>%s</w:t></w:r></w:hyperlink>
            <w:r><w:tab/><w:t>%d</w:t></w:r></w:p>""".formatted(level, bookmark, text, page);
    }

    /**
     * Documento con numeración automática de encabezados: la lista multinivel
     * enlazada a los estilos de título, como la que aplica Word. El número no
     * está en el texto de los párrafos, hay que calcularlo.
     */
    static Path documentWithAutomaticNumbering(Path folder, String fileName) throws IOException {
        String body = """
            <w:p><w:pPr><w:pStyle w:val="Ttulo"/></w:pPr><w:r><w:t>Manual numerado</w:t></w:r></w:p>
            %s
            %s
            %s
            <w:p><w:pPr><w:pStyle w:val="Ttulo1"/></w:pPr>
              <w:bookmarkStart w:id="1" w:name="_Toc20"/><w:r><w:t>Introduccion</w:t></w:r><w:bookmarkEnd w:id="1"/></w:p>
            <w:p><w:pPr><w:pStyle w:val="Ttulo1"/></w:pPr>
              <w:bookmarkStart w:id="2" w:name="_Toc21"/><w:r><w:t>Analisis</w:t></w:r><w:bookmarkEnd w:id="2"/></w:p>
            <w:p><w:pPr><w:pStyle w:val="Ttulo2"/></w:pPr>
              <w:bookmarkStart w:id="3" w:name="_Toc22"/><w:r><w:t>Alcance</w:t></w:r><w:bookmarkEnd w:id="3"/></w:p>
            <w:p><w:pPr><w:pStyle w:val="Ttulo3"/></w:pPr>
              <w:bookmarkStart w:id="4" w:name="_Toc23"/><w:r><w:t>Riesgos</w:t></w:r><w:bookmarkEnd w:id="4"/></w:p>
            <w:p><w:pPr><w:pStyle w:val="Ttulo4"/></w:pPr><w:r><w:t>Anexo sin numerar</w:t></w:r></w:p>
            """.formatted(
            indexEntry(1, "_Toc20", "Introduccion", 2),
            indexEntry(1, "_Toc21", "Analisis", 3),
            indexEntry(2, "_Toc22", "Alcance", 4));

        return write(folder.resolve(fileName), document(body), null, NUMBERED_STYLES);
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
        return write(target, documentXml, documentRels, STYLES);
    }

    private static Path write(Path target, String documentXml, String documentRels, String stylesXml)
            throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            entry(zip, "[Content_Types].xml", CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
            entry(zip, "_rels/.rels", ROOT_RELS.getBytes(StandardCharsets.UTF_8));
            entry(zip, "word/numbering.xml", NUMBERING.getBytes(StandardCharsets.UTF_8));
            entry(zip, "word/styles.xml", stylesXml.getBytes(StandardCharsets.UTF_8));
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
