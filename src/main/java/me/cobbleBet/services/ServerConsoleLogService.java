package me.cobbleBet.services;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class ServerConsoleLogService {
    private static final int MAX_ENTRIES = 250;
    private static final int MAX_MESSAGE_LENGTH = 1200;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private final Logger rootLogger = Logger.getLogger("");
    private final SimpleFormatter formatter = new SimpleFormatter();
    private final Handler handler;

    public ServerConsoleLogService(JavaPlugin plugin) {
        handler = new Handler() {
            @Override public void publish(LogRecord record) {
                if (record == null || !isLoggable(record)) return;
                capture(record);
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        handler.setLevel(Level.INFO);
        rootLogger.addHandler(handler);
        plugin.getLogger().info("Live owner console logging enabled.");
    }

    public synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    public void close() {
        rootLogger.removeHandler(handler);
    }

    private synchronized void capture(LogRecord record) {
        String message;
        try { message = formatter.formatMessage(record); }
        catch (RuntimeException ignored) { message = record.getMessage(); }
        if (message == null) message = "";
        if (record.getThrown() != null) {
            StringWriter trace = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(trace));
            message += "\n" + trace;
        }
        message = message.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "")
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        if (message.isEmpty()) return;
        if (message.length() > MAX_MESSAGE_LENGTH) message = message.substring(0, MAX_MESSAGE_LENGTH - 1) + "…";
        String logger = record.getLoggerName();
        if (logger == null || logger.isBlank()) logger = "Server";
        if (logger.length() > 80) logger = logger.substring(0, 80);
        entries.addFirst(new Entry(record.getMillis(), level(record.getLevel()), logger, message));
        while (entries.size() > MAX_ENTRIES) entries.removeLast();
    }

    private static String level(Level level) {
        if (level != null && level.intValue() >= Level.SEVERE.intValue()) return "error";
        if (level != null && level.intValue() >= Level.WARNING.intValue()) return "warning";
        return "info";
    }

    public record Entry(long timestamp, String level, String logger, String message) {}
}
