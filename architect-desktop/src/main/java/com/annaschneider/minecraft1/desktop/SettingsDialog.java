package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.CameraNpcSettings;
import com.annaschneider.minecraft1.link.LinkProtocol;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Connection settings: where to find {@code desktop-link.json} and which player to act for. */
final class SettingsDialog extends JDialog {
    private final DesktopSettings settings;
    private final JTextField linkFileField = new JTextField(42);
    private final JTextField playerField = new JTextField(16);
    private final JTextField layoutWeightsField = new JTextField(42);
    private final JCheckBox cameraEnabled = new JCheckBox("Enable cinematic camera");
    private final JCheckBox npcEnabled = new JCheckBox("Show builder NPCs");
    private final JSpinner maxNpcs = new JSpinner(new SpinnerNumberModel(4, 0, 12, 1));
    private final JSpinner cameraHeight = new JSpinner(new SpinnerNumberModel(12, 5, 80, 1));
    private final JSpinner rotationSpeed = new JSpinner(new SpinnerNumberModel(9, 1, 30, 1));
    private boolean saved;

    SettingsDialog(JFrame owner, DesktopSettings settings) {
        super(owner, "Settings", true);
        this.settings = settings;
        linkFileField.setText(settings.linkFile().toString());
        playerField.setText(settings.player());
        layoutWeightsField.setText(settings.layoutWeights());
        CameraNpcSettings cameraNpc = settings.cameraNpcSettings();
        cameraEnabled.setSelected(cameraNpc.cameraEnabled());
        npcEnabled.setSelected(cameraNpc.npcEnabled());
        maxNpcs.setValue(cameraNpc.maxNpcs());
        cameraHeight.setValue(cameraNpc.cameraHeight());
        rotationSpeed.setValue(cameraNpc.rotationSpeed());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Link file:"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(linkFileField, c);
        c.gridx = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        JButton browse = new JButton("Browse...");
        browse.addActionListener(event -> browse());
        form.add(browse, c);

        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 2;
        JLabel linkHelp = new JLabel("<html>Minecraft writes this file while a world is open. Default launcher: "
            + LinkFileLocator.defaultLinkFile() + "<br>Other launchers: <i>&lt;instance folder&gt;\\config\\architect\\"
            + LinkProtocol.LINK_FILE_NAME + "</i></html>");
        linkHelp.setForeground(Color.GRAY);
        form.add(linkHelp, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        form.add(new JLabel("Player name:"), c);
        c.gridx = 1;
        form.add(playerField, c);
        c.gridy = 3;
        c.gridwidth = 2;
        JLabel playerHelp = new JLabel("Optional. Leave empty in single-player; on a server, enter your Minecraft name.");
        playerHelp.setForeground(Color.GRAY);
        form.add(playerHelp, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton defaults = new JButton("Use default");
        defaults.addActionListener(event -> {
            linkFileField.setText(LinkFileLocator.defaultLinkFile().toString());
            playerField.setText("");
        });
        JButton save = new JButton("Save and apply");
        save.addActionListener(event -> save());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        buttons.add(defaults);
        buttons.add(save);
        buttons.add(cancel);

        JPanel cameraPanel = new JPanel(new GridBagLayout());
        cameraPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints row = new GridBagConstraints();
        row.anchor = GridBagConstraints.WEST;
        row.insets = new Insets(5, 5, 5, 5);
        row.gridwidth = 2;
        cameraPanel.add(cameraEnabled, row);
        row.gridy = 1;
        cameraPanel.add(npcEnabled, row);
        row.gridwidth = 1;
        row.gridy = 2;
        cameraPanel.add(new JLabel("Maximum NPCs (0–12, per build):"), row);
        row.gridx = 1;
        cameraPanel.add(maxNpcs, row);
        row.gridx = 0;
        row.gridy = 3;
        cameraPanel.add(new JLabel("Camera height (5–80 blocks):"), row);
        row.gridx = 1;
        cameraPanel.add(cameraHeight, row);
        row.gridx = 0;
        row.gridy = 4;
        cameraPanel.add(new JLabel("Rotation speed (1–30 degrees/second):"), row);
        row.gridx = 1;
        cameraPanel.add(rotationSpeed, row);
        row.gridx = 0;
        row.gridy = 5;
        row.gridwidth = 2;
        cameraPanel.add(new JLabel("<html>Saved locally. Applied to Minecraft when connected; camera is off until filming starts."
            + "<br>Disabled by default for safe single-player and multiplayer use.</html>"), row);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Connection", form);
        tabs.addTab("Camera & NPC Settings", cameraPanel);
        JPanel generationPanel = new JPanel(new BorderLayout(0, 8));
        generationPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        generationPanel.add(new JLabel("Advanced layout weights (Auto layout):"), BorderLayout.NORTH);
        generationPanel.add(layoutWeightsField, BorderLayout.CENTER);
        generationPanel.add(new JLabel("<html>Comma-separated layout=weight, e.g. RADIAL=1,LINEAR=2,GRID=0."
            + "<br>Weighted layouts: RADIAL, LINEAR, TERRACED, RING, GRID, CLIFF (case-insensitive)."
            + "<br>Finite nonnegative decimals only; no duplicate layouts. Blank uses built-in weights."
            + "<br>Saved per user in Preferences under layoutWeights.</html>"), BorderLayout.SOUTH);
        tabs.addTab("Plan generation", generationPanel);
        getContentPane().add(tabs, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(owner);
    }

    boolean saved() {
        return saved;
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser();
        try {
            Path current = Path.of(linkFileField.getText().trim()).getParent();
            if (current != null && current.toFile().isDirectory()) {
                chooser.setCurrentDirectory(current.toFile());
            }
        } catch (InvalidPathException ignored) {
            // start in the default folder
        }
        chooser.setFileFilter(new FileNameExtensionFilter("Architect link file (" + LinkProtocol.LINK_FILE_NAME + ")", "json"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            linkFileField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void save() {
        String player = playerField.getText().trim();
        if (!player.isEmpty() && !LinkProtocol.PLAYER_NAME.matcher(player).matches()) {
            JOptionPane.showMessageDialog(this, "A Minecraft name has 1-16 letters, digits or '_'.", "Player name",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path linkFile;
        try {
            PlanGenerationModel.parseWeights(layoutWeightsField.getText());
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Layout weights", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            linkFile = Path.of(linkFileField.getText().trim());
        } catch (InvalidPathException ex) {
            JOptionPane.showMessageDialog(this, "That is not a valid file path.", "Link file", JOptionPane.WARNING_MESSAGE);
            return;
        }
        settings.setLinkFile(linkFile);
        settings.setPlayer(player);
        settings.setLayoutWeights(layoutWeightsField.getText());
        settings.setCameraNpcSettings(new CameraNpcSettings(cameraEnabled.isSelected(), npcEnabled.isSelected(),
            (int) maxNpcs.getValue(), (int) cameraHeight.getValue(), (int) rotationSpeed.getValue()));
        saved = true;
        dispose();
    }
}
