package com.wordtomarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.datatransfer.DataFlavor
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val service = ConversionService()
private val selectionPolicy = SelectionPolicy(service)
private val reporter = ConversionReporter()

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Word a Markdown - Conversor (Kotlin)",
        state = rememberWindowState(width = 760.dp, height = 540.dp),
    ) {
        MaterialTheme {
            ConverterScreen()
        }
    }
}

@Composable
fun ConverterScreen() {
    var folderMode by remember { mutableStateOf(false) }
    var selectedPath by remember { mutableStateOf("") }
    var converting by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    /** Aplica una ruta soltada según decida [SelectionPolicy]. */
    fun applyDropped(dropped: File?) {
        val decision = selectionPolicy.decideDrop(dropped)
        log += decision.messages
        if (!decision.accepted) return
        folderMode = decision.folderMode
        selectedPath = decision.path!!.absolutePath
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectionModeRow(
            folderMode = folderMode,
            enabled = !converting,
            onModeChange = { nuevoModo ->
                // Al cambiar de modo la ruta anterior deja de ser válida
                folderMode = nuevoModo
                selectedPath = ""
            },
        )

        PathRow(
            folderMode = folderMode,
            selectedPath = selectedPath,
            enabled = !converting,
            onSelect = {
                chooseFile(folderMode)?.let { elegido ->
                    selectedPath = elegido.absolutePath
                    if (folderMode) {
                        log += "Carpeta seleccionada: ${elegido.absolutePath}"
                        log += "Documentos .docx encontrados: ${service.findDocxFiles(elegido).size}"
                    } else {
                        log += "Archivo seleccionado: ${elegido.name}"
                    }
                }
            },
        )

        LogPanel(
            lines = log,
            acceptsDrop = !converting,
            onDropped = ::applyDropped,
            modifier = Modifier.weight(1f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                enabled = selectedPath.isNotBlank() && !converting,
                onClick = {
                    val pendientes = pendingDocuments(File(selectedPath), folderMode)
                    if (pendientes.isEmpty()) {
                        log.clear()
                        log += if (folderMode) {
                            "La carpeta no contiene ningún archivo .docx."
                        } else {
                            "El archivo seleccionado no existe o no es válido."
                        }
                        return@Button
                    }

                    converting = true
                    log.clear()
                    scope.launch {
                        // La conversión ocurre fuera del hilo de interfaz
                        val lineas = withContext(Dispatchers.IO) { convertAll(pendientes) }
                        log += lineas
                        converting = false
                    }
                },
            ) {
                Text(if (converting) "Convirtiendo..." else "Convertir a Markdown")
            }
        }
    }
}

@Composable
private fun SelectionModeRow(
    folderMode: Boolean,
    enabled: Boolean,
    onModeChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Tipo de selección:", modifier = Modifier.padding(end = 12.dp))
        ModeOption("Archivo", selected = !folderMode, enabled = enabled) { onModeChange(false) }
        ModeOption("Carpeta", selected = folderMode, enabled = enabled) { onModeChange(true) }
    }
}

@Composable
private fun ModeOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .selectable(selected = selected, enabled = enabled, onClick = onSelect)
            .padding(end = 16.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Text(label)
    }
}

@Composable
private fun PathRow(
    folderMode: Boolean,
    selectedPath: String,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (folderMode) "Carpeta:" else "Archivo .docx:",
            // El ancho es fijo para que el campo no se desplace al cambiar de modo,
            // y sin ajuste de línea para que la etiqueta nunca se parta en dos.
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(150.dp),
        )
        OutlinedTextField(
            value = selectedPath,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onSelect, enabled = enabled) {
            Text("Seleccionar...")
        }
    }
}

@Composable
private fun LogPanel(
    lines: List<String>,
    acceptsDrop: Boolean,
    onDropped: (File?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    // Mantener el registro desplazado al final según llegan líneas
    LaunchedEffect(lines.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    val dropTarget = remember(acceptsDrop) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val soltados = event.droppedFiles()
                if (soltados.isEmpty()) return false
                onDropped(soltados.first())
                return true
            }
        }
    }

    Column(modifier = modifier) {
        Text("Registro (o arrastre aquí un .docx o una carpeta)")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { acceptsDrop },
                    target = dropTarget,
                )
                .verticalScroll(scrollState)
                .padding(8.dp),
        ) {
            Column {
                lines.forEach { linea ->
                    Text(
                        text = linea,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** Archivos soltados sobre la ventana, si el arrastre traía alguno. */
@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.droppedFiles(): List<File> {
    val transferable = awtTransferable
    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
    return (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
        .orEmpty()
        .filterIsInstance<File>()
}

/** Documentos a convertir según el modo elegido. */
internal fun pendingDocuments(input: File, folderMode: Boolean): List<File> = when {
    folderMode -> service.findDocxFiles(input)
    input.isFile -> listOf(input)
    else -> emptyList()
}

/** Convierte la lista completa y devuelve las líneas de registro. */
internal fun convertAll(pending: List<File>): List<String> = buildList {
    if (pending.size > 1) {
        add("Documentos a convertir: ${pending.size}")
        add("")
    }

    var converted = 0
    var failed = 0
    pending.forEach { docx ->
        val result = service.convert(docx)
        addAll(reporter.describe(result))
        if (result.success) converted++ else failed++
        add("")
    }

    if (pending.size > 1) {
        addAll(reporter.summarize(converted, failed))
    }
}

/** Diálogo de selección, equivalente al de la versión Swing. */
private fun chooseFile(folderMode: Boolean): File? {
    val chooser = JFileChooser()
    if (folderMode) {
        chooser.dialogTitle = "Seleccionar carpeta con documentos Word"
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    } else {
        chooser.dialogTitle = "Seleccionar documento Word"
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY
        chooser.fileFilter = FileNameExtensionFilter("Documentos Word (*.docx)", "docx")
        chooser.isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
