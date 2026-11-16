package com.soaproject.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
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
                "app.inventory.api-key=test-key-12345"
})
@Testcontainers
@Transactional
class InventoryMigrationTest {

        @Container
        @ServiceConnection
        static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

        @Autowired
        JdbcTemplate jdbc;

        @Test
        void seedsFiveSparePartsWithExpectedQuantities() {
                List<Map<String, Object>> rows = jdbc.queryForList(
                                "SELECT part_id, quantity_on_hand FROM inventory.spare_parts ORDER BY part_id");

                assertThat(rows).extracting(r -> r.get("part_id"))
                                .containsExactly("PART-001", "PART-002", "PART-003", "PART-004", "PART-005");
                assertThat(rows).extracting(r -> r.get("quantity_on_hand"))
                                .containsExactly(50, 20, 30, 15, 25);
        }

        @Test
        void everySeededStockLevelHasAnAuditMovement() {
                Integer unexplained = jdbc.queryForObject("""
                                SELECT count(*) FROM inventory.spare_parts p
                                WHERE p.quantity_on_hand <> COALESCE(
                                    (SELECT sum(m.quantity_delta) FROM inventory.stock_movements m
                                     WHERE m.part_id = p.part_id), 0)
                                """, Integer.class);

                assertThat(unexplained).isZero();
        }

        @Test
        void databaseRefusesNegativeStock() {
                assertThatThrownBy(() -> jdbc.update(
                                "UPDATE inventory.spare_parts SET quantity_on_hand = -1 WHERE part_id = 'PART-001'"))
                                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void generatesReadableReservationIds() {
                jdbc.update("""
                                INSERT INTO inventory.part_reservations (part_id, quantity, request_id, work_order_id)
                                VALUES ('PART-001', 1, 'REQ-1', 'WO-1')
                                """);

                String id = jdbc.queryForObject(
                                "SELECT reservation_id FROM inventory.part_reservations WHERE request_id = 'REQ-1'",
                                String.class);
                assertThat(id).matches("RES-\\d{6}");
        }

        @Test
        void rejectsDuplicateReservationRequestId() {
                jdbc.update("""
                                INSERT INTO inventory.part_reservations (part_id, quantity, request_id, work_order_id)
                                VALUES ('PART-001', 1, 'REQ-DUP', 'WO-1')
                                """);

                assertThatThrownBy(() -> jdbc.update("""
                                INSERT INTO inventory.part_reservations (part_id, quantity, request_id, work_order_id)
                                VALUES ('PART-002', 2, 'REQ-DUP', 'WO-2')
                                """)).isInstanceOf(DataIntegrityViolationException.class);
        }
}