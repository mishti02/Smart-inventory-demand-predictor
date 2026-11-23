package gui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

// Icons drawn with code instead of image files. Every shape is designed on a 24 x 24 grid
// and scaled to the size you ask for, so they stay sharp at any size.
public final class Icons {
    public enum Type { DASHBOARD, PRODUCTS, SALES, PREDICTIONS, REPORTS, SEARCH, BELL, CHEVRON, ARROW_UP_RIGHT, PULSE }

    private Icons() { }

    // An Icon object, so it can be used on any JLabel or JButton
    public static Icon of(Type type, int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                draw(g, type, x, y, size, color);
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    public static void draw(Graphics graphics, Type type, int x, int y, int size, Color color) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (type) {
            case DASHBOARD:   // four rounded squares
                g.draw(new RoundRectangle2D.Double(3, 3, 7, 7, 2, 2));
                g.draw(new RoundRectangle2D.Double(14, 3, 7, 7, 2, 2));
                g.draw(new RoundRectangle2D.Double(3, 14, 7, 7, 2, 2));
                g.draw(new RoundRectangle2D.Double(14, 14, 7, 7, 2, 2));
                break;
            case PRODUCTS:    // a box (cube)
                g.draw(path(true, 12, 2.5, 20.5, 7.25, 20.5, 16.75, 12, 21.5, 3.5, 16.75, 3.5, 7.25));
                g.draw(path(false, 3.5, 7.25, 12, 12, 20.5, 7.25));
                g.draw(new Line2D.Double(12, 12, 12, 21.5));
                break;
            case SALES:       // a shopping cart
                g.draw(path(false, 2, 3.5, 5, 3.5, 7, 15, 19, 15, 21, 7, 6, 7));
                g.fill(new Ellipse2D.Double(8, 18, 3, 3));
                g.fill(new Ellipse2D.Double(16, 18, 3, 3));
                break;
            case PREDICTIONS: // a rising line with an arrow
                g.draw(path(false, 3, 17, 9, 11, 13, 15, 21, 7));
                g.draw(path(false, 15, 7, 21, 7, 21, 13));
                break;
            case REPORTS:     // three bars
                g.draw(new RoundRectangle2D.Double(4, 12, 4, 9, 1.5, 1.5));
                g.draw(new RoundRectangle2D.Double(10, 5, 4, 16, 1.5, 1.5));
                g.draw(new RoundRectangle2D.Double(16, 9, 4, 12, 1.5, 1.5));
                break;
            case SEARCH:      // a magnifying glass
                g.draw(new Ellipse2D.Double(4, 4, 12, 12));
                g.draw(new Line2D.Double(14.5, 14.5, 20, 20));
                break;
            case BELL: {
                Path2D.Double bell = new Path2D.Double();
                bell.moveTo(5.5, 17);
                bell.curveTo(7, 15.5, 7.5, 14, 7.5, 10.5);
                bell.curveTo(7.5, 7.8, 9.5, 6, 12, 6);
                bell.curveTo(14.5, 6, 16.5, 7.8, 16.5, 10.5);
                bell.curveTo(16.5, 14, 17, 15.5, 18.5, 17);
                bell.closePath();
                g.draw(bell);
                g.draw(new Line2D.Double(10, 20, 14, 20));
                g.draw(new Line2D.Double(12, 3.5, 12, 6));
                break;
            }
            case CHEVRON:     // a small arrow pointing right
                g.draw(path(false, 9, 5, 16, 12, 9, 19));
                break;
            case ARROW_UP_RIGHT:
                g.draw(path(false, 7, 17, 17, 7));
                g.draw(path(false, 8, 7, 17, 7, 17, 16));
                break;
            case PULSE:       // a heartbeat line
                g.draw(path(false, 2, 12, 7, 12, 10, 5, 14, 19, 17, 12, 22, 12));
                break;
            default:
                break;
        }
        g.dispose();
    }

    // Builds a line through the given x, y, x, y ... points (closed = joins the last point to the first)
    private static Path2D path(boolean closed, double... p) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i + 1 < p.length; i += 2) {
            path.lineTo(p[i], p[i + 1]);
        }
        if (closed) path.closePath();
        return path;
    }
}