import gui.GradientPanel;
import gui.Icons;
import gui.Pill;
import gui.RoundedPanel;
import gui.SegmentedControl;
import gui.Theme;
import gui.UiStyle;
import model.UrgencyLevel;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

// A quick look at the new building blocks. It is only for checking Round 1.
public class UiPreview {
    public static void main(String[] args) {
        UiStyle.install();
        SwingUtilities.invokeLater(UiPreview::show);
    }

    private static void show() {
        JFrame frame = new JFrame("UI preview (Round 1)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(Theme.PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        root.add(heading("1. Gradient hero card"));
        root.add(heroCard());
        root.add(gap());

        root.add(heading("2. Status pills and change chips"));
        JPanel pills = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        pills.setOpaque(false);
        pills.add(Pill.urgency(UrgencyLevel.CRITICAL));
        pills.add(Pill.urgency(UrgencyLevel.WARNING));
        pills.add(Pill.urgency(UrgencyLevel.SAFE));
        pills.add(Pill.delta(8.4, true));
        pills.add(Pill.delta(-3.1, true));
        pills.add(Pill.delta(-11.2, false));
        root.add(left(pills));
        root.add(gap());

        root.add(heading("3. Segmented control (click the options)"));
        JPanel segmentRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        segmentRow.setOpaque(false);
        SegmentedControl segments = new SegmentedControl("Last 7 days", "Last 14 days", "All history");
        JLabel chosen = new JLabel("Selected: Last 7 days");
        chosen.setFont(Theme.BODY);
        segments.addChangeListener(i -> chosen.setText("Selected option number: " + i));
        segmentRow.add(segments);
        segmentRow.add(chosen);
        root.add(left(segmentRow));
        root.add(gap());

        root.add(heading("4. Icons"));
        RoundedPanel iconCard = new RoundedPanel(Theme.CARD, 24);
        iconCard.setLayout(new GridLayout(2, 5, 12, 14));
        iconCard.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        for (Icons.Type type : Icons.Type.values()) {
            JLabel label = new JLabel(type.name(), Icons.of(type, 28, Theme.PRIMARY), SwingConstants.CENTER);
            label.setFont(Theme.SMALL);
            label.setVerticalTextPosition(SwingConstants.BOTTOM);
            label.setHorizontalTextPosition(SwingConstants.CENTER);
            iconCard.add(label);
        }
        root.add(left(iconCard));
        root.add(gap());

        root.add(heading("5. Rounded buttons and fields"));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        controls.setOpaque(false);

        JButton primary = new JButton("Add product");
        primary.setBackground(Theme.PRIMARY);
        primary.setForeground(Color.WHITE);

        JTextField search = new JTextField(16);
        search.putClientProperty("JTextField.placeholderText", "Search products...");
        search.putClientProperty("JComponent.roundRect", true);

        controls.add(primary);
        controls.add(new JButton("Cancel"));
        controls.add(search);
        controls.add(new JComboBox<>(new String[]{"Dairy", "Grocery", "Bakery"}));
        controls.add(new JSpinner(new SpinnerNumberModel(2, 0, 60, 1)));
        root.add(left(controls));

        frame.setContentPane(root);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JComponent heroCard() {
        GradientPanel card = new GradientPanel(Theme.HERO_TOP, Theme.HERO_BOTTOM, 28);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        Dimension size = new Dimension(340, 150);
        card.setPreferredSize(size);
        card.setMaximumSize(size);

        JLabel title = new JLabel("Total Products");
        title.setFont(Theme.NAV.deriveFont(Font.BOLD, 16f));
        title.setForeground(Color.WHITE);

        JLabel number = new JLabel("12");
        number.setFont(Theme.BIG.deriveFont(40f));
        number.setForeground(Color.WHITE);

        card.add(left(title));
        card.add(left(number));
        card.add(javax.swing.Box.createVerticalStrut(8));
        card.add(left(Pill.delta(8.4, true)));
        return left(card);
    }

    private static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.H2);
        label.setForeground(Theme.TEXT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        return (JLabel) left(label);
    }

    private static Component gap() {
        return javax.swing.Box.createVerticalStrut(18);
    }

    private static <T extends JComponent> T left(T component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }
}