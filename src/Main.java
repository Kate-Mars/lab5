package src;

import src.gui.DatabaseSetupFrame;
import src.util.ConfigManager;
import src.util.AppLogger;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            ConfigManager.load();

            String theme = ConfigManager.get("app.theme", "system");
            if ("system".equals(theme)) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } else if ("nimbus".equals(theme)) {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                new DatabaseSetupFrame();
            } catch (Exception e) {
                AppLogger.logError("Failed to start application", e);
                JOptionPane.showMessageDialog(null,
                        "Failed to start application: " + e.getMessage(),
                        "Fatal Error",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}