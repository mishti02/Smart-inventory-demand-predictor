package gui;

import model.Product;
import model.ReorderSuggestion;
import service.DemandPredictor;
import service.InventoryManager;
import service.ReportExporter;
import service.ReportService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class ReportPanel extends JPanel {
    private static final int SALES_DAYS = 14;
    private static final int DEAD_DAYS = 30;

    private final DemandPredictor predictor;
    private final ReportService reports;
    private final ReportExporter exporter = new ReportExporter("data");

    private final BarChartPanel chart = new BarChartPanel();
    private final DefaultTableModel categoryModel = makeModel("Category", "Units sold");
    private final DefaultTableModel topModel = makeModel("Rank", "Product", "Units sold");
    private final DefaultTableModel deadModel = makeModel("Product", "Category", "Stock");

    public ReportPanel(InventoryManager manager) {
        this.predictor = new DemandPredictor(manager);
        this.reports = new ReportService(manager);

        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(32, 36, 32, 36));

        add(buildHeader(), BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 2, 16, 16));
        grid.setOpaque(false);
        grid.add(makeSection("Days left per product", scroll(chart)));
        grid.add(makeSection("Units sold by category (last " + SALES_DAYS + " days)",
                scroll(makeTable(categoryModel))));
        grid.add(makeSection("Top sellers (last " + SALES_DAYS + " days)",
                scroll(makeTable(topModel))));
        grid.add(makeSection("Dead stock (no sales in " + DEAD_DAYS + " days)",
                scroll(makeTable(deadModel))));
        add(grid, BorderLayout.CENTER);

        refresh();
    }

    // ---------- layout ----------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Reports");
        title.setFont(Theme.TITLE);
        header.add(title, BorderLayout.WEST);

        JButton export = new JButton("Export report");
        export.setFont(Theme.BODY);
        export.setBackground(Theme.GREEN);
        export.setForeground(Color.BLACK);
        export.setFocusPainted(false);
        export.addActionListener(e -> exportReport());
        header.add(export, BorderLayout.EAST);
        return header;
    }

    private RoundedPanel makeSection(String title, JComponent content) {
        RoundedPanel card = new RoundedPanel(Color.WHITE, 28);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JLabel label = new JLabel(title);
        label.setFont(Theme.NAV.deriveFont(Font.BOLD, 16f));
        card.add(label, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JScrollPane scroll(JComponent content) {
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        return sp;
    }

    private static DefaultTableModel makeModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
    }

    private JTable makeTable(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setFont(Theme.BODY);
        t.setRowHeight(26);
        return t;
    }

    // ---------- fill the page with fresh data (called every time the page is shown) ----------

    public void refresh() {
        LocalDate today = LocalDate.now();

        chart.setData(predictor.generateSuggestions(7, 2, 14, today));

        categoryModel.setRowCount(0);
        for (Map.Entry<String, Integer> e : reports.unitsByCategory(SALES_DAYS, today).entrySet()) {
            categoryModel.addRow(new Object[]{e.getKey(), e.getValue()});
        }

        topModel.setRowCount(0);
        int rank = 1;
        for (Map.Entry<String, Integer> e : reports.topSellers(SALES_DAYS, today, 5)) {
            topModel.addRow(new Object[]{rank++, e.getKey(), e.getValue()});
        }

        deadModel.setRowCount(0);
        for (Product p : reports.deadStock(DEAD_DAYS, today)) {
            deadModel.addRow(new Object[]{p.getName(), p.getCategory(), p.getCurrentStock()});
        }
    }

    // ---------- export ----------

    private void exportReport() {
        LocalDate today = LocalDate.now();
        List<ReorderSuggestion> list = predictor.generateSuggestions(7, 2, 14, today);
        String text = reports.buildTextReport(list, SALES_DAYS, DEAD_DAYS, today);

        try {
            Path saved = exporter.export(text, today);
            JOptionPane.showMessageDialog(this, "Report saved to:\n" + saved,
                    "Report exported", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save the report:\n" + ex.getMessage(),
                    "Export error", JOptionPane.ERROR_MESSAGE);
        }
    }
}