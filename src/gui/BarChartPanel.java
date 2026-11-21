package gui;

import model.ReorderSuggestion;
import model.UrgencyLevel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// A bar chart drawn by hand with Graphics2D. It implements Scrollable so it can sit
// inside a JScrollPane: the width follows the window, the height grows with the products.
public class BarChartPanel extends JPanel implements Scrollable {
    private static final double MAX_DAYS = 30;   // bars are capped at 30 days
    private static final int LABEL_WIDTH = 120;
    private static final int RIGHT_PAD = 80;
    private static final int ROW_HEIGHT = 32;
    private static final int TOP = 8;

    private List<ReorderSuggestion> data = new ArrayList<>();

    public BarChartPanel() {
        setOpaque(false);
    }

    public void setData(List<ReorderSuggestion> newData) {
        data = new ArrayList<>(newData);
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(300, TOP * 2 + Math.max(1, data.size()) * ROW_HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(Theme.BODY);
        FontMetrics fm = g2.getFontMetrics();

        if (data.isEmpty()) {
            g2.setColor(Color.GRAY);
            g2.drawString("No products yet.", 8, TOP + 20);
            g2.dispose();
            return;
        }

        int barAreaWidth = Math.max(40, getWidth() - LABEL_WIDTH - RIGHT_PAD);

        // the vertical axis line
        g2.setColor(new Color(0xCCCCCC));
        g2.drawLine(LABEL_WIDTH, TOP, LABEL_WIDTH, TOP + data.size() * ROW_HEIGHT);

        int y = TOP;
        for (ReorderSuggestion s : data) {
            int baseline = y + (ROW_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();

            // product name on the left
            g2.setColor(Color.BLACK);
            g2.drawString(shorten(s.getProduct().getName(), fm, LABEL_WIDTH - 12), 6, baseline);

            // the bar
            boolean noSales = s.getDaysLeft() == Double.MAX_VALUE;
            double shown = noSales ? MAX_DAYS : Math.min(s.getDaysLeft(), MAX_DAYS);
            int barWidth = Math.max(4, (int) Math.round(barAreaWidth * shown / MAX_DAYS));
            g2.setColor(colorFor(s.getUrgency()));
            g2.fillRoundRect(LABEL_WIDTH + 1, y + 6, barWidth, ROW_HEIGHT - 12, 10, 10);

            // the value to the right of the bar
            String value;
            if (noSales) value = "no sales";
            else if (s.getDaysLeft() > MAX_DAYS) value = "30+ d";
            else value = String.format("%.1f d", s.getDaysLeft());
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(value, LABEL_WIDTH + barWidth + 8, baseline);

            y += ROW_HEIGHT;
        }
        g2.dispose();
    }

    private String shorten(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String t = text;
        while (t.length() > 1 && fm.stringWidth(t + "...") > maxWidth) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }

    private Color colorFor(UrgencyLevel level) {
        switch (level) {
            case CRITICAL: return Theme.CRITICAL;
            case WARNING:  return Theme.WARNING;
            default:       return Theme.SAFE;
        }
    }

    // ---------- Scrollable ----------

    @Override
    public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 20; }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 80; }

    @Override
    public boolean getScrollableTracksViewportWidth() { return true; }

    @Override
    public boolean getScrollableTracksViewportHeight() { return false; }
}