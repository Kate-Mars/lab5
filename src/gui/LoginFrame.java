package src.gui;

import src.DBConnection;
import src.util.AppLogger;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;

public class LoginFrame extends JFrame {

    private final JTextField userField;
    private final JPasswordField passField;
    private final String host;
    private final int port;
    private final String database;
    private final String adminUser;
    private final String adminPassword;
    private JCheckBox showPasswordCheckBox;

    public LoginFrame(String host, int port, String database, String adminUser, String adminPassword) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;

        this.userField = new JTextField(15);
        this.passField = new JPasswordField(15);

        initializeUI();
        setVisible(true);
    }

    private void initializeUI() {
        setTitle("Login - " + database);
        setSize(400, 280);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel infoPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Connection Info"));
        infoPanel.add(new JLabel("Host: " + host + ":" + port));
        infoPanel.add(new JLabel("Database: " + database));
        infoPanel.add(new JLabel("Admin: " + adminUser));
        mainPanel.add(infoPanel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("Credentials"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        inputPanel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(passField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        showPasswordCheckBox = new JCheckBox("Show password");
        showPasswordCheckBox.addActionListener(e -> {
            if (showPasswordCheckBox.isSelected()) {
                passField.setEchoChar((char) 0);
            } else {
                passField.setEchoChar('•');
            }
        });
        inputPanel.add(showPasswordCheckBox, gbc);

        mainPanel.add(inputPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        JButton loginBtn = new JButton("Login");
        JButton cancelBtn = new JButton("Cancel");

        loginBtn.addActionListener(e -> login());
        cancelBtn.addActionListener(e -> {
            dispose();
            new DatabaseSetupFrame();
        });

        buttonPanel.add(loginBtn);
        buttonPanel.add(cancelBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);

        getRootPane().setDefaultButton(loginBtn);

        userField.requestFocus();
    }

    private void login() {
        String username = userField.getText().trim();
        String password = new String(passField.getPassword());

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter username",
                    "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            userField.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter password",
                    "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            passField.requestFocus();
            return;
        }

        try {
            AppLogger.log(String.format("Attempting login as '%s' to database '%s'", username, database));

            Connection conn = DBConnection.getConnection(host, port, database, username, password);

            AppLogger.log(String.format("Successfully connected as '%s'", username));

            new MainFrame(conn, username, database, host, port, adminUser, adminPassword);
            dispose();

        } catch (Exception ex) {
            AppLogger.log("Login failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Login failed: " + ex.getMessage(),
                    "Authentication Error",
                    JOptionPane.ERROR_MESSAGE);
            passField.setText("");
            passField.requestFocus();
        }
    }
}