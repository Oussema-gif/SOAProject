package com.soaproject.maintenance;

import com.soaproject.maintenance.dto.CreateTechnicianRequest;
import com.soaproject.maintenance.dto.LoginRequest;
import com.soaproject.maintenance.dto.LoginResponse;
import com.soaproject.maintenance.dto.RegisterRequest;
import com.soaproject.maintenance.dto.TechnicianResponse;
import com.soaproject.maintenance.dto.UpdateTechnicianRequest;
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
class TechnicianCrudIntegrationTest {

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
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(admin);
        h.setContentType(MediaType.APPLICATION_JSON);
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

    @Test
    void adminCanCreateAndReadTechnician() {
        HttpHeaders h = bearer(adminToken());
        var body = new CreateTechnicianRequest("Houssem A.", "houssem@example.com", null, true);
        ResponseEntity<TechnicianResponse> res = http.exchange(url("/api/v1/technicians"),
                HttpMethod.POST, new HttpEntity<>(body, h), TechnicianResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().fullName()).isEqualTo("Houssem A.");
        assertThat(res.getBody().email()).isEqualTo("houssem@example.com");
        assertThat(res.getBody().id()).isNotNull();

        ResponseEntity<TechnicianResponse> read = http.exchange(
                url("/api/v1/technicians/" + res.getBody().id()),
                HttpMethod.GET, new HttpEntity<>(h), TechnicianResponse.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody().fullName()).isEqualTo("Houssem A.");
    }

    @Test
    void maintenanceOperatorCannotCreateTechnician() {
        String op = tokenFor("op_t", "OpPass123!", Role.MAINTENANCE_OPERATOR);
        var body = new CreateTechnicianRequest("Blocked", "blocked@example.com", null, true);
        ResponseEntity<String> res = http.exchange(url("/api/v1/technicians"),
                HttpMethod.POST, new HttpEntity<>(body, bearer(op)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void duplicateEmailReturns409() {
        HttpHeaders h = bearer(adminToken());
        var body = new CreateTechnicianRequest("Dup", "dup@example.com", null, true);
        http.exchange(url("/api/v1/technicians"), HttpMethod.POST,
                new HttpEntity<>(body, h), TechnicianResponse.class);

        ResponseEntity<String> res = http.exchange(url("/api/v1/technicians"),
                HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidEmailReturns400() {
        HttpHeaders h = bearer(adminToken());
        var body = new CreateTechnicianRequest("Bad", "not-an-email", null, true);
        ResponseEntity<String> res = http.exchange(url("/api/v1/technicians"),
                HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).contains("VALIDATION_FAILED");
    }

    @Test
    void updateTechnician() {
        HttpHeaders h = bearer(adminToken());
        var create = new CreateTechnicianRequest("Old Name", "upd@example.com", null, true);
        TechnicianResponse created = http.exchange(url("/api/v1/technicians"),
                HttpMethod.POST, new HttpEntity<>(create, h), TechnicianResponse.class).getBody();

        var update = new UpdateTechnicianRequest("New Name", "upd@example.com", null, false);
        ResponseEntity<TechnicianResponse> res = http.exchange(
                url("/api/v1/technicians/" + created.id()),
                HttpMethod.PUT, new HttpEntity<>(update, h), TechnicianResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().fullName()).isEqualTo("New Name");
        assertThat(res.getBody().active()).isFalse();
    }

    @Test
    void deleteTechnicianReturns204() {
        HttpHeaders h = bearer(adminToken());
        var create = new CreateTechnicianRequest("Del", "del@example.com", null, true);
        TechnicianResponse created = http.exchange(url("/api/v1/technicians"),
                HttpMethod.POST, new HttpEntity<>(create, h), TechnicianResponse.class).getBody();

        ResponseEntity<Void> res = http.exchange(url("/api/v1/technicians/" + created.id()),
                HttpMethod.DELETE, new HttpEntity<>(h), Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> after = http.exchange(url("/api/v1/technicians/" + created.id()),
                HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getUnknownTechnicianReturns404() {
        HttpHeaders h = bearer(adminToken());
        ResponseEntity<String> res = http.exchange(url("/api/v1/technicians/" + UUID.randomUUID()),
                HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listWithoutTokenReturns401() {
        ResponseEntity<String> res = http.getForEntity(url("/api/v1/technicians"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}