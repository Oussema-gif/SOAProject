package com.soaproject.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.flyway.create-schemas=true",
        "spring.datasource.password=test",
        "app.jwt.secret=dGVzdC1zZWNyZXQtZm9yLXRlc3RzLW9ubHktMzItYnl0ZXMh",
        "app.seed.admin.password=TestPass123!"
})
@Testcontainers
@Transactional
class MaintenanceMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsAllMaintenanceTables() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'maintenance' AND table_type = 'BASE TABLE'
                """, String.class);

        assertThat(tables).contains("users", "machines", "technicians", "alerts",
                "work_orders", "work_order_reservations");
    }

    @Test
    void seedsFourDemoMachines() {
        List<String> codes = jdbc.queryForList(
                "SELECT code FROM maintenance.machines ORDER BY code", String.class);

        assertThat(codes).containsExactly("MACHINE-001", "MACHINE-002", "MACHINE-003", "MACHINE-004");
    }

    @Test
    void rejectsSecondOpenAlertForSameMachineAndMetric() {
        UUID machine = machineId("MACHINE-001");

        insertActiveAlert(machine, "TEMPERATURE");
        insertActiveAlert(machine, "VIBRATION"); // different metric is allowed

        assertThatThrownBy(() -> insertActiveAlert(machine, "TEMPERATURE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsNewAlertAfterPreviousOneIsResolved() {
        UUID machine = machineId("MACHINE-002");

        insertActiveAlert(machine, "PRESSURE");
        jdbc.update("""
                UPDATE maintenance.alerts SET status = 'RESOLVED', resolved_at = now()
                WHERE machine_id = ? AND metric_type = 'PRESSURE'
                """, machine);
        insertActiveAlert(machine, "PRESSURE");

        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM maintenance.alerts
                WHERE machine_id = ? AND metric_type = 'PRESSURE'
                """, Integer.class, machine);
        assertThat(total).isEqualTo(2);
    }

    @Test
    void rejectsResolvedAlertWithoutResolvedTimestamp() {
        UUID machine = machineId("MACHINE-003");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO maintenance.alerts
                (machine_id, metric_type, severity, observed_value, threshold_value, message, status, occurred_at)
                VALUES (?, 'ENERGY', 'WARNING', 13.0, 12.0, 'test', 'RESOLVED', now())
                """, machine)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID machineId(String code) {
        return jdbc.queryForObject("SELECT id FROM maintenance.machines WHERE code = ?", UUID.class, code);
    }

    private void insertActiveAlert(UUID machineId, String metric) {
        jdbc.update("""
                INSERT INTO maintenance.alerts
                (machine_id, metric_type, severity, observed_value, threshold_value, message, status, occurred_at)
                VALUES (?, ?, 'CRITICAL', 91.4, 85.0, 'test alert', 'ACTIVE', now())
                """, machineId, metric);
    }
}