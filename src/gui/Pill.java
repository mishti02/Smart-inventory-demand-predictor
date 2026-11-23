package gui;

import model.UrgencyLevel;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

// A small label with a fully rounded background: used for status and for change chips
public class Pill extends JLabel {
    private final Color fill;

    public Pill(String text, Color fill, Color textColor) {
        super(text, SwingConstants.CENTER);
        this.fill = fill;
        setForeground(textColor);
        setFont(Theme.SMALL.deriveFont(Font.BOLD));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(g);   // draws the text on top
    }

    // CRITICAL, WARNING or SAFE
    public static Pill urgency(UrgencyLevel level) {
        switch (level) {
            case CRITICAL: return new Pill("CRITICAL", Theme.CRITICAL, Color.WHITE);
            case WARNING:  return new Pill("WARNING", Theme.WARNING, Color.BLACK);
            default:       return new Pill("SAFE", Theme.SAFE, Color.WHITE);
        }
    }

    // A change chip such as +8.4% (green) or -3.1% (red).
    // upIsGood = false for numbers where an increase is bad (for example critical products).
    public static Pill delta(double percent, boolean upIsGood) {
        if (Math.abs(percent) < 0.05) {
            return new Pill("0.0%", Theme.TRACK, Theme.MUTED);
        }
        String text = String.format("%s%.1f%%", percent > 0 ? "+" : "", percent);
        boolean good = (percent > 0) == upIsGood;
        return good
                ? new Pill(text, Theme.SAFE_TINT, new Color(0x1E8E4E))
                : new Pill(text, Theme.CRITICAL_TINT, new Color(0xC62F2F));
    }
}