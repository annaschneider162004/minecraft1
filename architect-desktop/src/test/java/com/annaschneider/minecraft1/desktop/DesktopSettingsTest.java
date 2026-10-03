package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.CameraNpcSettings;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DesktopSettingsTest {
    @Test
    void cameraNpcPreferencesRoundTripAcrossInstances() throws Exception {
        Preferences node = Preferences.userRoot().node("architect-test-" + UUID.randomUUID());
        try {
            DesktopSettings first = new DesktopSettings(node);
            assertEquals("", first.layoutWeights());
            first.setLayoutWeights("RADIAL=1,LINEAR=2");
            assertThrows(IllegalArgumentException.class, () -> first.setLayoutWeights("GRID=NaN"));
            assertEquals(CameraNpcSettings.defaults(), first.cameraNpcSettings());
            first.setCameraNpcSettings(new CameraNpcSettings(true, true, 30, 100, -3));
            node.flush();
            DesktopSettings reopened = new DesktopSettings(Preferences.userRoot().node(node.absolutePath()));
            assertEquals("RADIAL=1,LINEAR=2", reopened.layoutWeights());
            assertEquals(new CameraNpcSettings(true, true, 12, 80, 1), reopened.cameraNpcSettings());
            node.putInt("cameraHeight", -100);
            assertEquals(5, reopened.cameraNpcSettings().cameraHeight());
        } finally {
            node.removeNode();
        }
    }
}
