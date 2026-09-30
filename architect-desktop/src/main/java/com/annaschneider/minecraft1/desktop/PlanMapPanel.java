package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.PlanSummary;
import com.annaschneider.minecraft1.link.RegionBox;

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Top-down map of a plan: one coloured rectangle per region, with a legend and the build origin marked. */
final class PlanMapPanel extends JPanel {
    private static final Map<String, Color> COLORS = Map.of(
        "palace", new Color(0xE0B84C),
        "terrace", new Color(0xC9C2B0),
        "island", new Color(0x7A8B5A),
        "bridge", new Color(0xF2F2F2),
        "path", new Color(0xD8C79A),
        "garden", new Color(0x5DAA4E),
        "cherry", new Color(0xF2A7C3),
        "waterfall", new Color(0x4A90D9),
        "clouds", new Color(0xDDE6F0));
    private static final Color BACKGROUND = new Color(0x2B3A4A);
    private static final int PADDING = 16;

    private PlanSummary plan;

    PlanMapPanel() {
        setPreferredSize(new Dimension(420, 320));
        setBackground(BACKGROUND);
        setToolTipText("Top-down view of the planned build (north is up).");
    }

    void setPlan(PlanSummary plan) {
        this.plan = plan;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font base = UIManager.getFont("Label.font");
        g.setFont(base == null ? getFont() : base);
        if (plan == null || plan.regions().isEmpty()) {
            drawCentered(g, "Click \"Preview\" to see a top-down map of your build here.");
            g.dispose();
            return;
        }
        List<RegionBox> regions = plan.regions();
        int minX = 0, minZ = 0, maxX = 0, maxZ = 0;
        for (RegionBox box : regions) {
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxZ = Math.max(maxZ, box.maxZ());
        }
        double spanX = maxX - minX + 1;
        double spanZ = maxZ - minZ + 1;
        int legendHeight = 24;
        double scale = Math.min((getWidth() - 2.0 * PADDING) / spanX, (getHeight() - 2.0 * PADDING - legendHeight) / spanZ);
        if (scale <= 0) {
            g.dispose();
            return;
        }
        double offsetX = (getWidth() - spanX * scale) / 2 - minX * scale;
        double offsetZ = legendHeight + (getHeight() - legendHeight - spanZ * scale) / 2 - minZ * scale;

        Map<String, Color> legend = new LinkedHashMap<>();
        // clouds first and translucent so they do not hide the structures
        for (int pass = 0; pass < 2; pass++) {
            for (RegionBox box : regions) {
                boolean clouds = "clouds".equals(box.type());
                if ((pass == 0) != clouds) {
                    continue;
                }
                Color color = colorOf(box.type());
                legend.putIfAbsent(box.type(), color);
                int x = (int) Math.floor(offsetX + box.minX() * scale);
                int z = (int) Math.floor(offsetZ + box.minZ() * scale);
                int w = Math.max(1, (int) Math.ceil((box.maxX() - box.minX() + 1) * scale));
                int h = Math.max(1, (int) Math.ceil((box.maxZ() - box.minZ() + 1) * scale));
                g.setColor(clouds ? new Color(color.getRed(), color.getGreen(), color.getBlue(), 60) : color);
                g.fillRect(x, z, w, h);
                if (!clouds && w > 6 && h > 6) {
                    g.setColor(color.darker());
                    g.drawRect(x, z, w - 1, h - 1);
                }
            }
        }
        int ox = (int) Math.round(offsetX);
        int oz = (int) Math.round(offsetZ);
        g.setColor(Color.RED);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(ox - 6, oz, ox + 6, oz);
        g.drawLine(ox, oz - 6, ox, oz + 6);
        drawLegend(g, legend);
        g.setColor(new Color(255, 255, 255, 200));
        String size = plan.sizeX() + " x " + plan.sizeZ() + " blocks, " + plan.sizeY() + " high";
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(size, getWidth() - metrics.stringWidth(size) - 8, getHeight() - 8);
        g.drawString("+ = your position", 8, getHeight() - 8);
        g.dispose();
    }

    private void drawLegend(Graphics2D g, Map<String, Color> legend) {
        FontMetrics metrics = g.getFontMetrics();
        int x = 8;
        int y = 6;
        int shown = 0;
        for (Map.Entry<String, Color> entry : legend.entrySet()) {
            if (shown++ >= 10) {
                break;
            }
            int width = 14 + metrics.stringWidth(entry.getKey()) + 12;
            if (x + width > getWidth()) {
                break;
            }
            g.setColor(entry.getValue());
            g.fillRect(x, y + 2, 10, 10);
            g.setColor(Color.WHITE);
            g.drawString(entry.getKey(), x + 14, y + metrics.getAscent());
            x += width;
        }
    }

    private void drawCentered(Graphics2D g, String text) {
        g.setColor(new Color(255, 255, 255, 180));
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, Math.max(8, (getWidth() - metrics.stringWidth(text)) / 2), getHeight() / 2);
    }

    static Color colorOf(String type) {
        Color known = COLORS.get(type);
        if (known != null) {
            return known;
        }
        // template blocks: stable colour per block id
        float hue = (type.hashCode() & 0xFFFF) / (float) 0xFFFF;
        return Color.getHSBColor(hue, 0.35f, 0.85f);
    }
}
