package com.soa.desktop.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ActivityLogger {

    public record LogEntry(String time, String protocol, String operation, String status, String details,
                           String requestId, Integer httpStatus, String protocolStatus, Long durationMillis) {}

    private static final ObservableList<LogEntry> logs = FXCollections.observableArrayList();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static ObservableList<LogEntry> getLogs() {
        return logs;
    }

    public static void log(String protocol, String operation, String status, String details) {
        log(protocol, operation, status, details, null, null, null);
    }

    public static void log(String protocol, String operation, String status, String details,
                           String requestId, Integer httpStatus, Long durationMillis) {
        log(protocol, operation, status, details, requestId, httpStatus, null, durationMillis);
    }

    public static void log(String protocol, String operation, String status, String details,
                           String requestId, Integer httpStatus, String protocolStatus, Long durationMillis) {
        LogEntry entry = new LogEntry(LocalDateTime.now().format(TIME_FMT), protocol, operation, status,
                details, requestId, httpStatus, protocolStatus, durationMillis);
        Runnable append = () -> {
            logs.add(0, entry);
            if (logs.size() > 500) logs.remove(500, logs.size());
        };
        if (Platform.isFxApplicationThread()) append.run();
        else Platform.runLater(append);
    }
}