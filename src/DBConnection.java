package src;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    public static Connection getConnection(String host, int port, String database, String user, String password) throws SQLException {
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC driver not found. Please add postgresql driver to classpath", e);
        }

        try {
            Connection conn = DriverManager.getConnection(url, user, password);
            conn.setAutoCommit(true);
            return conn;
        } catch (SQLException e) {
            throw new SQLException("Failed to connect to database: " + e.getMessage(), e);
        }
    }
}