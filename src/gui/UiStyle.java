package gui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import java.awt.Insets;
import java.util.Collections;

// Turns on FlatLaf and sets the rounded, blue look for every Swing component
public final class UiStyle {
    private UiStyle() { }

    public static void install() {
        // the accent color must be set BEFORE setup()
        FlatLaf.setGlobalExtraDefaults(Collections.singletonMap("@accentColor", "#2F6BFF"));
        FlatLightLaf.setup();

        UIManager.put("Button.arc", 999);            // pill-shaped buttons
        UIManager.put("Component.arc", 14);          // combo boxes, spinners
        UIManager.put("TextComponent.arc", 14);      // text fields
        UIManager.put("Component.focusWidth", 1);

        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 12);

        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.gridColor", Theme.BORDER);
        UIManager.put("Table.selectionBackground", Theme.PRIMARY_LIGHT);
        UIManager.put("Table.selectionForeground", Theme.TEXT);
        UIManager.put("Table.selectionInactiveBackground", Theme.PRIMARY_LIGHT);
        UIManager.put("Table.selectionInactiveForeground", Theme.TEXT);
        UIManager.put("TableHeader.background", Theme.CARD_ALT);
        UIManager.put("TableHeader.separatorColor", Theme.BORDER);
        UIManager.put("TableHeader.bottomSeparatorColor", Theme.BORDER);
    }
}