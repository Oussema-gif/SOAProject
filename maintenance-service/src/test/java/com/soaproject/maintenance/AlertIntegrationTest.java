package com.soaproject.maintenance;

import com.soaproject.maintenance.dto.AlertResponse;
import com.soaproject.maintenance.dto.CreateAlertRequest;
import com.soaproject.maintenance.dto.CreateMachineRequest;
import com.soaproject.maintenance.dto.LoginRequest;
import com.soaproject.maintenance.dto.LoginResponse;
import com.soaproject.maintenance.dto.RegisterRequest;
import com.soaproject.maintenance.entity.AlertMetricType;
import com.soaproject.maintenance.entity.AlertSeverity;
import com.soaproject.maintenance.entity.MachineStatus;
import com.soaproject.maintenance.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.create-schemas=true",
                "spring.datasource.password=test",
                "app.jwt.secret=dGVzdC1zZWNyZXQtZm9yLXRlc3RzLW9ubHktMzItYnl0ZXMh",
                "app.jwt.expiration-ms=3600000",
                "app.seed.admin.username=admin",
                "app.seed.admin.password=AdminPass123!"
        })
@Testcontainers
class AlertIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;

    private String url(String p) { return "http://localhost:" + port + p; }

    private String adminToken() {
        var res = http.postForEntity(url("/api/v1/auth/login"),
                new LoginRequest("admin", "AdminPass123!"), LoginResponse.class);
        return res.getBody().accessToken();
    }

    private String tokenFor(String username, String password, Role role) {
        String admin = adminToken();
        HttpHeaders h = bearer(admin);
        http.exchange(url("/api/v1/auth/register"), HttpMethod.POST,
                new HttpEntity<>(new RegisterRequest(username, password, role), h), String.class);
        var res = http.postForEntity(url("/api/v1/auth/login"),
                new LoginRequest(username, password), LoginResponse.class);
        return res.getBody().accessToken();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /** Create a fresh machine via the REST API, so alert tests do not collide. */
    private String newMachine() {
        String code = "MACHINE-A-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var body = new CreateMachineRequest(code, "Test " + code, "Lab", "T-1", MachineStatus.RUNNING);
        ResponseEntity<String> res = http.exchange(url("/api/v1/machines"),
                HttpMethod.POST, new HttpEntity<>(body, bearer(adminToken())), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return code;
    }

    private CreateAlertRequest alertBody(String machine, AlertMetricType metric,
                                         AlertSeverity severity, String sourceEventId) {
        return new CreateAlertRequest(
                machine,
                metric,
                severity,
                new BigDecimal("91.400"),
                new BigDecimal("85.000"),
                "Threshold crossed on " + metric,
                Instant.parse("2026-11-16T10:15:30Z"),
                sourceEventId);
    }

    private ResponseEntity<AlertResponse> postAlert(CreateAlertRequest body, String token) {
        return http.exchange(url("/api/v1/alerts"), HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)), AlertResponse.class);
    }

    // ---------- CREATE ----------

    @Test
    void createReturns201ForNewAlert() {
        String machine = newMachine();
        var res = postAlert(alertBody(machine, AlertMetricType.TEMPERATURE, AlertSeverity.CRITICAL,
                "EV-" + UUID.randomUUID()), adminToken());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().machineId()).isEqualTo(machine);
        assertThat(res.getBody().status()).isEqualTo("ACTIVE");
        assertThat(res.getBody().id()).isNotNull();
    }

    @Test
    void duplicateMachineAndMetricReturns200WithExisting() {
        String machine = newMachine();
        String token = adminToken();
        var first = postAlert(alertBody(machine, AlertMetricType.VIBRATION, AlertSeverity.WARNING, null), token);
        var second = postAlert(alertBody(machine, AlertMetricType.VIBRATION, AlertSeverity.WARNING, null), token);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());
    }

    @Test
    void sourceEventIdIsIdempotentRegardlessOfMetric() {
        String machine = newMachine();
        String token = adminToken();
        String ev = "EV-" + UUID.randomUUID();
        var first = postAlert(alertBody(machine, AlertMetricType.TEMPERATURE, AlertSeverity.CRITICAL, ev), token);
        // Same sourceEventId but a different metric/severity — the sourceEventId wins
        var second = postAlert(alertBody(machine, AlertMetricType.PRESSURE, AlertSeverity.WARNING, ev), token);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());
    }

    @Test
    void differentMetricOnSameMachineIsAllowed() {
        String machine = newMachine();
        String token = adminToken();
        var t = postAlert(alertBody(machine, AlertMetricType.TEMPERATURE, AlertSeverity.CRITICAL, null), token);
        var v = postAlert(alertBody(machine, AlertMetricType.VIBRATION, AlertSeverity.CRITICAL, null), token);
        assertThat(t.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(v.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(v.getBody().id()).isNotEqualTo(t.getBody().id());
    }

    @Test
    void canCreateNewAlertAfterPreviousIsResolved() {
        String machine = newMachine();
        String token = adminToken();
        var first = postAlert(alertBody(machine, AlertMetricType.ENERGY, AlertSeverity.WARNING, null), token);
        http.exchange(url("/api/v1/alerts/" + first.getBody().id() + "/resolve"),
                HttpMethod.PATCH, new HttpEntity<>(bearer(token)), AlertResponse.class);

        var second = postAlert(alertBody(machine, AlertMetricType.ENERGY, AlertSeverity.WARNING, null), token);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().id()).isNotEqualTo(first.getBody().id());
    }

    @Test
    void missingRequiredFieldReturns400() {
        String machine = newMachine();
        String token = adminToken();
        // message is blank → validation failure
        var body = new CreateAlertRequest(machine, AlertMetricType.TEMPERATURE, AlertSeverity.CRITICAL,
                new BigDecimal("91.4"), new BigDecimal("85"), "   ",
                Instant.now(), null);
        var res = postAlert(body, token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownMachineReturns404() {
        String token = adminToken();
        var body = alertBody("MACHINE-DOES-NOT-EXIST", AlertMetricType.TEMPERATURE,
                AlertSeverity.CRITICAL, null);

        ResponseEntity<String> res = http.exchange(url("/api/v1/alerts"), HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("NOT_FOUND");
    }

    @Test
    void technicianCannotCreateAlert() {
        String machine = newMachine();
        String tech = tokenFor("tech-al", "TechPass123!", Role.TECHNICIAN);
        var res = postAlert(alertBody(machine, AlertMetricType.TEMPERATURE,
                AlertSeverity.CRITICAL, null), tech);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---------- LIST / GET ----------

    @Test
    void listFiltersByStatus() {
        String token = adminToken();
        ResponseEntity<String> res = http.exchange(url("/api/v1/alerts?status=ACTIVE&size=50"),
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"content\"").contains("\"page\":0");
    }

    @Test
    void listFiltersByMachineAndSeverity() {
        String machine = newMachine();
        String token = adminToken();
        postAlert(alertBody(machine, AlertMetricType.PRESSURE, AlertSeverity.CRITICAL, null), token);
        postAlert(alertBody(machine, AlertMetricType.TEMPERATURE, AlertSeverity.WARNING, null), token);

        ResponseEntity<String> res = http.exchange(
                url("/api/v1/alerts?machineId=" + machine + "&severity=CRITICAL"),
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains(machine).contains("CRITICAL").doesNotContain("WARNING");
    }

    @Test
    void getByIdReturnsAlert() {
        String machine = newMachine();
        String token = adminToken();
        var created = postAlert(alertBody(machine, AlertMetricType.ENERGY, AlertSeverity.WARNING, null), token);

        var res = http.exchange(url("/api/v1/alerts/" + created.getBody().id()),
                HttpMethod.GET, new HttpEntity<>(bearer(token)), AlertResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().id()).isEqualTo(created.getBody().id());
    }

    @Test
    void getUnknownIdReturns404() {
        var res = http.exchange(url("/api/v1/alerts/" + UUID.randomUUID()),
                HttpMethod.GET, new HttpEntity<>(bearer(adminToken())), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("NOT_FOUND");
    }

    @Test
    void listWithoutTokenReturns401() {
        ResponseEntity<String> res = http.getForEntity(url("/api/v1/alerts"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- ACK / RESOLVE ----------

    @Test
    void acknowledgeActiveReturns200() {
        String machine = newMachine();
        String token = adminToken();
        var created = postAlert(alertBody(machine, AlertMetricType.VIBRATION, AlertSeverity.CRITICAL, null), token);

        var res = http.exchange(url("/api/v1/alerts/" + created.getBody().id() + "/acknowledge"),
                HttpMethod.PATCH, new HttpEntity<>(bearer(token)), AlertResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().status()).isEqualTo("ACKNOWLEDGED");
        assertThat(res.getBody().acknowledgedAt()).isNotNull();
    }

    @Test
    void acknowledgeAlreadyAckedReturns409() {
        String machine = newMachine();
        String token = adminToken();
        var created = postAlert(alertBody(machine, AlertMetricType.VIBRATION, AlertSeverity.CRITICAL, null), token);
        String id = created.getBody().id().toString();

        http.exchange(url("/api/v1/alerts/" + id + "/acknowledge"),
                HttpMethod.PATCH, new HttpEntity<>(bearer(token)), AlertResponse.class);
        var res = http.exchange(url("/api/v1/alerts/" + id + "/acknowledge"),
                HttpMethod.PATCH, new HttpEntity<>(bearer(token)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).contains("INVALID_STATE_TRANSITION");
    }

    @Test
    void resolveAcknowledgedReturns200() {
        String machine = newMachine();
        String token = adminToken();
        var created = postAlert(alertBody(machine, AlertMetricType.PRESSURE, AlertSeverity.CRITICAL, null), token);
        String id = created.getBody().id().toString();

        http.exchange(url("/api/v1/alerts/" + id + "/acknowledge"),
                HttpMethod.PATCH, new HttpEntity<>(bearer(token)), AlertResponse.class);
        var res = http.exchange(url("/api/v1/alerts/" + id + "/resolve"),
                HttpMethod.PATCH, new HttpEntity<>(bearer(token)), AlertResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().status()).isEqualTo("RESOLVED");
        assertThat(res.getBody().resolvedAt()).isNotNull();
    }

    @Test
    void resolveActiveReturns200() {
        String machine = newMachine();
        String token = adminToken();
        var created = postAlert(alertBody(machine, AlertMetricType.ENERGY, AlertSeverity.WARNING, null), token);

        var res = http.exchange(url("/api/v1/alerts/" + created.getBody().id() + "/resolve"),
                HttpMethod.PATCH, new HttpEntity<>(bearer(token)), AlertResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().status()).isEqualTo("RESOLVED");
    }

    @Test
    void resolveAlreadyResolvedReturns409() {
        String machine = newMachine();
        String token = adminToken();
        var created = postAlert(alertBody(machine, AlertMetricType.ENERGY, AlertSeverity.WARNING, null), token);
        String id = created.getBody().id().toString();

        http.exchange(url("/api/v1/alerts/" + id + "/resolve"),
                HttpMethod.PATCH, new HttpEntity<>(bearer(token)), AlertResponse.class);
        var res = http.exchange(url("/api/v1/alerts/" + id + "/resolve"),
                HttpMethod.PATCH, new HttpEntity<>(bearer(token)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).contains("INVALID_STATE_TRANSITION");
    }
}