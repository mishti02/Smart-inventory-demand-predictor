package gui;

import exception.InsufficientStockException;
import exception.InvalidInputException;
import model.Product;
import model.SaleRecord;
import service.InventoryManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class SalesPanel extends JPanel {
    private final InventoryManager manager;
    private final DefaultTableModel tableModel;

    private final JComboBox<Product> productCombo = new JComboBox<>();
    private final JTextField dateField = new JTextField(LocalDate.now().toString());
    private final JTextField qtyField = new JTextField();
    private final JLabel statusLabel = new JLabel(" ");

    public SalesPanel(InventoryManager manager) {
        this.manager = manager;

        tableModel = new DefaultTableModel(new String[]{"Date", "Product", "Quantity"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int col) {
                return col == 2 ? Integer.class : String.class;
            }
        };

        setLayout(new BorderLayout(20, 0));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(32, 36, 32, 36));

        add(buildLeft(), BorderLayout.CENTER);
        add(buildForm(), BorderLayout.EAST);
        refresh();
    }

    // ---------- layout ----------

    private JPanel buildLeft() {
        JPanel left = new JPanel(new BorderLayout(0, 14));
        left.setOpaque(false);

        JLabel title = new JLabel("Record Sales");
        title.setFont(Theme.TITLE);
        left.add(title, BorderLayout.NORTH);

        JTable table = new JTable(tableModel);
        table.setFont(Theme.BODY);
        table.setRowHeight(28);

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(Color.WHITE);
        left.add(scroll, BorderLayout.CENTER);
        return left;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Color.WHITE);
        form.setPreferredSize(new Dimension(280, 0));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel formTitle = new JLabel("New sale");
        formTitle.setFont(Theme.NAV.deriveFont(Font.BOLD, 17f));
        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(formTitle);
        form.add(Box.createRigidArea(new Dimension(0, 16)));

        productCombo.setFont(Theme.BODY);
        dateField.setFont(Theme.BODY);
        qtyField.setFont(Theme.BODY);

        addRow(form, "Product", productCombo);
        addRow(form, "Date (yyyy-MM-dd)", dateField);
        addRow(form, "Quantity", qtyField);

        form.add(makeButton("Record Sale", Theme.GREEN, Color.BLACK, this::recordSale));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(makeButton("Generate Sample Data", Theme.BLUE, Color.BLACK, this::generateSampleData));
        form.add(Box.createRigidArea(new Dimension(0, 14)));

        statusLabel.setFont(Theme.BODY);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(statusLabel);
        form.add(Box.createVerticalGlue());
        return form;
    }

    private void addRow(JPanel form, String label, JComponent field) {
        JLabel l = new JLabel(label);
        l.setFont(Theme.BODY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        form.add(l);
        form.add(Box.createRigidArea(new Dimension(0, 4)));
        form.add(field);
        form.add(Box.createRigidArea(new Dimension(0, 12)));
    }

    private JButton makeButton(String text, Color bg, Color fg, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(Theme.BODY);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFocusPainted(false);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        b.addActionListener(e -> action.run());
        return b;
    }

    // ---------- button actions ----------

    private void recordSale() {
        try {
            Product p = (Product) productCombo.getSelectedItem();
            if (p == null) {
                throw new InvalidInputException("Add a product first on the Products page.");
            }
            LocalDate date = LocalDate.parse(dateField.getText().trim());
            if (date.isAfter(LocalDate.now())) {
                throw new InvalidInputException("Date cannot be in the future.");
            }
            int qty = Integer.parseInt(qtyField.getText().trim());

            manager.recordSale(p.getId(), date, qty);

            statusLabel.setText("Recorded: " + qty + " x " + p.getName());
            qtyField.setText("");
            refresh();
        } catch (InvalidInputException | InsufficientStockException
                 | NumberFormatException | DateTimeParseException ex) {
            showError(ex);
        }
    }

    private void generateSampleData() {
        List<Product> products = manager.getAllProducts();
        if (products.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add products first on the Products page.",
                    "No products", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Add 14 days of random sales for every product?\nThis also reduces their stock.",
                "Generate sample data", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;

        Random random = new Random();
        LocalDate today = LocalDate.now();
        int added = 0;

        for (Product p : products) {
            for (int daysAgo = 13; daysAgo >= 0; daysAgo--) {
                int qty = 1 + random.nextInt(4);          // 1 to 4 units
                if (qty > p.getCurrentStock()) continue;  // not enough stock, skip this day
                try {
                    manager.recordSale(p.getId(), today.minusDays(daysAgo), qty);
                    added++;
                } catch (InvalidInputException | InsufficientStockException ex) {
                    // skipped, nothing to do
                }
            }
        }
        statusLabel.setText("Added " + added + " sample sales");
        refresh();
    }

    // ---------- helpers ----------

    // Called by MainFrame every time this page is shown
    public void refresh() {
        refreshProducts();
        refreshSalesTable();
    }

    private void refreshProducts() {
        Product selected = (Product) productCombo.getSelectedItem();
        int selectedId = (selected == null) ? -1 : selected.getId();

        productCombo.removeAllItems();
        Product toSelect = null;
        for (Product p : manager.getAllProducts()) {
            productCombo.addItem(p);
            if (p.getId() == selectedId) toSelect = p;
        }
        if (toSelect != null) productCombo.setSelectedItem(toSelect);
    }

    private void refreshSalesTable() {
        List<SaleRecord> all = new ArrayList<>();
        for (Product p : manager.getAllProducts()) {
            all.addAll(manager.getSalesFor(p.getId()));
        }
        all.sort(Comparator.comparing(SaleRecord::getDate).reversed());   // newest first

        tableModel.setRowCount(0);
        for (SaleRecord r : all) {
            Product p = manager.getProduct(r.getProductId());
            tableModel.addRow(new Object[]{
                    r.getDate().toString(),
                    p == null ? "?" : p.getName(),
                    r.getQuantitySold()});
        }
    }

    private void showError(Exception ex) {
        String message;
        if (ex instanceof NumberFormatException) {
            message = "Quantity must be a whole number.";
        } else if (ex instanceof DateTimeParseException) {
            message = "Date must look like 2026-11-20 (yyyy-MM-dd).";
        } else {
            message = ex.getMessage();
        }
        JOptionPane.showMessageDialog(this, message, "Cannot record sale", JOptionPane.ERROR_MESSAGE);
    }
}