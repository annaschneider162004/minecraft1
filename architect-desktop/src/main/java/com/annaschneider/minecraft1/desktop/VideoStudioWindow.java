package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.video.BuildContext;
import com.annaschneider.minecraft1.video.ExportMode;
import com.annaschneider.minecraft1.video.ExportRequest;
import com.annaschneider.minecraft1.video.ExportResult;
import com.annaschneider.minecraft1.video.FlowListener;
import com.annaschneider.minecraft1.video.FlowRequest;
import com.annaschneider.minecraft1.video.FlowResult;
import com.annaschneider.minecraft1.video.FlowStage;
import com.annaschneider.minecraft1.video.FlowStatus;
import com.annaschneider.minecraft1.video.Storyboard;
import com.annaschneider.minecraft1.video.VideoExportException;
import com.annaschneider.minecraft1.video.render.RenderOptions;
import com.annaschneider.minecraft1.video.story.PromptAnalysis;
import com.annaschneider.minecraft1.video.story.StoryRequest;
import com.annaschneider.minecraft1.video.story.StoryResult;
import com.annaschneider.minecraft1.video.voice.NarrationClip;
import com.annaschneider.minecraft1.video.voice.NarrationException;
import com.annaschneider.minecraft1.video.voice.VoiceDiscovery;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import com.annaschneider.minecraft1.video.voice.ClonedVoiceProfiles;
import com.annaschneider.minecraft1.video.voice.XttsTtsEngine;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Video Studio: 1) type a prompt, 2) add recorded footage, 3) pick a voice, then write the story and export a narrated
 * MP4. Or 5) pick a mode (Record only, Narrate only, Auto-export when both complete) and click Start: the screen is
 * recorded while the narration is generated, and the final video is exported automatically. Runs entirely on this
 * computer and is independent of the Minecraft connection. Long work runs on a background thread; the window stays
 * responsive.
 */
final class VideoStudioWindow extends JFrame {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final VideoStudio studio;
    private final Supplier<BuildContext> buildContext;
    private final Supplier<String> buildDescription;
    private final Path replayVideos;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "video-studio");
        thread.setDaemon(true);
        return thread;
    });

    private final JTextArea promptArea = new JTextArea(4, 30);
    private final JSpinner lengthSpinner = new JSpinner(new SpinnerNumberModel(60, (int) PromptAnalysis.MIN_SECONDS,
        (int) PromptAnalysis.MAX_SECONDS, 5));
    private final JCheckBox localAiBox = new JCheckBox("Use local AI (Ollama) to write the story");
    private final DefaultListModel<Path> footageModel = new DefaultListModel<>();
    private final JList<Path> footageList = new JList<>(footageModel);
    private final JLabel timelineLabel = new JLabel(" ");
    private final JComboBox<Object> voiceBox = new JComboBox<>();
    private final JComboBox<String> voiceMode = new JComboBox<>(new String[] {
        "Built-in voice", "Custom voice pack", "Clone from sample audio"
    });
    private final JLabel voiceInfo = new JLabel(" ");
    private final JTextArea storyArea = new JTextArea(14, 40);
    private final JButton writeButton = new JButton("Write story");
    private final JButton exportButton = new JButton("Export video");
    private final JButton previewButton = new JButton("Preview voice");
    private final JProgressBar progress = new JProgressBar();
    private final JTextArea logArea = new JTextArea(7, 60);
    private final List<JComponent> busyDisabled = new ArrayList<>();
    private final JComboBox<ExportMode> modeBox = new JComboBox<>(ExportMode.values());
    private final JSpinner recordSpinner = new JSpinner(new SpinnerNumberModel(VideoStudioSettings.DEFAULT_RECORD_SECONDS,
        VideoStudioSettings.MIN_RECORD_SECONDS, (int) com.annaschneider.minecraft1.video.ExportFlow.MAX_RECORD_SECONDS, 5));
    private final JButton startButton = new JButton("Start");
    private final JButton cancelButton = new JButton("Cancel");
    private final JLabel flowLabel = new JLabel(FlowStatus.idle().label("en"));
    private final JLabel recordingLine = new JLabel(" ");
    private final JLabel narrationLine = new JLabel(" ");
    private final JLabel outputLabel = new JLabel(" ");

    private VoiceDiscovery voices;
    private Storyboard storyboard;
    private boolean busy;
    private Path lastExport;
    private Future<?> flowTask;
    private boolean updatingVoiceSelection;
    private VoiceManagerWindow voiceManager;

    VideoStudioWindow(VideoStudio studio, Supplier<BuildContext> buildContext, Supplier<String> buildDescription, Path replayVideos) {
        super("Minecraft Architect - Video Studio");
        this.studio = studio;
        this.buildContext = buildContext;
        this.buildDescription = buildDescription;
        this.replayVideos = replayVideos;
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setMinimumSize(new Dimension(900, 620));
        setSize(1080, 720);
        setLocationRelativeTo(null);
        setContentPane(buildContent());
        localAiBox.setSelected(studio.settings().useLocalAi());
        modeBox.setSelectedItem(studio.settings().exportMode());
        recordSpinner.setValue(studio.settings().recordSeconds());
        refreshVoices();
    }

    void showStudio() {
        timelineLabel.setText(buildDescription.get());
        setVisible(true);
        toFront();
    }

    // ------------------------------------------------------------------ layout

    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JLabel intro = new JLabel("<html>Turn a build into a narrated video: describe the video, add recorded footage, pick a "
            + "voice, then <b>Write story</b> and <b>Export video</b> - or pick a mode in step 5 and click <b>Start</b> to record "
            + "and narrate at the same time and export automatically. Everything runs on this computer.</html>");
        root.add(intro, BorderLayout.NORTH);

        JSplitPane columns = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildInputs(), buildStoryPanel());
        columns.setResizeWeight(0.45);
        columns.setBorder(null);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Studio log"));
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        progress.setStringPainted(true);
        progress.setString("Ready");
        bottom.add(progress, BorderLayout.NORTH);
        bottom.add(logScroll, BorderLayout.CENTER);

        JSplitPane rows = new JSplitPane(JSplitPane.VERTICAL_SPLIT, columns, bottom);
        rows.setResizeWeight(0.75);
        rows.setBorder(null);
        root.add(rows, BorderLayout.CENTER);
        return root;
    }

    private JComponent buildInputs() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 4, 0);

        c.gridy = 0;
        panel.add(title("1. Describe the video"), c);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setText("60 second video: a kingdom rises from a deserted island");
        promptArea.setToolTipText("<html>Say what the video should tell. A length such as <i>45 seconds</i> or <i>2 minutes</i> "
            + "in the text wins over the Length box;<br>words like <i>timelapse</i> or <i>fast</i> give more sped-up footage. "
            + "Vietnamese prompts get a Vietnamese story.</html>");
        c.gridy = 1;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 0.4;
        panel.add(new JScrollPane(promptArea), c);

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        options.add(new JLabel("Length (seconds):"));
        options.add(lengthSpinner);
        localAiBox.setToolTipText("Needs Ollama running on this computer (Tools...). If it is not running, the built-in story "
            + "templates are used.");
        localAiBox.addActionListener(event -> studio.settings().setUseLocalAi(localAiBox.isSelected()));
        options.add(localAiBox);
        c.gridy = 2;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(options, c);

        c.gridy = 3;
        c.insets = new Insets(10, 0, 4, 0);
        panel.add(title("2. Footage (played in this order)"), c);
        footageList.setVisibleRowCount(4);
        footageList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                Component label = super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Path path) {
                    setText(path.getFileName().toString());
                    setToolTipText(path.toString());
                }
                return label;
            }
        });
        c.gridy = 4;
        c.insets = new Insets(0, 0, 4, 0);
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 0.4;
        panel.add(new JScrollPane(footageList), c);
        JPanel footageButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton add = new JButton("Add videos...");
        add.setToolTipText("Recorded videos (MP4, MKV, MOV, WEBM...), e.g. from OBS.");
        add.addActionListener(event -> addFootage(null));
        JButton replay = new JButton("ReplayMod videos...");
        replay.setToolTipText("Videos rendered by ReplayMod (Replay Viewer > Render): " + replayVideos);
        replay.addActionListener(event -> addFootage(replayVideos));
        JButton remove = new JButton("Remove");
        remove.addActionListener(event -> footageList.getSelectedValuesList().forEach(footageModel::removeElement));
        footageButtons.add(add);
        footageButtons.add(replay);
        footageButtons.add(remove);
        busyDisabled.addAll(List.of(add, replay, remove));
        c.gridy = 5;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(footageButtons, c);
        timelineLabel.setForeground(Color.GRAY);
        c.gridy = 6;
        panel.add(timelineLabel, c);

        c.gridy = 7;
        c.insets = new Insets(10, 0, 4, 0);
        panel.add(title("3. Voice"), c);
        voiceBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                Component label = super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof VoicePack voice) {
                    setText(voice.label());
                }
                return label;
            }
        });
        voiceBox.addActionListener(event -> {
            if (updatingVoiceSelection) {
                return;
            }
            VoicePack voice = selectedVoice();
            studio.settings().setVoiceId(voice == null ? "" : voice.id());
            updateVoiceInfo();
        });
        voiceMode.setToolTipText("Built-in/custom modes use installed Piper packs; clone mode uses local sample profiles.");
        voiceMode.addActionListener(event -> updateVoiceList(studio.settings().voiceId()));
        c.gridy = 8;
        c.insets = new Insets(0, 0, 4, 0);
        panel.add(voiceBox, c);
        JPanel voiceButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        previewButton.setToolTipText("Preview voice / Nghe thử voice: speak a short sample with the selected voice.");
        previewButton.addActionListener(event -> previewVoice());
        JButton refresh = new JButton("Refresh");
        refresh.setToolTipText("Look for new voice files in the voices folder.");
        refresh.addActionListener(event -> refreshVoices());
        JButton openVoices = new JButton("Open voices folder");
        openVoices.setToolTipText("Copy Piper voices (.onnx + .onnx.json) here, then click Refresh.");
        openVoices.addActionListener(event -> openFolder(studio.settings().voicesFolder(), true));
        voiceButtons.add(previewButton);
        voiceButtons.add(refresh);
        voiceButtons.add(openVoices);
        JButton manage = new JButton("Manage voices / Quản lý giọng...");
        manage.setToolTipText("Browse the offline catalog; filter, favorite, preview or explicitly install voices.");
        manage.addActionListener(event -> openVoiceManager());
        JPanel cloningButtons = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        cloningButtons.add(manage);
        JButton clone = new JButton("Clone voice from sample... / Clone voice từ sample...");
        clone.addActionListener(event -> cloneVoice());
        JButton recordSample = new JButton("Record sample / Ghi âm mẫu");
        recordSample.setEnabled(false);
        recordSample.setToolTipText("Microphone recording is not available in this UI. Upload a WAV sample instead.");
        cloningButtons.add(voiceMode);
        cloningButtons.add(clone);
        JPanel voiceControls = new JPanel(new BorderLayout(0, 4));
        JPanel cloneControls = new JPanel(new BorderLayout(0, 4));
        cloneControls.add(cloningButtons, BorderLayout.NORTH);
        cloneControls.add(recordSample, BorderLayout.SOUTH);
        voiceControls.add(voiceButtons, BorderLayout.NORTH);
        voiceControls.add(cloneControls, BorderLayout.SOUTH);
        busyDisabled.addAll(List.of(refresh, voiceBox, voiceMode, clone));
        c.gridy = 9;
        panel.add(voiceControls, c);
        voiceInfo.setForeground(Color.GRAY);
        c.gridy = 10;
        panel.add(voiceInfo, c);
        return panel;
    }

    private JComponent buildStoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        panel.add(title("4. Story and export"), BorderLayout.NORTH);
        storyArea.setEditable(false);
        storyArea.setLineWrap(true);
        storyArea.setWrapStyleWord(true);
        storyArea.setText("Click \"Write story\" to turn your prompt into scenes and narration.");
        panel.add(new JScrollPane(storyArea), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        writeButton.addActionListener(event -> writeStory(null));
        exportButton.setFont(exportButton.getFont().deriveFont(Font.BOLD));
        exportButton.setToolTipText("Narrate (if a voice is chosen), cut the footage and save an MP4 to the output folder.");
        exportButton.addActionListener(event -> export());
        JButton openOutput = new JButton("Open output folder");
        openOutput.addActionListener(event -> openFolder(lastExport != null ? lastExport.getParent() : studio.settings().outputFolder(), true));
        JButton tools = new JButton("Tools...");
        tools.setToolTipText("FFmpeg, Piper, folders and local AI settings; shows what is installed.");
        tools.addActionListener(event -> openTools());
        JButton help = new JButton("Help");
        help.addActionListener(event -> showHelp());
        buttons.add(writeButton);
        buttons.add(exportButton);
        buttons.add(openOutput);
        buttons.add(tools);
        buttons.add(help);
        busyDisabled.addAll(List.of(writeButton, exportButton, previewButton, tools));
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(buttons);
        JComponent flow = buildFlowPanel();
        flow.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(flow);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildFlowPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        JLabel heading = title("5. Record, narrate and export automatically");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        controls.add(new JLabel("Mode:"));
        modeBox.setToolTipText("<html><b>Record only</b>: record the screen and save the raw video.<br><b>Narrate only</b>: "
            + "make the narration audio (WAV), script and subtitles.<br><b>Auto-export when both complete</b>: record and "
            + "narrate at the same time, then mux and export the final MP4 automatically.</html>");
        modeBox.addActionListener(event -> {
            ExportMode mode = (ExportMode) modeBox.getSelectedItem();
            studio.settings().setExportMode(mode);
            recordSpinner.setEnabled(!busy && mode != null && mode.records());
        });
        controls.add(modeBox);
        controls.add(new JLabel("Record (seconds):"));
        recordSpinner.setToolTipText("How long the screen is recorded. Show the Minecraft window while recording.");
        recordSpinner.addChangeListener(event -> studio.settings().setRecordSeconds(((Number) recordSpinner.getValue()).intValue()));
        controls.add(recordSpinner);
        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD));
        startButton.setToolTipText("Run the selected mode. Writes the story first if needed.");
        startButton.addActionListener(event -> startFlow());
        cancelButton.setToolTipText("Stop recording and narration; finished files are kept.");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(event -> cancelFlow());
        controls.add(startButton);
        controls.add(cancelButton);
        busyDisabled.addAll(List.of(modeBox, recordSpinner, startButton));
        flowLabel.setFont(flowLabel.getFont().deriveFont(Font.BOLD));
        recordingLine.setForeground(Color.GRAY);
        narrationLine.setForeground(Color.GRAY);
        outputLabel.setForeground(Color.GRAY);
        for (JComponent component : List.of(heading, controls, flowLabel, recordingLine, narrationLine, outputLabel)) {
            component.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(component);
        }
        return panel;
    }

    // ------------------------------------------------------------------ actions

    private void refreshVoices() {
        runInBackground("Looking for voices...", () -> {
            VoiceDiscovery found = studio.discoverVoices();
            List<String> diagnostics = studio.diagnostics(found);
            SwingUtilities.invokeLater(() -> {
                acceptVoices(found);
                diagnostics.forEach(this::log);
                if (found.voices().isEmpty()) {
                    log("No voices yet: copy a Piper voice (.onnx + .onnx.json) into " + found.folder() + " and click Refresh.");
                }
            });
        });
    }

    private void updateVoiceList(String selectedId) {
        if (voices == null) {
            return;
        }
        boolean cloned = voiceMode.getSelectedIndex() == 2;
        updatingVoiceSelection = true;
        try {
            voiceBox.setModel(VoiceSelection.model(voices.voices(), selectedId, cloned));
        } finally {
            updatingVoiceSelection = false;
        }
        updateVoiceInfo();
    }

    private void updateVoiceInfo() {
        VoicePack voice = selectedVoice();
        String saved = studio.settings().voiceId();
        voiceInfo.setText(voice != null ? voice.id() + (voice.description().isBlank() ? "" : " - " + voice.description())
            : saved.isBlank() ? "The video is exported without narration."
            : "Saved voice unavailable here; choose explicitly (no substitute).");
        voiceInfo.setToolTipText(voice == null && !saved.isBlank() ? "Saved exact voice ID: " + saved : voiceInfo.getText());
    }

    private void acceptVoices(VoiceDiscovery found) {
        voices = found;
        String savedId = studio.settings().voiceId();
        found.find(savedId).ifPresent(voice ->
            voiceMode.setSelectedIndex(XttsTtsEngine.ID.equals(voice.engine()) ? 2 : 0));
        updateVoiceList(savedId);
    }

    private void openVoiceManager() {
        if (voiceManager == null || !voiceManager.isDisplayable()) {
            voiceManager = new VoiceManagerWindow(this, studio, this::acceptVoices, voice -> {
                if (busy) {
                    error("Please wait for the current studio task before changing the narration voice.");
                    return;
                }
                studio.settings().setVoiceId(voice.id());
                voiceMode.setSelectedIndex(XttsTtsEngine.ID.equals(voice.engine()) ? 2 : 0);
                updateVoiceList(voice.id());
                log("Selected exact voice: " + voice.id());
            }, this::previewVoice);
        }
        voiceManager.setVisible(true);
        voiceManager.toFront();
    }

    private void cloneVoice() {
        var unavailable = studio.cloningEngine().unavailableReason();
        if (unavailable.isPresent()) {
            error(unavailable.get());
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Upload sample audio / Tải file audio mẫu");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PCM WAV sample (6–60 seconds, max 20 MB)", "wav"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String name = JOptionPane.showInputDialog(this,
            "Voice profile name (English narration). Use only audio you have permission to clone.",
            "Clone voice from sample... / Clone voice từ sample...", JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        Path sample = chooser.getSelectedFile().toPath();
        runInBackground(ClonedVoiceProfiles.CHECKING, () -> {
            try {
                VoicePack profile = studio.clonedVoices().create(studio.settings().voicesFolder(), sample, name,
                    message -> SwingUtilities.invokeLater(() -> {
                        voiceInfo.setText(message);
                        log(message);
                    }));
                VoiceDiscovery found = studio.discoverVoices();
                SwingUtilities.invokeLater(() -> {
                    voices = found;
                    voiceMode.setSelectedIndex(2);
                    updateVoiceList(profile.id());
                    studio.settings().setVoiceId(profile.id());
                    voiceInfo.setText(ClonedVoiceProfiles.READY);
                });
            } catch (NarrationException ex) {
                SwingUtilities.invokeLater(() -> {
                    voiceInfo.setText(ClonedVoiceProfiles.FAILED + ex.getMessage());
                    error(ClonedVoiceProfiles.FAILED + ex.getMessage());
                });
            }
        });
    }

    private void writeStory(Runnable then) {
        String prompt = promptArea.getText().strip();
        if (prompt.length() > PromptAnalysis.MAX_PROMPT_LENGTH) {
            error("The prompt is too long (max " + PromptAnalysis.MAX_PROMPT_LENGTH + " characters).");
            return;
        }
        PromptAnalysis analysis = PromptAnalysis.of(prompt);
        Double length = analysis.lengthGiven() ? null : ((Number) lengthSpinner.getValue()).doubleValue();
        VoicePack voice = selectedVoice();
        BuildContext context = buildContext.get();
        timelineLabel.setText(buildDescription.get());
        runInBackground("Writing the story...", () -> {
            StoryResult result;
            try {
                result = studio.pipeline().writeStory(new StoryRequest(prompt, context, length, voice == null ? null : voice.storyLanguage()));
            } catch (IllegalArgumentException ex) {
                SwingUtilities.invokeLater(() -> error(ex.getMessage()));
                return;
            }
            StoryResult story = result;
            SwingUtilities.invokeLater(() -> {
                storyboard = story.storyboard();
                storyArea.setText(storyboard.describe());
                storyArea.setCaretPosition(0);
                story.warnings().forEach(warning -> log("Note: " + warning));
                log("Story written by " + storyboard.generator() + ": " + storyboard.scenes().size() + " scenes, about "
                    + Math.round(storyboard.totalSeconds()) + " s.");
                if (then != null) {
                    // queued behind the end of this background task, so the follow-up is not refused as "busy"
                    SwingUtilities.invokeLater(then);
                }
            });
        });
    }

    private void export() {
        if (storyboard == null || !storyboard.prompt().equals(promptArea.getText().strip())) {
            writeStory(this::export);
            return;
        }
        VoicePack voice = selectedVoice();
        if (voice != null && !voice.storyLanguage().equals(storyboard.language())) {
            error(voiceMismatch(voice));
            return;
        }
        if (voice == null && !studio.settings().voiceId().isBlank()) {
            error("The saved voice is unavailable. Use Manage voices to choose the exact speaker, or explicitly choose No narration.");
            return;
        }
        List<Path> footage = new ArrayList<>();
        for (int i = 0; i < footageModel.size(); i++) {
            footage.add(footageModel.get(i));
        }
        ExportRequest request = new ExportRequest(storyboard, buildContext.get(), footage, voice, studio.settings().outputFolder(),
            null, RenderOptions.hd720(), false);
        runInBackground("Exporting video...", () -> {
            try {
                ExportResult result = studio.pipeline().export(request, message -> SwingUtilities.invokeLater(() -> {
                    progress.setString(message);
                    log(message);
                }));
                SwingUtilities.invokeLater(() -> {
                    lastExport = result.video();
                    result.warnings().forEach(warning -> log("Note: " + warning));
                    log("Saved " + result.video() + (result.narrated() ? " (narrated)" : " (no narration)"));
                    if (result.subtitles() != null) {
                        log("Subtitles: " + result.subtitles());
                    }
                    log("Script: " + result.script());
                    JOptionPane.showMessageDialog(this, "Video saved:\n" + result.video()
                        + (result.warnings().isEmpty() ? "" : "\n\n" + String.join("\n", result.warnings())),
                        "Export finished", result.warnings().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                });
            } catch (VideoExportException | IllegalArgumentException ex) {
                SwingUtilities.invokeLater(() -> error("Export failed: " + ex.getMessage()));
            }
        });
    }

    private void startFlow() {
        if (storyboard == null || !storyboard.prompt().equals(promptArea.getText().strip())) {
            writeStory(this::startFlow);
            return;
        }
        ExportMode mode = modeBox.getSelectedItem() instanceof ExportMode chosen ? chosen : ExportMode.AUTO_EXPORT;
        VoicePack voice = selectedVoice();
        if (mode.narrates() && voice != null && !voice.storyLanguage().equals(storyboard.language())) {
            error(voiceMismatch(voice));
            return;
        }
        if (mode.narrates() && voice == null && !studio.settings().voiceId().isBlank()) {
            error("The saved voice is unavailable. Use Manage voices to choose the exact speaker before narrating.");
            return;
        }
        BuildContext built = buildContext.get();
        // fresh footage: milestones of an earlier recording do not line up with it, so scenes are spread evenly
        BuildContext context = new BuildContext(built.buildName(), List.of(), built.sections(), built.blocks());
        FlowRequest request = new FlowRequest(mode, storyboard, context, voice, ((Number) recordSpinner.getValue()).doubleValue(),
            studio.settings().outputFolder(), null, RenderOptions.hd720());
        String language = storyboard.language();
        outputLabel.setText(" ");
        Future<?> task = runInBackground(mode.label() + "...", () -> {
            FlowResult result = studio.flow().run(request, new FlowListener() {
                @Override
                public void status(FlowStatus status) {
                    SwingUtilities.invokeLater(() -> showFlowStatus(status, language));
                }

                @Override
                public void progress(String message) {
                    SwingUtilities.invokeLater(() -> {
                        progress.setString(message);
                        log(message);
                    });
                }
            });
            SwingUtilities.invokeLater(() -> finishFlow(result, language));
        });
        if (task != null) {
            flowTask = task;
            cancelButton.setEnabled(true);
        }
    }

    private void cancelFlow() {
        if (flowTask != null) {
            log("Cancelling...");
            flowTask.cancel(true);
        }
    }

    private void showFlowStatus(FlowStatus status, String language) {
        flowLabel.setText(status.label(language));
        flowLabel.setForeground(status.stage() == FlowStage.FAILED ? new Color(0xb00020)
            : status.stage() == FlowStage.COMPLETE ? new Color(0x1b7f3a) : Color.BLACK);
        recordingLine.setText(status.recordingLine(language));
        narrationLine.setText(status.narrationLine(language));
    }

    private void finishFlow(FlowResult result, String language) {
        result.warnings().forEach(warning -> log("Note: " + warning));
        for (Path file : new Path[] {result.recording(), result.narration(), result.script(), result.subtitles()}) {
            if (file != null) {
                log("Saved " + file);
            }
        }
        if (result.ok()) {
            lastExport = result.output();
            outputLabel.setText("Output: " + result.output());
            outputLabel.setToolTipText(result.output().toString());
            log(FlowStage.COMPLETE.label(language) + ": " + result.output());
            String[] options = {result.mode() == ExportMode.NARRATE_ONLY ? "Open audio" : "Open video", "Open folder", "Close"};
            int choice = JOptionPane.showOptionDialog(this, FlowStage.COMPLETE.label(language) + "\n" + result.output()
                + (result.warnings().isEmpty() ? "" : "\n\n" + String.join("\n", result.warnings())), result.mode().label(),
                JOptionPane.DEFAULT_OPTION, result.warnings().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
            if (choice == 0) {
                openFile(result.output());
            } else if (choice == 1) {
                openFolder(result.output().getParent(), false);
            }
        } else if (result.stage() == FlowStage.FAILED && result.failure() != null) {
            String message = FlowStage.FAILED.label(language) + result.failure().reason(language);
            log("Problem: " + FlowStage.FAILED.label(language) + result.failure().describe(language));
            boolean kept = result.recording() != null || result.narration() != null;
            outputLabel.setText(kept ? "Finished files were kept in " + studio.settings().outputFolder() : " ");
            String[] options = {"Retry", "Close"};
            int choice = JOptionPane.showOptionDialog(this, message + "\n\n" + result.failure().detail()
                + (kept ? "\n\nFinished files were kept in the output folder." : ""), "Video Studio",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
            if (choice == 0) {
                // queued after the end of the finished background task, so the retry is not refused as "busy"
                SwingUtilities.invokeLater(this::startFlow);
            }
        } else {
            log(FlowStage.CANCELLED.label(language));
        }
    }

    private void openFile(Path file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.toFile());
            } else {
                log("File: " + file);
            }
        } catch (IOException | UnsupportedOperationException | SecurityException | IllegalArgumentException ex) {
            error("Cannot open " + file + ": " + ex.getMessage());
        }
    }

    private void previewVoice() {
        VoicePack voice = selectedVoice();
        if (voice == null) {
            error("Choose a voice first (copy voices into the voices folder, then click Refresh).");
            return;
        }
        previewVoice(voice);
    }

    private String voiceMismatch(VoicePack voice) {
        return "The story is in '" + storyboard.language() + "', but the selected voice speaks '" + voice.language()
            + "'. Choose a matching installed voice in Manage voices or rewrite the story for this voice. "
            + "Local XTTS cloning does not support Vietnamese.";
    }

    private void previewVoice(VoicePack voice) {
        String language = voice.storyLanguage();
        runInBackground("Speaking a sample with " + voice.name() + "...", () -> {
            Path wav = null;
            try {
                Path previewFolder = studio.settings().voicesFolder().resolve(".previews");
                Files.createDirectories(previewFolder);
                wav = Files.createTempFile(previewFolder, "architect-voice-preview-", ".wav");
                NarrationClip clip = studio.narrator().preview(voice, com.annaschneider.minecraft1.video.voice.Narrator.previewText(language), wav);
                play(wav);
                wav = null; // deleted after playback
                SwingUtilities.invokeLater(() -> log(String.format(java.util.Locale.ROOT, "Playing %s (%.1f s).", voice.name(), clip.seconds())));
            } catch (NarrationException | IOException ex) {
                SwingUtilities.invokeLater(() -> error("Voice preview failed: " + ex.getMessage()));
            } finally {
                if (wav != null) {
                    try {
                        Files.deleteIfExists(wav);
                    } catch (IOException ignored) {
                        // temp file
                    }
                }
            }
        });
    }

    /** Plays a WAV without blocking and deletes it afterwards. */
    private static void play(Path wav) throws IOException {
        try {
            AudioInputStream stream = AudioSystem.getAudioInputStream(wav.toFile());
            Clip clip = AudioSystem.getClip();
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                    try {
                        stream.close();
                        Files.deleteIfExists(wav);
                    } catch (IOException ignored) {
                        // temp file
                    }
                }
            });
            clip.open(stream);
            clip.start();
        } catch (javax.sound.sampled.UnsupportedAudioFileException | javax.sound.sampled.LineUnavailableException
                 | IllegalArgumentException ex) {
            Files.deleteIfExists(wav);
            throw new IOException("cannot play audio on this computer (" + ex.getMessage() + ")", ex);
        }
    }

    private void addFootage(Path startFolder) {
        File start = startFolder != null && Files.isDirectory(startFolder) ? startFolder.toFile() : null;
        if (startFolder != null && start == null) {
            log("ReplayMod video folder not found yet: " + startFolder + " (render a replay in ReplayMod first).");
        }
        JFileChooser chooser = new JFileChooser(start);
        chooser.setDialogTitle("Add recorded videos");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("Videos (mp4, mkv, mov, webm, avi)", "mp4", "mkv", "mov", "webm", "avi", "m4v"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File file : chooser.getSelectedFiles()) {
                Path path = file.toPath().toAbsolutePath();
                if (!footageModel.contains(path)) {
                    footageModel.addElement(path);
                }
            }
        }
    }

    private void openTools() {
        VideoStudioSettings settings = studio.settings();
        JTextField ffmpeg = new JTextField(settings.ffmpegPath(), 32);
        JTextField piper = new JTextField(settings.piperPath(), 32);
        JTextField cloning = new JTextField(settings.cloningPath(), 32);
        JTextField cloningModel = new JTextField(settings.cloningModelFolder(), 32);
        JTextField voicesFolder = new JTextField(settings.voicesFolder().toString(), 32);
        JTextField output = new JTextField(settings.outputFolder().toString(), 32);
        JTextField ollamaUrl = new JTextField(settings.ollamaUrl(), 32);
        JTextField ollamaModel = new JTextField(settings.ollamaModel(), 32);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        Object[][] rows = {
            {"FFmpeg (ffmpeg.exe, blank = search PATH):", ffmpeg, JFileChooser.FILES_AND_DIRECTORIES},
            {"Piper (piper.exe, blank = search):", piper, JFileChooser.FILES_AND_DIRECTORIES},
            {"Voice cloning (Coqui tts, blank = search):", cloning, JFileChooser.FILES_AND_DIRECTORIES},
            {"Local XTTS v2 model folder:", cloningModel, JFileChooser.DIRECTORIES_ONLY},
            {"Voices folder:", voicesFolder, JFileChooser.DIRECTORIES_ONLY},
            {"Output folder:", output, JFileChooser.DIRECTORIES_ONLY},
            {"Local AI address (Ollama):", ollamaUrl, null},
            {"Local AI model:", ollamaModel, null},
        };
        for (int i = 0; i < rows.length; i++) {
            c.gridy = i;
            c.gridx = 0;
            form.add(new JLabel((String) rows[i][0]), c);
            c.gridx = 1;
            JTextField field = (JTextField) rows[i][1];
            form.add(field, c);
            if (rows[i][2] != null) {
                int mode = (Integer) rows[i][2];
                JButton browse = new JButton("Browse...");
                browse.addActionListener(event -> {
                    JFileChooser chooser = new JFileChooser(field.getText().isBlank() ? null : new File(field.getText()));
                    chooser.setFileSelectionMode(mode);
                    if (chooser.showOpenDialog(form) == JFileChooser.APPROVE_OPTION) {
                        field.setText(chooser.getSelectedFile().getAbsolutePath());
                    }
                });
                c.gridx = 2;
                form.add(browse, c);
            }
        }
        JTextArea status = new JTextArea(String.join("\n", studio.diagnostics(voices != null ? voices : studio.discoverVoices())), 7, 60);
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(form, BorderLayout.NORTH);
        JScrollPane statusScroll = new JScrollPane(status);
        statusScroll.setBorder(BorderFactory.createTitledBorder("Currently found"));
        content.add(statusScroll, BorderLayout.CENTER);
        int answer = JOptionPane.showConfirmDialog(this, content, "Video Studio tools", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            com.annaschneider.minecraft1.video.story.OllamaClient.validateLocalUrl(
                ollamaUrl.getText().isBlank() ? com.annaschneider.minecraft1.video.story.OllamaClient.DEFAULT_URL : ollamaUrl.getText().strip());
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
            return;
        }
        settings.setFfmpegPath(ffmpeg.getText());
        settings.setPiperPath(piper.getText());
        settings.setCloningPath(cloning.getText());
        settings.setCloningModelFolder(cloningModel.getText());
        settings.setVoicesFolder(voicesFolder.getText());
        settings.setOutputFolder(output.getText());
        settings.setOllamaUrl(ollamaUrl.getText());
        settings.setOllamaModel(ollamaModel.getText());
        log("Tools saved.");
        refreshVoices();
    }

    private void showHelp() {
        JOptionPane.showMessageDialog(this, "<html><body style='width: 480px'>" + helpHtml() + "</body></html>",
            "Video Studio help", JOptionPane.INFORMATION_MESSAGE);
    }

    static String helpHtml() {
        return "<h3>Make a narrated build video</h3><ol>"
            + "<li>Record the build: <b>Build + Record</b> in the main window (ReplayMod) or any screen recorder such as OBS. "
            + "In ReplayMod open the replay and use <i>Render</i> to save an MP4 into <i>.minecraft\\replay_videos</i>.</li>"
            + "<li>Describe the video, e.g. <i>60 second video: a kingdom rises from a deserted island</i>. "
            + "A length in the text (\"45 seconds\", \"2 phút\") wins over the Length box; <i>timelapse</i>/<i>fast</i> gives more "
            + "sped-up footage. Vietnamese text gives a Vietnamese story.</li>"
            + "<li>Add the footage (<b>ReplayMod videos...</b> or <b>Add videos...</b>). Without footage the video uses title cards.</li>"
            + "<li>Pick a voice and click <b>Preview voice</b>.</li>"
            + "<li><b>Write story</b>, then <b>Export video</b>. The MP4, subtitles (.srt) and script are saved in the output folder.</li></ol>"
            + "<p>Scenes follow the progress of the last build made with this app (start, 25 %, 75 %, finish). "
            + "Without it, scenes are spread evenly over the footage.</p>"
            + "<h3>Automatic recording and narration (step 5)</h3><p>Pick a <b>Mode</b> and click <b>Start</b>:</p><ul>"
            + "<li><b>Record only</b>: records the screen for <i>Record (seconds)</i> and saves the raw video "
            + "(<i>&lt;name&gt;-recording.mp4</i>).</li>"
            + "<li><b>Narrate only</b>: speaks the story with the selected voice and saves <i>&lt;name&gt;-narration.wav</i>, "
            + "the script and subtitles. No recording; FFmpeg is not needed.</li>"
            + "<li><b>Auto-export when both complete</b>: records the screen and generates the narration at the same time. "
            + "When both are finished the narration is muxed into the recording and the MP4 is exported automatically.</li></ul>"
            + "<p>Status: <i>Checking dependencies... &rarr; Recording video... + Generating narration... &rarr; Waiting for "
            + "remaining job... &rarr; Muxing audio and video... &rarr; Export complete</i> (or <i>Export failed: reason</i>; "
            + "Vietnamese stories show Vietnamese labels). Keep the Minecraft window visible while recording (Windows: whole "
            + "desktop, macOS: screen 0, Linux: X11 display). If a job fails, nothing is exported, the finished recording or "
            + "narration stays in the output folder, and <b>Retry</b> starts again. <b>Cancel</b> stops both jobs.</p>"
            + "<h3>Voices</h3><p>Download a Piper voice (both the <i>.onnx</i> and <i>.onnx.json</i> file, e.g. "
            + "<i>vi_VN-vais1000-medium</i> or <i>en_US-amy-medium</i>) from huggingface.co/rhasspy/piper-voices, copy them into "
            + "the voices folder (<b>Open voices folder</b>) and click <b>Refresh</b>. An optional <i>&lt;name&gt;.voice.json</i> "
            + "sets <i>name</i>, <i>language</i> and <i>description</i>. Invalid files are listed in the log with the reason.</p>"
            + "<p><b>Manage voices / Quản lý giọng...</b> opens a searchable offline catalog with language, source, installation and favorite "
            + "filters. Refresh reads only bundled metadata and installed files. Preview and Use this voice require installed "
            + "voices. Install / Retry downloads a shared model only after explicit license acceptance; Cancel interrupts "
            + "the download. Catalog counts describe verified model-local IDs, not independently deduplicated people; "
            + "cloned profiles and presets do not count as model inventory. Missing saved "
            + "speakers are never silently replaced; choose a voice explicitly. Story/voice language mismatches block narration.</p>"
            + "<h3>Clone voice from sample...</h3><p>Choose a local Coqui <i>tts</i> executable and a preinstalled XTTS v2 "
            + "model folder (model.pth, config.json, vocab.json) in <b>Tools...</b>. Click <b>Clone voice from sample...</b>, "
            + "upload a 6–60 second, 16-bit PCM WAV (mono/stereo, 8–96 kHz, max 20 MB), and name the profile. "
            + "Only use audio you have permission to clone. A successful synthesis check saves a reusable local profile "
            + "in the voices folder. Select it in clone mode and click <b>Preview voice</b> before export. "
            + "This backend supports English narration, not Vietnamese. Microphone recording is unavailable; use upload. "
            + "If cloning fails, existing Piper packs and export without narration remain available.</p>"
            + "<h3>Optional programs</h3><ul>"
            + "<li><b>FFmpeg</b> (required to export): install it and add it to PATH, or choose ffmpeg.exe in <b>Tools...</b>.</li>"
            + "<li><b>Piper</b> (narration): unzip the Piper release and choose piper.exe in <b>Tools...</b>. Without it the video "
            + "is exported without narration.</li>"
            + "<li><b>Ollama</b> (optional AI story writer): install, run <i>ollama pull llama3.2</i> and tick <b>Use local AI</b>. "
            + "If it is not running, the built-in templates are used.</li></ul>"
            + "<p>Nothing is uploaded: the prompt, voices and videos stay on this computer.</p>";
    }

    private void openFolder(Path folder, boolean create) {
        try {
            if (create) {
                Files.createDirectories(folder);
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(folder.toFile());
            } else {
                log("Folder: " + folder);
            }
        } catch (IOException | UnsupportedOperationException | SecurityException ex) {
            error("Cannot open " + folder + ": " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers

    private VoicePack selectedVoice() {
        Object selected = voiceBox.getSelectedItem();
        return selected instanceof VoicePack voice ? voice : null;
    }

    /** @return the running task (cancel it to interrupt the work), or {@code null} when another task is still running */
    private Future<?> runInBackground(String message, Runnable task) {
        if (busy) {
            log("Please wait - still working.");
            return null;
        }
        setBusy(true, message);
        return worker.submit(() -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                SwingUtilities.invokeLater(() -> error("Unexpected problem: " + ex));
            } finally {
                SwingUtilities.invokeLater(() -> setBusy(false, "Ready"));
            }
        });
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        progress.setIndeterminate(value);
        progress.setString(message);
        busyDisabled.forEach(component -> component.setEnabled(!value));
        if (!value) {
            ExportMode mode = (ExportMode) modeBox.getSelectedItem();
            recordSpinner.setEnabled(mode != null && mode.records());
            flowTask = null;
            cancelButton.setEnabled(false);
        }
        if (value) {
            log(message);
        }
    }

    private void error(String message) {
        log("Problem: " + message);
        JOptionPane.showMessageDialog(this, message, "Video Studio", JOptionPane.WARNING_MESSAGE);
    }

    private void log(String text) {
        logArea.append(LocalTime.now().format(TIME) + "  " + text + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() + 1f));
        return label;
    }

    @Override
    public void dispose() {
        if (voiceManager != null) {
            voiceManager.dispose();
        }
        worker.shutdownNow();
        super.dispose();
    }
}
