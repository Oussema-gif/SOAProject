package com.soaproject.maintenance;

import com.soaproject.maintenance.dto.CreateMachineRequest;
import com.soaproject.maintenance.dto.LoginRequest;
import com.soaproject.maintenance.dto.LoginResponse;
import com.soaproject.maintenance.dto.MachineResponse;
import com.soaproject.maintenance.dto.RegisterRequest;
import com.soaproject.maintenance.dto.UpdateMachineRequest;
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
class MachineCrudIntegrationTest {

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

    private String tokenForUser(String username, String password, Role role) {
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
    void listReturnsSeededMachinesWithPaginationShape() {
        HttpHeaders h = bearer(adminToken());
        ResponseEntity<String> res = http.exchange(url("/api/v1/machines?page=0&size=20"),
                HttpMethod.GET, new HttpEntity<>(h), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody())
                .contains("\"content\"")
                .contains("\"page\":0")
                .contains("\"size\":20")
                .contains("MACHINE-001")
                .contains("MACHINE-004");
    }

    @Test
    void listFiltersByStatus() {
        HttpHeaders h = bearer(adminToken());
        ResponseEntity<String> res = http.exchange(url("/api/v1/machines?status=RUNNING"),
                HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("RUNNING").doesNotContain("OFFLINE");
    }

    @Test
    void listRejectsUnboundedPagination() {
        HttpHeaders h = bearer(adminToken());
        ResponseEntity<String> res = http.exchange(url("/api/v1/machines?page=-1&size=101"),
                HttpMethod.GET, new HttpEntity<>(h), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).contains("INVALID_REQUEST");
    }

    @Test
    void getByMachineIdReturnsDetails() {
        HttpHeaders h = bearer(adminToken());
        ResponseEntity<MachineResponse> res = http.exchange(url("/api/v1/machines/MACHINE-001"),
                HttpMethod.GET, new HttpEntity<>(h), MachineResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().machineId()).isEqualTo("MACHINE-001");
        assertThat(res.getBody().status()).isEqualTo("RUNNING");
    }

    @Test
    void getUnknownMachineReturns404() {
        HttpHeaders h = bearer(adminToken());
        ResponseEntity<String> res = http.exchange(url("/api/v1/machines/NOPE-999"),
                HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("NOT_FOUND");
    }

    @Test
    void listWithoutTokenReturns401() {
        ResponseEntity<String> res = http.getForEntity(url("/api/v1/machines"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void maintenanceOperatorCanCreateMachine() {
        String op = tokenForUser("op1", "OpPass123!", Role.MAINTENANCE_OPERATOR);
        var body = new CreateMachineRequest("MACHINE-TEST-OP", "Op Machine",
                "Workshop Z", "MODEL-OP", MachineStatus.RUNNING);
        ResponseEntity<MachineResponse> res = http.exchange(url("/api/v1/machines"),
                HttpMethod.POST, new HttpEntity<>(body, bearer(op)), MachineResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().machineId()).isEqualTo("MACHINE-TEST-OP");
    }

    @Test
    void technicianCannotCreateMachine() {
        String tech = tokenForUser("tech1", "TechPass123!", Role.TECHNICIAN);
        var body = new CreateMachineRequest("MACHINE-TEST-TECH", "Tech Machine",
                "Workshop T", "MODEL-T", MachineStatus.RUNNING);
        ResponseEntity<String> res = http.exchange(url("/api/v1/machines"),
                HttpMethod.POST, new HttpEntity<>(body, bearer(tech)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void duplicateMachineIdReturns409() {
        HttpHeaders h = bearer(adminToken());
        var body = new CreateMachineRequest("MACHINE-001", "Duplicate",
                "X", "Y", MachineStatus.RUNNING);
        ResponseEntity<String> res = http.exchange(url("/api/v1/machines"),
                HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidMachineIdReturns400() {
        HttpHeaders h = bearer(adminToken());
        var body = new CreateMachineRequest("bad id!", "Bad", null, null, null);
        ResponseEntity<String> res = http.exchange(url("/api/v1/machines"),
                HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).contains("VALIDATION_FAILED");
    }

    @Test
    void updateMachineNameAndStatus() {
        HttpHeaders h = bearer(adminToken());
        http.exchange(url("/api/v1/machines"), HttpMethod.POST,
                new HttpEntity<>(new CreateMachineRequest("MACHINE-TEST-UPD", "Before",
                        "L1", "M1", MachineStatus.RUNNING), h), MachineResponse.class);

        ResponseEntity<MachineResponse> res = http.exchange(url("/api/v1/machines/MACHINE-TEST-UPD"),
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateMachineRequest("After", "L2", "M2",
                        MachineStatus.WARNING), h),
                MachineResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().name()).isEqualTo("After");
        assertThat(res.getBody().status()).isEqualTo("WARNING");
    }

    @Test
    void deleteUnreferencedMachineReturns204() {
        HttpHeaders h = bearer(adminToken());
        http.exchange(url("/api/v1/machines"), HttpMethod.POST,
                new HttpEntity<>(new CreateMachineRequest("MACHINE-TEST-DEL", "Del",
                        null, null, MachineStatus.OFFLINE), h), MachineResponse.class);

        ResponseEntity<Void> res = http.exchange(url("/api/v1/machines/MACHINE-TEST-DEL"),
                HttpMethod.DELETE, new HttpEntity<>(h), Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> after = http.exchange(url("/api/v1/machines/MACHINE-TEST-DEL"),
                HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void statusEndpointReturnsCurrentStatus() {
        HttpHeaders h = bearer(adminToken());
        ResponseEntity<String> res = http.exchange(url("/api/v1/machines/MACHINE-002/status"),
                HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("MACHINE-002").contains("RUNNING");
    }
}