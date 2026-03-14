package src;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GymClientService {

    private static List<String[]> extractClients(ResultSet rs) throws SQLException {
        List<String[]> clients = new ArrayList<>();
        while (rs.next()) {
            clients.add(new String[]{
                    String.valueOf(rs.getInt("client_id")),
                    rs.getString("last_name") != null ? rs.getString("last_name") : "",
                    rs.getString("first_name") != null ? rs.getString("first_name") : "",
                    rs.getString("phone") != null ? rs.getString("phone") : "",
                    rs.getString("email") != null ? rs.getString("email") : "",
                    rs.getString("membership_type") != null ? rs.getString("membership_type") : "",
                    rs.getString("status") != null ? rs.getString("status") : "",
                    rs.getDate("registration_date") != null ? rs.getDate("registration_date").toString() : ""
            });
        }
        return clients;
    }

    public static List<String[]> getAllClients(Connection conn) throws SQLException {
        String query = "SELECT * FROM get_all_clients()";
        try (PreparedStatement st = conn.prepareStatement(query);
             ResultSet rs = st.executeQuery()) {
            return extractClients(rs);
        }
    }

    public static List<String[]> findClientsByLastName(Connection conn, String lastName) throws SQLException {
        if (lastName == null || lastName.trim().isEmpty()) {
            return getAllClients(conn);
        }

        String query = "SELECT * FROM find_clients_by_last_name(?)";
        try (PreparedStatement st = conn.prepareStatement(query)) {
            st.setString(1, lastName.trim());
            try (ResultSet rs = st.executeQuery()) {
                return extractClients(rs);
            }
        }
    }

    public static void addClient(Connection conn, String lastName, String firstName, String phone,
                                 String email, String membershipType, String status, Date registrationDate) throws SQLException {
        validateClientData(lastName, firstName, phone, email, membershipType, status, registrationDate);

        String query = "CALL add_client(?, ?, ?, ?, ?, ?, ?)";
        try (CallableStatement st = conn.prepareCall(query)) {
            st.setString(1, lastName.trim());
            st.setString(2, firstName.trim());
            st.setString(3, phone != null && !phone.isEmpty() ? phone.trim() : null);
            st.setString(4, email != null && !email.isEmpty() ? email.trim() : null);
            st.setString(5, membershipType.trim());
            st.setString(6, status.trim());
            st.setDate(7, registrationDate);
            st.execute();
        }
    }

    public static void updateClient(Connection conn, int clientId, String lastName, String firstName, String phone,
                                    String email, String membershipType, String status, Date registrationDate) throws SQLException {
        if (clientId <= 0) {
            throw new SQLException("Invalid client ID");
        }
        validateClientData(lastName, firstName, phone, email, membershipType, status, registrationDate);

        String query = "CALL update_client(?, ?, ?, ?, ?, ?, ?, ?)";
        try (CallableStatement st = conn.prepareCall(query)) {
            st.setInt(1, clientId);
            st.setString(2, lastName.trim());
            st.setString(3, firstName.trim());
            st.setString(4, phone != null && !phone.isEmpty() ? phone.trim() : null);
            st.setString(5, email != null && !email.isEmpty() ? email.trim() : null);
            st.setString(6, membershipType.trim());
            st.setString(7, status.trim());
            st.setDate(8, registrationDate);
            st.execute();
        }
    }

    public static void clearTable(Connection conn) throws SQLException {
        String query = "CALL clear_clients_table()";
        try (CallableStatement st = conn.prepareCall(query)) {
            st.execute();
        }
    }

    public static void deleteByLastName(Connection conn, String lastName) throws SQLException {
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new SQLException("Last name cannot be empty");
        }

        String query = "CALL delete_clients_by_last_name(?)";
        try (CallableStatement st = conn.prepareCall(query)) {
            st.setString(1, lastName.trim());
            st.execute();
        }
    }

    public static boolean isAdmin(Connection conn) throws SQLException {
        if (conn == null || conn.isClosed()) {
            return false;
        }

        String currentUser = conn.getMetaData().getUserName();
        if (currentUser != null) {
            currentUser = currentUser.toLowerCase();
            if (currentUser.equals("postgres") || currentUser.equals("db_admin")) {
                return true;
            }
        }

        try (PreparedStatement st = conn.prepareStatement(
                "SELECT pg_has_role(session_user, 'admin_role', 'member') " +
                        "OR session_user IN ('postgres', 'db_admin')");
             ResultSet rs = st.executeQuery()) {
            if (rs.next()) {
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            // если session_user недоступен по какой-то причине, пробуем резервные варианты ниже
        }

        try (PreparedStatement st = conn.prepareStatement(
                "SELECT pg_has_role(current_user, 'admin_role', 'member') " +
                        "OR current_user IN ('postgres', 'db_admin')");
             ResultSet rs = st.executeQuery()) {
            if (rs.next()) {
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            // Игнорируем и падаем на финальную проверку
        }

        return false;
    }

    public static void createDbUser(Connection conn, String username, String password, String role) throws SQLException {
        if (username == null || username.trim().isEmpty()) {
            throw new SQLException("Username cannot be empty");
        }
        if (password == null || password.isEmpty()) {
            throw new SQLException("Password cannot be empty");
        }
        if (!username.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new SQLException("Username must start with a letter and contain only letters, numbers, and underscores");
        }
        if (password.length() < 4) {
            throw new SQLException("Password must be at least 4 characters long");
        }
        if (role == null || (!role.equals("admin_role") && !role.equals("guest_role"))) {
            throw new SQLException("Role must be either 'admin_role' or 'guest_role'");
        }

        String query = "CALL create_db_user(?, ?, ?)";
        try (CallableStatement st = conn.prepareCall(query)) {
            st.setString(1, username.trim());
            st.setString(2, password);
            st.setString(3, role);
            st.execute();
        }
    }

    public static List<String[]> getManagedDbUsers(Connection conn) throws SQLException {
        List<String[]> users = new ArrayList<>();
        String query = "SELECT * FROM get_managed_db_users()";
        try (PreparedStatement st = conn.prepareStatement(query);
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                users.add(new String[]{
                        rs.getString("username"),
                        rs.getString("role_name"),
                        rs.getTimestamp("created_at") != null ?
                                rs.getTimestamp("created_at").toString() : ""
                });
            }
        }
        return users;
    }

    public static void deleteDbUsers(Connection conn, List<String> usernames) throws SQLException {
        if (usernames == null || usernames.isEmpty()) {
            return;
        }

        String currentUser = conn.getMetaData().getUserName();
        for (String username : usernames) {
            if (username.equalsIgnoreCase(currentUser)) {
                throw new SQLException("Cannot delete current user: " + username);
            }
        }

        String query = "CALL delete_db_users(?)";
        try (CallableStatement st = conn.prepareCall(query)) {
            Array array = conn.createArrayOf("VARCHAR", usernames.toArray(new String[0]));
            st.setArray(1, array);
            st.execute();
        }
    }

    public static void createDatabase(String host, int port, String adminUser, String adminPassword, String dbName) throws SQLException {
        if (dbName == null || dbName.trim().isEmpty()) {
            throw new SQLException("Database name cannot be empty");
        }
        if (!dbName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new SQLException("Database name must start with a letter and contain only letters, numbers, and underscores");
        }

        try (Connection conn = DBConnection.getConnection(host, port, "postgres", adminUser, adminPassword)) {
            conn.setAutoCommit(true);

            try (CallableStatement st = conn.prepareCall("CALL sp_create_gym_database(?)")) {
                st.setString(1, dbName.trim());
                st.execute();
            }

            try (CallableStatement st = conn.prepareCall("CALL sp_init_gym_database(?)")) {
                st.setString(1, dbName.trim());
                st.execute();
            }
        }
    }

    public static void dropDatabase(String host, int port, String adminUser, String adminPassword, String dbName) throws SQLException {
        if (dbName == null || dbName.trim().isEmpty()) {
            throw new SQLException("Database name cannot be empty");
        }

        try (Connection conn = DBConnection.getConnection(host, port, "postgres", adminUser, adminPassword)) {
            conn.setAutoCommit(true);
            try (CallableStatement st = conn.prepareCall("CALL sp_drop_gym_database(?)")) {
                st.setString(1, dbName.trim());
                st.execute();
            }
        }
    }

    private static void validateClientData(String lastName, String firstName, String phone,
                                           String email, String membershipType, String status,
                                           Date registrationDate) throws SQLException {
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new SQLException("Last name is required");
        }
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new SQLException("First name is required");
        }
        if (membershipType == null || membershipType.trim().isEmpty()) {
            throw new SQLException("Membership type is required");
        }
        if (status == null || status.trim().isEmpty()) {
            throw new SQLException("Status is required");
        }
        if (registrationDate == null) {
            throw new SQLException("Registration date is required");
        }

        // Валидация email
        if (email != null && !email.trim().isEmpty()) {
            String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
            if (!email.matches(emailRegex)) {
                throw new SQLException("Invalid email format");
            }
        }

        // Валидация телефона
        if (phone != null && !phone.trim().isEmpty()) {
            String phoneRegex = "^[0-9+\\-\\s()]+$";
            if (!phone.matches(phoneRegex)) {
                throw new SQLException("Invalid phone format. Use only digits, spaces, +, -, and parentheses");
            }
        }
    }
}