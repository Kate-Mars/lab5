package src.util;

import javax.swing.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AppLogger {

    private static JTextArea logArea;
    private static final String LOG_FILE = "gym_jdbc.log";
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private static Thread logWriterThread;
    private static boolean running = true;
    private static final int MAX_LOG_LINES = ConfigManager.getInt("app.max_log_lines", 1000);

    static {
        logWriterThread = new Thread(() -> {
            try (FileWriter fw = new FileWriter(LOG_FILE, true)) {
                while (running) {
                    try {
                        String message = logQueue.take();
                        fw.write(message + "\n");
                        fw.flush();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        logWriterThread.setDaemon(true);
        logWriterThread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            logWriterThread.interrupt();
        }));
    }

    public static void setLogArea(JTextArea area) {
        logArea = area;
    }
    public static void log(String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        String line = "[" + timestamp + "] " + message;
        if (logArea != null) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(line + "\n");
                if (logArea.getLineCount() > MAX_LOG_LINES) {
                    try {
                        int end = logArea.getLineEndOffset(logArea.getLineCount() - MAX_LOG_LINES - 1);
                        logArea.replaceRange("", 0, end);
                    } catch (Exception e) {
                        // Игнорируем ошибки при обрезке лога
                    }
                }
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }
        try {
            logQueue.put(line);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void logError(String message, Exception e) {
        log("ERROR: " + message + " - " + e.getMessage());
        if (e.getStackTrace().length > 0) {
            log("Stack trace: " + e.getStackTrace()[0]);
        }
    }

    public static void shutdown() {
        running = false;
        logWriterThread.interrupt();
    }
}