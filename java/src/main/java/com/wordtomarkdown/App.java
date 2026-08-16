package com.wordtomarkdown;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class App extends JFrame {

    private JTextField txtFilePath;
    private JButton btnSelect;
    private JButton btnConvert;
    private JTextArea txtLog;
    private JLabel lblFile;
    private JRadioButton rbFile;
    private JRadioButton rbFolder;
    private boolean converting;

    private final ConversionService service = new ConversionService();
    private final SelectionPolicy selectionPolicy = new SelectionPolicy(service);
    private final ConversionReporter reporter = new ConversionReporter();

    public App() {
        super("Word a Markdown - Conversor");
        initUI();
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

        // Fila 1: tipo de selección (archivo individual o carpeta completa)
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

        topPanel.add(modePanel, BorderLayout.NORTH);

        // Fila 2: ruta seleccionada
        JPanel pathPanel = new JPanel(new BorderLayout(8, 0));

        lblFile = new JLabel("Archivo .docx:");
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

        JScrollPane scrollPane = new JScrollPane(txtLog);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            "Registro (o arrastre aquí un .docx o una carpeta)"));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Arrastrar y soltar sobre el área de registro equivale a usar "Seleccionar..."
        TransferHandler dropHandler = new PathDropHandler();
        txtLog.setTransferHandler(dropHandler);
        scrollPane.setTransferHandler(dropHandler);

        // Panel inferior: botón de conversión
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnConvert = new JButton("Convertir a Markdown");
        btnConvert.setEnabled(false);
        btnConvert.setPreferredSize(new Dimension(180, 32));
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

    private void updateModeLabel() {
        lblFile.setText(rbFolder.isSelected() ? "Carpeta:" : "Archivo .docx:");
    }

    /**
     * Acepta archivos y carpetas soltados sobre el área de registro, con el mismo
     * efecto que elegirlos desde el diálogo "Seleccionar...".
     */
    private class PathDropHandler extends TransferHandler {

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
        SelectionPolicy.Decision decision = selectionPolicy.decideDrop(dropped);
        decision.messages().forEach(this::log);

        if (!decision.accepted()) {
            return false;
        }

        (decision.folderMode() ? rbFolder : rbFile).setSelected(true);
        updateModeLabel();
        txtFilePath.setText(decision.path().getAbsolutePath());
        btnConvert.setEnabled(true);
        return true;
    }

    private void onSelectFile(ActionEvent e) {
        boolean folderMode = rbFolder.isSelected();

        JFileChooser chooser = new JFileChooser();
        if (folderMode) {
            chooser.setDialogTitle("Seleccionar carpeta con documentos Word");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        } else {
            chooser.setDialogTitle("Seleccionar documento Word");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setFileFilter(new FileNameExtensionFilter("Documentos Word (*.docx)", "docx"));
            chooser.setAcceptAllFileFilterUsed(false);
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            txtFilePath.setText(selected.getAbsolutePath());
            btnConvert.setEnabled(true);
            if (folderMode) {
                log("Carpeta seleccionada: " + selected.getAbsolutePath());
                log("Documentos .docx encontrados: " + service.findDocxFiles(selected).size());
            } else {
                log("Archivo seleccionado: " + selected.getName());
            }
        }
    }

    private void onConvert(ActionEvent e) {
        boolean folderMode = rbFolder.isSelected();
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
            pending = service.findDocxFiles(input);
            if (pending.isEmpty()) {
                showError("La carpeta no contiene ningún archivo .docx.");
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
        txtLog.setText("");

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
                for (File docx : pending) {
                    ConversionResult result = service.convert(docx);
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

    String selectedPath() {
        return txtFilePath.getText();
    }

    String pathLabel() {
        return lblFile.getText();
    }

    boolean isConvertEnabled() {
        return btnConvert.isEnabled();
    }

    /** Bloquea los controles mientras hay una conversión en curso. */
    private void setUiBusy(boolean busy) {
        converting = busy;
        btnConvert.setEnabled(!busy);
        btnSelect.setEnabled(!busy);
        rbFile.setEnabled(!busy);
        rbFolder.setEnabled(!busy);
    }

    private void log(String message) {
        txtLog.append(message + "\n");
        txtLog.setCaretPosition(txtLog.getDocument().getLength());
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
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
