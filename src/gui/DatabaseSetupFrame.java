package src.gui;

import src.GymClientService;
import src.util.AppLogger;
import src.util.ConfigManager;

import javax.swing.*;
import java.awt.*;

public class DatabaseSetupFrame extends JFrame {

    private JTextField hostField;
    private JTextField portField;
    private JTextField dbField;
    private JTextField adminUserField;
    private JPasswordField adminPasswordField;
    private JTextArea logArea;
    private JButton connectBtn;
    private JButton createBtn;
    private JCheckBox saveConfigCheckBox;

    public DatabaseSetupFrame() {
        initializeUI();
        loadSavedSettings();
        setVisible(true);
    }

    private void initializeUI() {
        setTitle("Gym Database Setup");
        setSize(600, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("Database Connection Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Host:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        hostField = new JTextField();
        inputPanel.add(hostField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        inputPanel.add(new JLabel("Port:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        portField = new JTextField();
        inputPanel.add(portField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        inputPanel.add(new JLabel("Database:"), gbc);
        gbc.gridx = 1;
        dbField = new JTextField();
        inputPanel.add(dbField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        inputPanel.add(new JLabel("Admin user:"), gbc);
        gbc.gridx = 1;
        adminUserField = new JTextField();
        inputPanel.add(adminUserField, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        inputPanel.add(new JLabel("Admin password:"), gbc);
        gbc.gridx = 1;
        adminPasswordField = new JPasswordField();
        inputPanel.add(adminPasswordField, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        saveConfigCheckBox = new JCheckBox("Save these settings as default");
        saveConfigCheckBox.setSelected(true);
        inputPanel.add(saveConfigCheckBox, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        connectBtn = new JButton("Connect");
        createBtn = new JButton("Create Database");
        buttonPanel.add(connectBtn);
        buttonPanel.add(createBtn);

        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        inputPanel.add(buttonPanel, gbc);

        add(inputPanel, BorderLayout.NORTH);

        logArea = new JTextArea(12, 40);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Log"));
        add(scrollPane, BorderLayout.CENTER);

        AppLogger.setLogArea(logArea);

        connectBtn.addActionListener(e -> connect());
        createBtn.addActionListener(e -> createDatabase());

        getRootPane().setDefaultButton(connectBtn);
    }

    private void loadSavedSettings() {
        hostField.setText(ConfigManager.get("db.host", "localhost"));
        portField.setText(ConfigManager.get("db.port", "5432"));
        dbField.setText(ConfigManager.get("db.default_name", "gym_db"));
        adminUserField.setText(ConfigManager.get("db.admin_user", "db_admin"));
        adminPasswordField.setText(ConfigManager.get("db.admin_password", "admin"));
    }

    private void saveSettings() {
        if (saveConfigCheckBox.isSelected()) {
            ConfigManager.set("db.host", hostField.getText().trim());
            ConfigManager.set("db.port", portField.getText().trim());
            ConfigManager.set("db.default_name", dbField.getText().trim());
            ConfigManager.set("db.admin_user", adminUserField.getText().trim());
            ConfigManager.set("db.admin_password", new String(adminPasswordField.getPassword()));
            ConfigManager.save();
            AppLogger.log("Settings saved to configuration file");
        }
    }

    private void connect() {
        if (!validateInputs()) return;

        try {
            String host = hostField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            String db = dbField.getText().trim();

            AppLogger.log(String.format("Connecting to %s:%d/%s as %s",
                    host, port, db, adminUserField.getText().trim()));

            saveSettings();

            new LoginFrame(host, port, db,
                    adminUserField.getText().trim(),
                    new String(adminPasswordField.getPassword()));
            dispose();
        } catch (NumberFormatException ex) {
            showError("Port must be a valid number");
        }
    }

    private void createDatabase() {
        if (!validateInputs()) return;

        String newDbName = JOptionPane.showInputDialog(this,
                "Enter new database name:",
                dbField.getText().trim());

        if (newDbName == null || newDbName.trim().isEmpty()) {
            return;
        }

        newDbName = newDbName.trim();

        if (!newDbName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            showError("Database name must start with a letter and contain only letters, numbers, and underscores");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                String.format("Create database '%s'? This action cannot be undone.", newDbName),
                "Confirm Database Creation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            String host = hostField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            String adminUser = adminUserField.getText().trim();
            String adminPassword = new String(adminPasswordField.getPassword());

            AppLogger.log(String.format("Creating database '%s' on %s:%d", newDbName, host, port));

            GymClientService.createDatabase(host, port, adminUser, adminPassword, newDbName);

            dbField.setText(newDbName);
            AppLogger.log("Database created and initialized successfully: " + newDbName);

            JOptionPane.showMessageDialog(this,
                    "Database created successfully!",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE);

            saveSettings();

            connect();

        } catch (NumberFormatException ex) {
            showError("Port must be a valid number");
        } catch (Exception ex) {
            AppLogger.log("ERROR: " + ex.getMessage());
            showError("Failed to create database: " + ex.getMessage());
        }
    }

    private boolean validateInputs() {
        if (hostField.getText().trim().isEmpty()) {
            showError("Host cannot be empty");
            return false;
        }

        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                showError("Port must be between 1 and 65535");
                return false;
            }
        } catch (NumberFormatException e) {
            showError("Port must be a valid number");
            return false;
        }

        if (dbField.getText().trim().isEmpty()) {
            showError("Database name cannot be empty");
            return false;
        }

        if (adminUserField.getText().trim().isEmpty()) {
            showError("Admin username cannot be empty");
            return false;
        }

        return true;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
        AppLogger.log("ERROR: " + message);
    }
}