package gui;

import java.awt.Color;
import java.awt.Font;

public final class Theme {
    private Theme() { }   // nobody should create a Theme object

    // Colors
    public static final Color BACKGROUND    = new Color(0xFAF3E3);
    public static final Color SIDEBAR       = new Color(0x111111);
    public static final Color SIDEBAR_HOVER = new Color(0x2A2A2A);
    public static final Color SIDEBAR_TEXT  = new Color(0xDDDDDD);
    public static final Color ACTIVE        = new Color(0xF4B6D8);

    public static final Color YELLOW = new Color(0xF5D65B);
    public static final Color PINK   = new Color(0xF4B6D8);
    public static final Color GREEN  = new Color(0xA3B56B);
    public static final Color BLUE   = new Color(0xB5C8EC);

    public static final Color CRITICAL = new Color(0xE53935);
    public static final Color WARNING  = new Color(0xFFB300);
    public static final Color SAFE     = new Color(0x43A047);

    // Fonts
    public static final Font BRAND = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font TITLE = new Font("Segoe UI", Font.BOLD, 28);
    public static final Font NAV   = new Font("Segoe UI", Font.PLAIN, 15);
    public static final Font BODY  = new Font("Segoe UI", Font.PLAIN, 14);
}