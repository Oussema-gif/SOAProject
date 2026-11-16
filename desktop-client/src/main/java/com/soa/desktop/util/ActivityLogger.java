package com.soa.desktop.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
        logs.add(0, new LogEntry(LocalTime.now().format(TIME_FMT), protocol, operation, status, details));
    }
}