package gui;

import exception.InvalidInputException;
import model.Product;
import service.InventoryManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.regex.Pattern;

public class ProductPanel extends JPanel {
    private final InventoryManager manager;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final TableRowSorter<DefaultTableModel> sorter;

    private final JTextField nameField = new JTextField();
    private final JTextField categoryField = new JTextField();
    private final JTextField stockField = new JTextField();
    private final JTextField leadField = new JTextField();
    private final JTextField searchField = new JTextField();

    public ProductPanel(InventoryManager manager) {
        this.manager = manager;

        tableModel = new DefaultTableModel(
                new String[]{"ID", "Name", "Category", "Stock", "Lead Time (days)"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;   // the table is read-only, editing happens in the form
            }

            @Override
            public Class<?> getColumnClass(int col) {
                return (col == 0 || col >= 3) ? Integer.class : String.class;   // numbers sort as numbers
            }
        };
        table = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        setLayout(new BorderLayout(20, 0));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(32, 36, 32, 36));

        add(buildLeft(), BorderLayout.CENTER);
        add(buildForm(), BorderLayout.EAST);
        refreshTable();
    }

    // ---------- layout ----------

    private JPanel buildLeft() {
        JPanel left = new JPanel(new BorderLayout(0, 14));
        left.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout(0, 12));
        header.setOpaque(false);
        JLabel title = new JLabel("Products");
        title.setFont(Theme.TITLE);
        header.add(title, BorderLayout.NORTH);

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(Theme.BODY);
        searchField.setFont(Theme.BODY);
        searchRow.add(searchLabel, BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        header.add(searchRow, BorderLayout.SOUTH);
        left.add(header, BorderLayout.NORTH);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        table.setFont(Theme.BODY);
        table.setRowHeight(28);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) fillFormFromSelection();
        });

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

        JLabel formTitle = new JLabel("Product details");
        formTitle.setFont(Theme.NAV.deriveFont(Font.BOLD, 17f));
        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(formTitle);
        form.add(Box.createRigidArea(new Dimension(0, 16)));

        addField(form, "Name", nameField);
        addField(form, "Category", categoryField);
        addField(form, "Stock", stockField);
        addField(form, "Lead time (days)", leadField);

        form.add(makeButton("Add", Theme.GREEN, Color.BLACK, this::addProduct));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(makeButton("Update", Theme.BLUE, Color.BLACK, this::updateProduct));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(makeButton("Restock +", Theme.YELLOW, Color.BLACK, this::restockProduct));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(makeButton("Delete", Theme.CRITICAL, Color.WHITE, this::deleteProduct));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(makeButton("Clear", new Color(0xDDDDDD), Color.BLACK, this::clearForm));
        form.add(Box.createVerticalGlue());
        return form;
    }

    private void addField(JPanel form, String label, JTextField field) {
        JLabel l = new JLabel(label);
        l.setFont(Theme.BODY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setFont(Theme.BODY);
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

    private void addProduct() {
        try {
            manager.addProduct(
                    nameField.getText(),
                    categoryOrDefault(),
                    Integer.parseInt(stockField.getText().trim()),
                    Integer.parseInt(leadField.getText().trim()));
            refreshTable();
            clearForm();
        } catch (InvalidInputException | NumberFormatException ex) {
            showError(ex);
        }
    }

    private void updateProduct() {
        int id = requireSelectedId();
        if (id < 0) return;
        try {
            manager.updateProduct(
                    id,
                    nameField.getText(),
                    categoryOrDefault(),
                    Integer.parseInt(stockField.getText().trim()),
                    Integer.parseInt(leadField.getText().trim()));
            refreshTable();
            clearForm();
        } catch (InvalidInputException | NumberFormatException ex) {
            showError(ex);
        }
    }

    private void restockProduct() {
        int id = requireSelectedId();
        if (id < 0) return;
        String input = JOptionPane.showInputDialog(this, "Units to add:");
        if (input == null) return;   // user pressed Cancel
        try {
            manager.restock(id, Integer.parseInt(input.trim()));
            refreshTable();
            clearForm();
        } catch (InvalidInputException | NumberFormatException ex) {
            showError(ex);
        }
    }

    private void deleteProduct() {
        int id = requireSelectedId();
        if (id < 0) return;
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete this product and its sales history?", "Confirm delete",
                JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;
        try {
            manager.deleteProduct(id);
            refreshTable();
            clearForm();
        } catch (InvalidInputException ex) {
            showError(ex);
        }
    }

    private void clearForm() {
        nameField.setText("");
        categoryField.setText("");
        stockField.setText("");
        leadField.setText("");
        table.clearSelection();
    }

    // ---------- helpers ----------

    public void refreshTable() {
        tableModel.setRowCount(0);
        for (Product p : manager.getAllProducts()) {
            tableModel.addRow(new Object[]{
                    p.getId(), p.getName(), p.getCategory(),
                    p.getCurrentStock(), p.getLeadTimeDays()});
        }
    }

    private void applyFilter() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text), 1, 2));
        }
    }

    private void fillFormFromSelection() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return;
        int row = table.convertRowIndexToModel(viewRow);
        nameField.setText(tableModel.getValueAt(row, 1).toString());
        categoryField.setText(tableModel.getValueAt(row, 2).toString());
        stockField.setText(tableModel.getValueAt(row, 3).toString());
        leadField.setText(tableModel.getValueAt(row, 4).toString());
    }

    private int requireSelectedId() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a product from the table first.",
                    "No selection", JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        int row = table.convertRowIndexToModel(viewRow);
        return (Integer) tableModel.getValueAt(row, 0);
    }

    private String categoryOrDefault() {
        String c = categoryField.getText().trim();
        return c.isEmpty() ? "General" : c;
    }

    private void showError(Exception ex) {
        String message = (ex instanceof NumberFormatException)
                ? "Stock and lead time must be whole numbers."
                : ex.getMessage();
        JOptionPane.showMessageDialog(this, message, "Invalid input", JOptionPane.ERROR_MESSAGE);
    }
}