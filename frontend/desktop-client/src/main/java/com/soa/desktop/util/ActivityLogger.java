package com.soa.desktop.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ActivityLogger {

    public record LogEntry(String time, String protocol, String operation, String status, String details) {}

    private static final ObservableList<LogEntry> logs = FXCollections.observableArrayList();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public static ObservableList<LogEntry> getLogs() {
        return logs;
    }

    public static void log(String protocol, String operation, String status, String details) {
        LogEntry entry = new LogEntry(LocalTime.now().format(TIME_FMT), protocol, operation, status, details);
        Runnable append = () -> {
            logs.add(0, entry);
            if (logs.size() > 500) logs.remove(500, logs.size());
        };
        if (Platform.isFxApplicationThread()) append.run();
        else Platform.runLater(append);
    }
}