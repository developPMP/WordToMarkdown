package com.wordtomarkdown;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import org.zwobble.mammoth.DocumentConverter;
import org.zwobble.mammoth.Result;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class App extends JFrame {

    private JTextField txtFilePath;
    private JButton btnSelect;
    private JButton btnConvert;
    private JTextArea txtLog;

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

        // Panel superior: selección de archivo
        JPanel topPanel = new JPanel(new BorderLayout(8, 0));

        JLabel lblFile = new JLabel("Archivo .docx:");
        lblFile.setPreferredSize(new Dimension(90, 26));
        topPanel.add(lblFile, BorderLayout.WEST);

        txtFilePath = new JTextField();
        txtFilePath.setEditable(false);
        topPanel.add(txtFilePath, BorderLayout.CENTER);

        btnSelect = new JButton("Seleccionar...");
        btnSelect.addActionListener(this::onSelectFile);
        topPanel.add(btnSelect, BorderLayout.EAST);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Panel central: log de salida
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        txtLog.setBackground(new Color(245, 245, 245));
        txtLog.setLineWrap(true);
        txtLog.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(txtLog);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Registro"));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

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

    private void onSelectFile(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar documento Word");
        chooser.setFileFilter(new FileNameExtensionFilter("Documentos Word (*.docx)", "docx"));
        chooser.setAcceptAllFileFilterUsed(false);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            txtFilePath.setText(selected.getAbsolutePath());
            btnConvert.setEnabled(true);
            log("Archivo seleccionado: " + selected.getName());
        }
    }

    private void onConvert(ActionEvent e) {
        String inputPath = txtFilePath.getText().trim();
        if (inputPath.isEmpty()) {
            showError("No se ha seleccionado ningún archivo.");
            return;
        }

        File inputFile = new File(inputPath);
        if (!inputFile.exists() || !inputFile.isFile()) {
            showError("El archivo seleccionado no existe o no es válido.");
            return;
        }

        // Determinar ruta de salida (.md con mismo nombre)
        String baseName = inputFile.getName();
        if (baseName.toLowerCase().endsWith(".docx")) {
            baseName = baseName.substring(0, baseName.length() - 5);
        }
        File outputFile = new File(inputFile.getParent(), baseName + ".md");
        Path imageDir = Path.of(inputFile.getParent(), baseName + "_images");

        btnConvert.setEnabled(false);
        btnSelect.setEnabled(false);
        txtLog.setText("");

        // Ejecutar conversión en hilo separado para no bloquear la UI
        final String finalBaseName = baseName;
        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                publish("Iniciando conversión...");
                publish("Entrada : " + inputFile.getAbsolutePath());
                publish("Salida  : " + outputFile.getAbsolutePath());

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
                        publish("");
                        publish("Advertencias durante la conversión:");
                        warnings.forEach(w -> publish("  [!] " + w));
                    }

                    // Convertir HTML a Markdown
                    String markdown = FlexmarkHtmlConverter.builder().build().convert(html);

                    Files.writeString(
                        Path.of(outputFile.getAbsolutePath()),
                        markdown,
                        StandardCharsets.UTF_8
                    );

                    publish("");
                    if (imageCounter.get() > 0) {
                        publish("Imágenes extraídas: " + imageCounter.get() + " → " + imageDir.getFileName() + "/");
                    }
                    publish("Conversión completada con éxito.");
                    publish("Archivo generado: " + outputFile.getName());

                } catch (IOException ex) {
                    publish("");
                    publish("ERROR: " + ex.getMessage());
                }
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                chunks.forEach(App.this::log);
            }

            @Override
            protected void done() {
                btnConvert.setEnabled(true);
                btnSelect.setEnabled(true);
            }
        };

        worker.execute();
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
