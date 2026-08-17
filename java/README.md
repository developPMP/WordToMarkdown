# Word to Markdown

Aplicación de escritorio en **Java 21** con interfaz **Swing** que convierte
documentos Word (`.docx`) a **Markdown** (`.md`) **y al revés**.

## Descripción

El usuario elige el **sentido** de la conversión y, mediante un diálogo, un
archivo individual o una carpeta completa. La aplicación genera el archivo
convertido con el mismo nombre, en la misma carpeta del original.

### Flujo de conversión

**De Word a Markdown**

```
documento.docx  →  [Mammoth]  →  HTML  →  [flexmark]  →  documento.md
```

1. **Mammoth** lee el `.docx` y produce HTML semántico limpio.
2. **flexmark-html2md-converter** transforma ese HTML a Markdown.
3. **`MarkdownCleaner`** repasa el resultado y corrige lo que Word arrastra
   (tablas, índice, anclas y espaciado). Ver [Ajustes del Markdown](#ajustes-del-markdown).

> La librería Java de Mammoth no incluye conversión directa a Markdown (es una _missing feature_ documentada), por eso se realiza en dos pasos.

**De Markdown a Word**

```
documento.md  →  [flexmark]  →  árbol  →  [Apache POI]  →  documento.docx
```

1. **flexmark** analiza el Markdown y devuelve su árbol de elementos.
2. **`WordWriter`** lo recorre y escribe el documento elemento a elemento con
   **Apache POI**, apoyándose en **`WordStyles`** para los estilos y las listas.

> Aquí no se pasa por HTML a propósito: escribir el documento a mano cuesta más
> código, pero es lo que permite generar estilos de Word de verdad (`Heading 1`,
> listas numeradas, tablas, marcadores) en lugar de un documento con el formato
> imitado a base de negritas y tamaños. Ver [De Markdown a Word](#de-markdown-a-word).

## Tecnologías

| Librería | Versión | Rol |
|---|---|---|
| Java | 21 | Lenguaje / plataforma |
| Swing | JDK | Interfaz gráfica |
| [Mammoth](https://github.com/mwilliamson/java-mammoth) | 1.12.0 | Conversión `.docx` → HTML |
| [flexmark-html2md-converter](https://github.com/vsch/flexmark-java) | 0.64.8 | Conversión HTML → Markdown |
| [flexmark](https://github.com/vsch/flexmark-java) | 0.64.8 | Análisis del Markdown (sentido inverso) |
| [Apache POI](https://poi.apache.org/) (`poi-ooxml`) | 5.4.1 | Escritura del `.docx` (sentido inverso) |
| Maven | 3.x | Gestión de dependencias y build |

## Estructura del proyecto

```
WordToMarkdown/
├── pom.xml
└── src/
    ├── main/java/com/wordtomarkdown/
    │   ├── App.java                 Interfaz Swing (ventana, selección, registro)
    │   ├── Converter.java           Contrato común a los dos sentidos
    │   ├── ConversionDirection.java Qué cambia entre un sentido y el otro
    │   ├── ConversionService.java   Word → Markdown, sin dependencias de UI
    │   ├── MarkdownToWordService.java  Markdown → Word, sin dependencias de UI
    │   ├── WordWriter.java          Vuelca el árbol del Markdown en un .docx
    │   ├── WordStyles.java          Estilos y listas del documento generado
    │   ├── ConversionResult.java    Resultado de convertir un documento
    │   ├── ConversionReporter.java  Traduce el resultado a líneas de registro
    │   ├── MarkdownCleaner.java     Ajusta el Markdown generado (tablas, índice...)
    │   ├── WordNumbering.java       Reconstruye la numeración automática (1, 1.1, 1.1.1)
    │   ├── Slug.java                Identificador de un encabezado, igual en ambos sentidos
    │   └── SelectionPolicy.java     Decide qué hacer con una ruta arrastrada
    └── test/java/com/wordtomarkdown/
        ├── ConversionServiceTest.java
        ├── MarkdownToWordServiceTest.java
        ├── MarkdownCleanerTest.java
        ├── WordNumberingTest.java
        ├── FindDocxFilesTest.java
        ├── ConversionReporterTest.java
        ├── SelectionPolicyTest.java
        ├── AppUiTest.java           Ventana (se omite sin entorno gráfico)
        └── DocxFixtures.java        Genera documentos .docx de prueba
```

La lógica de conversión vive en los servicios, separada de la ventana: no depende
de Swing, devuelve el resultado como datos (`ConversionResult`) y es `App` quien
decide cómo mostrarlo. Así puede probarse sin abrir la interfaz. Los dos sentidos
implementan la misma interfaz `Converter`, de modo que la ventana trabaja siempre
contra ella y cambiar de sentido es cambiar de implementación, sin condicionales
repartidos por la interfaz gráfica.

## Requisitos previos

- Java 21 o superior
- Maven 3.6 o superior

## Compilar

Antes de compilar, asegúrate de que Maven use Java 21.

### macOS (zsh / bash)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package
```

### Git Bash (Windows)

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package
```

### PowerShell (Windows)

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\\bin;$env:Path"
mvn clean package
```

Si tu instalación de Java 21 está en otra ruta, sustituye el valor de `JAVA_HOME` por el correspondiente.

Si Maven arranca con un JDK anterior, el build se detiene en la fase `validate`
(mediante `maven-enforcer-plugin`) con un mensaje explícito indicando que hace falta
JDK 21, en lugar del críptico `invalid target release: 21` del compilador.

El JAR ejecutable se genera en:

```
target/word-to-markdown.jar
```

## Ejecutar

```bash
java -jar target/word-to-markdown.jar
```

## Tests

```bash
mvn test
```

Las pruebas (JUnit 5) cubren la selección de documentos en carpeta, el filtrado de
temporales de Word, la conversión a Markdown, los ajustes sobre el Markdown
generado (tablas, índice, anclas y espaciado), la extracción de imágenes, el
comportamiento ante documentos ilegibles, el criterio al arrastrar y soltar, el
formato del registro y el estado de la ventana.

Del sentido inverso se comprueba, releyendo con POI el `.docx` generado, que los
encabezados llevan su estilo de Word, que las listas usan la numeración nativa y
respetan el anidamiento, que las tablas son tablas, que las imágenes quedan
incrustadas, que los enlaces internos apuntan a marcadores y que no se sobrescribe
un documento existente; también la ida y vuelta completa (`.docx` → `.md` →
`.docx`). Los `.docx` de prueba se generan al vuelo en carpetas temporales
(`DocxFixtures`), por lo que no se versiona ningún binario.

Las pruebas de `AppUiTest` necesitan entorno gráfico para crear la ventana. En un
entorno headless (por ejemplo integración continua) **se omiten** en lugar de
fallar, y el resto de la batería se ejecuta con normalidad:

```bash
mvn test -DargLine="-Djava.awt.headless=true"
```

## Uso

1. Ejecutar la aplicación con el comando anterior.
2. Elegir el **sentido** de la conversión:
   - **Word → Markdown** (valor por defecto).
   - **Markdown → Word**.
3. Elegir el **tipo de selección**:
   - **Archivo** (valor por defecto): convierte un único documento.
   - **Carpeta**: convierte por lote todos los archivos convertibles de la carpeta
     elegida (`.docx`, o `.md` y `.markdown` en sentido inverso).
4. Hacer clic en **Seleccionar...** y elegir el archivo o la carpeta.
5. Hacer clic en **Convertir a Markdown** / **Convertir a Word**.
6. Cada archivo se genera automáticamente junto a su original.

Al cambiar de sentido se vacía la selección: un `.docx` no sirve como entrada para
convertir *a* Word, así que la ruta anterior deja de ser válida.

### Arrastrar y soltar

Como alternativa al diálogo, se puede arrastrar un archivo (o una carpeta) sobre el
área de **Registro**: equivale a seleccionarlo, ajustando además el tipo de selección
al contenido soltado —**Archivo** si es un documento, **Carpeta** si es un directorio—
y rellenando la ruta. Lo que no valga para el sentido activo se ignora sin alterar la
selección previa —y el registro dice qué se esperaba (`Ignorado (no es un .md)`)—, y
no se admiten arrastres mientras hay una conversión en curso. El sentido no se cambia
solo: lo elige quien usa el programa.

### Copiar y limpiar el registro

El texto del registro (`Convirtiendo: ...`, avisos, rutas de salida) se puede
seleccionar y copiar de tres formas:

- **Botón "Copiar registro"**, junto al de convertir: copia el registro completo.
- **Botón derecho** sobre el registro: *Copiar* (lo seleccionado, o todo si no hay
  selección) y *Seleccionar todo*.
- **Teclado**: seleccionar con el ratón y `Cmd+C` / `Ctrl+C`.

El botón **"Limpiar"** deja la ventana como recién abierta: vacía el registro y
también la ruta seleccionada, con lo que la conversión vuelve a quedar
deshabilitada.

El registro se limpia además por su cuenta al **elegir o arrastrar** otro archivo
o carpeta y al **empezar** una conversión —ahí la ruta no se toca, porque acaba de
elegirse—, de forma que lo que se ve siempre corresponde a lo último que se ha
hecho. Lo que se ignora al arrastrar (algo que no es un `.docx`) no borra nada:
solo añade el aviso.

### Modo carpeta

- Se procesan los archivos **directamente contenidos** en la carpeta; las subcarpetas no se recorren.
- Se ignoran los archivos temporales que Word crea al tener un documento abierto (`~$nombre.docx`), que no son documentos válidos, y los ocultos.
- Si un documento falla, el lote continúa con los siguientes y al final se muestra un resumen con el total de conversiones correctas y con error.

### Archivos que no son .docx de verdad

Un `.docx` es un paquete ZIP. Si el archivo tiene esa extensión pero por dentro es
otra cosa —una página web guardada desde el navegador, un PDF, un `.doc` anterior
a 2007, un RTF— la conversión falla y el registro dice **qué es en realidad** en
lugar del error técnico de la librería:

```
ERROR: No es un documento Word (.docx) válido: es una página web (HTML) guardada
con la extensión .docx. Ábrelo en Word y usa Guardar como > Documento de Word (.docx).
```

Pasa a menudo al guardar una conversación de ChatGPT o Gemini como `.docx` desde
el navegador: lo que se guarda es la página, no un documento.

En el sentido inverso se comprueba lo mismo antes de leer nada: un binario con la
extensión cambiada a `.md` se podría interpretar como texto en cualquier
codificación y saldría un documento lleno de basura, así que en su lugar se avisa
(`No es un archivo de texto Markdown: es un archivo comprimido, probablemente un
.docx con la extensión cambiada`).

## Ajustes del Markdown

El HTML que sale de un `.docx` no se traduce a un Markdown legible sin más: hay
detalles del formato de Word que, tal cual, se ven mal (o directamente no se ven)
en un visor de Markdown. `MarkdownCleaner` corrige estos:

- **Tablas.** Word solo marca fila de encabezado si el documento activó *repetir
  fila de título*, y sin ella flexmark escribe el separador (`|---|---|`) en la
  primera línea. Un visor de Markdown no reconoce esa tabla y **el cuadro no se
  ve**. Se pasa el separador detrás de la primera fila, que queda como
  encabezado. Las tablas que ya lo traían bien no se tocan.
- **Índice.** Los estilos `toc 1…6` se convierten en una lista anidada, se quita
  el número de página del documento original y la sangría de cada entrada sigue
  su numeración (`3` → `3.1` → `3.3.1.1`), sin saltarse niveles.
- **Anclas de Word.** Los marcadores internos (`{#_Toc12345}`) se eliminan del
  texto, y los enlaces que apuntaban a ellos se reapuntan al encabezado
  correspondiente para que el índice siga siendo navegable. Si un enlace se queda
  sin destino, se conserva el texto y se descarta el enlace.
- **Numeración automática.** Cuando los apartados se numeran con una lista
  multinivel enlazada a los estilos de título (`1`, `1.1`, `3.3.1.1`), ese número
  **no está en el texto**: Word solo guarda a qué lista y nivel pertenece cada
  párrafo y lo pinta al mostrarlo, así que Mammoth no lo emite y los encabezados
  llegarían sin numerar. `WordNumbering` lee `numbering.xml` del `.docx`, calcula
  el número de cada encabezado y se lo devuelve al Markdown; el índice hereda esa
  numeración si no la traía ya escrita. Los encabezados sin lista asociada se
  dejan tal cual.
- **Títulos y encabezados.** El estilo *Título* del documento pasa a ser un
  encabezado de nivel 1, y todos los encabezados se escriben con almohadilla
  (`# Título`) en lugar de subrayados. También se reconocen los nombres de estilo
  en español (*Título*, *Título 1*, *Subtítulo*, *TDC 1*), por si el documento no
  guarda los canónicos en inglés.
- **Espaciado y puntuación.** Se eliminan los espacios sobrantes (incluidos los
  espacios duros de Word) y los que preceden a un signo de puntuación, se deja
  como mucho una línea en blanco seguida y el archivo termina con un solo salto.
  Los saltos de línea forzados y el contenido de los bloques de código se
  respetan.

## De Markdown a Word

El sentido inverso no se limita a volcar el texto: cada elemento del Markdown se
traduce a su equivalente **nativo** de Word, de modo que el documento se pueda
seguir editando con normalidad.

- **Encabezados.** Llevan el estilo `Heading 1…6` (el que Word muestra como
  *Título 1…6* en español) y su **nivel de esquema**. Es lo que hace que aparezcan
  en el panel de navegación y que se pueda insertar un índice automático con
  *Referencias > Tabla de contenido*.
- **Estilos del documento.** Un `.docx` creado desde cero no trae ninguno, así que
  se generan: `Normal`, los seis encabezados, `Quote` para las citas y estilos
  propios para el código (bloque y en línea). Al estar aplicados **por nombre**,
  cambiar el aspecto de todo el documento es cambiar el estilo, no repasarlo
  párrafo a párrafo.
- **Listas.** Usan la numeración de Word (`numbering.xml`), con viñetas y niveles
  de anidamiento reales. Cada lista ordenada estrena numeración: compartiéndola,
  la segunda lista del documento seguiría contando donde lo dejó la primera.
- **Tablas.** Se convierten en tablas de Word, con la primera fila sombreada como
  encabezado y la alineación de cada columna.
- **Enlaces.** Los externos quedan como hipervínculos. Los internos (los del
  índice, `[Apartado](#apartado)`) se convierten en **marcadores** y referencias a
  ellos, así que el índice sigue siendo navegable dentro de Word. Si un enlace
  apunta a un encabezado que no existe se conserva el texto y se descarta el
  enlace, igual que en el otro sentido.
- **Imágenes.** Se **incrustan** en el documento (no se enlazan): el `.docx`
  resultante es un único archivo y no depende de que la carpeta `{nombre}_images/`
  siga estando ahí. Se ajustan al ancho de la página conservando su proporción y
  el texto alternativo se guarda como descripción de la imagen.
- **Página.** A4 con márgenes de 2,5 cm.

### El documento original no se sobrescribe

Lo normal es que el `.md` venga de un `.docx` que está **en esa misma carpeta y
con el mismo nombre** —así lo deja la conversión de ida—, de modo que escribir sin
mirar destruiría el documento original. Cuando el nombre ya está ocupado, el nuevo
se numera (`Informe (2).docx`); el registro indica siempre cuál se ha escrito.

### Qué no puede recuperarse

El Markdown no guarda parte de lo que sí guarda Word, así que la ida y vuelta no
devuelve el documento de partida:

- Encabezados y pies de página, saltos de sección, portadas y numeración de páginas.
- La **numeración automática** de los apartados: al ir a Markdown se escribe dentro
  del texto del encabezado (`## 3.1 Alcance`), y al volver se queda ahí, como texto,
  en lugar de reconstruirse como lista multinivel enlazada a los estilos de título.
- El índice vuelve como la lista de enlaces que es en el Markdown, no como un campo
  `TOC` que Word actualice solo. Con los encabezados bien estilados, insertar uno
  automático es cuestión de un clic.
- Tipografías, colores y tamaños del documento original: el generado sale con el
  formato base descrito arriba.
- El HTML incrustado en el Markdown se descarta, avisando en el registro.

## Notas

- La conversión se ejecuta en un hilo secundario (`SwingWorker`) para no bloquear la interfaz; durante el proceso los controles quedan deshabilitados, salvo el de copiar el registro (limpiar sí se bloquea: borraría lo que se está escribiendo).
- El área de registro usa un `TransferHandler` propio para admitir arrastrar y soltar. Como ese mismo objeto es el que copia al portapapeles, implementa las dos cosas: sustituirlo sin más dejaría el registro sin copiar.
- Cualquier advertencia generada por Mammoth durante la conversión se muestra en el panel de registro de la ventana.
- Las imágenes del documento se extraen como archivos independientes en una carpeta `{nombre}_images/` junto al `.md`. Las referencias quedan como rutas relativas en el Markdown.
- Si una imagen concreta no se puede extraer, la conversión del documento continúa, pero el fallo **no pasa inadvertido**: se detalla en el registro (con número de imagen y causa), se resume en la línea de resultado del documento y en el Markdown queda marcada como `imagen N (no se pudo extraer)`.
- El texto alternativo (alt text) generado automáticamente por la IA de Microsoft Word (e.g. *"el contenido generado por IA puede ser incorrecto"*) es ignorado; en su lugar se usa un texto genérico (`imagen N`).
- En el sentido inverso, una imagen que no se encuentre (o que apunte a una dirección de internet, que no se descarga) tampoco aborta el documento: queda anotada en el registro y en su sitio aparece `[imagen no insertada: ...]`.
- Se espera que el `.md` esté en UTF-8, que es lo que escribe la conversión de ida. Si no lo está —un archivo editado con el Bloc de notas de Windows— se lee con la codificación del sistema y se avisa, en lugar de fallar.
- Apache POI registra sus mensajes con log4j. Como no se incluye ninguna implementación de registro, la aplicación selecciona la mínima que trae la propia API para que no aparezca un error por consola al arrancar.
