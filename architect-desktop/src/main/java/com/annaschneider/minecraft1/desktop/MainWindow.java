package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.BuildMode;
import com.annaschneider.minecraft1.link.JobStatus;
import com.annaschneider.minecraft1.link.LinkClient;
import com.annaschneider.minecraft1.link.LinkException;
import com.annaschneider.minecraft1.link.LinkMessage;
import com.annaschneider.minecraft1.link.LinkProtocol;
import com.annaschneider.minecraft1.link.LinkRequest;
import com.annaschneider.minecraft1.link.CameraNpcSettings;
import com.annaschneider.minecraft1.link.MessageKind;
import com.annaschneider.minecraft1.link.PlanSummary;
import com.annaschneider.minecraft1.link.RecordingStatus;
import com.annaschneider.minecraft1.link.RequestType;
import com.annaschneider.minecraft1.link.ServerInfo;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The main window. Three numbered steps, left to right and top to bottom: 1) choose what to build (describe it, use a
 * picture or pick a template), 2) preview it, 3) build / pause / cancel / undo in Minecraft. Connection status is always
 * visible at the top and everything that happens is written to the log at the bottom.
 */
final class MainWindow extends JFrame implements ArchitectConnection.Listener {
    private static final List<String> DEFAULT_TEMPLATES = List.of("castle", "house", "temple", "village");
    private static final Color GREEN = new Color(0x2E7D32);
    private static final Color AMBER = new Color(0xE0A000);
    private static final Color RED = new Color(0xC62828);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int TAB_PROMPT = 0;
    private static final int TAB_IMAGE = 1;
    private static final int TAB_TEMPLATE = 2;

    private final DesktopSettings settings;
    private final ArchitectConnection connection = new ArchitectConnection(this);
    private final Timer retryTimer = new Timer(5_000, event -> onRetryTimer());

    private final StatusDot statusDot = new StatusDot();
    private final JLabel statusLabel = new JLabel("Not connected");
    private final JButton connectButton = new JButton("Connect");
    private final JTabbedPane sourceTabs = new JTabbedPane();
    private final JTextArea promptArea = new JTextArea(5, 30);
    private final JLabel imagePreview = new JLabel("", SwingConstants.CENTER);
    private final JLabel imageInfo = new JLabel("No picture chosen.");
    private final DefaultListModel<String> templateModel = new DefaultListModel<>();
    private final JList<String> templateList = new JList<>(templateModel);
    private final JSlider scaleSlider = new JSlider(1, 16, 1);
    private final JLabel scaleLabel = new JLabel();
    private final JTextField planNameField = new JTextField(20);
    private final JButton previewButton = new JButton("Preview");
    private final PlanMapPanel mapPanel = new PlanMapPanel();
    private final JTextArea summaryArea = new JTextArea(6, 30);
    private final JButton buildButton = new JButton("Build in Minecraft");
    private final JButton buildAndRecordButton = new JButton("Build + Record");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton undoButton = new JButton("Undo last build");
    private final JButton startRecordButton = new JButton("Start Recording");
    private final JButton stopRecordButton = new JButton("Stop & Save");
    private final javax.swing.JCheckBox cinematicCameraBox = new javax.swing.JCheckBox("Cinematic camera");
    private final javax.swing.JCheckBox npcBuildersBox = new javax.swing.JCheckBox("Builder NPCs");
    private final JLabel cameraStatusLabel = new JLabel(" ");
    private final StatusDot recordingDot = new StatusDot();
    private final JLabel recordingStatusLabel = new JLabel("Recording: Not connected");
    private final JProgressBar progressBar = new JProgressBar(0, 1000);
    private final JLabel noticeLabel = new JLabel(" ");
    private final JTextArea logArea = new JTextArea(7, 80);

    private Path imageFile;
    private String lastSuggestedName = "";
    private ServerInfo server;
    private JobStatus job;
    private RecordingStatus recordingStatus;
    private boolean autoStopRecordingOnJobComplete;
    private boolean busy;
    private boolean autoReconnect = true;
    private String lastConnectionMessage = "";
    private volatile String plannedKey;

    MainWindow(DesktopSettings settings) {
        super("Minecraft Architect");
        this.settings = settings;
        cinematicCameraBox.setSelected(settings.cameraNpcSettings().cameraEnabled());
        npcBuildersBox.setSelected(settings.cameraNpcSettings().npcEnabled());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(960, 640));
        setSize(1180, 780);
        setLocationRelativeTo(null);
        setContentPane(buildContent());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                retryTimer.stop();
                connection.shutdown();
            }
        });
        setTemplates(DEFAULT_TEMPLATES);
        suggestName();
        updateScaleLabel();
        updateControls();
        log("Welcome! Start Minecraft with the Architect mod and open a world - this app connects automatically.");
    }

    void start() {
        setVisible(true);
        connect();
        retryTimer.start();
    }

    // ------------------------------------------------------------------ layout

    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        root.add(buildConnectionBar(), BorderLayout.NORTH);

        JSplitPane columns = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildSourcePanel(), buildPreviewPanel());
        columns.setResizeWeight(0.42);
        columns.setBorder(null);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.add(columns, BorderLayout.CENTER);
        center.add(buildActionPanel(), BorderLayout.SOUTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Activity log"));

        JSplitPane rows = new JSplitPane(JSplitPane.VERTICAL_SPLIT, center, logScroll);
        rows.setResizeWeight(0.8);
        rows.setBorder(null);
        root.add(rows, BorderLayout.CENTER);
        return root;
    }

    private JComponent buildConnectionBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground") == null
                ? Color.LIGHT_GRAY : UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(2, 2, 8, 2)));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, statusLabel.getFont().getSize2D() + 1f));
        left.add(statusDot);
        left.add(statusLabel);
        bar.add(left, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        connectButton.addActionListener(event -> {
            if (connection.state() == ArchitectConnection.State.CONNECTED) {
                autoReconnect = false;
                connection.disconnect();
            } else {
                autoReconnect = true;
                connect();
            }
        });
        JButton settingsButton = new JButton("Settings...");
        settingsButton.addActionListener(event -> openSettings());
        JButton helpButton = new JButton("Help");
        helpButton.addActionListener(event -> showHelp());
        right.add(connectButton);
        right.add(settingsButton);
        right.add(helpButton);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildSourcePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 6, 0);

        c.gridy = 0;
        panel.add(stepTitle("1", "What do you want to build?"), c);

        sourceTabs.addTab("Describe it", buildPromptTab());
        sourceTabs.addTab("From a picture", buildImageTab());
        sourceTabs.addTab("Template", buildTemplateTab());
        sourceTabs.setToolTipTextAt(TAB_PROMPT, "Type what you want, e.g. \"white palace with waterfalls and cherry trees\".");
        sourceTabs.setToolTipTextAt(TAB_IMAGE, "Use a picture as inspiration for a big fantasy build.");
        sourceTabs.setToolTipTextAt(TAB_TEMPLATE, "Small ready-made buildings, built right where you stand.");
        sourceTabs.addChangeListener(event -> {
            plannedKey = null;
            suggestName();
            updateControls();
        });
        c.gridy = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        panel.add(sourceTabs, c);

        c.gridy = 2;
        c.weighty = 0;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 0, 2, 8);
        c.weightx = 0;
        panel.add(new JLabel("Size:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(8, 0, 2, 0);
        scaleSlider.setMajorTickSpacing(5);
        scaleSlider.setMinorTickSpacing(1);
        scaleSlider.setPaintTicks(true);
        scaleSlider.setSnapToTicks(true);
        scaleSlider.setToolTipText("Bigger = more floating islands around the palace (and many more blocks).");
        scaleSlider.addChangeListener(event -> {
            updateScaleLabel();
            plannedKey = null;
        });
        panel.add(scaleSlider, c);
        c.gridy = 3;
        c.insets = new Insets(0, 0, 6, 0);
        scaleLabel.setForeground(Color.GRAY);
        panel.add(scaleLabel, c);

        c.gridy = 4;
        c.gridx = 0;
        c.weightx = 0;
        c.insets = new Insets(4, 0, 6, 8);
        panel.add(new JLabel("Name:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(4, 0, 6, 0);
        planNameField.setToolTipText("Saved plan name: a-z, 0-9, '-' or '_'. Reusing a name replaces that plan.");
        planNameField.getDocument().addDocumentListener(onChange(() -> {
            plannedKey = null;
            boolean valid = BuildInputs.isValidPlanName(planNameField.getText());
            planNameField.setForeground(valid ? UIManager.getColor("TextField.foreground") : RED);
            updateControls();
        }));
        panel.add(planNameField, c);

        c.gridy = 5;
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(8, 0, 0, 0);
        previewButton.setToolTipText("Create the plan and show a map and summary - nothing is built yet.");
        previewButton.addActionListener(event -> preview());
        makeLarge(previewButton, false);
        panel.add(previewButton, c);
        return panel;
    }

    private JComponent buildPromptTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 6));
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tab.add(new JLabel("Describe your build in a few words:"), BorderLayout.NORTH);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setText("white palace with waterfalls, bridges and cherry trees");
        promptArea.getDocument().addDocumentListener(onChange(() -> {
            plannedKey = null;
            suggestName();
            updateControls();
        }));
        tab.add(new JScrollPane(promptArea), BorderLayout.CENTER);
        JLabel tip = new JLabel("<html>Tip: these words shape the result - <b>palace, castle, temple, tower, bridge, terrace, "
            + "waterfall, island, floating, cloud, sky, garden, flower, cherry, sakura, path, road</b>. "
            + "Add <b>dark</b>/<b>night</b> or <b>desert</b> for another style. Works offline, no account needed.</html>");
        tip.setForeground(Color.GRAY);
        tab.add(tip, BorderLayout.SOUTH);
        return tab;
    }

    private JComponent buildImageTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 6));
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        imagePreview.setText("<html><center>Drag a picture here<br>or click \"Choose picture...\"</center></html>");
        imagePreview.setBorder(BorderFactory.createDashedBorder(Color.GRAY, 2, 6, 4, true));
        imagePreview.setPreferredSize(new Dimension(300, 180));
        imagePreview.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        imagePreview.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                chooseImage();
            }
        });
        TransferHandler dropHandler = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        setImage(files.get(0).toPath());
                        return true;
                    }
                } catch (Exception ex) {
                    notice("Could not read the dropped file: " + ex.getMessage(), true);
                }
                return false;
            }
        };
        tab.setTransferHandler(dropHandler);
        imagePreview.setTransferHandler(dropHandler);
        tab.add(imagePreview, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 4));
        JButton choose = new JButton("Choose picture...");
        choose.addActionListener(event -> chooseImage());
        bottom.add(choose, BorderLayout.WEST);
        bottom.add(imageInfo, BorderLayout.CENTER);
        JLabel tip = new JLabel("<html>PNG, JPG, GIF or WebP up to " + LinkProtocol.MAX_IMAGE_BYTES / (1024 * 1024)
            + " MiB. The picture's name and shape guide the layout (e.g. <i>sky-palace-waterfall.png</i>).</html>");
        tip.setForeground(Color.GRAY);
        bottom.add(tip, BorderLayout.SOUTH);
        tab.add(bottom, BorderLayout.SOUTH);
        return tab;
    }

    private JComponent buildTemplateTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 6));
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tab.add(new JLabel("Pick a ready-made building:"), BorderLayout.NORTH);
        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.setVisibleRowCount(6);
        templateList.setFont(templateList.getFont().deriveFont(templateList.getFont().getSize2D() + 3f));
        templateList.setFixedCellHeight(32);
        templateList.setCellRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel("  " + Character.toUpperCase(value.charAt(0)) + value.substring(1));
            label.setOpaque(true);
            label.setFont(list.getFont());
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        templateList.addListSelectionListener(event -> updateControls());
        tab.add(new JScrollPane(templateList), BorderLayout.CENTER);
        JLabel tip = new JLabel("Templates are built at your current position in the game.");
        tip.setForeground(Color.GRAY);
        tab.add(tip, BorderLayout.SOUTH);
        return tab;
    }

    private JComponent buildPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        panel.add(stepTitle("2", "Preview"), BorderLayout.NORTH);
        summaryArea.setEditable(false);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setText("Nothing to preview yet.");
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapPanel, new JScrollPane(summaryArea));
        split.setResizeWeight(0.72);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildActionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        panel.add(stepTitle("3", "Build it in Minecraft"), BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        makeLarge(buildButton, true);
        buildButton.setToolTipText("Start building at your position in the game.");
        buildButton.addActionListener(event -> build());

        makeLarge(buildAndRecordButton, false);
        buildAndRecordButton.setToolTipText("Start in-game recording (ReplayMod), then build automatically.");
        buildAndRecordButton.addActionListener(event -> buildAndRecord());

        makeLarge(pauseButton, false);
        pauseButton.setToolTipText("Pause or resume the current build.");
        pauseButton.addActionListener(event -> simple(job != null && job.isPaused() ? RequestType.RESUME : RequestType.PAUSE));
        makeLarge(cancelButton, false);
        cancelButton.setToolTipText("Stop the current build. Blocks already placed stay until you click Undo.");
        cancelButton.addActionListener(event -> {
            int answer = JOptionPane.showConfirmDialog(this, "Stop the current build?\nBlocks already placed stay until you click Undo.",
                "Cancel build", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                simple(RequestType.CANCEL);
            }
        });
        makeLarge(undoButton, false);
        undoButton.setToolTipText("Remove your last build and restore what was there before.");
        undoButton.addActionListener(event -> {
            int answer = JOptionPane.showConfirmDialog(this, "Remove your last build and restore what was there before?",
                "Undo last build", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                simple(RequestType.UNDO);
            }
        });
        buttons.add(buildButton);
        buttons.add(buildAndRecordButton);
        buttons.add(pauseButton);
        buttons.add(cancelButton);
        buttons.add(undoButton);

        progressBar.setStringPainted(true);
        progressBar.setString("No build running");
        progressBar.setPreferredSize(new Dimension(200, 28));
        JPanel status = new JPanel(new BorderLayout(0, 4));
        status.add(progressBar, BorderLayout.CENTER);
        noticeLabel.setFont(noticeLabel.getFont().deriveFont(Font.BOLD));
        status.add(noticeLabel, BorderLayout.SOUTH);

        JPanel mainRow = new JPanel(new BorderLayout(12, 0));
        mainRow.add(buttons, BorderLayout.WEST);
        mainRow.add(status, BorderLayout.CENTER);

        // Recording controls section
        JPanel recordBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        recordBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground") == null
                ? Color.LIGHT_GRAY : UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(4, 0, 0, 0)));
        JLabel recordTitle = new JLabel("Recording:");
        recordTitle.setFont(recordTitle.getFont().deriveFont(Font.BOLD));
        recordBar.add(recordTitle);
        recordBar.add(recordingDot);
        recordBar.add(recordingStatusLabel);

        startRecordButton.setToolTipText("Start recording with ReplayMod without OBS.");
        startRecordButton.addActionListener(event -> startRecording());
        stopRecordButton.setToolTipText("Stop recording and save the replay file.");
        stopRecordButton.addActionListener(event -> stopRecording());

        recordBar.add(startRecordButton);
        recordBar.add(stopRecordButton);

        cinematicCameraBox.setToolTipText("Film the build with the automatic cinematic camera during Build + Record.");
        cinematicCameraBox.addActionListener(event -> {
            saveQuickSettings();
            if (!cinematicCameraBox.isSelected()) {
                sendCamera(LinkRequest.camera("stop"));
            }
        });
        npcBuildersBox.setToolTipText("Show villager workers around the sections currently being built.");
        npcBuildersBox.addActionListener(event -> saveQuickSettings());
        recordBar.add(cinematicCameraBox);
        recordBar.add(npcBuildersBox);
        recordBar.add(cameraStatusLabel);

        JPanel combined = new JPanel(new BorderLayout(0, 6));
        combined.add(mainRow, BorderLayout.CENTER);
        combined.add(recordBar, BorderLayout.SOUTH);

        panel.add(combined, BorderLayout.CENTER);
        return panel;
    }

    // ------------------------------------------------------------------ actions

    private void connect() {
        connection.setCameraNpcSettings(settings.cameraNpcSettings());
        connection.connect(settings.linkFile(), settings.player());
    }

    private void saveQuickSettings() {
        CameraNpcSettings previous = settings.cameraNpcSettings();
        CameraNpcSettings updated = new CameraNpcSettings(cinematicCameraBox.isSelected(), npcBuildersBox.isSelected(),
            previous.maxNpcs(), previous.cameraHeight(), previous.rotationSpeed());
        settings.setCameraNpcSettings(updated);
        connection.setCameraNpcSettings(updated);
        if (connection.isConnected()) {
            sendCamera(LinkRequest.settings(updated));
        }
        updateControls();
    }

    private void onRetryTimer() {
        if (connection.state() == ArchitectConnection.State.DISCONNECTED && autoReconnect) {
            connect();
        } else if (connection.state() == ArchitectConnection.State.CONNECTED && !busy
            && (server == null || server.player() == null || recordingStatus == null)) {
            connection.submit(client -> client.call(LinkRequest.of(RequestType.STATUS)), this::applyStatus, message -> { });
        }
    }

    private void preview() {
        Selection selection = currentSelection();
        if (selection == null) {
            return;
        }
        runBusy("Preparing preview...", client -> selection.mode() == BuildMode.TEMPLATE
            ? client.call(LinkRequest.preview(BuildMode.TEMPLATE, selection.template()))
            : ensurePlan(client, selection), result -> {
                if (result.plan() != null) {
                    showPlan(result.plan());
                }
            });
    }

    private void build() {
        Selection selection = currentSelection();
        if (selection == null) {
            return;
        }
        runBusy("Sending build to Minecraft...", client -> {
            if (selection.mode() == BuildMode.TEMPLATE) {
                return client.call(LinkRequest.build(BuildMode.TEMPLATE, selection.template()));
            }
            if (!selection.key().equals(plannedKey)) {
                LinkMessage plan = ensurePlan(client, selection);
                if (!plan.isOk()) {
                    return plan;
                }
                javax.swing.SwingUtilities.invokeLater(() -> {
                    showPlan(plan.plan());
                    log(plan.message());
                });
            }
            return client.call(LinkRequest.build(BuildMode.PLAN, selection.planName()));
        }, result -> { });
    }

    private void buildAndRecord() {
        Selection selection = currentSelection();
        if (selection == null) {
            return;
        }
        if (recordingStatus != null && recordingStatus.recording()) {
            build();
            return;
        }
        if (recordingStatus != null && !recordingStatus.available()) {
            notice("Recording unavailable (" + (recordingStatus.message() != null ? recordingStatus.message() : "ReplayMod not active") + "). Building normally.", false);
            build();
            return;
        }
        autoStopRecordingOnJobComplete = true;
        boolean cinematic = cinematicCameraBox.isSelected();
        runBusy("Starting recording before build...", client -> {
            LinkMessage rec = client.call(LinkRequest.recordStart());
            if (!rec.isOk()) {
                return rec;
            }
            boolean filming = false;
            if (cinematic) {
                LinkMessage camera = client.call(LinkRequest.camera("auto"));
                filming = camera.isOk();
                javax.swing.SwingUtilities.invokeLater(() -> showCameraMessage(camera.message()));
            }
            LinkMessage result;
            if (selection.mode() == BuildMode.TEMPLATE) {
                result = client.call(LinkRequest.build(BuildMode.TEMPLATE, selection.template()));
            } else {
                if (!selection.key().equals(plannedKey)) {
                    LinkMessage plan = ensurePlan(client, selection);
                    if (!plan.isOk()) {
                        result = plan;
                        if (filming) {
                            client.call(LinkRequest.camera("stop"));
                        }
                        return result;
                    }
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        showPlan(plan.plan());
                        log(plan.message());
                    });
                }
                result = client.call(LinkRequest.build(BuildMode.PLAN, selection.planName()));
            }
            if (!result.isOk() && filming) {
                // the build never started: do not leave the player stuck in a cinematic view
                client.call(LinkRequest.camera("stop"));
            }
            return result;
        }, result -> { });
    }

    /** Sends a camera/NPC request without blocking the UI; failures only show up in the log. */
    private void sendCamera(LinkRequest request) {
        runBusy("Updating camera settings...", client -> client.call(request),
            result -> showCameraMessage(result.message()));
    }

    private void showCameraMessage(String message) {
        if (message != null && !message.isBlank()) {
            cameraStatusLabel.setText(message);
            cameraStatusLabel.setToolTipText(message);
            log(message);
        }
    }

    private void startRecording() {
        runBusy("Starting recording...", client -> client.call(LinkRequest.recordStart()), result -> { });
    }

    private void stopRecording() {
        autoStopRecordingOnJobComplete = false;
        runBusy("Stopping recording...", client -> client.call(LinkRequest.recordStop()), result -> { });
    }

    private void updateRecording(RecordingStatus status) {
        if (status == null) {
            return;
        }
        this.recordingStatus = status;
        if (status.recording()) {
            recordingDot.setColor(RED);
            String desc = "Recording active" + (status.backend() != null ? " (" + status.backend() + ")" : "");
            recordingStatusLabel.setText(desc);
            recordingStatusLabel.setToolTipText(status.outputPath() != null ? "Output: " + status.outputPath() : "Recording in progress");
        } else if (status.available()) {
            recordingDot.setColor(GREEN);
            String desc = "Ready (" + (status.backend() != null ? status.backend() : "ReplayMod") + ")";
            if (status.outputPath() != null) {
                desc += " - Saved: " + status.outputPath();
            }
            recordingStatusLabel.setText(desc);
            recordingStatusLabel.setToolTipText(status.message() != null ? status.message() : "Recording backend ready");
        } else {
            recordingDot.setColor(Color.GRAY);
            String msg = status.message() != null ? status.message() : "ReplayMod not detected";
            recordingStatusLabel.setText("Recording unavailable (" + msg + ")");
            recordingStatusLabel.setToolTipText(msg);
        }
        updateControls();
    }

    private void simple(RequestType type) {
        runBusy(null, client -> client.call(LinkRequest.of(type)), result -> { });
    }

    /** Worker thread: uploads the picture if needed and (re)creates the plan. */
    private LinkMessage ensurePlan(LinkClient client, Selection selection) throws LinkException, IOException {
        LinkMessage plan;
        if (selection.image() != null) {
            LinkMessage upload = client.call(BuildInputs.uploadRequest(selection.image()));
            if (!upload.isOk()) {
                return upload;
            }
            plan = client.call(LinkRequest.planFromSource(selection.planName(), upload.source(), selection.scale()));
        } else {
            plan = client.call(LinkRequest.planFromPrompt(selection.planName(), selection.prompt(), selection.scale()));
        }
        if (plan.isOk()) {
            plannedKey = selection.key();
        }
        return plan;
    }

    private void runBusy(String startMessage, ArchitectConnection.Task task, java.util.function.Consumer<LinkMessage> onOk) {
        if (busy) {
            return;
        }
        busy = true;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        if (startMessage != null) {
            notice(startMessage, false);
        }
        updateControls();
        connection.submit(task, result -> {
            finishBusy();
            if (result.job() != null) {
                updateJob(result.job());
            }
            if (result.recording() != null) {
                updateRecording(result.recording());
            }
            if (result.isOk()) {
                notice(firstLine(result.message()), false);
                log(result.message());
                onOk.accept(result);
            } else {
                notice(firstLine(result.message()), true);
                log("Problem: " + result.message());
            }
        }, error -> {
            finishBusy();
            notice(firstLine(error), true);
            log("Problem: " + error);
        });
    }

    private void finishBusy() {
        busy = false;
        setCursor(Cursor.getDefaultCursor());
        updateControls();
    }

    private Selection currentSelection() {
        int tab = sourceTabs.getSelectedIndex();
        if (tab == TAB_TEMPLATE) {
            String template = templateList.getSelectedValue();
            if (template == null) {
                notice("Choose a template first.", true);
                return null;
            }
            return new Selection(BuildMode.TEMPLATE, null, null, template, 0, template, "");
        }
        String name = planNameField.getText().trim();
        if (!BuildInputs.isValidPlanName(name)) {
            notice("The name may only use a-z, 0-9, '-' and '_' (for example: sky-palace).", true);
            planNameField.requestFocusInWindow();
            return null;
        }
        int scale = scaleSlider.getValue();
        if (tab == TAB_IMAGE) {
            String problem = BuildInputs.checkImage(imageFile);
            if (problem != null) {
                notice(problem, true);
                return null;
            }
            String version;
            try {
                version = Files.getLastModifiedTime(imageFile) + "/" + Files.size(imageFile);
            } catch (IOException ex) {
                notice("Cannot read the picture: " + ex.getMessage(), true);
                return null;
            }
            return new Selection(BuildMode.PLAN, null, imageFile, null, scale, name, version);
        }
        String prompt = promptArea.getText().trim();
        if (prompt.isEmpty()) {
            notice("Describe what you want to build first.", true);
            promptArea.requestFocusInWindow();
            return null;
        }
        if (prompt.length() > LinkProtocol.MAX_PROMPT_LENGTH) {
            notice("The description is too long (max " + LinkProtocol.MAX_PROMPT_LENGTH + " characters).", true);
            return null;
        }
        return new Selection(BuildMode.PLAN, prompt, null, null, scale, name, "");
    }

    private void chooseImage() {
        JFileChooser chooser = new JFileChooser(imageFile == null ? null : imageFile.getParent().toFile());
        chooser.setDialogTitle("Choose a picture");
        chooser.setFileFilter(new FileNameExtensionFilter("Pictures (png, jpg, gif, webp)", "png", "jpg", "jpeg", "gif", "webp"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setImage(chooser.getSelectedFile().toPath());
        }
    }

    private void setImage(Path file) {
        String problem = BuildInputs.checkImage(file);
        if (problem != null) {
            notice(problem, true);
            return;
        }
        imageFile = file;
        plannedKey = null;
        sourceTabs.setSelectedIndex(TAB_IMAGE);
        imagePreview.setIcon(null);
        imagePreview.setText("Loading preview...");
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image != null) {
                int maxW = Math.max(120, imagePreview.getWidth() - 12);
                int maxH = Math.max(90, imagePreview.getHeight() - 12);
                double factor = Math.min(1.0, Math.min((double) maxW / image.getWidth(), (double) maxH / image.getHeight()));
                Image scaled = image.getScaledInstance(Math.max(1, (int) (image.getWidth() * factor)),
                    Math.max(1, (int) (image.getHeight() * factor)), Image.SCALE_SMOOTH);
                imagePreview.setIcon(new ImageIcon(scaled));
                imagePreview.setText("");
                imageInfo.setText(file.getFileName() + "  (" + image.getWidth() + " x " + image.getHeight() + ")");
            } else {
                imagePreview.setText("No preview for this format - it can still be used.");
                imageInfo.setText(file.getFileName().toString());
            }
        } catch (IOException ex) {
            imagePreview.setText("No preview available.");
            imageInfo.setText(file.getFileName().toString());
        }
        suggestName();
        updateControls();
        log("Picture chosen: " + file);
    }

    private void openSettings() {
        SettingsDialog dialog = new SettingsDialog(this, settings);
        dialog.setVisible(true);
        if (dialog.saved()) {
            cinematicCameraBox.setSelected(settings.cameraNpcSettings().cameraEnabled());
            npcBuildersBox.setSelected(settings.cameraNpcSettings().npcEnabled());
            autoReconnect = true;
            connection.disconnect();
            connect();
        }
    }

    private void showHelp() {
        JOptionPane.showMessageDialog(this, "<html><body style='width: 440px'>"
            + "<h3>Connecting to Minecraft</h3><ol>"
            + "<li>Install the Architect mod (Fabric, Minecraft 1.20.1) and start Minecraft.</li>"
            + "<li>Open a world. The mod then writes <i>desktop-link.json</i> into "
            + "<i>%APPDATA%\\.minecraft\\config\\architect</i> and this app connects on its own.</li>"
            + "<li>Using a custom launcher (CurseForge, Prism, ...)? Click <b>Settings...</b> and choose the "
            + "<i>config\\architect\\desktop-link.json</i> file inside that instance's folder.</li></ol>"
            + "<h3>Building</h3><ol>"
            + "<li>Describe your build, pick a picture or choose a template.</li>"
            + "<li>Click <b>Preview</b> to see the map and size.</li>"
            + "<li>Click <b>Build in Minecraft</b>. It is built at your position in the game.</li></ol>"
            + "<p><b>Tip:</b> Minecraft pauses single-player games when you switch windows. Press <b>F3+P</b> in the game "
            + "(or open the world to LAN) so building continues while you use this app.</p>"
            + "<p>Everything runs on your computer; nothing is sent to the internet.</p></body></html>",
            "Help", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------ connection callbacks

    @Override
    public void onStateChanged(ArchitectConnection.State state, String message, ServerInfo info) {
        switch (state) {
            case CONNECTING -> {
                statusDot.setColor(AMBER);
                statusLabel.setText("Connecting to Minecraft...");
                connectButton.setText("Connect");
            }
            case CONNECTED -> {
                connectButton.setText("Disconnect");
                applyServer(info);
                log(message);
                lastConnectionMessage = "";
            }
            case DISCONNECTED -> {
                server = null;
                recordingStatus = null;
                recordingDot.setColor(Color.GRAY);
                recordingStatusLabel.setText("Recording: Not connected");
                recordingStatusLabel.setToolTipText(null);
                statusDot.setColor(RED);
                statusLabel.setText("Not connected - start Minecraft and open a world");
                statusLabel.setToolTipText(message);
                connectButton.setText("Connect");
                if (!message.equals(lastConnectionMessage)) {
                    log(message + (autoReconnect ? " (retrying automatically)" : ""));
                    lastConnectionMessage = message;
                }
            }
        }
        updateControls();
    }

    @Override
    public void onEvent(LinkMessage message) {
        if (message.kind() == MessageKind.PROGRESS && message.job() != null) {
            updateJob(message.job());
        } else if (message.message() != null) {
            log(message.message());
        }
        if (message.recording() != null) {
            updateRecording(message.recording());
        }
    }

    private void applyStatus(LinkMessage status) {
        if (status.isOk() && status.server() != null) {
            boolean joined = (server == null || server.player() == null) && status.server().player() != null;
            applyServer(status.server());
            if (joined) {
                log(status.message());
            }
        }
        if (status.job() != null) {
            updateJob(status.job());
        }
        if (status.recording() != null) {
            updateRecording(status.recording());
        }
    }

    private void applyServer(ServerInfo info) {
        server = info;
        if (info == null) {
            return;
        }
        if (info.player() != null) {
            statusDot.setColor(GREEN);
            statusLabel.setText("Connected to Minecraft - player " + info.player());
            statusLabel.setToolTipText("Mod version " + info.modVersion() + ", protocol " + info.protocol());
        } else {
            statusDot.setColor(AMBER);
            statusLabel.setText("Connected - open a world in Minecraft to build");
            statusLabel.setToolTipText("Minecraft is running but no player is in a world yet.");
        }
        if (!info.templates().isEmpty()) {
            setTemplates(info.templates());
        }
        scaleSlider.setMaximum(Math.max(1, info.maxScale()));
        updateScaleLabel();
        updateControls();
    }

    private void setTemplates(List<String> templates) {
        String selected = templateList.getSelectedValue();
        templateModel.clear();
        templates.forEach(templateModel::addElement);
        templateList.setSelectedValue(selected != null && templates.contains(selected) ? selected : templates.get(0), true);
    }

    private void updateJob(JobStatus status) {
        JobStatus previous = job;
        job = status;
        progressBar.setValue((int) Math.round(status.percent() * 10));
        String verb = "undo".equals(status.kind()) ? "Undoing" : "Building";
        String text = switch (status.state()) {
            case "queued" -> "Waiting in queue: " + status.name();
            case "running" -> String.format(Locale.ROOT, "%s '%s' - %.1f%%%s", verb, status.name(), status.percent(),
                status.waitingForChunks() ? " (waiting for chunks)" : "");
            case "paused" -> String.format(Locale.ROOT, "Paused '%s' at %.1f%%", status.name(), status.percent());
            case "completed" -> ("undo".equals(status.kind()) ? "Undo finished: " : "Finished: ") + status.name();
            case "cancelled" -> String.format(Locale.ROOT, "Cancelled '%s' at %.1f%%", status.name(), status.percent());
            default -> "Failed: " + status.name() + (status.message() == null ? "" : " - " + status.message());
        };
        progressBar.setString(text);
        if (previous == null || previous.jobId() != status.jobId() || !previous.state().equals(status.state())) {
            log(status.describe());
        }
        if (autoStopRecordingOnJobComplete && (status.state().equals("completed") || status.state().equals("cancelled") || status.state().equals("failed"))) {
            autoStopRecordingOnJobComplete = false;
            log("Build finished, stopping recording...");
            connection.submit(client -> client.call(LinkRequest.recordStop()), result -> {
                if (result.recording() != null) {
                    updateRecording(result.recording());
                }
            }, error -> log("Could not stop recording: " + error));
        }
        updateControls();
    }

    // ------------------------------------------------------------------ helpers

    private void updateControls() {
        boolean connected = connection.state() == ArchitectConnection.State.CONNECTED;
        boolean hasPlayer = connected && server != null && server.player() != null;
        boolean template = sourceTabs.getSelectedIndex() == TAB_TEMPLATE;
        boolean active = job != null && job.isActive();
        boolean ready = template ? templateList.getSelectedValue() != null : BuildInputs.isValidPlanName(planNameField.getText().trim());
        scaleSlider.setEnabled(!template);
        planNameField.setEnabled(!template);
        previewButton.setEnabled(connected && !busy && ready);
        buildButton.setEnabled(hasPlayer && !busy && ready && !active);
        buildAndRecordButton.setEnabled(hasPlayer && !busy && ready && !active);
        pauseButton.setEnabled(hasPlayer && !busy && active);
        pauseButton.setText(job != null && job.isPaused() ? "Resume" : "Pause");
        cancelButton.setEnabled(hasPlayer && !busy && active && "build".equals(job.kind()));
        undoButton.setEnabled(hasPlayer && !busy && !active);
        boolean isRecording = recordingStatus != null && recordingStatus.recording();
        startRecordButton.setEnabled(hasPlayer && !busy && !isRecording && (recordingStatus == null || recordingStatus.available()));
        stopRecordButton.setEnabled(hasPlayer && !busy && isRecording);
        String why = !connected ? "Connect to Minecraft first." : !hasPlayer ? "Open a world in Minecraft first." : null;
        buildButton.setToolTipText(why != null ? why : active ? "A build is already running." : "Start building at your position in the game.");
        buildAndRecordButton.setToolTipText(why != null ? why : active ? "A build is already running." : "Start in-game recording, then build automatically.");
    }

    private void suggestName() {
        String current = planNameField.getText().trim();
        if (!current.isEmpty() && !current.equals(lastSuggestedName)) {
            return;
        }
        String source = switch (sourceTabs.getSelectedIndex()) {
            case TAB_IMAGE -> imageFile == null ? "" : imageFile.getFileName().toString().replaceFirst("\\.[^.]*$", "");
            case TAB_PROMPT -> promptArea.getText();
            default -> null;
        };
        if (source == null) {
            return;
        }
        lastSuggestedName = BuildInputs.suggestPlanName(source);
        planNameField.setText(lastSuggestedName);
    }

    private void updateScaleLabel() {
        int scale = scaleSlider.getValue();
        String size = scale <= 1 ? "about half a million blocks" : scale <= 4 ? "up to about 2 million blocks"
            : scale <= 8 ? "up to about 7 million blocks" : "tens of millions of blocks - takes a long time";
        scaleLabel.setText("Scale " + scale + " of " + scaleSlider.getMaximum() + ": " + size);
    }

    private void showPlan(PlanSummary plan) {
        mapPanel.setPlan(plan);
        summaryArea.setText(plan.text());
        summaryArea.setCaretPosition(0);
    }

    private void notice(String text, boolean error) {
        noticeLabel.setForeground(error ? RED : GREEN);
        noticeLabel.setText(text == null || text.isBlank() ? " " : text);
    }

    private void log(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String time = LocalTime.now().format(TIME);
        for (String line : text.split("\\R")) {
            logArea.append(time + "  " + line + "\n");
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }

    private static JComponent stepTitle(String number, String text) {
        JLabel label = new JLabel(number + ".  " + text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() + 3f));
        label.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        return label;
    }

    private static void makeLarge(JButton button, boolean primary) {
        Font font = button.getFont();
        button.setFont(font.deriveFont(primary ? Font.BOLD : Font.PLAIN, font.getSize2D() + (primary ? 3f : 1f)));
        button.setMargin(new Insets(8, primary ? 22 : 14, 8, primary ? 22 : 14));
    }

    private static DocumentListener onChange(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                action.run();
            }
        };
    }

    /** Everything needed to plan/build, captured on the event thread. */
    private record Selection(BuildMode mode, String prompt, Path image, String template, int scale, String planName,
                             String imageVersion) {
        String key() {
            return mode + "|" + prompt + "|" + image + "|" + imageVersion + "|" + scale + "|" + planName;
        }
    }

    /** Round coloured status indicator. */
    private static final class StatusDot extends JComponent implements Icon {
        private Color color = RED;

        StatusDot() {
            setPreferredSize(new Dimension(16, 16));
        }

        void setColor(Color newColor) {
            color = newColor;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            paintIcon(this, graphics, (getWidth() - 14) / 2, (getHeight() - 14) / 2);
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.fillOval(x, y, 14, 14);
            g.setColor(color.darker());
            g.drawOval(x, y, 13, 13);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }
}
