package gui;

import javax.swing.JComponent;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

// A row of options in a rounded track: the selected one is filled with the brand blue.
// It replaces radio buttons such as "Last 7 days / Last 14 days / All history".
public class SegmentedControl extends JComponent {
    private static final int PAD = 4;           // space between the track edge and the segments
    private static final int SEGMENT_PAD = 16;  // space left and right of each label

    private final String[] options;
    private final List<IntConsumer> listeners = new ArrayList<>();
    private int selected = 0;

    public SegmentedControl(String... options) {
        this.options = options;
        setFont(Theme.BODY);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = indexAt(e.getX());
                if (index >= 0) setSelectedIndex(index);
            }
        });
    }

    public int getSelectedIndex() {
        return selected;
    }

    public void setSelectedIndex(int index) {
        if (index < 0 || index >= options.length || index == selected) return;
        selected = index;
        repaint();
        for (IntConsumer listener : listeners) {
            listener.accept(index);
        }
    }

    // The listener receives the index of the newly selected option
    public void addChangeListener(IntConsumer listener) {
        listeners.add(listener);
    }

    private int segmentWidth(FontMetrics fm, int i) {
        return fm.stringWidth(options[i]) + 2 * SEGMENT_PAD;
    }

    // Which option is under this x position? (-1 if none)
    private int indexAt(int x) {
        FontMetrics fm = getFontMetrics(getFont());
        int left = PAD;
        for (int i = 0; i < options.length; i++) {
            int w = segmentWidth(fm, i);
            if (x >= left && x < left + w) return i;
            left += w;
        }
        return -1;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int width = 2 * PAD;
        for (int i = 0; i < options.length; i++) {
            width += segmentWidth(fm, i);
        }
        return new Dimension(width, 38);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();   // stops layouts from stretching it
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();

        // the track
        g2.setColor(Theme.TRACK);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());

        int left = PAD;
        int segmentHeight = getHeight() - 2 * PAD;
        int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();

        for (int i = 0; i < options.length; i++) {
            int w = segmentWidth(fm, i);
            boolean active = (i == selected);

            if (active) {
                g2.setColor(Theme.PRIMARY);
                g2.fillRoundRect(left, PAD, w, segmentHeight, segmentHeight, segmentHeight);
            }
            g2.setColor(active ? java.awt.Color.WHITE : Theme.TEXT);
            g2.drawString(options[i], left + (w - fm.stringWidth(options[i])) / 2, textY);
            left += w;
        }
        g2.dispose();
    }
}