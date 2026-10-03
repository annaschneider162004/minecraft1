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
import com.annaschneider.minecraft1.video.voice.VoiceCatalog;
import com.annaschneider.minecraft1.video.voice.VoiceCatalogEntry;
import com.annaschneider.minecraft1.video.voice.VoiceDiscovery;
import com.annaschneider.minecraft1.video.voice.VoiceFilter;
import com.annaschneider.minecraft1.video.voice.VoiceInstallException;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import com.annaschneider.minecraft1.video.voice.VoiceSelection;
import com.annaschneider.minecraft1.video.voice.NarrationAudioOptions;
import com.annaschneider.minecraft1.video.voice.VoiceDropInput;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.AbstractListModel;
import javax.swing.DefaultComboBoxModel;
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
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
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
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final String NO_VOICE = "(Không thuyết minh)";
    private static final String ANY_LANGUAGE = "Mọi ngôn ngữ";
    private static final String ANY_ENGINE = "Mọi bộ đọc";

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
    private final JCheckBox localAiBox = new JCheckBox("Viết kịch bản bằng AI cục bộ (Ollama)");
    private final DefaultListModel<Path> footageModel = new DefaultListModel<>();
    private final JList<Path> footageList = new JList<>(footageModel);
    private final JLabel timelineLabel = new JLabel(" ");
    private final ExecutorService filterWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "voice-catalog-filter");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger filterGeneration = new AtomicInteger();
    private final EntryListModel voiceListModel = new EntryListModel();
    private final JList<Object> voiceList = new JList<>(voiceListModel);
    private final JTextField voiceSearch = new JTextField(14);
    private final JComboBox<String> languageBox = new JComboBox<>(new String[] {ANY_LANGUAGE});
    private final JComboBox<String> engineBox = new JComboBox<>(new String[] {ANY_ENGINE});
    private final JComboBox<VoiceFilter.State> stateBox = new JComboBox<>(VoiceFilter.State.values());
    private final JCheckBox multiSpeakerBox = new JCheckBox("Chỉ giọng nhiều người nói");
    private final JLabel catalogCount = new JLabel(" ");
    private final JButton installButton = new JButton("Cài giọng...");
    private final Timer searchDelay = new Timer(200, event -> refilter());
    private final JLabel voiceInfo = new JLabel(" ");
    private final JTextArea storyArea = new JTextArea(14, 40);
    private final JButton writeButton = new JButton("Viết kịch bản");
    private final JButton exportButton = new JButton("Xuất video");
    private final JButton previewButton = new JButton("Nghe thử giọng");
    private final JSpinner speedSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.5, 2.0, 0.1));
    private final JComboBox<NarrationAudioOptions.Effect> effectBox = new JComboBox<>(NarrationAudioOptions.Effect.values());
    private final JProgressBar progress = new JProgressBar();
    private final JTextArea logArea = new JTextArea(7, 60);
    private final List<JComponent> busyDisabled = new ArrayList<>();
    private final JComboBox<ExportMode> modeBox = new JComboBox<>(ExportMode.values());
    private final JSpinner recordSpinner = new JSpinner(new SpinnerNumberModel(VideoStudioSettings.DEFAULT_RECORD_SECONDS,
        VideoStudioSettings.MIN_RECORD_SECONDS, (int) com.annaschneider.minecraft1.video.ExportFlow.MAX_RECORD_SECONDS, 5));
    private final JButton startButton = new JButton("Bắt đầu");
    private final JButton cancelButton = new JButton("Hủy");
    private final JLabel flowLabel = new JLabel(FlowStatus.idle().label("vi"));
    private final JLabel recordingLine = new JLabel(" ");
    private final JLabel narrationLine = new JLabel(" ");
    private final JLabel outputLabel = new JLabel(" ");

    private VoiceDiscovery voices;
    private VoiceCatalog catalog;
    /** Catalog id of the chosen voice ("" = no narration); kept when filters hide it. */
    private String selectedId = "";
    private boolean updatingVoices;
    private boolean catalogInitialized;
    private Storyboard storyboard;
    private boolean busy;
    private Path lastExport;
    private Future<?> flowTask;

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
        speedSpinner.setValue(studio.settings().narrationSpeed());
        effectBox.setSelectedItem(studio.settings().narrationEffect());
        selectedId = studio.settings().voiceId();
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
        JLabel intro = new JLabel("<html>Tạo video thuyết minh công trình: mô tả video, thêm bản ghi, chọn giọng, "
            + "rồi <b>Viết kịch bản</b> và <b>Xuất video</b>. Hoặc chọn chế độ ở bước 5 và nhấn <b>Bắt đầu</b> để ghi hình, "
            + "thuyết minh đồng thời và tự xuất. Mọi xử lý diễn ra trên máy này.</html>");
        root.add(intro, BorderLayout.NORTH);

        JSplitPane columns = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildInputs(), buildStoryPanel());
        columns.setResizeWeight(0.45);
        columns.setBorder(null);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Nhật ký Studio"));
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        progress.setStringPainted(true);
        progress.setString("Sẵn sàng");
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
        panel.add(title("1. Mô tả video"), c);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setText("Video 60 giây: một vương quốc mọc lên từ hòn đảo hoang");
        promptArea.setToolTipText("<html>Mô tả câu chuyện của video. Thời lượng như <i>45 giây</i> hoặc <i>2 phút</i> "
            + "trong mô tả được ưu tiên hơn ô thời lượng.<br>Dùng <i>timelapse</i> hoặc <i>fast</i> để tăng cảnh tua nhanh. "
            + "Mô tả tiếng Việt tạo kịch bản tiếng Việt; giọng đã chọn quyết định ngôn ngữ thuyết minh.</html>");
        c.gridy = 1;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 0.4;
        panel.add(new JScrollPane(promptArea), c);

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        options.add(new JLabel("Thời lượng (giây):"));
        options.add(lengthSpinner);
        localAiBox.setToolTipText("Cần Ollama chạy trên máy này (Công cụ...). Nếu không chạy, dùng mẫu kịch bản có sẵn.");
        localAiBox.addActionListener(event -> studio.settings().setUseLocalAi(localAiBox.isSelected()));
        options.add(localAiBox);
        c.gridy = 2;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(options, c);

        c.gridy = 3;
        c.insets = new Insets(10, 0, 4, 0);
        panel.add(title("2. Bản ghi (phát theo thứ tự này)"), c);
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
        JButton add = new JButton("Thêm video...");
        add.setToolTipText("Bản ghi video (MP4, MKV, MOV, WEBM...), ví dụ từ OBS.");
        add.addActionListener(event -> addFootage(null));
        JButton replay = new JButton("Video ReplayMod...");
        replay.setToolTipText("Video đã kết xuất từ ReplayMod (Replay Viewer > Render): " + replayVideos);
        replay.addActionListener(event -> addFootage(replayVideos));
        JButton remove = new JButton("Xóa");
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
        panel.add(title("3. Giọng thuyết minh"), c);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filters.add(new JLabel("Tìm:"));
        voiceSearch.setToolTipText("Tìm tên giọng, mã, người nói, ngôn ngữ hoặc bộ đọc (ví dụ \"arctic awb\", \"vi_VN\").");
        voiceSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                searchDelay.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                searchDelay.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                searchDelay.restart();
            }
        });
        searchDelay.setRepeats(false);
        filters.add(voiceSearch);
        languageBox.setToolTipText("Nhóm ngôn ngữ (en, vi) hoặc mã đầy đủ (en_US, vi_VN).");
        engineBox.setToolTipText("piper = gói giọng đã cài/có thể tải; xtts = giọng nhân bản.");
        stateBox.setToolTipText("Giọng đã cài dùng ngoại tuyến; giọng có thể tải cần cài trước.");
        stateBox.setRenderer(localizedRenderer());
        multiSpeakerBox.setToolTipText("Chỉ người nói trong mô hình nhiều người nói (mỗi người là một mục).");
        for (JComboBox<?> box : List.of(languageBox, engineBox, stateBox)) {
            box.addActionListener(event -> {
                if (!updatingVoices) {
                    refilter();
                }
            });
            filters.add(box);
        }
        multiSpeakerBox.addActionListener(event -> refilter());
        filters.add(multiSpeakerBox);
        c.gridy = 8;
        c.insets = new Insets(0, 0, 4, 0);
        panel.add(filters, c);

        voiceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        voiceList.setVisibleRowCount(6);
        voiceList.setPrototypeCellValue("English (United States) multi-speaker voice name - speaker 9999/9999 [downloadable]");
        voiceList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                Component label = super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof VoiceCatalogEntry entry) {
                    setText(DesktopVoiceSupport.label(entry));
                    if (!entry.installed() && !selected) {
                        setForeground(Color.GRAY);
                    }
                }
                return label;
            }
        });
        voiceList.addListSelectionListener(event -> {
            if (updatingVoices || event.getValueIsAdjusting()) {
                return;
            }
            Object value = voiceList.getSelectedValue();
            if (value == null) {
                return;
            }
            selectedId = value instanceof VoiceCatalogEntry entry ? entry.id() : "";
            studio.settings().setVoiceId(selectedId);
            showSelectedVoice();
        });
        c.gridy = 9;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 0.4;
        panel.add(new JScrollPane(voiceList), c);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weighty = 0;
        catalogCount.setForeground(Color.GRAY);
        c.gridy = 10;
        panel.add(catalogCount, c);

        JPanel voiceButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        previewButton.setToolTipText("Nghe mẫu bằng đúng giọng/người nói đã chọn, tốc độ và hiệu ứng hiện tại.");
        previewButton.addActionListener(event -> previewVoice());
        installButton.setToolTipText("Tải và cài giọng đã xác minh sau khi bạn nhấn và xác nhận.");
        installButton.setEnabled(false);
        installButton.addActionListener(event -> installVoice());
        JButton refresh = new JButton("Làm mới");
        refresh.setToolTipText("Tìm giọng mới trong thư mục giọng, giữ giọng đang chọn.");
        refresh.addActionListener(event -> refreshVoices());
        JButton openVoices = new JButton("Mở thư mục giọng");
        openVoices.setToolTipText("Thư mục chứa gói Piper (.onnx + .onnx.json) và giọng nhân bản.");
        openVoices.addActionListener(event -> openFolder(studio.settings().voicesFolder(), true));
        voiceButtons.add(previewButton);
        voiceButtons.add(installButton);
        voiceButtons.add(refresh);
        voiceButtons.add(openVoices);
        JPanel cloningButtons = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        JButton clone = new JButton("Nhân bản giọng từ WAV...");
        clone.addActionListener(event -> cloneVoice());
        clone.setToolTipText(DesktopVoiceSupport.SAMPLE_GUIDANCE);
        JButton importPack = new JButton("Nhập gói giọng .onnx + .onnx.json...");
        importPack.addActionListener(event -> chooseVoicePack());
        JButton sampleGuide = new JButton("Bài đọc mẫu / Hướng dẫn ghi âm");
        sampleGuide.addActionListener(event -> showSampleGuide());
        JButton recordSample = new JButton("Ghi âm mẫu (chưa hỗ trợ)");
        recordSample.setEnabled(false);
        recordSample.setToolTipText("Chưa ghi micro trực tiếp. Thu WAV PCM bằng công cụ ngoài rồi chọn hoặc kéo thả vào đây.");
        cloningButtons.add(clone);
        cloningButtons.add(importPack);
        cloningButtons.add(sampleGuide);
        JLabel dropHint = new JLabel("<html>" + DesktopVoiceSupport.DROP_HINT + "</html>");
        dropHint.setToolTipText(DesktopVoiceSupport.SAMPLE_GUIDANCE);
        cloningButtons.add(dropHint);
        TransferHandler dropHandler = voiceDropHandler();
        voiceList.setTransferHandler(dropHandler);
        dropHint.setTransferHandler(dropHandler);
        JPanel audioControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        audioControls.add(new JLabel("Tốc độ đọc:"));
        speedSpinner.setToolTipText("0,5–2,0×; tốc độ khác 1× cần FFmpeg, không đổi cao độ.");
        speedSpinner.addChangeListener(event -> studio.settings().setNarrationSpeed(((Number) speedSpinner.getValue()).doubleValue()));
        audioControls.add(speedSpinner);
        audioControls.add(new JLabel("Hiệu ứng:"));
        effectBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(value == NarrationAudioOptions.Effect.SOFT_ECHO ? "Vang nhẹ" : "Không hiệu ứng");
                return this;
            }
        });
        effectBox.setToolTipText("Vang nhẹ cần FFmpeg. Áp dụng cho nghe thử và mọi chế độ thuyết minh.");
        effectBox.addActionListener(event -> studio.settings().setNarrationEffect((NarrationAudioOptions.Effect) effectBox.getSelectedItem()));
        audioControls.add(effectBox);
        cloningButtons.add(audioControls);
        JPanel voiceControls = new JPanel(new BorderLayout(0, 4));
        JPanel cloneControls = new JPanel(new BorderLayout(0, 4));
        cloneControls.add(cloningButtons, BorderLayout.NORTH);
        cloneControls.add(recordSample, BorderLayout.SOUTH);
        voiceControls.add(voiceButtons, BorderLayout.NORTH);
        voiceControls.add(cloneControls, BorderLayout.SOUTH);
        busyDisabled.addAll(List.of(refresh, clone, importPack, speedSpinner, effectBox));
        c.gridy = 11;
        panel.add(voiceControls, c);
        voiceInfo.setForeground(Color.GRAY);
        c.gridy = 12;
        panel.add(voiceInfo, c);
        return panel;
    }

    private JComponent buildStoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        panel.add(title("4. Kịch bản và xuất video"), BorderLayout.NORTH);
        storyArea.setEditable(false);
        storyArea.setLineWrap(true);
        storyArea.setWrapStyleWord(true);
        storyArea.setText("Nhấn \"Viết kịch bản\" để tạo các cảnh và lời thuyết minh từ mô tả.");
        panel.add(new JScrollPane(storyArea), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        writeButton.addActionListener(event -> writeStory(null));
        exportButton.setFont(exportButton.getFont().deriveFont(Font.BOLD));
        exportButton.setToolTipText("Thuyết minh bằng giọng đã chọn, dựng bản ghi và lưu MP4 vào thư mục đầu ra.");
        exportButton.addActionListener(event -> export());
        JButton openOutput = new JButton("Mở thư mục đầu ra");
        openOutput.addActionListener(event -> openFolder(lastExport != null ? lastExport.getParent() : studio.settings().outputFolder(), true));
        JButton tools = new JButton("Công cụ...");
        tools.setToolTipText("Cấu hình FFmpeg, Piper, XTTS, thư mục và AI cục bộ; xem công cụ đã cài.");
        tools.addActionListener(event -> openTools());
        JButton help = new JButton("Trợ giúp");
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
        JLabel heading = title("5. Ghi hình, thuyết minh và tự xuất");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        controls.add(new JLabel("Chế độ:"));
        modeBox.setRenderer(localizedRenderer());
        modeBox.setToolTipText("<html><b>Chỉ ghi hình</b>: lưu video màn hình gốc.<br><b>Chỉ thuyết minh</b>: "
            + "tạo WAV, kịch bản và phụ đề.<br><b>Tự xuất</b>: ghi hình và thuyết minh đồng thời, rồi ghép MP4 khi cả hai xong.</html>");
        modeBox.addActionListener(event -> {
            ExportMode mode = (ExportMode) modeBox.getSelectedItem();
            studio.settings().setExportMode(mode);
            recordSpinner.setEnabled(!busy && mode != null && mode.records());
        });
        controls.add(modeBox);
        controls.add(new JLabel("Ghi hình (giây):"));
        recordSpinner.setToolTipText("Thời gian ghi màn hình. Giữ cửa sổ Minecraft hiển thị khi ghi.");
        recordSpinner.addChangeListener(event -> studio.settings().setRecordSeconds(((Number) recordSpinner.getValue()).intValue()));
        controls.add(recordSpinner);
        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD));
        startButton.setToolTipText("Chạy chế độ đã chọn, viết kịch bản trước nếu cần.");
        startButton.addActionListener(event -> startFlow());
        cancelButton.setToolTipText("Dừng ghi hình và thuyết minh; giữ lại các tệp đã hoàn tất.");
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
        runInBackground("Đang tìm giọng...", () -> {
            VoiceDiscovery found = studio.discoverVoices();
            VoiceCatalog built = studio.voiceCatalog(found);
            List<String> diagnostics = studio.diagnostics(found);
            SwingUtilities.invokeLater(() -> {
                diagnostics.forEach(this::log);
                showCatalog(found, built);
                if (found.voices().isEmpty()) {
                    log("Chưa có giọng: chọn giọng có thể tải và nhấn Cài giọng, hoặc Nhập gói giọng (.onnx + .onnx.json) vào " + found.folder());
                }
            });
        });
    }

    /** Shows a freshly built catalog; the chosen voice is kept by its stable id. */
    private void showCatalog(VoiceDiscovery found, VoiceCatalog built) {
        voices = found;
        catalog = built;
        log("Danh mục: " + built.size() + " mục, " + built.installedCount() + " đã cài, " + built.downloadableCount() + " có thể tải.");
        built.problems().stream().filter(problem -> !found.problems().contains(problem)).forEach(problem -> log("  " + problem));
        if (!selectedId.isEmpty() && built.find(selectedId).isEmpty()) {
            log("Không tìm thấy giọng đã lưu '" + selectedId + "'; hãy chọn giọng trong danh sách.");
            selectedId = built.entries().stream().filter(VoiceCatalogEntry::installed).map(VoiceCatalogEntry::id).findFirst().orElse("");
        } else if (!catalogInitialized && selectedId.isEmpty() && studio.settings().voiceId().isEmpty()) {
            selectedId = built.entries().stream().filter(VoiceCatalogEntry::installed).map(VoiceCatalogEntry::id).findFirst().orElse("");
        }
        catalogInitialized = true;
        updatingVoices = true;
        try {
            List<String> languages = new ArrayList<>();
            built.languages().forEach(code -> {
                String family = code.split("[_-]")[0];
                if (!languages.contains(family)) {
                    languages.add(family);
                }
                if (!languages.contains(code)) {
                    languages.add(code);
                }
            });
            languages.sort(null);
            refill(languageBox, ANY_LANGUAGE, languages);
            refill(engineBox, ANY_ENGINE, built.engines());
        } finally {
            updatingVoices = false;
        }
        refilter();
    }

    private static void refill(JComboBox<String> box, String any, List<String> values) {
        Object current = box.getSelectedItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(any);
        values.forEach(model::addElement);
        box.setModel(model);
        box.setSelectedItem(current != null && values.contains(current) ? current : any);
    }

    /** Filters the catalog on a background thread; only the newest result is shown. */
    private void refilter() {
        VoiceCatalog current = catalog;
        if (current == null) {
            return;
        }
        String language = ANY_LANGUAGE.equals(languageBox.getSelectedItem()) ? "" : String.valueOf(languageBox.getSelectedItem());
        String engine = ANY_ENGINE.equals(engineBox.getSelectedItem()) ? "" : String.valueOf(engineBox.getSelectedItem());
        VoiceFilter filter = new VoiceFilter(voiceSearch.getText(), language, engine,
            (VoiceFilter.State) stateBox.getSelectedItem(), multiSpeakerBox.isSelected());
        int generation = filterGeneration.incrementAndGet();
        filterWorker.submit(() -> {
            List<VoiceCatalogEntry> shown = current.filter(filter);
            SwingUtilities.invokeLater(() -> {
                if (generation == filterGeneration.get() && current == catalog) {
                    showEntries(current, shown);
                }
            });
        });
    }

    private void showEntries(VoiceCatalog current, List<VoiceCatalogEntry> shown) {
        updatingVoices = true;
        try {
            voiceListModel.set(shown);
            int index = selectedId.isEmpty() ? 0 : shown.stream().map(VoiceCatalogEntry::id).toList().indexOf(selectedId) + 1;
            if (index > 0 || selectedId.isEmpty()) {
                voiceList.setSelectedIndex(index);
                voiceList.ensureIndexIsVisible(index);
            } else {
                voiceList.clearSelection();
            }
        } finally {
            updatingVoices = false;
        }
        catalogCount.setText(String.format(java.util.Locale.ROOT, "Hiển thị %d/%d mục (%d đã cài, %d có thể tải đã xác minh, %d mô hình).", shown.size(), current.size(),
            current.installedCount(), current.downloadableCount(), current.modelCount()));
        showSelectedVoice();
    }

    private void showSelectedVoice() {
        VoiceCatalogEntry entry = catalog == null ? null : catalog.find(selectedId).orElse(null);
        installButton.setEnabled(!busy && entry != null && entry.downloadable());
        if (entry == null) {
            voiceInfo.setText(selectedId.isEmpty() ? "Xuất video không thuyết minh." : "Đang tải danh sách giọng...");
            return;
        }
        boolean hidden = voiceListModel.indexOf(entry) < 0;
        String speaker = entry.speaker() == null ? "" : ", người nói " + entry.speaker().index() + " '" + entry.speaker().name()
            + "' / " + entry.speakerCount();
        voiceInfo.setText("<html>" + escape(entry.id() + " — " + DesktopVoiceSupport.label(entry.source()) + ", " + entry.language() + ", "
            + entry.engine() + speaker + (entry.installed() ? ", đã cài" : ", chưa cài (nhấn Cài giọng)")
            + (entry.description().isBlank() ? "" : " — " + entry.description()) + (hidden ? " (ẩn bởi bộ lọc)" : ""))
            + "</html>");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void installVoice() {
        VoiceCatalogEntry entry = catalog == null ? null : catalog.find(selectedId).orElse(null);
        if (entry == null || !entry.downloadable()) {
            error("Hãy chọn một giọng có thể tải trước.");
            return;
        }
        var model = entry.download();
        String speakers = model.speakers().isEmpty() ? "1 người nói" : model.speakers().size() + " người nói (mỗi người là một mục)";
        int answer = JOptionPane.showConfirmDialog(this, "Tải và cài " + model.modelId() + "?\n\n"
            + VoiceCatalogEntry.megabytes(model.totalBytes()) + " từ " + model.baseUrl().getHost() + ", " + speakers + ".\n"
            + "Kiểm tra kích thước và mã kiểm tra đã xác minh trước khi cài vào\n"
            + studio.settings().voicesFolder() + ". Không tải nội dung khác.\n"
            + "Xem giấy phép trong model card tại huggingface.co/rhasspy/piper-voices.",
            "Cài giọng", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        String id = entry.id();
        Future<?> task = runInBackground("Đang cài " + model.modelId() + "...", () -> {
            try {
                studio.voiceInstaller().install(model, studio.settings().voicesFolder(), message -> SwingUtilities.invokeLater(() -> {
                    progress.setString(message);
                    log(message);
                }));
            } catch (VoiceInstallException ex) {
                SwingUtilities.invokeLater(() -> error(ex.getMessage()));
                return;
            }
            VoiceDiscovery found = studio.discoverVoices();
            VoiceCatalog built = studio.voiceCatalog(found);
            SwingUtilities.invokeLater(() -> {
                selectedId = id;
                studio.settings().setVoiceId(id);
                showCatalog(found, built);
                log("Đã cài. Giọng đã chọn: " + built.find(id).map(DesktopVoiceSupport::label).orElse(id));
            });
        });
        if (task != null) {
            flowTask = task;
            cancelButton.setEnabled(true);
        }
    }

    private void cloneVoice() {
        cloneVoice(null);
    }

    private void cloneVoice(Path preselectedSample) {
        var unavailable = studio.cloningEngine().unavailableReason();
        if (unavailable.isPresent()) {
            error("Nhân bản giọng chưa sẵn sàng. Cấu hình Coqui tts và mô hình XTTS v2 cục bộ trong Công cụ.\n" + unavailable.get());
            return;
        }
        Path sample = preselectedSample;
        if (sample == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Chọn mẫu WAV PCM để nhân bản giọng tiếng Anh");
            chooser.setFileFilter(new FileNameExtensionFilter("WAV PCM 16-bit (6–60 giây, tối đa 20 MB)", "wav"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            sample = chooser.getSelectedFile().toPath();
        }
        String name = JOptionPane.showInputDialog(this,
            "Tên giọng nhân bản (chỉ thuyết minh tiếng Anh):",
            "Nhân bản giọng từ WAV", JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Mẫu: " + sample + "\n\n" + DesktopVoiceSupport.SAMPLE_GUIDANCE
            + "\n\nTôi có quyền sử dụng và nhân bản giọng trong tệp này.",
            "Xác nhận quyền sử dụng giọng", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        Path chosenSample = sample;
        runInBackground("Đang kiểm tra và nhân bản giọng...", () -> {
            try {
                VoicePack profile = studio.clonedVoices().create(studio.settings().voicesFolder(), chosenSample, name,
                    message -> SwingUtilities.invokeLater(() -> {
                        voiceInfo.setText(message);
                        log(message);
                    }));
                VoiceDiscovery found = studio.discoverVoices();
                VoiceCatalog built = studio.voiceCatalog(found);
                SwingUtilities.invokeLater(() -> {
                    selectedId = profile.id();
                    studio.settings().setVoiceId(profile.id());
                    showCatalog(found, built);
                    log("Giọng nhân bản sẵn sàng (tiếng Anh): " + profile.name());
                });
            } catch (NarrationException ex) {
                SwingUtilities.invokeLater(() -> {
                    voiceInfo.setText("Nhân bản giọng thất bại: " + ex.getMessage());
                    error("Nhân bản giọng thất bại: " + ex.getMessage());
                });
            }

        });
    }

    private void chooseVoicePack() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn cả mô hình .onnx và cấu hình .onnx.json tương ứng");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("Gói giọng Piper (.onnx + .onnx.json)", "onnx", "json"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            importDroppedFiles(java.util.Arrays.stream(chooser.getSelectedFiles()).map(File::toPath).toList(), true);
        }
    }

    private TransferHandler voiceDropHandler() {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return !busy && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!(data instanceof List<?> files) || files.stream().anyMatch(file -> !(file instanceof File))) {
                        error("Không đọc được danh sách tệp kéo thả.");
                        return false;
                    }
                    List<Path> paths = files.stream().map(file -> ((File) file).toPath()).toList();
                    return importDroppedFiles(paths, false);
                } catch (UnsupportedFlavorException | IOException | SecurityException ex) {
                    error("Không đọc được tệp kéo thả: " + ex.getMessage());
                    return false;
                }
            }
        };
    }

    private boolean importDroppedFiles(List<Path> paths, boolean packOnly) {
        try {
            VoiceDropInput input = DesktopVoiceSupport.classifyFiles(paths, packOnly);
            if (input.kind() == VoiceDropInput.Kind.WAV_SAMPLE) {
                cloneVoice(input.sample());
            } else {
                importVoicePack(input.model(), input.config());
            }
            return true;
        } catch (VoiceInstallException ex) {
            error(ex.getMessage());
            return false;
        }
    }

    private void importVoicePack(Path model, Path config) {
        JTextField name = new JTextField(24);
        JTextField description = new JTextField(24);
        JCheckBox selectImported = new JCheckBox("Chọn giọng vừa nhập để thuyết minh", false);
        JPanel form = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        form.add(new JLabel("Mô hình: " + model.getFileName()));
        form.add(new JLabel("Cấu hình: " + config.getFileName()));
        form.add(new JLabel("Tên giọng (tùy chọn):"));
        form.add(name);
        form.add(new JLabel("Mô tả (tùy chọn):"));
        form.add(description);
        form.add(selectImported);
        form.add(new JLabel("Chỉ nhập gói từ nguồn tin cậy mà bạn có quyền sử dụng."));
        if (JOptionPane.showConfirmDialog(this, form, "Nhập gói giọng cục bộ",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        String displayName = name.getText();
        String details = description.getText();
        boolean select = selectImported.isSelected();
        runInBackground("Đang nhập gói giọng...", () -> {
            try {
                VoicePack imported = studio.importVoicePack(model, config, displayName, details);
                VoiceDiscovery found = studio.discoverVoices();
                VoiceCatalog built = studio.voiceCatalog(found);
                SwingUtilities.invokeLater(() -> {
                    String importedId = built.entries().stream().filter(entry -> entry.modelId().equals(imported.id()))
                        .map(VoiceCatalogEntry::id).findFirst().orElse(imported.id());
                    selectedId = DesktopVoiceSupport.selectionAfterImport(selectedId, importedId, select);
                    if (select) {
                        studio.settings().setVoiceId(selectedId);
                    }
                    showCatalog(found, built);
                    log("Đã nhập gói giọng: " + imported.name());
                });
            } catch (VoiceInstallException ex) {
                SwingUtilities.invokeLater(() -> error("Không thể nhập gói giọng: " + ex.getMessage()));
            }
        });
    }

    private void showSampleGuide() {
        JTextArea guide = new JTextArea(DesktopVoiceSupport.SAMPLE_GUIDANCE + "\n\nBài đọc tiếng Anh khuyến nghị (backend hỗ trợ):\n"
            + DesktopVoiceSupport.ENGLISH_SAMPLE + "\n\nBài đọc tiếng Việt tham khảo để ghi âm; KHÔNG được backend nhân bản tiếng Việt hỗ trợ:\n"
            + DesktopVoiceSupport.VIETNAMESE_REFERENCE, 14, 54);
        guide.setEditable(false);
        guide.setLineWrap(true);
        guide.setWrapStyleWord(true);
        guide.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(guide), "Bài đọc mẫu và hướng dẫn ghi âm", JOptionPane.INFORMATION_MESSAGE);
    }

    private void writeStory(Runnable then) {
        String prompt = promptArea.getText().strip();
        if (prompt.length() > PromptAnalysis.MAX_PROMPT_LENGTH) {
            error("Mô tả quá dài (tối đa " + PromptAnalysis.MAX_PROMPT_LENGTH + " ký tự).");
            return;
        }
        PromptAnalysis analysis = PromptAnalysis.of(prompt);
        Double length = analysis.lengthGiven() ? null : ((Number) lengthSpinner.getValue()).doubleValue();
        VoiceSelection voice;
        try {
            voice = selectedVoice();
        } catch (NarrationException ex) {
            voice = null;
        }
        String storyLanguage = voice == null ? null : voice.storyLanguage();
        BuildContext context = buildContext.get();
        timelineLabel.setText(buildDescription.get());
        runInBackground("Đang viết kịch bản...", () -> {
            StoryResult result;
            try {
                result = studio.pipeline().writeStory(new StoryRequest(prompt, context, length, storyLanguage));
            } catch (IllegalArgumentException ex) {
                SwingUtilities.invokeLater(() -> error(ex.getMessage()));
                return;
            }
            StoryResult story = result;
            SwingUtilities.invokeLater(() -> {
                storyboard = story.storyboard();
                storyArea.setText(DesktopVoiceSupport.describe(storyboard));
                storyArea.setCaretPosition(0);
                story.warnings().forEach(warning -> log("Lưu ý: " + warning));
                log("Kịch bản tạo bởi " + storyboard.generator() + ": " + storyboard.scenes().size() + " cảnh, khoảng "
                    + Math.round(storyboard.totalSeconds()) + " giây.");
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
        VoiceSelection voice;
        try {
            voice = selectedVoice();
        } catch (NarrationException ex) {
            error(ex.getMessage());
            return;
        }
        log(voice == null ? "Giọng: không (xuất video không thuyết minh)" : "Giọng: " + voice.describe());
        if (voice != null && !voice.storyLanguage().equals(storyboard.language())) {
            log("Lưu ý: kịch bản dùng '" + storyboard.language() + "' nhưng giọng đọc '" + voice.language()
                + "'. Nhấn Viết kịch bản lại để khớp ngôn ngữ giọng.");
        }
        List<Path> footage = new ArrayList<>();
        for (int i = 0; i < footageModel.size(); i++) {
            footage.add(footageModel.get(i));
        }
        ExportRequest request = new ExportRequest(storyboard, buildContext.get(), footage, voice, studio.settings().outputFolder(),
            null, RenderOptions.hd720(), false);
        runInBackground("Đang xuất video...", () -> {
            try {
                ExportResult result = studio.pipeline().export(request, message -> SwingUtilities.invokeLater(() -> {
                    progress.setString(message);
                    log(message);
                }));
                SwingUtilities.invokeLater(() -> {
                    lastExport = result.video();
                    result.warnings().forEach(warning -> log("Lưu ý: " + warning));
                    log("Đã lưu " + result.video() + (result.narrated() ? " (có thuyết minh)" : " (không thuyết minh)"));
                    if (result.subtitles() != null) {
                        log("Phụ đề: " + result.subtitles());
                    }
                    log("Kịch bản: " + result.script());
                    JOptionPane.showMessageDialog(this, "Đã lưu video:\n" + result.video()
                        + (result.warnings().isEmpty() ? "" : "\n\n" + String.join("\n", result.warnings())),
                        "Xuất hoàn tất", result.warnings().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                });
            } catch (VideoExportException | IllegalArgumentException ex) {
                SwingUtilities.invokeLater(() -> error("Xuất thất bại: " + ex.getMessage()));
            }
        });
    }

    private void startFlow() {
        if (storyboard == null || !storyboard.prompt().equals(promptArea.getText().strip())) {
            writeStory(this::startFlow);
            return;
        }
        ExportMode mode = modeBox.getSelectedItem() instanceof ExportMode chosen ? chosen : ExportMode.AUTO_EXPORT;
        VoiceSelection voice;
        try {
            voice = selectedVoice();
        } catch (NarrationException ex) {
            if (mode.narrates()) {
                error(ex.getMessage());
                return;
            }
            voice = null;
        }
        if (mode.narrates() && voice != null) {
            log("Giọng: " + voice.describe());
        }
        if (mode.narrates() && voice != null && !voice.storyLanguage().equals(storyboard.language())) {
            log("Lưu ý: kịch bản dùng '" + storyboard.language() + "' nhưng giọng đọc '" + voice.language()
                + "'. Nhấn Viết kịch bản lại để khớp ngôn ngữ giọng.");
        }
        BuildContext built = buildContext.get();
        // fresh footage: milestones of an earlier recording do not line up with it, so scenes are spread evenly
        BuildContext context = new BuildContext(built.buildName(), List.of(), built.sections(), built.blocks());
        FlowRequest request = new FlowRequest(mode, storyboard, context, voice, ((Number) recordSpinner.getValue()).doubleValue(),
            studio.settings().outputFolder(), null, RenderOptions.hd720());
        String language = "vi";
        outputLabel.setText(" ");
        Future<?> task = runInBackground(DesktopVoiceSupport.label(mode) + "...", () -> {
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
            log("Đang hủy...");
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
        result.warnings().forEach(warning -> log("Lưu ý: " + warning));
        for (Path file : new Path[] {result.recording(), result.narration(), result.script(), result.subtitles()}) {
            if (file != null) {
                log("Đã lưu " + file);
            }
        }
        if (result.ok()) {
            lastExport = result.output();
            outputLabel.setText("Đầu ra: " + result.output());
            outputLabel.setToolTipText(result.output().toString());
            log(FlowStage.COMPLETE.label(language) + ": " + result.output());
            String[] options = {result.mode() == ExportMode.NARRATE_ONLY ? "Mở âm thanh" : "Mở video", "Mở thư mục", "Đóng"};
            int choice = JOptionPane.showOptionDialog(this, FlowStage.COMPLETE.label(language) + "\n" + result.output()
                + (result.warnings().isEmpty() ? "" : "\n\n" + String.join("\n", result.warnings())), DesktopVoiceSupport.label(result.mode()),
                JOptionPane.DEFAULT_OPTION, result.warnings().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
            if (choice == 0) {
                openFile(result.output());
            } else if (choice == 1) {
                openFolder(result.output().getParent(), false);
            }
        } else if (result.stage() == FlowStage.FAILED && result.failure() != null) {
            String message = FlowStage.FAILED.label(language) + result.failure().reason(language);
            log("Sự cố: " + FlowStage.FAILED.label(language) + result.failure().describe(language));
            boolean kept = result.recording() != null || result.narration() != null;
            outputLabel.setText(kept ? "Tệp đã hoàn tất được giữ tại " + studio.settings().outputFolder() : " ");
            String[] options = {"Thử lại", "Đóng"};
            int choice = JOptionPane.showOptionDialog(this, message + "\n\n" + result.failure().detail()
                + (kept ? "\n\nTệp đã hoàn tất được giữ trong thư mục đầu ra." : ""), "Video Studio",
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
                log("Tệp: " + file);
            }
        } catch (IOException | UnsupportedOperationException | SecurityException | IllegalArgumentException ex) {
            error("Không thể mở " + file + ": " + ex.getMessage());
        }
    }

    private void previewVoice() {
        VoiceSelection voice;
        try {
            voice = selectedVoice();
        } catch (NarrationException ex) {
            error("Nghe thử giọng thất bại: " + ex.getMessage());
            return;
        }
        if (voice == null) {
            error("Hãy chọn giọng trước (cài từ danh sách hoặc nhập gói giọng rồi nhấn Làm mới).");
            return;
        }
        String language = voice.storyLanguage();
        runInBackground("Đang đọc mẫu bằng " + voice.describe() + "...", () -> {
            Path wav = null;
            try {
                Path previewFolder = studio.settings().outputFolder().resolve(".preview");
                Files.createDirectories(previewFolder);
                wav = Files.createTempFile(previewFolder, "architect-voice-preview-", ".wav");
                NarrationClip clip = studio.narrator().preview(voice, com.annaschneider.minecraft1.video.voice.Narrator.previewText(language), wav);
                play(wav);
                wav = null; // deleted after playback
                SwingUtilities.invokeLater(() -> log(String.format(java.util.Locale.ROOT, "Đang phát %s (%.1f giây).", voice.describe(), clip.seconds())));
            } catch (NarrationException | IOException ex) {
                SwingUtilities.invokeLater(() -> error("Nghe thử giọng thất bại: " + ex.getMessage()));
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
            throw new IOException("không thể phát âm thanh trên máy này (" + ex.getMessage() + ")", ex);
        }
    }

    private void addFootage(Path startFolder) {
        File start = startFolder != null && Files.isDirectory(startFolder) ? startFolder.toFile() : null;
        if (startFolder != null && start == null) {
            log("Chưa tìm thấy thư mục video ReplayMod: " + startFolder + " (hãy kết xuất bản phát lại trong ReplayMod trước).");
        }
        JFileChooser chooser = new JFileChooser(start);
        chooser.setDialogTitle("Thêm bản ghi video");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("Video (mp4, mkv, mov, webm, avi)", "mp4", "mkv", "mov", "webm", "avi", "m4v"));
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
            {"FFmpeg (để trống = tìm trong PATH):", ffmpeg, JFileChooser.FILES_AND_DIRECTORIES},
            {"Piper (để trống = tự tìm):", piper, JFileChooser.FILES_AND_DIRECTORIES},
            {"Nhân bản giọng (Coqui tts, để trống = tự tìm):", cloning, JFileChooser.FILES_AND_DIRECTORIES},
            {"Thư mục mô hình XTTS v2 cục bộ:", cloningModel, JFileChooser.DIRECTORIES_ONLY},
            {"Thư mục giọng:", voicesFolder, JFileChooser.DIRECTORIES_ONLY},
            {"Thư mục đầu ra:", output, JFileChooser.DIRECTORIES_ONLY},
            {"Địa chỉ AI cục bộ (Ollama):", ollamaUrl, null},
            {"Mô hình AI cục bộ:", ollamaModel, null},
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
                JButton browse = new JButton("Chọn...");
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
        statusScroll.setBorder(BorderFactory.createTitledBorder("Công cụ tìm thấy"));
        content.add(statusScroll, BorderLayout.CENTER);
        int answer = JOptionPane.showConfirmDialog(this, content, "Công cụ Video Studio", JOptionPane.OK_CANCEL_OPTION,
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
        log("Đã lưu cấu hình công cụ.");
        refreshVoices();
    }

    private void showHelp() {
        JOptionPane.showMessageDialog(this, "<html><body style='width: 480px'>" + helpHtml() + "</body></html>",
            "Trợ giúp Video Studio", JOptionPane.INFORMATION_MESSAGE);
    }

    static String helpHtml() {
        return "<h3>Tạo video thuyết minh công trình</h3><ol>"
            + "<li>Ghi công trình bằng <b>Build + Record</b> (ReplayMod) hoặc OBS. Trong ReplayMod, dùng <i>Render</i> "
            + "để lưu MP4 vào <i>.minecraft/replay_videos</i>.</li>"
            + "<li>Mô tả video, ví dụ <i>Video 60 giây: một vương quốc mọc lên từ hòn đảo hoang</i>. "
            + "Thời lượng trong mô tả được ưu tiên; <i>timelapse</i>/<i>fast</i> tăng cảnh tua nhanh. "
            + "Giọng đã chọn quyết định ngôn ngữ kịch bản.</li>"
            + "<li>Nhấn <b>Video ReplayMod...</b> hoặc <b>Thêm video...</b>. Không có bản ghi thì dùng thẻ tiêu đề.</li>"
            + "<li>Chọn đúng giọng/người nói, <b>Nghe thử giọng</b>, rồi <b>Viết kịch bản</b> và <b>Xuất video</b>. "
            + "MP4, phụ đề SRT và kịch bản được lưu trong thư mục đầu ra.</li></ol>"
            + "<p>Các cảnh theo tiến độ công trình gần nhất (bắt đầu, 25%, 75%, kết thúc); nếu không có thì chia đều.</p>"
            + "<h3>Ghi hình và thuyết minh tự động (bước 5)</h3><ul>"
            + "<li><b>Chỉ ghi hình</b>: lưu video màn hình gốc theo số giây đã đặt.</li>"
            + "<li><b>Chỉ thuyết minh</b>: lưu WAV, kịch bản và phụ đề, không ghi hình. "
            + "Cài đặt mặc định không cần FFmpeg; tốc độ khác 1× hoặc hiệu ứng Vang nhẹ cần FFmpeg.</li>"
            + "<li><b>Tự xuất khi ghi hình và thuyết minh xong</b>: thực hiện đồng thời, ghép âm thanh và xuất MP4 khi cả hai xong.</li></ul>"
            + "<p>Trạng thái: Kiểm tra công cụ → Ghi hình + Tạo thuyết minh → Chờ tác vụ còn lại → Ghép âm thanh/video → Hoàn tất. "
            + "Giữ Minecraft hiển thị (Windows: toàn màn hình desktop; macOS: màn hình 0; Linux: X11). "
            + "Khi lỗi, giữ tệp đã hoàn tất; <b>Thử lại</b> chạy lại, <b>Hủy</b> dừng cả hai tác vụ.</p>"
            + "<h3>Gói giọng và kéo thả</h3><p>" + DesktopVoiceSupport.DROP_HINT + "</p>"
            + "<p><b>Nhập gói giọng .onnx + .onnx.json...</b>: chọn cả hai tệp tương ứng, nhập tên/mô tả tùy chọn. "
            + "Kiểm tra trước khi sao chép; không ghi đè gói cũ. Giữ giọng đang chọn, trừ khi đánh dấu chọn giọng vừa nhập. "
            + "<b>Làm mới</b> giữ lựa chọn hiện tại. <b>Mở thư mục giọng</b> để xem tệp.</p>"
            + "<p>Danh mục chỉ liệt kê gói đã cài, giọng nhân bản và mục tải đã xác minh. Tìm theo tên/mã/người nói, "
            + "lọc ngôn ngữ, bộ đọc, trạng thái hoặc nhiều người nói. Mỗi người nói có mã riêng, ví dụ "
            + "<i>en_US-arctic-medium#speaker-2</i>; mọi chế độ dùng đúng lựa chọn, không tự thay giọng.</p>"
            + "<p><b>Cài giọng...</b> chỉ tải sau khi xác nhận, kiểm tra kích thước và checksum. "
            + "Giọng đã cài dùng ngoại tuyến. Xem giấy phép tại huggingface.co/rhasspy/piper-voices.</p>"
            + "<h3>Nhân bản giọng từ WAV</h3><p>Cấu hình Coqui <i>tts</i> và XTTS v2 đã cài "
            + "(model.pth, config.json, vocab.json) trong <b>Công cụ...</b>. Chọn WAV hoặc kéo thả, đặt tên và xác nhận "
            + "quyền sử dụng giọng. Chỉ lưu hồ sơ khi kiểm tra tổng hợp thành công. " + DesktopVoiceSupport.SAMPLE_GUIDANCE
            + " Không thể dùng âm thanh bất kỳ làm giọng TTS. Chưa ghi micro trực tiếp; hãy thu bằng công cụ ngoài.</p>"
            + "<p><b>Bài đọc mẫu / Hướng dẫn ghi âm</b> có bài tiếng Anh khuyến nghị cho backend và đoạn tiếng Việt "
            + "tham khảo ghi âm (không hỗ trợ nhân bản tiếng Việt). Muốn thuyết minh tiếng Việt hãy dùng gói Piper tiếng Việt.</p>"
            + "<h3>Tốc độ và hiệu ứng</h3><p>Tốc độ 0,5–2,0× và hiệu ứng Không hiệu ứng/Vang nhẹ được lưu, "
            + "áp dụng cả nghe thử và xuất video. Xử lý âm thanh diễn ra sau tổng hợp, không đổi cao độ. "
            + "Nếu thiếu FFmpeg cho tùy chọn cần xử lý, báo lỗi chứ không bỏ qua.</p>"
            + "<h3>Công cụ cục bộ</h3><ul><li><b>FFmpeg</b>: cần để ghi hình/xuất MP4 và xử lý âm thanh.</li>"
            + "<li><b>Piper</b>: đọc gói giọng Piper. Thiếu bộ đọc thì chọn không thuyết minh hoặc cấu hình công cụ.</li>"
            + "<li><b>Ollama</b>: tùy chọn viết kịch bản; chạy <i>ollama pull llama3.2</i>, bật AI cục bộ. "
            + "Nếu không chạy, dùng mẫu có sẵn.</li></ul>"
            + "<p>Không tải mẫu ghi âm lên mạng: mô tả, giọng và video được xử lý trên máy này.</p>";
    }

    private void openFolder(Path folder, boolean create) {
        try {
            if (create) {
                Files.createDirectories(folder);
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(folder.toFile());
            } else {
                log("Thư mục: " + folder);
            }
        } catch (IOException | UnsupportedOperationException | SecurityException ex) {
            error("Không thể mở " + folder + ": " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The exact selected voice and speaker, or {@code null} for no narration.
     *
     * @throws NarrationException when the selected entry is not installed or no longer exists - never another voice
     */
    private VoiceSelection selectedVoice() throws NarrationException {
        if (selectedId.isEmpty()) {
            return null;
        }
        if (catalog == null) {
            throw new NarrationException("Đang tải danh sách giọng. Hãy thử lại sau.");
        }
        VoiceCatalogEntry entry = catalog.find(selectedId).orElseThrow(
            () -> new NarrationException("Không tìm thấy giọng đã chọn. Hãy làm mới và chọn lại."));
        if (!entry.installed()) {
            throw new NarrationException("Giọng '" + entry.displayName() + "' chưa được cài. Nhấn Cài giọng trước.");
        }
        return catalog.select(selectedId);
    }

    /** List model that swaps the whole filtered list at once (fast with thousands of entries). */
    private static final class EntryListModel extends AbstractListModel<Object> {
        private List<VoiceCatalogEntry> entries = List.of();

        void set(List<VoiceCatalogEntry> shown) {
            int old = entries.size();
            entries = List.copyOf(shown);
            if (old > 0) {
                fireIntervalRemoved(this, 1, old);
            }
            if (!entries.isEmpty()) {
                fireIntervalAdded(this, 1, entries.size());
            }
        }

        int indexOf(VoiceCatalogEntry entry) {
            return entries.indexOf(entry);
        }

        @Override
        public int getSize() {
            return entries.size() + 1;
        }

        @Override
        public Object getElementAt(int index) {
            return index == 0 ? NO_VOICE : entries.get(index - 1);
        }
    }

    /** @return the running task (cancel it to interrupt the work), or {@code null} when another task is still running */
    private Future<?> runInBackground(String message, Runnable task) {
        if (busy) {
            log("Vui lòng đợi — đang xử lý.");
            return null;
        }
        setBusy(true, message);
        return worker.submit(() -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                SwingUtilities.invokeLater(() -> error("Sự cố ngoài dự kiến: " + ex));
            } finally {
                SwingUtilities.invokeLater(() -> setBusy(false, "Sẵn sàng"));
            }
        });
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        progress.setIndeterminate(value);
        progress.setString(message);
        busyDisabled.forEach(component -> component.setEnabled(!value));
        VoiceCatalogEntry chosen = catalog == null ? null : catalog.find(selectedId).orElse(null);
        installButton.setEnabled(!value && chosen != null && chosen.downloadable());
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
        log("Sự cố: " + message);
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

    private static DefaultListCellRenderer localizedRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(DesktopVoiceSupport.label(value));
                return this;
            }
        };
    }

    @Override
    public void dispose() {
        worker.shutdownNow();
        filterWorker.shutdownNow();
        searchDelay.stop();
        super.dispose();
    }
}
