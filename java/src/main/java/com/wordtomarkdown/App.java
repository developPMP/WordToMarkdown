package com.wordtomarkdown;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class App extends JFrame {

    private JTextField txtFilePath;
    private JButton btnSelect;
    private JButton btnConvert;
    private JButton btnCopyLog;
    private JButton btnClearLog;
    private JTextArea txtLog;
    private JLabel lblFile;
    private JRadioButton rbFile;
    private JRadioButton rbFolder;
    private JRadioButton rbToMarkdown;
    private JRadioButton rbToWord;
    private boolean converting;

    private final ConversionService wordToMarkdown = new ConversionService();
    private final MarkdownToWordService markdownToWord = new MarkdownToWordService();
    private final SelectionPolicy selectionPolicy = new SelectionPolicy();
    private final ConversionReporter reporter = new ConversionReporter();

    public App() {
        super("Word y Markdown - Conversor");
        initUI();
    }

    /** Conversor correspondiente al sentido elegido en la ventana. */
    private Converter converter() {
        return rbToWord.isSelected() ? markdownToWord : wordToMarkdown;
    }

    private void initUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(650, 420);
        setLocationRelativeTo(null);
        setResizable(true);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Panel superior: modo de selección + ruta seleccionada
        JPanel topPanel = new JPanel(new BorderLayout(0, 8));

        // Fila 1: sentido de la conversión
        JPanel directionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        directionPanel.add(new JLabel("Convertir:"));

        rbToMarkdown = new JRadioButton("Word → Markdown", true);
        rbToWord = new JRadioButton("Markdown → Word");
        ButtonGroup directionGroup = new ButtonGroup();
        directionGroup.add(rbToMarkdown);
        directionGroup.add(rbToWord);
        rbToMarkdown.addActionListener(this::onDirectionChanged);
        rbToWord.addActionListener(this::onDirectionChanged);
        directionPanel.add(rbToMarkdown);
        directionPanel.add(rbToWord);

        // Fila 2: tipo de selección (archivo individual o carpeta completa)
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        modePanel.add(new JLabel("Tipo de selección:"));

        rbFile = new JRadioButton("Archivo", true);
        rbFolder = new JRadioButton("Carpeta");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(rbFile);
        modeGroup.add(rbFolder);
        rbFile.addActionListener(this::onModeChanged);
        rbFolder.addActionListener(this::onModeChanged);
        modePanel.add(rbFile);
        modePanel.add(rbFolder);

        JPanel optionsPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        optionsPanel.add(directionPanel);
        optionsPanel.add(modePanel);
        topPanel.add(optionsPanel, BorderLayout.NORTH);

        // Fila 3: ruta seleccionada
        JPanel pathPanel = new JPanel(new BorderLayout(8, 0));

        lblFile = new JLabel(ConversionDirection.WORD_TO_MARKDOWN.pathLabel());
        pathPanel.add(lblFile, BorderLayout.WEST);

        txtFilePath = new JTextField();
        txtFilePath.setEditable(false);
        pathPanel.add(txtFilePath, BorderLayout.CENTER);

        btnSelect = new JButton("Seleccionar...");
        btnSelect.addActionListener(this::onSelectFile);
        pathPanel.add(btnSelect, BorderLayout.EAST);

        topPanel.add(pathPanel, BorderLayout.CENTER);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Panel central: log de salida
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        txtLog.setBackground(new Color(245, 245, 245));
        txtLog.setLineWrap(true);
        txtLog.setWrapStyleWord(true);

        // El registro no se edita, pero sí se selecciona y se copia
        txtLog.setComponentPopupMenu(logPopupMenu());

        JScrollPane scrollPane = new JScrollPane(txtLog);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            "Registro (o arrastre aquí un archivo o una carpeta)"));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Arrastrar y soltar sobre el área de registro equivale a usar "Seleccionar..."
        TransferHandler dropHandler = new PathDropHandler();
        txtLog.setTransferHandler(dropHandler);
        scrollPane.setTransferHandler(dropHandler);

        // Panel inferior: acciones sobre el registro y conversión
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        btnClearLog = new JButton("Limpiar");
        btnClearLog.setEnabled(false);
        btnClearLog.setToolTipText("Vacía el registro y la selección");
        btnClearLog.addActionListener(e -> clearAll());
        bottomPanel.add(btnClearLog);

        btnCopyLog = new JButton("Copiar registro");
        btnCopyLog.setEnabled(false);
        btnCopyLog.setToolTipText("Copia todo el registro al portapapeles");
        btnCopyLog.addActionListener(e -> copyLog(getToolkit().getSystemClipboard()));
        bottomPanel.add(btnCopyLog);

        btnConvert = new JButton(ConversionDirection.WORD_TO_MARKDOWN.actionLabel());
        btnConvert.setEnabled(false);
        btnConvert.setPreferredSize(new Dimension(190, 32));
        btnConvert.addActionListener(this::onConvert);
        bottomPanel.add(btnConvert);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    /** Al cambiar de modo la ruta anterior deja de ser válida. */
    private void onModeChanged(ActionEvent e) {
        updateModeLabel();
        txtFilePath.setText("");
        btnConvert.setEnabled(false);
    }

    /**
     * Al cambiar de sentido lo seleccionado tampoco sirve —un .docx no se
     * convierte a Word— y cambian las etiquetas de la ventana.
     */
    private void onDirectionChanged(ActionEvent e) {
        btnConvert.setText(converter().direction().actionLabel());
        onModeChanged(e);
    }

    private void updateModeLabel() {
        lblFile.setText(rbFolder.isSelected() ? "Carpeta:" : converter().direction().pathLabel());
    }

    /**
     * Acepta archivos y carpetas soltados sobre el área de registro, con el mismo
     * efecto que elegirlos desde el diálogo "Seleccionar...".
     *
     * <p>Reemplazar el {@code TransferHandler} de un componente de texto también
     * sustituye el que copia al portapapeles, así que aquí se implementa la
     * copia además de la recepción: si no, el registro no se podría copiar.
     */
    private class PathDropHandler extends TransferHandler {

        @Override
        public int getSourceActions(JComponent component) {
            return COPY;
        }

        @Override
        protected Transferable createTransferable(JComponent component) {
            if (component instanceof JTextComponent text) {
                String selected = text.getSelectedText();
                if (selected != null && !selected.isEmpty()) {
                    return new StringSelection(selected);
                }
            }
            return null;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDrop()
                && !converting
                && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                @SuppressWarnings("unchecked")
                List<File> dropped = (List<File>) support.getTransferable()
                    .getTransferData(DataFlavor.javaFileListFlavor);
                if (dropped.isEmpty()) {
                    return false;
                }
                if (dropped.size() > 1) {
                    log("Se soltaron " + dropped.size() + " elementos; se usa solo el primero.");
                }
                return applyDroppedPath(dropped.get(0));
            } catch (Exception ex) {
                log("No se pudo leer lo arrastrado: " + ex.getMessage());
                return false;
            }
        }
    }

    /**
     * Aplica la ruta arrastrada ajustando el tipo de selección al contenido soltado.
     * El criterio lo decide {@link SelectionPolicy}; aquí solo se refleja en la ventana.
     *
     * <p>Visible para las pruebas.
     */
    boolean applyDroppedPath(File dropped) {
        SelectionPolicy.Decision decision = selectionPolicy.decideDrop(dropped, converter());
        if (!decision.accepted()) {
            // Lo rechazado no es una carga nueva: se avisa sin borrar lo anterior
            decision.messages().forEach(this::log);
            return false;
        }

        clearLog();
        decision.messages().forEach(this::log);

        (decision.folderMode() ? rbFolder : rbFile).setSelected(true);
        updateModeLabel();
        txtFilePath.setText(decision.path().getAbsolutePath());
        btnConvert.setEnabled(true);
        return true;
    }

    private void onSelectFile(ActionEvent e) {
        boolean folderMode = rbFolder.isSelected();
        Converter converter = converter();
        ConversionDirection direction = converter.direction();

        JFileChooser chooser = new JFileChooser();
        if (folderMode) {
            chooser.setDialogTitle("Seleccionar carpeta con " + direction.inputKindPlural().toLowerCase());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        } else {
            chooser.setDialogTitle("Seleccionar " + direction.inputKind());
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setFileFilter(new FileNameExtensionFilter(
                direction.fileFilterDescription(), direction.fileFilterExtensions()));
            chooser.setAcceptAllFileFilterUsed(false);
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            clearLog();
            txtFilePath.setText(selected.getAbsolutePath());
            btnConvert.setEnabled(true);
            if (folderMode) {
                log("Carpeta seleccionada: " + selected.getAbsolutePath());
                log(direction.inputKindPlural() + " encontrados: " + converter.findInputFiles(selected).size());
            } else {
                log("Archivo seleccionado: " + selected.getName());
            }
        }
    }

    private void onConvert(ActionEvent e) {
        boolean folderMode = rbFolder.isSelected();
        Converter converter = converter();
        String inputPath = txtFilePath.getText().trim();
        if (inputPath.isEmpty()) {
            showError(folderMode
                ? "No se ha seleccionado ninguna carpeta."
                : "No se ha seleccionado ningún archivo.");
            return;
        }

        File input = new File(inputPath);
        final List<File> pending;

        if (folderMode) {
            if (!input.isDirectory()) {
                showError("La carpeta seleccionada no existe o no es válida.");
                return;
            }
            pending = converter.findInputFiles(input);
            if (pending.isEmpty()) {
                showError("La carpeta no contiene ningún archivo "
                    + String.join(" ni ", converter.direction().inputExtensions()) + ".");
                return;
            }
        } else {
            if (!input.exists() || !input.isFile()) {
                showError("El archivo seleccionado no existe o no es válido.");
                return;
            }
            pending = List.of(input);
        }

        setUiBusy(true);
        clearLog();

        // Ejecutar conversión en hilo separado para no bloquear la UI
        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                Consumer<String> out = this::publish;

                if (pending.size() > 1) {
                    out.accept("Documentos a convertir: " + pending.size());
                    out.accept("");
                }

                int converted = 0;
                int failed = 0;
                for (File document : pending) {
                    ConversionResult result = converter.convert(document);
                    reporter.describe(result).forEach(out);
                    if (result.success()) {
                        converted++;
                    } else {
                        failed++;
                    }
                    out.accept("");
                }

                if (pending.size() > 1) {
                    reporter.summarize(converted, failed).forEach(out);
                }
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                chunks.forEach(App.this::log);
            }

            @Override
            protected void done() {
                setUiBusy(false);
            }
        };

        worker.execute();
    }

    // --- Accesores visibles para las pruebas (mismo paquete) ---

    JTextArea logArea() {
        return txtLog;
    }

    boolean isFolderMode() {
        return rbFolder.isSelected();
    }

    ConversionDirection direction() {
        return converter().direction();
    }

    void selectDirection(ConversionDirection direction) {
        (direction == ConversionDirection.MARKDOWN_TO_WORD ? rbToWord : rbToMarkdown).setSelected(true);
        onDirectionChanged(null);
    }

    String convertLabel() {
        return btnConvert.getText();
    }

    String selectedPath() {
        return txtFilePath.getText();
    }

    String pathLabel() {
        return lblFile.getText();
    }

    boolean isConvertEnabled() {
        return btnConvert.isEnabled();
    }

    boolean isCopyLogEnabled() {
        return btnCopyLog.isEnabled();
    }

    boolean isClearLogEnabled() {
        return btnClearLog.isEnabled();
    }

    /**
     * Bloquea los controles mientras hay una conversión en curso. Copiar sigue
     * disponible —el registro se va llenando— pero limpiar no: borraría lo que
     * se está escribiendo.
     */
    private void setUiBusy(boolean busy) {
        converting = busy;
        btnConvert.setEnabled(!busy);
        btnSelect.setEnabled(!busy);
        rbFile.setEnabled(!busy);
        rbFolder.setEnabled(!busy);
        rbToMarkdown.setEnabled(!busy);
        rbToWord.setEnabled(!busy);
        updateClearState();
    }

    /** Menú del botón derecho sobre el registro. */
    private JPopupMenu logPopupMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem copy = new JMenuItem("Copiar");
        copy.addActionListener(e -> copySelectionOrAll());
        menu.add(copy);

        JMenuItem selectAll = new JMenuItem("Seleccionar todo");
        selectAll.addActionListener(e -> {
            txtLog.requestFocusInWindow();
            txtLog.selectAll();
        });
        menu.add(selectAll);

        return menu;
    }

    /** Copia lo seleccionado o, si no hay selección, el registro entero. */
    private void copySelectionOrAll() {
        if (txtLog.getSelectedText() != null) {
            txtLog.copy();
        } else {
            copyLog(getToolkit().getSystemClipboard());
        }
    }

    /**
     * Copia el registro completo al portapapeles indicado. Devuelve false si no
     * hay nada que copiar.
     *
     * <p>Recibe el portapapeles como parámetro para poder probarlo sin tocar el
     * del sistema. Visible para las pruebas.
     */
    boolean copyLog(Clipboard clipboard) {
        String text = txtLog.getText();
        if (text.isEmpty()) {
            return false;
        }
        clipboard.setContents(new StringSelection(text), null);
        return true;
    }

    private void log(String message) {
        txtLog.append(message + "\n");
        txtLog.setCaretPosition(txtLog.getDocument().getLength());
        btnCopyLog.setEnabled(true);
        updateClearState();
    }

    /**
     * Vacía el registro. Se hace al elegir un archivo o una carpeta y al empezar
     * una conversión: lo que se ve siempre es lo último que se ha hecho. La ruta
     * seleccionada no se toca, porque en esos casos se acaba de elegir.
     */
    private void clearLog() {
        txtLog.setText("");
        btnCopyLog.setEnabled(false);
        updateClearState();
    }

    /**
     * Botón "Limpiar": deja la ventana como recién abierta, sin registro y sin
     * selección.
     *
     * <p>Visible para las pruebas.
     */
    void clearAll() {
        txtFilePath.setText("");
        btnConvert.setEnabled(false);
        clearLog();
    }

    /** Solo hay algo que limpiar si queda registro o una ruta seleccionada. */
    private void updateClearState() {
        btnClearLog.setEnabled(!converting
            && !(txtLog.getText().isEmpty() && txtFilePath.getText().isEmpty()));
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        // POI registra sus mensajes con log4j, que sin implementación detrás
        // escupe un error por consola al primer uso. Con esto usa el registro
        // mínimo que trae la propia API y no molesta.
        System.setProperty("log4j2.loggerContextFactory",
            "org.apache.logging.log4j.simple.SimpleLoggerContextFactory");

        // Aplicar Look & Feel del sistema antes de crear la ventana
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            App app = new App();
            app.setVisible(true);
        });
    }
}
