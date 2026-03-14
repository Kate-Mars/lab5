package src.util;

import java.io.*;
import java.util.Properties;

public class ConfigManager {
    private static final String CONFIG_FILE = "config.properties";
    private static Properties props = new Properties();
    private static boolean loaded = false;

    public static void load() {
        if (loaded) return;

        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            props.load(input);
            AppLogger.log("Configuration loaded from " + CONFIG_FILE);
        } catch (IOException e) {
            setDefaultValues();
            save();
            AppLogger.log("Created default configuration file");
        }
        loaded = true;
    }

    public static void save() {
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            props.store(output, "Gym Application Configuration");
            AppLogger.log("Configuration saved to " + CONFIG_FILE);
        } catch (IOException e) {
            AppLogger.logError("Failed to save configuration", e);
        }
    }

    private static void setDefaultValues() {
        props.setProperty("db.host", "localhost");
        props.setProperty("db.port", "5432");
        props.setProperty("db.default_name", "gym");
        props.setProperty("db.admin_user", "admin");
        props.setProperty("db.admin_password", "admin");
        props.setProperty("app.auto_refresh_seconds", "60");
        props.setProperty("app.max_log_lines", "1000");
        props.setProperty("app.date_format", "yyyy-MM-dd");
        props.setProperty("app.theme", "system");
    }

    public static String get(String key) {
        load();
        return props.getProperty(key);
    }

    public static String get(String key, String defaultValue) {
        load();
        return props.getProperty(key, defaultValue);
    }

    public static void set(String key, String value) {
        load();
        props.setProperty(key, value);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }
}