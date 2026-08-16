package com.wordtomarkdown;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import org.zwobble.mammoth.DocumentConverter;
import org.zwobble.mammoth.Result;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** Aplica la ruta arrastrada ajustando el tipo de selección al contenido soltado. */
    private boolean applyDroppedPath(File dropped) {
        if (dropped.isDirectory()) {
            rbFolder.setSelected(true);
            updateModeLabel();
            txtFilePath.setText(dropped.getAbsolutePath());
            btnConvert.setEnabled(true);
            log("Carpeta arrastrada: " + dropped.getAbsolutePath());
            log("Documentos .docx encontrados: " + findDocxFiles(dropped).size());
            return true;
        }

        if (!dropped.getName().toLowerCase().endsWith(".docx")) {
            log("Ignorado (no es un .docx): " + dropped.getName());
            return false;
        }

        rbFile.setSelected(true);
        updateModeLabel();
        txtFilePath.setText(dropped.getAbsolutePath());
        btnConvert.setEnabled(true);
        log("Archivo arrastrado: " + dropped.getName());
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
                log("Documentos .docx encontrados: " + findDocxFiles(selected).size());
            } else {
                log("Archivo seleccionado: " + selected.getName());
            }
        }
    }

    /**
     * Documentos .docx directamente contenidos en la carpeta, ordenados por nombre.
     * Se ignoran los archivos temporales que Word deja al abrir un documento ("~$nombre.docx"),
     * que no son documentos válidos.
     */
    private List<File> findDocxFiles(File folder) {
        File[] files = folder.listFiles(f -> f.isFile()
            && f.getName().toLowerCase().endsWith(".docx")
            && !f.getName().startsWith("~$")
            && !f.getName().startsWith("."));
        if (files == null) {
            return List.of();
        }
        return Arrays.stream(files)
            .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
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
            pending = findDocxFiles(input);
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
                    if (convertOne(docx, out)) {
                        converted++;
                    } else {
                        failed++;
                    }
                    out.accept("");
                }

                if (pending.size() > 1) {
                    out.accept("----------------------------------------");
                    out.accept("Resumen: " + converted + " convertido(s), " + failed + " con error.");
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

    /** Convierte un único .docx a Markdown junto a él. Devuelve true si tuvo éxito. */
    private boolean convertOne(File inputFile, Consumer<String> publish) {
        // Determinar ruta de salida (.md con mismo nombre)
        String baseName = inputFile.getName();
        if (baseName.toLowerCase().endsWith(".docx")) {
            baseName = baseName.substring(0, baseName.length() - 5);
        }
        File outputFile = new File(inputFile.getParent(), baseName + ".md");
        Path imageDir = Path.of(inputFile.getParent(), baseName + "_images");
        final String finalBaseName = baseName;

        publish.accept("Convirtiendo: " + inputFile.getName());
        publish.accept("  Salida: " + outputFile.getAbsolutePath());

        try {
            AtomicInteger imageCounter = new AtomicInteger(0);

            DocumentConverter converter = new DocumentConverter()
                .imageConverter(image -> {
                    Map<String, String> attrs = new HashMap<>();
                    try {
                        Files.createDirectories(imageDir);
                        String mime = image.getContentType();
                        String ext = mime.contains("/") ? mime.split("/")[1] : "png";
                        // Normalizar algunos tipos MIME compuestos (e.g. "jpeg", "svg+xml")
                        if (ext.contains("+")) ext = ext.split("\\+")[0];
                        String imgName = "image" + imageCounter.incrementAndGet() + "." + ext;
                        Path imgPath = imageDir.resolve(imgName);
                        try (InputStream is = image.getInputStream()) {
                            Files.copy(is, imgPath);
                        }
                        attrs.put("src", finalBaseName + "_images/" + imgName);
                        // Usar alt text limpio: ignorar disclaimers de IA de Word
                        image.getAltText()
                            .map(String::trim)
                            .filter(alt -> !alt.isBlank())
                            .filter(alt -> !alt.toLowerCase().contains("ia puede ser"))
                            .filter(alt -> !alt.toLowerCase().contains("generated by ai"))
                            .filter(alt -> !alt.toLowerCase().contains("generado por ia"))
                            .ifPresentOrElse(
                                alt -> attrs.put("alt", alt),
                                () -> attrs.put("alt", "imagen " + imageCounter.get())
                            );
                    } catch (IOException ex) {
                        attrs.put("src", "");
                        attrs.put("alt", "imagen " + imageCounter.incrementAndGet());
                    }
                    return attrs;
                });

            Result<String> htmlResult = converter.convertToHtml(inputFile);

            String html = htmlResult.getValue();
            Set<String> warnings = htmlResult.getWarnings();

            if (!warnings.isEmpty()) {
                publish.accept("  Advertencias durante la conversión:");
                warnings.forEach(w -> publish.accept("    [!] " + w));
            }

            // Convertir HTML a Markdown
            String markdown = FlexmarkHtmlConverter.builder().build().convert(html);

            Files.writeString(
                Path.of(outputFile.getAbsolutePath()),
                markdown,
                StandardCharsets.UTF_8
            );

            if (imageCounter.get() > 0) {
                publish.accept("  Imágenes extraídas: " + imageCounter.get() + " → " + imageDir.getFileName() + "/");
            }
            publish.accept("  OK: " + outputFile.getName());
            return true;

        } catch (IOException ex) {
            publish.accept("  ERROR: " + ex.getMessage());
            return false;
        }
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
