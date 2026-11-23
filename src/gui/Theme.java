package gui;

import java.awt.Color;
import java.awt.Font;

public final class Theme {
    private Theme() { }   // nobody should create a Theme object

    // ---------- brand (blue) ----------
    public static final Color PRIMARY       = new Color(0x2F6BFF);
    public static final Color PRIMARY_DARK  = new Color(0x1F4FD8);
    public static final Color PRIMARY_LIGHT = new Color(0xDCE8FF);
    public static final Color HERO_TOP      = new Color(0x5B9BFF);
    public static final Color HERO_BOTTOM   = new Color(0x2457E6);
    public static final Color NAVY          = new Color(0x0F1E4A);
    public static final Color NAVY_LIGHT    = new Color(0x1B2F6B);

    // ---------- surfaces and text ----------
    public static final Color PAGE     = new Color(0xF3F6FB);
    public static final Color CARD     = Color.WHITE;
    public static final Color CARD_ALT = new Color(0xF1F4FA);
    public static final Color TRACK    = new Color(0xE6EBF5);
    public static final Color BORDER   = new Color(0xE2E8F3);
    public static final Color TEXT     = new Color(0x141C2F);
    public static final Color MUTED    = new Color(0x7B879C);

    // ---------- status colors: used ONLY to show stock status, never for decoration ----------
    public static final Color CRITICAL = new Color(0xE53935);
    public static final Color WARNING  = new Color(0xFFB300);
    public static final Color SAFE     = new Color(0x43A047);

    public static final Color CRITICAL_TINT = new Color(0xFDE7E6);
    public static final Color WARNING_TINT  = new Color(0xFFF3D6);
    public static final Color SAFE_TINT     = new Color(0xE4F4E6);

    // ---------- names used by the existing pages (kept so nothing breaks) ----------
    public static final Color BACKGROUND    = PAGE;
    public static final Color SIDEBAR       = NAVY;                 // replaced by a white sidebar in Round 2
    public static final Color SIDEBAR_HOVER = NAVY_LIGHT;
    public static final Color SIDEBAR_TEXT  = new Color(0xDCE4F8);
    public static final Color ACTIVE        = PRIMARY_LIGHT;

    public static final Color YELLOW = new Color(0xCFE0FF);   // the old pastel cards, now blue tints
    public static final Color PINK   = new Color(0xDDE6FB);
    public static final Color GREEN  = new Color(0x9FBEFF);
    public static final Color BLUE   = new Color(0xBBD2FF);

    // ---------- fonts ----------
    public static final Font BRAND = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font TITLE = new Font("Segoe UI", Font.BOLD, 28);
    public static final Font H2    = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font NAV   = new Font("Segoe UI", Font.PLAIN, 15);
    public static final Font BODY  = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font BIG   = new Font("Segoe UI", Font.BOLD, 34);
}