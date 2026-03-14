package src.gui;

import src.GymClientService;
import src.util.AppLogger;
import src.util.ConfigManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.Connection;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

public class MainFrame extends JFrame {

    private final Connection conn;
    private final String user;
    private final String database;
    private final String host;
    private final int port;
    private final String adminUser;
    private final String adminPassword;

    private final JTable table;
    private final DefaultTableModel model;
    private final JLabel countLabel;
    private final JTextArea logArea;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final JTextField searchField;

    private boolean adminMode;
    private Timer refreshTimer;
    private TableRowSorter<DefaultTableModel> sorter;

    public MainFrame(Connection conn, String user, String database, String host, int port, String adminUser, String adminPassword) {
        this.conn = conn;
        this.user = user;
        this.database = database;
        this.host = host;
        this.port = port;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;

        this.model = new DefaultTableModel(
                new String[]{"ID", "Last Name", "First Name", "Phone", "Email", "Membership", "Status", "Registration Date"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) return Integer.class;
                return String.class;
            }
        };

        this.table = new JTable(model);
        this.countLabel = new JLabel("Clients: 0");
        this.logArea = new JTextArea(8, 30);
        this.statusLabel = new JLabel("Ready");
        this.progressBar = new JProgressBar();
        this.searchField = new JTextField(20);

        initializeUI();
        checkAdminMode();
        setupAdminButtons();
        setupAutoRefresh();

        AppLogger.log("User " + user + " connected to database " + database + " as " + (adminMode ? "ADMIN" : "guest"));
        loadAll();
        setVisible(true);
    }

    private void initializeUI() {
        setTitle("Gym Clients - User: " + user + " @ " + database);
        setSize(1300, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        table.setRowHeight(25);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Clients"));
        add(scrollPane, BorderLayout.CENTER);

        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        searchPanel.add(new JLabel("Search:"));
        searchField.addActionListener(e -> searchClients());
        searchPanel.add(searchField);

        JButton searchBtn = new JButton("Search in DB");
        searchBtn.addActionListener(e -> searchClients());
        searchPanel.add(searchBtn);

        JButton clearSearchBtn = new JButton("Clear Search");
        clearSearchBtn.addActionListener(e -> {
            searchField.setText("");
            sorter.setRowFilter(null);
            loadAll();
            statusLabel.setText("Search cleared");
        });
        searchPanel.add(clearSearchBtn);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadAll());
        searchPanel.add(refreshBtn);

        topPanel.add(searchPanel, BorderLayout.WEST);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton viewAllBtn = new JButton("View All");
        viewAllBtn.addActionListener(e -> loadAll());
        actionPanel.add(viewAllBtn);

        topPanel.add(actionPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        JButton backBtn = new JButton("Back to Setup");
        backBtn.addActionListener(e -> backToSetup());
        buttonPanel.add(backBtn);

        buttonPanel.add(countLabel);

        bottomPanel.add(buttonPanel, BorderLayout.WEST);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        statusLabel.setText("Ready");
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(100, 20));
        statusPanel.add(statusLabel);
        statusPanel.add(progressBar);
        bottomPanel.add(statusPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Application Log"));
        logScrollPane.setPreferredSize(new Dimension(300, 0));
        add(logScrollPane, BorderLayout.EAST);

        AppLogger.setLogArea(logArea);
    }

    private void checkAdminMode() {
        try {
            adminMode = GymClientService.isAdmin(conn);
            AppLogger.log("Admin check via service: " + adminMode);
        } catch (Exception e) {
            AppLogger.log("Admin check failed: " + e.getMessage());
            adminMode = checkAdminDirectly();
        }

        AppLogger.log("User '" + user + "' admin mode: " + adminMode);
        statusLabel.setText(adminMode ? "Mode: ADMIN" : "Mode: GUEST (view/search only)");
    }

    private boolean checkAdminDirectly() {
        if ("postgres".equalsIgnoreCase(user) || "db_admin".equalsIgnoreCase(user)) {
            AppLogger.log("User " + user + " is admin by username");
            return true;
        }

        try {
            var stmt = conn.prepareStatement(
                    "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname = current_user AND " +
                            "rolsuper = true) OR pg_has_role(current_user, 'admin_role', 'member')"
            );
            var rs = stmt.executeQuery();
            if (rs.next()) {
                boolean isAdmin = rs.getBoolean(1);
                AppLogger.log("Direct SQL admin check: " + isAdmin);
                return isAdmin;
            }
        } catch (Exception e) {
            AppLogger.log("Direct SQL check failed: " + e.getMessage());
        }

        return false;
    }

    private void setupAdminButtons() {
        if (!adminMode) {
            AppLogger.log("User " + user + " does not have admin rights - admin buttons hidden");
            return;
        }

        AppLogger.log("Setting up admin buttons for user " + user);

        Container contentPane = getContentPane();
        JPanel bottomPanel = (JPanel) ((BorderLayout) contentPane.getLayout()).getLayoutComponent(BorderLayout.SOUTH);

        if (bottomPanel != null) {
            JPanel buttonPanel = (JPanel) bottomPanel.getComponent(0);

            buttonPanel.add(new JLabel("  |  "));

            JButton addBtn = new JButton("Add Client");
            addBtn.addActionListener(e -> addClient());
            buttonPanel.add(addBtn);

            JButton updateBtn = new JButton("Update Client");
            updateBtn.addActionListener(e -> updateClient());
            buttonPanel.add(updateBtn);

            JButton deleteBtn = new JButton("Delete by Last Name");
            deleteBtn.addActionListener(e -> deleteByLastName());
            buttonPanel.add(deleteBtn);

            JButton clearBtn = new JButton("Clear Table");
            clearBtn.addActionListener(e -> clearTable());
            buttonPanel.add(clearBtn);

            buttonPanel.add(new JLabel("  |  "));

            JButton createUserBtn = new JButton("Create DB User");
            createUserBtn.addActionListener(e -> createDbUser());
            buttonPanel.add(createUserBtn);

            JButton manageUsersBtn = new JButton("Manage Users");
            manageUsersBtn.addActionListener(e -> manageDbUsers());
            buttonPanel.add(manageUsersBtn);

            JButton dropDbBtn = new JButton("Drop Database");
            dropDbBtn.setForeground(Color.RED);
            dropDbBtn.addActionListener(e -> dropDatabase());
            buttonPanel.add(dropDbBtn);

            bottomPanel.revalidate();
            bottomPanel.repaint();
        }
    }

    private boolean isAdminByUser() {
        return adminMode; // Просто возвращаем сохраненное значение
    }

    private void setupAutoRefresh() {
        int refreshSeconds = ConfigManager.getInt("app.auto_refresh_seconds", 60);
        if (refreshSeconds > 0) {
            refreshTimer = new Timer(refreshSeconds * 1000, e -> loadAll());
            refreshTimer.start();
            statusLabel.setText("Auto-refresh every " + refreshSeconds + "s");
        }
    }

    private void searchClients() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadAll();
            return;
        }

        showProgress(true);
        new SwingWorker<List<String[]>, Void>() {
            @Override
            protected List<String[]> doInBackground() throws Exception {
                return GymClientService.findClientsByLastName(conn, searchTerm);
            }

            @Override
            protected void done() {
                try {
                    List<String[]> rows = get();
                    fillTable(rows);
                    sorter.setRowFilter(null);
                    AppLogger.log("Search completed for last name: " + searchTerm);
                    statusLabel.setText("Found " + rows.size() + " record(s) for: " + searchTerm);
                } catch (Exception ex) {
                    showError(ex);
                } finally {
                    showProgress(false);
                }
            }
        }.execute();
    }

    private void fillTable(List<String[]> rows) {
        model.setRowCount(0);
        for (String[] row : rows) {
            model.addRow(row);
        }
        countLabel.setText("Clients: " + rows.size());
        statusLabel.setText("Last update: " + LocalDate.now());
    }

    private void showProgress(boolean show) {
        progressBar.setVisible(show);
        if (show) {
            progressBar.setIndeterminate(true);
        }
    }

    private void loadAll() {
        showProgress(true);
        new SwingWorker<List<String[]>, Void>() {
            @Override
            protected List<String[]> doInBackground() throws Exception {
                return GymClientService.getAllClients(conn);
            }

            @Override
            protected void done() {
                try {
                    fillTable(get());
                    AppLogger.log("Loaded all clients");
                } catch (Exception ex) {
                    showError(ex);
                } finally {
                    showProgress(false);
                }
            }
        }.execute();
    }

    private void addClient() {
        if (!adminMode) {
            JOptionPane.showMessageDialog(this, "You don't have permission to add clients");
            return;
        }

        try {
            ClientFormData data = showClientDialog(null);
            if (data == null) return;

            showProgress(true);
            Date sqlDate = Date.valueOf(data.registrationDate);

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    GymClientService.addClient(conn, data.lastName, data.firstName, data.phone,
                            data.email, data.membershipType, data.status, sqlDate);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        AppLogger.log("Added client: " + data.lastName + " " + data.firstName);
                        loadAll();
                    } catch (Exception ex) {
                        showError(ex);
                    } finally {
                        showProgress(false);
                    }
                }
            }.execute();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void updateClient() {
        if (!adminMode) {
            JOptionPane.showMessageDialog(this, "You don't have permission to update clients");
            return;
        }

        int viewRow = table.getSelectedRow();
        if (viewRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a client to update");
            return;
        }
        int row = table.convertRowIndexToModel(viewRow);

        try {
            ClientFormData current = new ClientFormData(
                    model.getValueAt(row, 1).toString(),
                    model.getValueAt(row, 2).toString(),
                    model.getValueAt(row, 3) == null ? "" : model.getValueAt(row, 3).toString(),
                    model.getValueAt(row, 4) == null ? "" : model.getValueAt(row, 4).toString(),
                    model.getValueAt(row, 5).toString(),
                    model.getValueAt(row, 6).toString(),
                    model.getValueAt(row, 7).toString()
            );

            ClientFormData data = showClientDialog(current);
            if (data == null) return;

            showProgress(true);
            int clientId = Integer.parseInt(model.getValueAt(row, 0).toString());
            Date sqlDate = Date.valueOf(data.registrationDate);

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    GymClientService.updateClient(conn, clientId, data.lastName, data.firstName,
                            data.phone, data.email, data.membershipType, data.status, sqlDate);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        AppLogger.log("Updated client ID: " + clientId);
                        loadAll();
                    } catch (Exception ex) {
                        showError(ex);
                    } finally {
                        showProgress(false);
                    }
                }
            }.execute();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void deleteByLastName() {
        if (!adminMode) {
            JOptionPane.showMessageDialog(this, "You don't have permission to delete clients");
            return;
        }

        String lastName = JOptionPane.showInputDialog(this, "Enter last name to delete:");
        if (lastName == null || lastName.isBlank()) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete all clients with last name containing '" + lastName.trim() + "'?",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        showProgress(true);
        String searchTerm = lastName.trim();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                GymClientService.deleteByLastName(conn, searchTerm);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    AppLogger.log("Deleted clients by last name: " + searchTerm);
                    loadAll();
                } catch (Exception ex) {
                    showError(ex);
                } finally {
                    showProgress(false);
                }
            }
        }.execute();
    }

    private void clearTable() {
        if (!adminMode) {
            JOptionPane.showMessageDialog(this, "You don't have permission to clear the table");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete ALL clients?\nThis action cannot be undone!",
                "Clear Table Confirmation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        showProgress(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                GymClientService.clearTable(conn);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    AppLogger.log("Cleared table gym_clients");
                    loadAll();
                } catch (Exception ex) {
                    showError(ex);
                } finally {
                    showProgress(false);
                }
            }
        }.execute();
    }

    private void createDbUser() {
        if (!adminMode) {
            JOptionPane.showMessageDialog(this, "You don't have permission to create users");
            return;
        }

        JTextField userField = new JTextField();
        JPasswordField passField = new JPasswordField();
        JComboBox<String> roleBox = new JComboBox<>(new String[]{"admin_role", "guest_role"});

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.add(new JLabel("Username:"));
        panel.add(userField);
        panel.add(new JLabel("Password:"));
        panel.add(passField);
        panel.add(new JLabel("Role:"));
        panel.add(roleBox);

        int result = JOptionPane.showConfirmDialog(this, panel, "Create Database User",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        showProgress(true);
        String username = userField.getText().trim();
        String password = new String(passField.getPassword());
        String role = (String) roleBox.getSelectedItem();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                GymClientService.createDbUser(conn, username, password, role);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    AppLogger.log("Created database user: " + username);
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "User created successfully!",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    showError(ex);
                } finally {
                    showProgress(false);
                }
            }
        }.execute();
    }

    private void manageDbUsers() {
        if (!adminMode) {
            JOptionPane.showMessageDialog(this, "You don't have permission to manage users");
            return;
        }

        try {
            showProgress(true);
            List<String[]> users = GymClientService.getManagedDbUsers(conn);
            showProgress(false);

            DefaultTableModel userModel = new DefaultTableModel(
                    new String[]{"Select", "Username", "Role", "Created At"}, 0) {
                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    return columnIndex == 0 ? Boolean.class : String.class;
                }

                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == 0;
                }
            };

            for (String[] userRow : users) {
                userModel.addRow(new Object[]{false, userRow[0], userRow[1], userRow[2]});
            }

            JTable userTable = new JTable(userModel);
            userTable.setRowHeight(25);
            JScrollPane scrollPane = new JScrollPane(userTable);
            scrollPane.setPreferredSize(new Dimension(500, 300));

            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.add(new JLabel("Select users to delete:"), BorderLayout.NORTH);
            panel.add(scrollPane, BorderLayout.CENTER);

            int result = JOptionPane.showConfirmDialog(
                    this,
                    panel,
                    "Manage Database Users",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );

            if (result != JOptionPane.OK_OPTION) {
                return;
            }

            List<String> selectedUsers = new ArrayList<>();
            for (int i = 0; i < userModel.getRowCount(); i++) {
                Boolean selected = (Boolean) userModel.getValueAt(i, 0);
                if (Boolean.TRUE.equals(selected)) {
                    String username = userModel.getValueAt(i, 1).toString();
                    if (!username.equals(user)) { // Нельзя удалить себя
                        selectedUsers.add(username);
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "You cannot delete your own user account!",
                                "Warning",
                                JOptionPane.WARNING_MESSAGE);
                    }
                }
            }

            if (selectedUsers.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No valid users selected for deletion");
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Delete selected users: " + String.join(", ", selectedUsers) + "?\nThis action cannot be undone!",
                    "Confirm Deletion",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            showProgress(true);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    GymClientService.deleteDbUsers(conn, selectedUsers);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        AppLogger.log("Deleted DB users: " + String.join(", ", selectedUsers));
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "Selected users deleted successfully");
                    } catch (Exception ex) {
                        showError(ex);
                    } finally {
                        showProgress(false);
                    }
                }
            }.execute();

        } catch (Exception ex) {
            showProgress(false);
            showError(ex);
        }
    }

    private void dropDatabase() {
        if (!adminMode) {
            JOptionPane.showMessageDialog(this, "You don't have permission to drop the database");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "WARNING: You are about to delete the entire database '" + database + "'!\n" +
                        "All data will be permanently lost.\n\n" +
                        "Are you absolutely sure?",
                "DANGER: Drop Database",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        // Второе подтверждение
        String password = JOptionPane.showInputDialog(this,
                "Type the database name to confirm deletion:\n'" + database + "'",
                "Final Confirmation",
                JOptionPane.WARNING_MESSAGE);

        if (password == null || !password.trim().equals(database)) {
            JOptionPane.showMessageDialog(this, "Database name does not match. Deletion cancelled.");
            return;
        }

        showProgress(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                conn.close();
                GymClientService.dropDatabase(host, port, adminUser, adminPassword, database);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    AppLogger.log("Dropped database: " + database);
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Database deleted successfully");
                    backToSetup();
                } catch (Exception ex) {
                    showError(ex);
                } finally {
                    showProgress(false);
                }
            }
        }.execute();
    }

    private void backToSetup() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (Exception ignored) {}

        new DatabaseSetupFrame();
        dispose();
    }

    private ClientFormData showClientDialog(ClientFormData existing) {
        JTextField lastNameField = new JTextField(existing == null ? "" : existing.lastName, 20);
        JTextField firstNameField = new JTextField(existing == null ? "" : existing.firstName, 20);
        JTextField phoneField = new JTextField(existing == null ? "" : existing.phone, 20);
        JTextField emailField = new JTextField(existing == null ? "" : existing.email, 20);
        JTextField membershipField = new JTextField(existing == null ? "Standard" : existing.membershipType, 20);
        JComboBox<String> statusBox = new JComboBox<>(new String[]{"active", "pause", "expired"});
        if (existing != null) {
            statusBox.setSelectedItem(existing.status);
        }
        JTextField registrationField = new JTextField(
                existing == null ? LocalDate.now().toString() : existing.registrationDate, 20);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Last Name*:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(lastNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("First Name*:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(firstNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(new JLabel("Phone:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(phoneField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        panel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(emailField, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        panel.add(new JLabel("Membership Type*:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(membershipField, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        panel.add(new JLabel("Status*:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(statusBox, gbc);

        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0;
        panel.add(new JLabel("Registration Date* (YYYY-MM-DD):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(registrationField, gbc);

        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2;
        panel.add(new JLabel("* Required fields"), gbc);

        int result = JOptionPane.showConfirmDialog(this, panel,
                existing == null ? "Add New Client" : "Update Client",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;

        // Валидация
        String lastName = lastNameField.getText().trim();
        String firstName = firstNameField.getText().trim();
        String membership = membershipField.getText().trim();
        String regDate = registrationField.getText().trim();

        if (lastName.isEmpty() || firstName.isEmpty() || membership.isEmpty() || regDate.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please fill all required fields",
                    "Validation Error",
                    JOptionPane.ERROR_MESSAGE);
            return showClientDialog(existing);
        }

        try {
            LocalDate.parse(regDate);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Invalid date format. Please use YYYY-MM-DD",
                    "Validation Error",
                    JOptionPane.ERROR_MESSAGE);
            return showClientDialog(existing);
        }

        return new ClientFormData(
                lastName,
                firstName,
                phoneField.getText().trim(),
                emailField.getText().trim(),
                membership,
                (String) statusBox.getSelectedItem(),
                regDate
        );
    }

    private void showError(Exception ex) {
        AppLogger.log("ERROR: " + ex.getMessage());
        JOptionPane.showMessageDialog(this,
                "Error: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
    }

    private record ClientFormData(String lastName, String firstName, String phone, String email,
                                  String membershipType, String status, String registrationDate) {}
}