package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.video.voice.VoicePack;
import com.annaschneider.minecraft1.video.voice.XttsTtsEngine;

import javax.swing.DefaultComboBoxModel;
import java.util.List;

/** Rebuilding a list must never substitute another speaker for a saved ID. */
final class VoiceSelection {
    static final String NONE = "(No narration)";

    private VoiceSelection() { }

    static DefaultComboBoxModel<Object> model(List<VoicePack> voices, String id, boolean cloned) {
        DefaultComboBoxModel<Object> model = new DefaultComboBoxModel<>();
        model.addElement(NONE);
        VoicePack selected = null;
        for (VoicePack voice : voices) {
            if (cloned == XttsTtsEngine.ID.equals(voice.engine())) {
                model.addElement(voice);
                if (voice.id().equals(id)) {
                    selected = voice;
                }
            }
        }
        model.setSelectedItem(selected == null ? NONE : selected);
        return model;
    }
}
