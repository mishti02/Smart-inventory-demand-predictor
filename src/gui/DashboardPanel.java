package gui;

import model.Product;
import model.ReorderSuggestion;
import model.UrgencyLevel;
import service.DemandPredictor;
import service.InventoryManager;
import service.ReportService;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class DashboardPanel extends JPanel {
    private static final int ALERT_DAYS = 3;     // "running out soon" means within 3 days
    private static final int WINDOW_DAYS = 7;    // sales are analysed over the last 7 days

    private final InventoryManager manager;
    private final DemandPredictor predictor;
    private final ReportService reports;

    private final JLabel greetingLabel = new JLabel();
    private final JLabel summaryLabel = new JLabel();
    private final JPanel cardsRow = new JPanel(new GridLayout(1, 4, 16, 0));
    private final RoundedPanel listPanel = new RoundedPanel(Color.WHITE, 28);

    public DashboardPanel(InventoryManager manager) {
        this.manager = manager;
        this.predictor = new DemandPredictor(manager);
        this.reports = new ReportService(manager);

        setLayout(new BorderLayout(0, 18));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(32, 36, 32, 36));

        add(buildHeader(), BorderLayout.NORTH);

        cardsRow.setOpaque(false);
        cardsRow.setPreferredSize(new Dimension(0, 175));

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel body = new JPanel(new BorderLayout(0, 20));
        body.setOpaque(false);
        body.add(cardsRow, BorderLayout.NORTH);
        body.add(listPanel, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        refresh();
    }

    // ---------- layout ----------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setOpaque(false);
        greetingLabel.setFont(Theme.TITLE);
        summaryLabel.setFont(Theme.BODY);
        header.add(greetingLabel, BorderLayout.NORTH);
        header.add(summaryLabel, BorderLayout.CENTER);
        return header;
    }

    // ---------- fill the page with fresh data (called every time the page is shown) ----------

    public void refresh() {
        LocalDate today = LocalDate.now();
        List<ReorderSuggestion> list = predictor.generateSuggestions(WINDOW_DAYS, 2, 14, today);
        List<Product> products = manager.getAllProducts();

        // greeting and one-line summary
        greetingLabel.setText(greeting() + ", Shop Owner");
        long soon = reports.countRunningOutWithin(list, ALERT_DAYS);
        String summary;
        if (list.isEmpty()) {
            summary = "Add products on the Products page to get started.";
        } else if (soon == 0) {
            summary = "No products will run out within " + ALERT_DAYS + " days.";
        } else {
            summary = soon + (soon == 1 ? " product" : " products")
                    + " will run out within " + ALERT_DAYS + " days.";
        }
        summaryLabel.setText(summary);

        // numbers for the cards
        long categories = products.stream().map(Product::getCategory).distinct().count();
        int totalStock = products.stream().mapToInt(Product::getCurrentStock).sum();
        int sold = reports.unitsSold(WINDOW_DAYS, today);
        String best = reports.bestSeller(WINDOW_DAYS, today)
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .orElse("none yet");
        Map<UrgencyLevel, Long> counts = reports.countByUrgency(list);

        cardsRow.removeAll();
        cardsRow.add(makeCard("Total Products", Theme.YELLOW, String.valueOf(products.size()),
                categories + (categories == 1 ? " category" : " categories"),
                "Stock: " + totalStock + " units"));
        cardsRow.add(makeCard("Sales (7 days)", Theme.PINK, sold + " units",
                String.format("Avg/day: %.1f", sold / (double) WINDOW_DAYS),
                "Top: " + best));
        cardsRow.add(makeCard("By Urgency", Theme.GREEN, counts.get(UrgencyLevel.CRITICAL) + " critical",
                "Warning: " + counts.get(UrgencyLevel.WARNING),
                "Safe: " + counts.get(UrgencyLevel.SAFE)));
        cardsRow.add(makeCard("Reorder", Theme.BLUE, reports.itemsToReorder(list) + " items",
                "Units needed: " + reports.totalUnitsToReorder(list),
                "Out in " + ALERT_DAYS + " days: " + soon));

        // list of the 5 most urgent products
        listPanel.removeAll();
        JLabel listTitle = new JLabel("Most urgent products");
        listTitle.setFont(Theme.NAV.deriveFont(Font.BOLD, 18f));
        listTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        listPanel.add(listTitle);
        listPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        if (list.isEmpty()) {
            JLabel empty = new JLabel("No products yet.");
            empty.setFont(Theme.BODY);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            listPanel.add(empty);
        } else {
            int rank = 1;
            for (ReorderSuggestion s : reports.topUrgent(list, 5)) {
                listPanel.add(makeRow(rank++, s));
            }
        }
        listPanel.add(Box.createVerticalGlue());

        cardsRow.revalidate();
        cardsRow.repaint();
        listPanel.revalidate();
        listPanel.repaint();
    }

    // ---------- building blocks ----------

    private RoundedPanel makeCard(String title, Color color, String big, String... lines) {
        RoundedPanel card = new RoundedPanel(color, 28);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.NAV.deriveFont(Font.BOLD, 16f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel bigLabel = new JLabel(big);
        bigLabel.setFont(Theme.TITLE.deriveFont(Font.BOLD, 30f));
        bigLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createRigidArea(new Dimension(0, 8)));
        card.add(bigLabel);
        card.add(Box.createRigidArea(new Dimension(0, 10)));
        for (String line : lines) {
            JLabel l = new JLabel(line);
            l.setFont(Theme.BODY);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(l);
            card.add(Box.createRigidArea(new Dimension(0, 3)));
        }
        return card;
    }

    private JPanel makeRow(int rank, ReorderSuggestion s) {
        Product p = s.getProduct();
        String daysText = (s.getDaysLeft() == Double.MAX_VALUE)
                ? "no recent sales" : String.format("%.1f days left", s.getDaysLeft());

        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xEEEEEE)));

        JLabel rankLabel = new JLabel(String.valueOf(rank));
        rankLabel.setFont(Theme.NAV.deriveFont(Font.BOLD, 16f));
        rankLabel.setPreferredSize(new Dimension(24, 0));

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        text.setBorder(BorderFactory.createEmptyBorder(8, 0, 6, 0));
        JLabel name = new JLabel(p.getName());
        name.setFont(Theme.NAV.deriveFont(Font.BOLD, 15f));
        JLabel detail = new JLabel("Stock " + p.getCurrentStock() + "  |  " + daysText
                + "  |  lead " + p.getLeadTimeDays() + " days");
        detail.setFont(Theme.BODY.deriveFont(12f));
        detail.setForeground(new Color(0x666666));
        text.add(name);
        text.add(detail);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        right.setOpaque(false);
        JLabel qty = new JLabel(s.getSuggestedQty() > 0 ? "Reorder " + s.getSuggestedQty() : "No reorder");
        qty.setFont(Theme.BODY);
        right.add(qty);
        right.add(new StatusPill(s.getUrgency().name(), fillFor(s.getUrgency()), textFor(s.getUrgency())));

        row.add(rankLabel, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private String greeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    private static Color fillFor(UrgencyLevel level) {
        switch (level) {
            case CRITICAL: return Theme.CRITICAL;
            case WARNING:  return Theme.WARNING;
            default:       return Theme.SAFE;
        }
    }

    private static Color textFor(UrgencyLevel level) {
        return level == UrgencyLevel.WARNING ? Color.BLACK : Color.WHITE;
    }

    // ---------- a small rounded "pill" label ----------

    private static class StatusPill extends JLabel {
        private final Color fill;

        StatusPill(String text, Color fill, Color textColor) {
            super(text, SwingConstants.CENTER);
            this.fill = fill;
            setForeground(textColor);
            setFont(Theme.BODY.deriveFont(Font.BOLD, 12f));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}