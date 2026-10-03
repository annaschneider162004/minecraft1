package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.video.voice.VoiceCatalog;
import com.annaschneider.minecraft1.video.voice.VoiceDiscovery;
import com.annaschneider.minecraft1.video.voice.VoiceModelInstaller;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import com.annaschneider.minecraft1.video.voice.XttsTtsEngine;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Offline browsing; only an explicit, licensed install contacts model hosts. */
final class VoiceManagerWindow extends JDialog {
    static final String PIPER_REQUIREMENT = "Requires Piper installed separately; choose executable in Video Studio > Tools. "
        + "Installing this model does not install Piper.";
    private final VideoStudio studio;
    private final Consumer<VoiceDiscovery> discovered;
    private final Consumer<VoicePack> chooseVoice;
    private final Consumer<VoicePack> previewVoice;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "voice-manager");
        thread.setDaemon(true);
        return thread;
    });
    private final JComboBox<String> language = new JComboBox<>(new String[] {"all", "en", "vi"});
    private final JComboBox<String> state = new JComboBox<>(new String[] {"All states", "INSTALLED", "DOWNLOADABLE", "UNAVAILABLE"});
    private final JComboBox<String> source = new JComboBox<>(new String[] {"All sources"});
    private final JTextField search = new JTextField(18);
    private final JCheckBox favoritesOnly = new JCheckBox("Favorites");
    private final VoiceTable tableModel = new VoiceTable();
    private final JTable table = new JTable(tableModel);
    private final JTextArea details = new JTextArea(10, 40);
    private final JLabel inventory = new JLabel("Loading offline catalog...");
    private final JLabel status = new JLabel(" ");
    private final JButton refresh = new JButton("Refresh offline");
    private final JButton favorite = new JButton("Favorite");
    private final JButton preview = new JButton("Preview installed");
    private final JButton choose = new JButton("Use this voice");
    private final JButton install = new JButton("Install / Retry");
    private final JButton cancel = new JButton("Cancel download");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final Timer filterTimer = new Timer(120, event -> filter());
    private Set<String> favorites = new HashSet<>();
    private List<VoiceCatalog.Entry> entries = List.of();
    private VoiceCatalog catalog;
    private Future<?> task;
    private volatile boolean closed;
    private boolean working;
    private long operation;

    VoiceManagerWindow(JFrame owner, VideoStudio studio, Consumer<VoiceDiscovery> discovered,
                       Consumer<VoicePack> chooseVoice, Consumer<VoicePack> previewVoice) {
        super(owner, "Voice manager — offline catalog", false);
        this.studio = studio;
        this.discovered = discovered;
        this.chooseVoice = chooseVoice;
        this.previewVoice = previewVoice;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(680, 480));
        setSize(1000, 680);
        setLocationRelativeTo(owner);
        filterTimer.setRepeats(false);
        JPanel searchFilters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchFilters.add(new JLabel("Language:"));
        searchFilters.add(language);
        searchFilters.add(new JLabel("Search:"));
        searchFilters.add(search);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        search.setToolTipText("Search voice name, ID and catalog metadata");
        filters.add(state);
        filters.add(source);
        filters.add(favoritesOnly);
        filters.add(refresh);
        for (JComboBox<String> combo : List.of(language, state, source)) {
            combo.addActionListener(event -> filterTimer.restart());
        }
        favoritesOnly.addActionListener(event -> filter());
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { filterTimer.restart(); }
            public void removeUpdate(DocumentEvent event) { filterTimer.restart(); }
            public void changedUpdate(DocumentEvent event) { filterTimer.restart(); }
        });
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showDetails();
            }
        });
        table.setFillsViewportHeight(true);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(details));
        split.setResizeWeight(0.65);
        JPanel actions = new JPanel(new java.awt.GridLayout(0, 3, 6, 4));
        for (JButton button : List.of(favorite, preview, choose, install, cancel)) {
            actions.add(button);
        }
        favorite.addActionListener(event -> toggleFavorite());
        preview.addActionListener(event -> {
            VoiceCatalog.Entry entry = selected();
            if (entry != null && entry.installed()) {
                previewVoice.accept(entry.installedVoice());
            }
        });
        choose.addActionListener(event -> {
            VoiceCatalog.Entry entry = selected();
            if (entry != null && entry.installed()) {
                chooseVoice.accept(entry.installedVoice());
                status.setText("Selected exact voice: " + entry.id());
            }
        });
        install.addActionListener(event -> install());
        cancel.addActionListener(event -> {
            if (task != null) {
                task.cancel(true);
                // Invalidate queued callbacks even if cancellation happens before the worker starts.
                operation++;
                setWorking(false, false);
                status.setText("Download cancelled. Partial files are not selectable; retry when ready.");
            }
        });
        refresh.addActionListener(event -> refresh(null));
        JPanel top = new JPanel(new BorderLayout());
        top.add(searchFilters, BorderLayout.NORTH);
        top.add(filters, BorderLayout.CENTER);
        top.add(inventory, BorderLayout.SOUTH);
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.add(actions, BorderLayout.NORTH);
        progress.setStringPainted(true);
        progress.setString("No downloads requested");
        bottom.add(progress, BorderLayout.CENTER);
        bottom.add(status, BorderLayout.SOUTH);
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(top, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                closed = true;
                filterTimer.stop();
                worker.shutdownNow();
            }
        });
        refresh(null);
    }

    private void refresh(String selectInstalledId) {
        if (working) {
            return;
        }
        long token = ++operation;
        setWorking(true, false);
        status.setText("Reading bundled catalog and installed files (no network)...");
        task = worker.submit(() -> {
            try {
                VoiceDiscovery found = studio.discoverVoices();
                VoiceCatalog loaded;
                String warning = "";
                try {
                    loaded = VoiceCatalog.bundled();
                } catch (java.io.IOException ex) {
                    loaded = new VoiceCatalog(List.of());
                    warning = message(ex);
                }
                List<VoiceCatalog.Entry> rows = loaded.entries(found);
                Set<String> savedFavorites = studio.settings().favoriteVoiceIds();
                VoiceCatalog offlineCatalog = loaded;
                String catalogWarning = warning;
                post(token, () -> {
                    acceptDiscovery(offlineCatalog, found, rows, savedFavorites, selectInstalledId);
                    if (!catalogWarning.isEmpty()) {
                        inventory.setText("Catalog unavailable; showing installed voices and cloned profiles only.");
                        status.setText(catalogWarning);
                    }
                });
            } catch (Exception ex) {
                post(token, () -> status.setText("Refresh failed: " + message(ex)));
            } finally {
                post(token, () -> setWorking(false, false));
            }
        });
    }

    private void acceptDiscovery(VoiceCatalog loaded, VoiceDiscovery found, List<VoiceCatalog.Entry> rows,
                                 Set<String> savedFavorites, String selectInstalledId) {
        catalog = loaded;
        entries = List.copyOf(rows);
        favorites = new HashSet<>(savedFavorites);
        Object selectedSource = source.getSelectedItem();
        source.removeAllItems();
        source.addItem("All sources");
        entries.stream().map(VoiceCatalog.Entry::source).distinct().sorted().forEach(source::addItem);
        source.setSelectedItem(selectedSource);
        if (source.getSelectedIndex() < 0) {
            source.setSelectedIndex(0);
        }
        inventory.setText(inventoryText(catalog, found, rows));
        filter();
        discovered.accept(found);
        if (selectInstalledId != null) {
            rows.stream().filter(e -> e.id().equals(selectInstalledId) && e.installed()).findFirst()
                .ifPresent(e -> chooseVoice.accept(e.installedVoice()));
        }
        status.setText(found.problems().isEmpty() ? "Offline catalog ready; downloads require explicit license acceptance."
            : "Offline scan: " + String.join("; ", found.problems()));
    }

    private void filter() {
        String selectedId = selected() == null ? studio.settings().voiceId() : selected().id();
        tableModel.rows = filterEntries(entries, (String) language.getSelectedItem(), search.getText(),
            favoritesOnly.isSelected(), favorites, (String) source.getSelectedItem(), (String) state.getSelectedItem());
        tableModel.favorites = favorites;
        tableModel.fireTableDataChanged();
        for (int i = 0; i < tableModel.rows.size(); i++) {
            if (tableModel.rows.get(i).id().equals(selectedId)) {
                table.setRowSelectionInterval(i, i);
                break;
            }
        }
        showDetails();
    }

    static List<VoiceCatalog.Entry> filterEntries(List<VoiceCatalog.Entry> entries, String language, String query,
                                                boolean favoritesOnly, Set<String> favorites, String source, String state) {
        return VoiceCatalog.filter(entries, "all".equals(language) ? "" : language, query, favoritesOnly, favorites).stream()
            .filter(e -> source == null || "All sources".equals(source) || source.equals(e.source()))
            .filter(e -> state == null || "All states".equals(state) || state.equals(e.state().name())).toList();
    }

    static String inventoryText(VoiceCatalog catalog, VoiceDiscovery found, List<VoiceCatalog.Entry> rows) {
        int en = catalog.speakerCount("en");
        int vi = catalog.speakerCount("vi");
        long downloadableEn = rows.stream().filter(e -> e.state() == VoiceCatalog.State.DOWNLOADABLE
            && e.language().startsWith("en")).count();
        long downloadableVi = rows.stream().filter(e -> e.state() == VoiceCatalog.State.DOWNLOADABLE
            && e.language().startsWith("vi")).count();
        long unavailable = rows.stream().filter(e -> e.state() == VoiceCatalog.State.UNAVAILABLE).count();
        long clones = found.voices().stream().filter(v -> XttsTtsEngine.ID.equals(v.engine())).count();
        long speakers = found.voices().size() - clones;
        long models = found.voices().stream().filter(v -> !XttsTtsEngine.ID.equals(v.engine()))
            .map(VoicePack::model).distinct().count();
        return "<html>1000+ row capacity; verified model-local catalog IDs (includes unavailable): EN " + en + " / VI " + vi
            + "<br>Not independently deduplicated people; model-local catalog gap to1000: " + Math.max(0, 1000 - en - vi)
            + "<br>Downloadable now: EN " + downloadableEn + " / VI " + downloadableVi
            + "; unavailable: " + unavailable + ". Download eligibility is not permission for every use; review licenses."
            + "<br>Installed Piper models: " + models + "; model-local IDs: " + speakers
            + "; installed Piper gap to1000: " + Math.max(0, 1000 - speakers)
            + "<br>Cloned profiles: " + clones + ". Profiles/presets do not count as model inventory or target progress. "
            + "Install only voices you need.</html>";
    }

    private VoiceCatalog.Entry selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= tableModel.rows.size() ? null : tableModel.rows.get(row);
    }

    private void showDetails() {
        VoiceCatalog.Entry entry = selected();
        favorite.setEnabled(entry != null && !working);
        favorite.setText(entry != null && favorites.contains(entry.id()) ? "Remove favorite" : "Favorite");
        choose.setEnabled(entry != null && entry.installed() && !working);
        preview.setEnabled(entry != null && entry.installed() && !working);
        install.setEnabled(entry != null && !entry.installed() && entry.model() != null
            && entry.model().downloadable() && !working);
        if (entry == null) {
            details.setText("Select a row. Installed voices and cloned profiles work offline. "
                + "Refresh never imports or downloads anything. XTTS cloning supports English here, not Vietnamese.\n"
                + PIPER_REQUIREMENT);
            return;
        }
        String text = entry.name() + "\nExact speaker ID: " + entry.id() + "\nLanguage: " + entry.language()
            + "\nSource: " + entry.source() + "\nState: " + entry.state();
        if (entry.model() != null) {
            VoiceCatalog.Model model = entry.model();
            long reused = entries.stream().filter(e -> e.model() != null && e.model().id().equals(model.id()) && e.installed()).count();
            text += "\nModel card / source URL: " + model.source() + "\nLicense: " + model.license()
                + "\nRestrictions: " + model.restrictions() + "\nShared model: " + model.id()
                + "\nVerified model-local IDs: " + model.numSpeakers() + "; installed entries reusing these files: " + reused
                + "\nModel-local IDs are not independently deduplicated people."
                + "\nModel download size (shared, not per speaker): " + bytes(model.downloadBytes())
                + "\nSample rate: " + model.sampleRate() + " Hz\n" + PIPER_REQUIREMENT;
            if (!model.downloadable()) {
                text += "\nThis catalog does not enable downloading this model. Verified model-local inventory does not "
                    + "imply license permission; review the original restrictions.";
            }
        } else {
            text += "\nLocal voice: consult its original model card/license. No catalog download is available.";
            if (entry.installedVoice() != null && "piper".equals(entry.installedVoice().engine())) {
                text += "\n" + PIPER_REQUIREMENT;
            }
        }
        details.setText(text);
        details.setCaretPosition(0);
    }

    private void toggleFavorite() {
        VoiceCatalog.Entry entry = selected();
        if (entry == null || working) {
            return;
        }
        boolean enabled = !favorites.contains(entry.id());
        long token = ++operation;
        setWorking(true, false);
        task = worker.submit(() -> {
            try {
                studio.settings().setVoiceFavorite(entry.id(), enabled);
                post(token, () -> {
                    if (enabled) {
                        favorites.add(entry.id());
                    } else {
                        favorites.remove(entry.id());
                    }
                    filter();
                    status.setText("Favorite saved: " + entry.id());
                });
            } catch (Exception ex) {
                post(token, () -> status.setText("Cannot save favorite: " + message(ex)));
            } finally {
                post(token, () -> setWorking(false, false));
            }
        });
    }

    private void install() {
        VoiceCatalog.Entry entry = selected();
        if (entry == null || entry.installed() || entry.model() == null || !entry.model().downloadable() || working) {
            return;
        }
        VoiceCatalog.Model model = entry.model();
        JTextArea license = new JTextArea("Model: " + model.name() + "\nSource: " + model.source()
            + "\nLicense: " + model.license() + "\nRestrictions: " + model.restrictions()
            + "\nDownload: " + bytes(model.downloadBytes()) + "\n" + PIPER_REQUIREMENT
            + "\n\nReview the source model card before accepting.", 11, 48);
        license.setEditable(false);
        license.setLineWrap(true);
        license.setWrapStyleWord(true);
        JCheckBox accepted = new JCheckBox("I have reviewed and accept this model's license and restrictions.");
        JPanel confirmation = new JPanel(new BorderLayout(0, 8));
        confirmation.add(new JScrollPane(license), BorderLayout.CENTER);
        confirmation.add(accepted, BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(this, confirmation, "Explicit model installation",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION || !accepted.isSelected()) {
            status.setText("Not installed: explicit license acceptance is required.");
            return;
        }
        long token = ++operation;
        setWorking(true, true);
        status.setText("Installing " + model.id() + "; speakers share one model download.");
        var folder = studio.settings().voicesFolder();
        task = worker.submit(() -> {
            try {
                long[] lastUpdate = {0};
                new VoiceModelInstaller().install(model, folder, true, update -> {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new CancellationException("Download cancelled");
                    }
                    long now = System.nanoTime();
                    if (now - lastUpdate[0] >= 100_000_000L || update.downloadedBytes() == update.totalBytes()) {
                        lastUpdate[0] = now;
                        post(token, () -> {
                            progress.setIndeterminate(update.totalBytes() <= 0);
                            progress.setValue(update.totalBytes() <= 0 ? 0
                                : (int) Math.min(100, 100.0 * update.downloadedBytes() / update.totalBytes()));
                            progress.setString(bytes(update.downloadedBytes()) + " / " + bytes(update.totalBytes()));
                        });
                    }
                });
                VoiceDiscovery found = studio.discoverVoices();
                List<VoiceCatalog.Entry> rows = catalog.entries(found);
                Set<String> savedFavorites = studio.settings().favoriteVoiceIds();
                post(token, () -> {
                    acceptDiscovery(catalog, found, rows, savedFavorites, entry.id());
                    progress.setValue(100);
                    progress.setString("Installed and verified");
                    status.setText(rows.stream().anyMatch(e -> e.id().equals(entry.id()) && e.installed())
                        ? "Installed; selected exact voice: " + entry.id()
                        : "Files installed, but requested speaker is unavailable. Selection was not changed; inspect scan problems.");
                });
            } catch (Exception ex) {
                post(token, () -> {
                    progress.setString("Not installed");
                    status.setText("Install failed: " + message(ex) + ". Select Install / Retry to try again.");
                });
            } finally {
                post(token, () -> setWorking(false, false));
            }
        });
    }

    private void setWorking(boolean value, boolean downloading) {
        working = value;
        refresh.setEnabled(!value);
        cancel.setEnabled(value && downloading);
        progress.setIndeterminate(value && !downloading);
        showDetails();
    }

    private void post(long token, Runnable action) {
        SwingUtilities.invokeLater(() -> {
            if (!closed && operation == token) {
                action.run();
            }
        });
    }

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static String bytes(long size) {
        return size <= 0 ? "unknown" : String.format(java.util.Locale.ROOT, "%.1f MiB", size / 1048576.0);
    }

    private static final class VoiceTable extends AbstractTableModel {
        private List<VoiceCatalog.Entry> rows = List.of();
        private Set<String> favorites = Set.of();
        private static final String[] COLUMNS = {"Favorite", "Voice / speaker", "Language", "Source", "State", "Exact ID"};

        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return COLUMNS.length; }
        public String getColumnName(int column) { return COLUMNS[column]; }
        public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : String.class; }
        public Object getValueAt(int row, int column) {
            VoiceCatalog.Entry entry = rows.get(row);
            return switch (column) {
                case 0 -> favorites.contains(entry.id());
                case 1 -> entry.name();
                case 2 -> entry.language();
                case 3 -> entry.source();
                case 4 -> entry.state().name();
                default -> entry.id();
            };
        }
    }
}
