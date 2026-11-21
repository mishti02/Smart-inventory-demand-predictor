package gui;

import model.Product;
import model.ReorderSuggestion;
import model.UrgencyLevel;
import service.DemandPredictor;
import service.InventoryManager;
import service.PredictionStrategy;
import service.SimpleAverageStrategy;
import service.WeightedAverageStrategy;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

public class PredictionPanel extends JPanel {
    private static final int STATUS_COLUMN = 6;

    private final DemandPredictor predictor;
    private final DefaultTableModel tableModel;
    private final JTable table;

    private final JComboBox<String> methodCombo =
            new JComboBox<>(new String[]{"Simple average", "Weighted average"});
    private final JRadioButton allHistory = new JRadioButton("All history");
    private final JRadioButton last7 = new JRadioButton("Last 7 days", true);
    private final JRadioButton last14 = new JRadioButton("Last 14 days");
    private final JSpinner safetySpinner = new JSpinner(new SpinnerNumberModel(2, 0, 60, 1));
    private final JSpinner coverSpinner = new JSpinner(new SpinnerNumberModel(14, 1, 120, 1));
    private final JLabel summaryLabel = new JLabel(" ");

    public PredictionPanel(InventoryManager manager) {
        this.predictor = new DemandPredictor(manager);

        tableModel = new DefaultTableModel(new String[]{
                "Rank", "Product", "Stock", "Avg/Day", "Days Left", "Lead", "Status", "Reorder Qty"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        table = new JTable(tableModel);

        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(32, 36, 32, 36));

        add(buildTop(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);

        summaryLabel.setFont(Theme.BODY);
        add(summaryLabel, BorderLayout.SOUTH);

        calculate();
    }

    // ---------- layout ----------

    private JPanel buildTop() {
        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);

        JLabel title = new JLabel("Predictions");
        title.setFont(Theme.TITLE);
        top.add(title, BorderLayout.NORTH);

        // two rows of controls, so nothing is cut off in a narrow window
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(Color.WHITE);

        ButtonGroup group = new ButtonGroup();
        for (JRadioButton rb : new JRadioButton[]{allHistory, last7, last14}) {
            rb.setFont(Theme.BODY);
            rb.setOpaque(false);
            group.add(rb);
        }
        methodCombo.setFont(Theme.BODY);
        methodCombo.setToolTipText("Weighted: recent days count more than older days");
        safetySpinner.setToolTipText("Reorder when days left <= lead time + safety days");
        coverSpinner.setToolTipText("How many days of sales the new order should cover");

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        row1.setOpaque(false);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);
        row1.add(makeLabel("Method:"));
        row1.add(methodCombo);
        row1.add(makeLabel("Analysis window:"));
        row1.add(allHistory);
        row1.add(last7);
        row1.add(last14);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        row2.setOpaque(false);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        row2.add(makeLabel("Safety days:"));
        row2.add(safetySpinner);
        row2.add(makeLabel("Cover days:"));
        row2.add(coverSpinner);

        JButton calcButton = new JButton("Calculate");
        calcButton.setFont(Theme.BODY);
        calcButton.setBackground(Theme.GREEN);
        calcButton.setFocusPainted(false);
        calcButton.addActionListener(e -> calculate());
        row2.add(calcButton);

        controls.add(row1);
        controls.add(row2);
        top.add(controls, BorderLayout.CENTER);
        return top;
    }

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.BODY);
        return l;
    }

    private JScrollPane buildTable() {
        table.setRowHeight(34);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setFont(Theme.BODY.deriveFont(Font.BOLD));
        table.setDefaultRenderer(Object.class, new UrgencyRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(Color.WHITE);
        return scroll;
    }

    // ---------- calculation ----------

    // Called by the Calculate button and by MainFrame whenever this page is shown
    public void calculate() {
        int window = allHistory.isSelected() ? 0 : (last14.isSelected() ? 14 : 7);
        int safety = (Integer) safetySpinner.getValue();
        int cover = (Integer) coverSpinner.getValue();

        // POLYMORPHISM: the variable has the interface type, the object is one of two classes.
        // Java decides at run time which calculate() to use.
        PredictionStrategy strategy = (methodCombo.getSelectedIndex() == 1)
                ? new WeightedAverageStrategy(window)
                : new SimpleAverageStrategy(window);

        List<ReorderSuggestion> list =
                predictor.generateSuggestions(strategy, safety, cover, LocalDate.now());

        tableModel.setRowCount(0);
        int critical = 0, warning = 0, safe = 0, rank = 1;

        for (ReorderSuggestion s : list) {
            Product p = s.getProduct();
            String daysLeft = (s.getDaysLeft() == Double.MAX_VALUE)
                    ? "No sales" : String.format("%.2f", s.getDaysLeft());

            tableModel.addRow(new Object[]{
                    rank++,
                    p.getName(),
                    p.getCurrentStock(),
                    String.format("%.2f", s.getAvgDailySales()),
                    daysLeft,
                    p.getLeadTimeDays(),
                    s.getUrgency().name(),
                    s.getSuggestedQty()});

            switch (s.getUrgency()) {
                case CRITICAL: critical++; break;
                case WARNING:  warning++;  break;
                default:       safe++;     break;
            }
        }

        if (list.isEmpty()) {
            summaryLabel.setText("No products yet. Add products and record sales first.");
        } else {
            summaryLabel.setText(list.size() + " products: " + critical + " critical, "
                    + warning + " warning, " + safe + " safe   |   Method: " + strategy.getName());
        }
    }

    // ---------- colors ----------

    private static class UrgencyRenderer extends DefaultTableCellRenderer {
        private static final Color CRITICAL_TINT = new Color(0xFDE0DF);
        private static final Color WARNING_TINT = new Color(0xFFF1CC);
        private static final Color SAFE_TINT = new Color(0xE3F1E4);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setFont(Theme.BODY);
            setHorizontalAlignment(column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
            if (isSelected) return this;

            int modelRow = table.convertRowIndexToModel(row);
            UrgencyLevel level = UrgencyLevel.valueOf(
                    table.getModel().getValueAt(modelRow, STATUS_COLUMN).toString());

            Color tint, strong, strongText;
            switch (level) {
                case CRITICAL:
                    tint = CRITICAL_TINT; strong = Theme.CRITICAL; strongText = Color.WHITE;
                    break;
                case WARNING:
                    tint = WARNING_TINT; strong = Theme.WARNING; strongText = Color.BLACK;
                    break;
                default:
                    tint = SAFE_TINT; strong = Theme.SAFE; strongText = Color.WHITE;
                    break;
            }

            if (column == STATUS_COLUMN) {
                setBackground(strong);
                setForeground(strongText);
                setFont(Theme.BODY.deriveFont(Font.BOLD));
            } else {
                setBackground(tint);
                setForeground(Color.BLACK);
            }
            return this;
        }
    }
}