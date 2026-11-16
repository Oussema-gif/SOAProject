package com.soaproject.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.ws.test.server.RequestCreator;
import org.springframework.xml.transform.StringSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.xml.transform.Source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.create-schemas=true",
                "spring.datasource.password=test",
                "app.inventory.api-key=test-key-12345"
        })
@Testcontainers
class InventorySoapIntegrationTest {

    private static final String NS = "http://soaproject.com/inventory";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired ApplicationContext ctx;
    @Autowired JdbcTemplate jdbc;

    MockWebServiceClient client;

    @BeforeEach
    void setUp() {
        client = MockWebServiceClient.createClient(ctx);
        jdbc.execute("DELETE FROM inventory.stock_movements WHERE reference_id <> 'INITIAL-SEED'");
        jdbc.execute("DELETE FROM inventory.part_reservations");
        jdbc.execute("DELETE FROM inventory.purchase_orders");
        jdbc.execute("UPDATE inventory.spare_parts SET quantity_on_hand = CASE part_id " +
                "WHEN 'PART-001' THEN 50 WHEN 'PART-002' THEN 20 WHEN 'PART-003' THEN 30 " +
                "WHEN 'PART-004' THEN 15 WHEN 'PART-005' THEN 25 END");
        jdbc.execute("ALTER SEQUENCE inventory.reservation_seq RESTART WITH 1");
        jdbc.execute("ALTER SEQUENCE inventory.purchase_order_seq RESTART WITH 1");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Source req(String localPart, String bodyXml) {
        return new StringSource(
                "<inv:" + localPart + " xmlns:inv=\"" + NS + "\">" + bodyXml + "</inv:" + localPart + ">");
    }

    // ---------- getSparePart ----------

    @Test
    void getSparePartReturnsSeededData() {
        client.sendRequest(withPayload(req("getSparePartRequest", "<inv:partId>PART-001</inv:partId>")))
                .andExpect(noFault())
                .andExpect(xpath("//*[local-name()='partId']/text()").evaluatesTo("PART-001"))
                .andExpect(xpath("//*[local-name()='sku']/text()").evaluatesTo("SKU-BRG-001"))
                .andExpect(xpath("//*[local-name()='availableQuantity']/text()").evaluatesTo("50"));
    }

    @Test
    void getSparePartUnknownReturnsPartNotFoundFault() {
        client.sendRequest(withPayload(req("getSparePartRequest", "<inv:partId>PART-XXX</inv:partId>")))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//*[local-name()='code']/text()").evaluatesTo("PART_NOT_FOUND"));
    }

    // ---------- checkStock ----------

    @Test
    void checkStockWithEnoughReturnsAvailableTrue() {
        client.sendRequest(withPayload(req("checkStockRequest",
                        "<inv:partId>PART-001</inv:partId><inv:quantity>10</inv:quantity>")))
                .andExpect(noFault())
                .andExpect(xpath("//*[local-name()='available']/text()").evaluatesTo("true"))
                .andExpect(xpath("//*[local-name()='requestedQuantity']/text()").evaluatesTo("10"))
                .andExpect(xpath("//*[local-name()='availableQuantity']/text()").evaluatesTo("50"));
    }

    @Test
    void checkStockWithInsufficientReturnsAvailableFalseNotFault() {
        client.sendRequest(withPayload(req("checkStockRequest",
                        "<inv:partId>PART-002</inv:partId><inv:quantity>999</inv:quantity>")))
                .andExpect(noFault())
                .andExpect(xpath("//*[local-name()='available']/text()").evaluatesTo("false"))
                .andExpect(xpath("//*[local-name()='availableQuantity']/text()").evaluatesTo("20"));
    }

    // ---------- reservePart ----------

    @Test
    void reservePartDecrementsStock() {
        client.sendRequest(withPayload(req("reservePartRequest",
                        "<inv:requestId>req-1</inv:requestId>" +
                        "<inv:partId>PART-001</inv:partId>" +
                        "<inv:quantity>5</inv:quantity>" +
                        "<inv:workOrderId>wo-1</inv:workOrderId>")))
                .andExpect(noFault())
                .andExpect(xpath("//*[local-name()='reservationId']/text()").exists())
                .andExpect(xpath("//*[local-name()='status']/text()").evaluatesTo("CONFIRMED"))
                .andExpect(xpath("//*[local-name()='reservedQuantity']/text()").evaluatesTo("5"));

        Integer onHand = jdbc.queryForObject(
                "SELECT quantity_on_hand FROM inventory.spare_parts WHERE part_id='PART-001'",
                Integer.class);
        assertThat(onHand).isEqualTo(45);
    }

    @Test
    void reservePartInsufficientStockFaults() {
        client.sendRequest(withPayload(req("reservePartRequest",
                        "<inv:requestId>req-x</inv:requestId>" +
                        "<inv:partId>PART-004</inv:partId>" +
                        "<inv:quantity>999</inv:quantity>" +
                        "<inv:workOrderId>wo-x</inv:workOrderId>")))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//*[local-name()='code']/text()").evaluatesTo("INSUFFICIENT_STOCK"));
    }

    @Test
    void reservePartIsIdempotentByRequestId() {
        RequestCreator r = withPayload(req("reservePartRequest",
                "<inv:requestId>req-idem</inv:requestId>" +
                "<inv:partId>PART-001</inv:partId>" +
                "<inv:quantity>3</inv:quantity>" +
                "<inv:workOrderId>wo-i</inv:workOrderId>"));

        client.sendRequest(r).andExpect(noFault());
        client.sendRequest(r).andExpect(noFault())
                .andExpect(xpath("//*[local-name()='reservedQuantity']/text()").evaluatesTo("3"));

        Integer onHand = jdbc.queryForObject(
                "SELECT quantity_on_hand FROM inventory.spare_parts WHERE part_id='PART-001'",
                Integer.class);
        Integer reservations = jdbc.queryForObject(
                "SELECT count(*) FROM inventory.part_reservations WHERE request_id='req-idem'",
                Integer.class);
        assertThat(onHand).isEqualTo(47);  // only one decrement
        assertThat(reservations).isEqualTo(1);
    }

    @Test
    void reservePartSameRequestDifferentPayloadFaults() {
        client.sendRequest(withPayload(req("reservePartRequest",
                        "<inv:requestId>req-dup</inv:requestId>" +
                        "<inv:partId>PART-001</inv:partId>" +
                        "<inv:quantity>1</inv:quantity>" +
                        "<inv:workOrderId>wo-1</inv:workOrderId>")))
                .andExpect(noFault());

        client.sendRequest(withPayload(req("reservePartRequest",
                        "<inv:requestId>req-dup</inv:requestId>" +
                        "<inv:partId>PART-002</inv:partId>" +
                        "<inv:quantity>2</inv:quantity>" +
                        "<inv:workOrderId>wo-2</inv:workOrderId>")))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//*[local-name()='code']/text()").evaluatesTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void reservePartZeroQuantityFaults() {
        client.sendRequest(withPayload(req("reservePartRequest",
                        "<inv:requestId>req-zero</inv:requestId>" +
                        "<inv:partId>PART-001</inv:partId>" +
                        "<inv:quantity>0</inv:quantity>" +
                        "<inv:workOrderId>wo-zero</inv:workOrderId>")))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//*[local-name()='code']/text()").evaluatesTo("INVALID_QUANTITY"));
    }

    // ---------- releaseReservation ----------

    @Test
    void releaseRestoresStockAndIsIdempotent() {
        client.sendRequest(withPayload(req("reservePartRequest",
                        "<inv:requestId>req-rel</inv:requestId>" +
                        "<inv:partId>PART-003</inv:partId>" +
                        "<inv:quantity>7</inv:quantity>" +
                        "<inv:workOrderId>wo-rel</inv:workOrderId>")))
                .andExpect(noFault());

        String reservationId = jdbc.queryForObject(
                "SELECT reservation_id FROM inventory.part_reservations WHERE request_id='req-rel'",
                String.class);

        Integer afterReserve = jdbc.queryForObject(
                "SELECT quantity_on_hand FROM inventory.spare_parts WHERE part_id='PART-003'",
                Integer.class);
        assertThat(afterReserve).isEqualTo(23);

        client.sendRequest(withPayload(req("releaseReservationRequest",
                        "<inv:reservationId>" + reservationId + "</inv:reservationId>" +
                        "<inv:reason>cancelled</inv:reason>")))
                .andExpect(noFault())
                .andExpect(xpath("//*[local-name()='status']/text()").evaluatesTo("RELEASED"));

        client.sendRequest(withPayload(req("releaseReservationRequest",
                        "<inv:reservationId>" + reservationId + "</inv:reservationId>" +
                        "<inv:reason>cancelled-again</inv:reason>")))
                .andExpect(noFault())
                .andExpect(xpath("//*[local-name()='status']/text()").evaluatesTo("RELEASED"));

        Integer afterRelease = jdbc.queryForObject(
                "SELECT quantity_on_hand FROM inventory.spare_parts WHERE part_id='PART-003'",
                Integer.class);
        assertThat(afterRelease).isEqualTo(30);  // restored to initial
    }

    @Test
    void releaseUnknownFaults() {
        client.sendRequest(withPayload(req("releaseReservationRequest",
                        "<inv:reservationId>RES-999999</inv:reservationId>")))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//*[local-name()='code']/text()").evaluatesTo("RESERVATION_NOT_FOUND"));
    }

    // ---------- createPurchaseOrder ----------

    @Test
    void createPurchaseOrderReturnsIdAndStatus() {
        client.sendRequest(withPayload(req("createPurchaseOrderRequest",
                        "<inv:partId>PART-005</inv:partId>" +
                        "<inv:quantity>50</inv:quantity>" +
                        "<inv:workOrderId>wo-po</inv:workOrderId>" +
                        "<inv:reason>Restock</inv:reason>" +
                        "<inv:supplierReference>SUP-1</inv:supplierReference>")))
                .andExpect(noFault())
                .andExpect(xpath("//*[local-name()='purchaseOrderId']/text()").exists())
                .andExpect(xpath("//*[local-name()='status']/text()").evaluatesTo("CREATED"));
    }

    // ---------- security via real HTTP ----------

    @Test
    void missingApiKeyReturns401() {
        String envelope =
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "xmlns:inv=\"" + NS + "\">" +
                "<soapenv:Body>" +
                "<inv:checkStockRequest>" +
                "<inv:partId>PART-001</inv:partId>" +
                "<inv:quantity>1</inv:quantity>" +
                "</inv:checkStockRequest>" +
                "</soapenv:Body></soapenv:Envelope>";

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.TEXT_XML);
        ResponseEntity<String> res = http.exchange(url("/services/inventory"),
                HttpMethod.POST, new HttpEntity<>(envelope, h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void correctApiKeyIsAccepted() {
        String envelope =
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "xmlns:inv=\"" + NS + "\">" +
                "<soapenv:Body>" +
                "<inv:checkStockRequest>" +
                "<inv:partId>PART-001</inv:partId>" +
                "<inv:quantity>1</inv:quantity>" +
                "</inv:checkStockRequest>" +
                "</soapenv:Body></soapenv:Envelope>";

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.TEXT_XML);
        h.set("X-Inventory-Api-Key", "test-key-12345");
        ResponseEntity<String> res = http.exchange(url("/services/inventory"),
                HttpMethod.POST, new HttpEntity<>(envelope, h), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("availableQuantity");
        assertThat(res.getBody()).contains("50");
    }

    @Test
    void wsdlIsReachableWithoutApiKey() {
        ResponseEntity<String> res = http.getForEntity(
                url("/services/inventory.wsdl"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("reservePart");
    }
}