package com.soa.desktop.client.rest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.soa.desktop.client.rest.dto.*;
import com.soa.desktop.util.ActivityLogger;
import com.soa.desktop.ui.topology.TrafficEventBus;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MaintenanceRestClient implements AutoCloseable {

    private static final Duration CONNECT_TIMEOUT  = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT  = Duration.ofSeconds(10);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final ScheduledExecutorService refreshScheduler;

    private volatile String accessToken;
    private volatile Instant tokenExpiresAt;

    public MaintenanceRestClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "rest-token-refresher");
            t.setDaemon(true);
            return t;
        });
    }

    // ---------------------------------------------------------------
    // Auth
    // ---------------------------------------------------------------

    public LoginResponse login(String username, String password) {
        try {
            String body = mapper.writeValueAsString(new LoginRequest(username, password));
            String requestId = UUID.randomUUID().toString();
            long startedAt = System.nanoTime();
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/auth/login"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            ActivityLogger.log("REST", "POST /auth/login", "PENDING", "Authenticating...",
                    requestId, null, null);
            TrafficEventBus.publish("REST", "REQUEST", "PENDING");

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                TrafficEventBus.publish("REST", "RESPONSE", "FAILED");
                ActivityLogger.log("REST", "POST /auth/login", "FAILED", "HTTP " + resp.statusCode(),
                        requestId, resp.statusCode(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                throw parseError(resp, "Login failed");
            }
            TrafficEventBus.publish("REST", "RESPONSE", "SUCCESS");
            LoginResponse lr = mapper.readValue(resp.body(), LoginResponse.class);
            this.accessToken = lr.accessToken();
            this.tokenExpiresAt = Instant.now().plusSeconds(lr.expiresInSeconds());
            scheduleRefresh(username, password, lr.expiresInSeconds());

            ActivityLogger.log("REST", "POST /auth/login", "SUCCESS", "Logged in as " + username,
                    requestId, resp.statusCode(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            return lr;
        } catch (ServiceError e) {
            throw e;
        } catch (Exception e) {
            TrafficEventBus.publish("REST", "RESPONSE", "FAILED");
            ActivityLogger.log("REST", "POST /auth/login", "FAILED", e.getMessage());
            throw new ServiceError(0, "NETWORK", "Cannot reach auth service: " + e.getMessage(), null);
        }
    }

    private void scheduleRefresh(String username, String password, long ttlSeconds) {
        long delay = Math.max(30, ttlSeconds - 300);   // 5 min before expiry
        refreshScheduler.schedule(() -> {
            try { login(username, password); }
            catch (Exception ignored) { }
        }, delay, TimeUnit.SECONDS);
    }

    public boolean hasToken() {
        return accessToken != null && Instant.now().isBefore(tokenExpiresAt);
    }

    public CompletableFuture<Boolean> checkHealth() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.replaceFirst("/api/v1$", "")
                        + "/actuator/health"))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            boolean healthy = false;
            if (response.statusCode() == 200) {
                try {
                    healthy = "UP".equalsIgnoreCase(mapper.readTree(response.body()).path("status").asText());
                } catch (Exception exception) {
                    throw new ServiceError(0, "PARSE", "Malformed /actuator/health response", response.body());
                }
            }
            ActivityLogger.log("REST", "GET /actuator/health", healthy ? "SUCCESS" : "FAILED",
                    "HTTP " + response.statusCode() + " · " + elapsedMillis + " ms",
                    requestId, response.statusCode(), elapsedMillis);
            return healthy;
        });
    }

    // ---------------------------------------------------------------
    // Machines, Alerts, Work Orders
    // ---------------------------------------------------------------

    public CompletableFuture<List<MachineDto>> getMachines() {
        return sendAsync("GET", "/machines", null).thenApply(body -> {
            try {
                var type = mapper.getTypeFactory().constructParametricType(PageResponse.class, MachineDto.class);
                PageResponse<MachineDto> page = mapper.readValue(body, type);
                return page.content();
            } catch (Exception e) { throw new ServiceError(0, "PARSE", "Malformed /machines response", body); }
        });
    }

    public CompletableFuture<PageResponse<MachineDto>> getMachinePage(int page, int size, String status) {
        return getMachinePage(page, size, status, null);
    }

    public CompletableFuture<PageResponse<MachineDto>> getMachinePage(int page, int size,
                                                                       String status, String query) {
        validatePage(page, size);
        String path = "/machines?page=" + page + "&size=" + size
                + (status == null ? "" : "&status=" + encodeQuery(status))
                + (query == null || query.isBlank() ? "" : "&q=" + encodeQuery(query));
        return sendAsync("GET", path, null).thenApply(body -> parsePage(body, MachineDto.class, "/machines"));
    }

    public CompletableFuture<AlertDto> createAlert(CreateAlertRequest req) {
        return postJsonAsync("/alerts", req, AlertDto.class);
    }

    public CompletableFuture<List<AlertDto>> getActiveAlerts() {
        return getAlerts("/alerts?status=ACTIVE");
    }

    public CompletableFuture<List<AlertDto>> getAlerts() {
        return getAlerts("/alerts");
    }

    public CompletableFuture<PageResponse<AlertDto>> getAlertPage(int page, int size) {
        return getAlertPage(page, size, null);
    }

    public CompletableFuture<PageResponse<AlertDto>> getAlertPage(int page, int size, String status) {
        validatePage(page, size);
        String path = "/alerts?page=" + page + "&size=" + size
                + (status == null ? "" : "&status=" + encodeQuery(status));
        return sendAsync("GET", path, null)
                .thenApply(body -> parsePage(body, AlertDto.class, "/alerts"));
    }

    public CompletableFuture<List<AlertDto>> getAlertsForMachine(String machineId) {
        return getAlerts("/alerts?machineId=" + encodeQuery(machineId) + "&size=100");
    }

    private CompletableFuture<List<AlertDto>> getAlerts(String path) {
        return sendAsync("GET", path, null).thenApply(body -> {
            try {
                var type = mapper.getTypeFactory().constructParametricType(PageResponse.class, AlertDto.class);
                PageResponse<AlertDto> page = mapper.readValue(body, type);
                return page.content();
            } catch (Exception exception) {
                throw new ServiceError(0, "PARSE", "Malformed " + path + " response", body);
            }
        });
    }

    public CompletableFuture<AlertDto> acknowledgeAlert(String alertId) {
        return sendAsync("PATCH", "/alerts/" + alertId + "/acknowledge", "")
                .thenApply(body -> deserialize(body, AlertDto.class, "/alerts/{id}/acknowledge"));
    }

    public CompletableFuture<AlertDto> resolveAlert(String alertId) {
        return sendAsync("PATCH", "/alerts/" + alertId + "/resolve", "")
                .thenApply(body -> deserialize(body, AlertDto.class, "/alerts/{id}/resolve"));
    }

    public CompletableFuture<WorkOrderDto> createWorkOrder(CreateWorkOrderRequest req) {
        return postJsonAsync("/work-orders", req, WorkOrderDto.class);
    }

    public CompletableFuture<List<WorkOrderDto>> getWorkOrders() {
        return getWorkOrders("/work-orders");
    }

    public CompletableFuture<PageResponse<WorkOrderDto>> getWorkOrderPage(int page, int size) {
        return getWorkOrderPage(page, size, null);
    }

    public CompletableFuture<PageResponse<WorkOrderDto>> getWorkOrderPage(int page, int size, String status) {
        validatePage(page, size);
        String path = "/work-orders?page=" + page + "&size=" + size
                + (status == null ? "" : "&status=" + encodeQuery(status));
        return sendAsync("GET", path, null)
                .thenApply(body -> parsePage(body, WorkOrderDto.class, "/work-orders"));
    }

    public CompletableFuture<List<WorkOrderDto>> getWorkOrdersForMachine(String machineId) {
        return getWorkOrders("/work-orders?machineId=" + encodeQuery(machineId));
    }

    private CompletableFuture<List<WorkOrderDto>> getWorkOrders(String path) {
        return sendAsync("GET", path, null).thenApply(body -> {
            try {
                var type = mapper.getTypeFactory().constructParametricType(PageResponse.class, WorkOrderDto.class);
                PageResponse<WorkOrderDto> page = mapper.readValue(body, type);
                return page.content();
            } catch (Exception e) { throw new ServiceError(0, "PARSE", "Malformed " + path + " response", body); }
        });
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private <T> PageResponse<T> parsePage(String body, Class<T> itemType, String context) {
        try {
            var type = mapper.getTypeFactory().constructParametricType(PageResponse.class, itemType);
            return mapper.readValue(body, type);
        } catch (Exception exception) {
            throw new ServiceError(0, "PARSE", "Malformed paged " + context + " response", body);
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
    }

    public CompletableFuture<WorkOrderDto> updateWorkOrderStatus(String id, String newStatus) {
        return sendAsync("PATCH", "/work-orders/" + id + "/status",
                "{\"status\":\"" + newStatus + "\"}")
                .thenApply(body -> deserialize(body, WorkOrderDto.class, "/work-orders/{id}/status"));
    }

    public CompletableFuture<WorkOrderDto> associateReservation(String id, String reservationId, String partId, int quantity) {
        String payload = String.format(
                "{\"reservationId\":\"%s\", \"partId\":\"%s\", \"quantity\":%d, \"status\":\"ACTIVE\"}",
                reservationId, partId, quantity);

        return sendAsync("PATCH", "/work-orders/" + id + "/reservation", payload)
                .thenApply(body -> deserialize(body, WorkOrderDto.class, "/work-orders/{id}/reservation"));
    }

    // ---------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------

    private <T> CompletableFuture<T> postJsonAsync(String path, Object payload, Class<T> type) {
        try {
            String body = mapper.writeValueAsString(payload);
            return sendAsync("POST", path, body).thenApply(resp -> deserialize(resp, type, path));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<String> sendAsync(String method, String path, String body) {
        if (accessToken == null) {
            return CompletableFuture.failedFuture(
                    new ServiceError(401, "NO_TOKEN", "Not authenticated — call login() first", null));
        }

        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json");

        if ("GET".equals(method)) {
            b.GET();
        } else if ("POST".equals(method)) {
            b.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else if ("PATCH".equals(method)) {
            b.header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            return CompletableFuture.failedFuture(new ServiceError(0, "METHOD", "Unsupported " + method, null));
        }

        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        ActivityLogger.log("REST", method + " " + path, "PENDING", "Sending...",
                requestId, null, null);
        TrafficEventBus.publish("REST", "REQUEST", "PENDING");

        CompletableFuture<HttpResponse<String>> response = http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString());
        response.whenComplete((resp, error) -> {
            if (error != null) {
                TrafficEventBus.publish("REST", "RESPONSE", "FAILED");
                ActivityLogger.log("REST", method + " " + path, "FAILED", error.getMessage(),
                        requestId, null, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            }
        });
        return response.thenApply(resp -> {
                    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        TrafficEventBus.publish("REST", "RESPONSE", "SUCCESS");
                        ActivityLogger.log("REST", method + " " + path, "SUCCESS", "HTTP " + resp.statusCode(),
                                requestId, resp.statusCode(), elapsedMillis);
                        return resp.body();
                    }
                    TrafficEventBus.publish("REST", "RESPONSE", "FAILED");
                    ActivityLogger.log("REST", method + " " + path, "FAILED", "HTTP " + resp.statusCode(),
                            requestId, resp.statusCode(), elapsedMillis);
                    throw parseError(resp, method + " " + path + " failed");
                });
    }

    private <T> T deserialize(String body, Class<T> type, String context) {
        try { return mapper.readValue(body, type); }
        catch (Exception e) { throw new ServiceError(0, "PARSE", "Malformed response from " + context, body); }
    }

    private ServiceError parseError(HttpResponse<String> resp, String fallbackMessage) {
        String body = resp.body();
        String code = null;
        String message = fallbackMessage;
        try {
            var tree = mapper.readTree(body);
            if (tree.has("error"))   code    = tree.get("error").asText();
            if (tree.has("message")) message = tree.get("message").asText();
        } catch (Exception ignored) { }
        return new ServiceError(resp.statusCode(), code, message, body);
    }

    @Override
    public void close() {
        refreshScheduler.shutdownNow();
    }
}