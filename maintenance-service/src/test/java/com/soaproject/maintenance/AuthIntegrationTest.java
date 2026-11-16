package com.soaproject.maintenance;

import com.soaproject.maintenance.dto.LoginRequest;
import com.soaproject.maintenance.dto.LoginResponse;
import com.soaproject.maintenance.dto.RegisterRequest;
import com.soaproject.maintenance.dto.UserResponse;
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
class AuthIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate http;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String loginAsAdmin() {
        var res = http.postForEntity(url("/api/v1/auth/login"),
                new LoginRequest("admin", "AdminPass123!"), LoginResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody().accessToken();
    }

    @Test
    void seededAdminCanLogIn() {
        var res = http.postForEntity(url("/api/v1/auth/login"),
                new LoginRequest("admin", "AdminPass123!"), LoginResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().accessToken()).isNotBlank();
        assertThat(res.getBody().role()).isEqualTo("ADMIN");
        assertThat(res.getBody().tokenType()).isEqualTo("Bearer");
    }

    @Test
    void wrongPasswordReturns401() {
        var res = http.postForEntity(url("/api/v1/auth/login"),
                new LoginRequest("admin", "wrong"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void meWithoutTokenReturns401() {
        var res = http.getForEntity(url("/api/v1/auth/me"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void meWithTokenReturnsCurrentUser() {
        String token = loginAsAdmin();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var res = http.exchange(url("/api/v1/auth/me"),
                HttpMethod.GET, new HttpEntity<>(headers), UserResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().username()).isEqualTo("admin");
        assertThat(res.getBody().role()).isEqualTo("ADMIN");
    }

    @Test
    void registerWithoutTokenReturns401() {
        var res = http.postForEntity(url("/api/v1/auth/register"),
                new RegisterRequest("newtech", "TechPass123!", Role.TECHNICIAN), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminCanRegisterNewUser() {
        String token = loginAsAdmin();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var res = http.exchange(url("/api/v1/auth/register"),
                HttpMethod.POST,
                new HttpEntity<>(new RegisterRequest("newtech", "TechPass123!", Role.TECHNICIAN), headers),
                UserResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().username()).isEqualTo("newtech");
        assertThat(res.getBody().role()).isEqualTo("TECHNICIAN");
    }

    @Test
    void adminRegisteringDuplicateUsernameReturns409() {
        String token = loginAsAdmin();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var res = http.exchange(url("/api/v1/auth/register"),
                HttpMethod.POST,
                new HttpEntity<>(new RegisterRequest("admin", "AnotherPass123!", Role.TECHNICIAN), headers),
                String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}