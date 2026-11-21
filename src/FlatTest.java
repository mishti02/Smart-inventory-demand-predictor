import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class FlatTest {
    public static void main(String[] args) {
        FlatLightLaf.setup();   // turn on the modern look

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("FlatLaf Test");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(400, 200);
            frame.setLocationRelativeTo(null);   // center on screen

            JPanel panel = new JPanel();
            panel.add(new JButton("Hello FlatLaf"));
            frame.add(panel);

            frame.setVisible(true);
        });
    }
}