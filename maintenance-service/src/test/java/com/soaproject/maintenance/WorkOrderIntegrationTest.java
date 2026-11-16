package com.soaproject.maintenance;

import com.soaproject.maintenance.dto.AlertResponse;
import com.soaproject.maintenance.dto.AssignTechnicianRequest;
import com.soaproject.maintenance.dto.CreateAlertRequest;
import com.soaproject.maintenance.dto.CreateMachineRequest;
import com.soaproject.maintenance.dto.CreateTechnicianRequest;
import com.soaproject.maintenance.dto.CreateWorkOrderRequest;
import com.soaproject.maintenance.dto.LoginRequest;
import com.soaproject.maintenance.dto.LoginResponse;
import com.soaproject.maintenance.dto.RegisterRequest;
import com.soaproject.maintenance.dto.ReservationAssociationRequest;
import com.soaproject.maintenance.dto.TechnicianResponse;
import com.soaproject.maintenance.dto.UpdateWorkOrderStatusRequest;
import com.soaproject.maintenance.dto.WorkOrderResponse;
import com.soaproject.maintenance.entity.AlertMetricType;
import com.soaproject.maintenance.entity.AlertSeverity;
import com.soaproject.maintenance.entity.MachineStatus;
import com.soaproject.maintenance.entity.ReservationStatus;
import com.soaproject.maintenance.entity.Role;
import com.soaproject.maintenance.entity.WorkOrderPriority;
import com.soaproject.maintenance.entity.WorkOrderStatus;
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
class WorkOrderIntegrationTest {

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

    private String newMachine() {
        String code = "M-WO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var body = new CreateMachineRequest(code, "Test " + code, "Lab", "T-1", MachineStatus.RUNNING);
        var res = http.exchange(url("/api/v1/machines"), HttpMethod.POST,
                new HttpEntity<>(body, bearer(adminToken())), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return code;
    }

    private UUID newTechnician() {
        String email = "tech-" + UUID.randomUUID() + "@example.com";
        var body = new CreateTechnicianRequest("Tech " + email, email, null, true);
        var res = http.exchange(url("/api/v1/technicians"), HttpMethod.POST,
                new HttpEntity<>(body, bearer(adminToken())), TechnicianResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return res.getBody().id();
    }

    private WorkOrderResponse createWorkOrder(String machine, UUID alertId) {
        var body = new CreateWorkOrderRequest(machine, alertId,
                "Inspect machine", "Investigate", WorkOrderPriority.HIGH);
        var res = http.exchange(url("/api/v1/work-orders"), HttpMethod.POST,
                new HttpEntity<>(body, bearer(adminToken())), WorkOrderResponse.class);
        assertThat(res.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);
        return res.getBody();
    }

    private WorkOrderResponse moveStatus(UUID id, WorkOrderStatus target) {
        return moveStatus(id, target, adminToken());
    }

    private WorkOrderResponse moveStatus(UUID id, WorkOrderStatus target, String token) {
        return http.exchange(url("/api/v1/work-orders/" + id + "/status"), HttpMethod.PATCH,
                new HttpEntity<>(new UpdateWorkOrderStatusRequest(target), bearer(token)),
                WorkOrderResponse.class).getBody();
    }

    // -------------------- CREATE --------------------

    @Test
    void createReturnsOpenWorkOrder() {
        String machine = newMachine();
        WorkOrderResponse wo = createWorkOrder(machine, null);
        assertThat(wo.id()).isNotNull();
        assertThat(wo.machineId()).isEqualTo(machine);
        assertThat(wo.status()).isEqualTo("OPEN");
        assertThat(wo.priority()).isEqualTo("HIGH");
        assertThat(wo.reservations()).isEmpty();
    }

    @Test
    void createWithAlertLinksTheAlert() {
        String machine = newMachine();
        String token = adminToken();

        var alertBody = new CreateAlertRequest(
                machine, AlertMetricType.TEMPERATURE, AlertSeverity.CRITICAL,
                new BigDecimal("91.4"), new BigDecimal("85"),
                "Temperature exceeded", Instant.now(), null);
        var alert = http.exchange(url("/api/v1/alerts"), HttpMethod.POST,
                new HttpEntity<>(alertBody, bearer(token)), AlertResponse.class).getBody();

        WorkOrderResponse wo = createWorkOrder(machine, alert.id());
        assertThat(wo.alertId()).isEqualTo(alert.id());
    }

    @Test
    void duplicateLiveWorkOrderForAlertReturns200WithExisting() {
        String machine = newMachine();
        String token = adminToken();

        var alertBody = new CreateAlertRequest(
                machine, AlertMetricType.VIBRATION, AlertSeverity.WARNING,
                new BigDecimal("8.1"), new BigDecimal("7.0"),
                "Vibration exceeded", Instant.now(), null);
        var alert = http.exchange(url("/api/v1/alerts"), HttpMethod.POST,
                new HttpEntity<>(alertBody, bearer(token)), AlertResponse.class).getBody();

        WorkOrderResponse first = createWorkOrder(machine, alert.id());
        var second = http.exchange(url("/api/v1/work-orders"), HttpMethod.POST,
                new HttpEntity<>(new CreateWorkOrderRequest(machine, alert.id(),
                        "Duplicate", "dup", WorkOrderPriority.LOW), bearer(token)),
                WorkOrderResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().id()).isEqualTo(first.id());
    }

    @Test
    void createUnknownMachineReturns404() {
        var body = new CreateWorkOrderRequest("NO-SUCH-MACHINE", null,
                "x", "y", WorkOrderPriority.LOW);
        var res = http.exchange(url("/api/v1/work-orders"), HttpMethod.POST,
                new HttpEntity<>(body, bearer(adminToken())), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("NOT_FOUND");
    }

    @Test
    void technicianCannotCreateWorkOrder() {
        String machine = newMachine();
        String tech = tokenFor("wo-tech-1", "TechPass123!", Role.TECHNICIAN);
        var body = new CreateWorkOrderRequest(machine, null,
                "Blocked", "no", WorkOrderPriority.LOW);
        var res = http.exchange(url("/api/v1/work-orders"), HttpMethod.POST,
                new HttpEntity<>(body, bearer(tech)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -------------------- STATE MACHINE --------------------

    @Test
    void happyPathOpenToCompleted() {
        String machine = newMachine();
        WorkOrderResponse wo = createWorkOrder(machine, null);

        assertThat(moveStatus(wo.id(), WorkOrderStatus.CHECKING_INVENTORY).status())
                .isEqualTo("CHECKING_INVENTORY");
        assertThat(moveStatus(wo.id(), WorkOrderStatus.READY_FOR_MAINTENANCE).status())
                .isEqualTo("READY_FOR_MAINTENANCE");

        UUID techId = newTechnician();
        var assignRes = http.exchange(url("/api/v1/work-orders/" + wo.id() + "/assign"),
                HttpMethod.PATCH,
                new HttpEntity<>(new AssignTechnicianRequest(techId), bearer(adminToken())),
                WorkOrderResponse.class);
        assertThat(assignRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        WorkOrderResponse inProgress = moveStatus(wo.id(), WorkOrderStatus.IN_PROGRESS);
        assertThat(inProgress.status()).isEqualTo("IN_PROGRESS");

        WorkOrderResponse completed = moveStatus(wo.id(), WorkOrderStatus.COMPLETED);
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.completedAt()).isNotNull();
    }

    @Test
    void invalidTransitionReturns409() {
        String machine = newMachine();
        WorkOrderResponse wo = createWorkOrder(machine, null);

        var res = http.exchange(url("/api/v1/work-orders/" + wo.id() + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(new UpdateWorkOrderStatusRequest(WorkOrderStatus.COMPLETED),
                        bearer(adminToken())),
                String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).contains("INVALID_STATE_TRANSITION");
    }

    @Test
    void inProgressWithoutTechnicianReturns409() {
        String machine = newMachine();
        WorkOrderResponse wo = createWorkOrder(machine, null);
        moveStatus(wo.id(), WorkOrderStatus.CHECKING_INVENTORY);
        moveStatus(wo.id(), WorkOrderStatus.READY_FOR_MAINTENANCE);

        var res = http.exchange(url("/api/v1/work-orders/" + wo.id() + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(new UpdateWorkOrderStatusRequest(WorkOrderStatus.IN_PROGRESS),
                        bearer(adminToken())),
                String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // -------------------- ASSIGN --------------------

    @Test
    void assignUnknownTechnicianReturns404() {
        String machine = newMachine();
        WorkOrderResponse wo = createWorkOrder(machine, null);
        var res = http.exchange(url("/api/v1/work-orders/" + wo.id() + "/assign"),
                HttpMethod.PATCH,
                new HttpEntity<>(new AssignTechnicianRequest(UUID.randomUUID()),
                        bearer(adminToken())),
                String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -------------------- RESERVATION ATTACH --------------------

    @Test
    void attachReservationMovesToReadyForMaintenance() {
        String machine = newMachine();
        WorkOrderResponse wo = createWorkOrder(machine, null);
        moveStatus(wo.id(), WorkOrderStatus.CHECKING_INVENTORY);

        String resId = "RES-" + UUID.randomUUID().toString().substring(0, 8);
        var body = new ReservationAssociationRequest(resId, "PART-001", 2, null);
        var res = http.exchange(url("/api/v1/work-orders/" + wo.id() + "/reservation"),
                HttpMethod.PATCH, new HttpEntity<>(body, bearer(adminToken())),
                WorkOrderResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().status()).isEqualTo("READY_FOR_MAINTENANCE");
        assertThat(res.getBody().reservations()).hasSize(1);
        assertThat(res.getBody().reservations().get(0).reservationId()).isEqualTo(resId);
        assertThat(res.getBody().reservations().get(0).status()).isEqualTo("ACTIVE");
    }

    @Test
    void attachReservationFromPendingReservationWorks() {
        String machine = newMachine();
        WorkOrderResponse wo = createWorkOrder(machine, null);
        moveStatus(wo.id(), WorkOrderStatus.CHECKING_INVENTORY);
        moveStatus(wo.id(), WorkOrderStatus.PENDING_RESERVATION);

        var body = new ReservationAssociationRequest("RES-" + UUID.randomUUID(),
                "PART-002", 1, null);
        var res = http.exchange(url("/api/v1/work-orders/" + wo.id() + "/reservation"),
                HttpMethod.PATCH, new HttpEntity<>(body, bearer(adminToken())),
                WorkOrderResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().status()).isEqualTo("READY_FOR_MAINTENANCE");
    }

    @Test
    void attachReservationFromOpenReturns409() {
        String machine = newMachine();
        WorkOrderResponse wo = createWorkOrder(machine, null);

        var body = new ReservationAssociationRequest("RES-1", "PART-001", 1, null);
        var res = http.exchange(url("/api/v1/work-orders/" + wo.id() + "/reservation"),
                HttpMethod.PATCH, new HttpEntity<>(body, bearer(adminToken())),
                String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).contains("INVALID_STATE_TRANSITION");
    }

    @Test
    void releaseActiveReservationMarksReleased() {
        String machine = newMachine();
        WorkOrderResponse wo = createWorkOrder(machine, null);
        moveStatus(wo.id(), WorkOrderStatus.CHECKING_INVENTORY);

        String resId = "RES-" + UUID.randomUUID();
        http.exchange(url("/api/v1/work-orders/" + wo.id() + "/reservation"),
                HttpMethod.PATCH,
                new HttpEntity<>(new ReservationAssociationRequest(resId, "PART-003", 1, null),
                        bearer(adminToken())),
                WorkOrderResponse.class);

        var release = new ReservationAssociationRequest(resId, "PART-003", 1, ReservationStatus.RELEASED);
        var res = http.exchange(url("/api/v1/work-orders/" + wo.id() + "/reservation"),
                HttpMethod.PATCH, new HttpEntity<>(release, bearer(adminToken())),
                WorkOrderResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().reservations()).hasSize(1);
        assertThat(res.getBody().reservations().get(0).status()).isEqualTo("RELEASED");
        assertThat(res.getBody().reservations().get(0).releasedAt()).isNotNull();
    }

    // -------------------- MISC --------------------

    @Test
    void listFiltersByStatusAndMachine() {
        String machine = newMachine();
        createWorkOrder(machine, null);

        var res = http.exchange(
                url("/api/v1/work-orders?status=OPEN&machineId=" + machine),
                HttpMethod.GET, new HttpEntity<>(bearer(adminToken())), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains(machine).contains("OPEN");
    }

    @Test
    void listWithoutTokenReturns401() {
        var res = http.getForEntity(url("/api/v1/work-orders"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getUnknownReturns404() {
        var res = http.exchange(url("/api/v1/work-orders/" + UUID.randomUUID()),
                HttpMethod.GET, new HttpEntity<>(bearer(adminToken())), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}