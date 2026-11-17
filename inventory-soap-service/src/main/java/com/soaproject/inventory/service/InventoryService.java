package com.soaproject.inventory.service;

import com.soaproject.inventory.entity.PartReservationEntity;
import com.soaproject.inventory.entity.PurchaseOrderEntity;
import com.soaproject.inventory.entity.SparePartEntity;
import com.soaproject.inventory.entity.StockMovementEntity;
import com.soaproject.inventory.exception.InventoryException;
import com.soaproject.inventory.repository.PartReservationRepository;
import com.soaproject.inventory.repository.PurchaseOrderRepository;
import com.soaproject.inventory.repository.SparePartRepository;
import com.soaproject.inventory.repository.StockMovementRepository;
import com.soaproject.inventory.wsdl.CheckStockResponse;
import com.soaproject.inventory.wsdl.CreatePurchaseOrderResponse;
import com.soaproject.inventory.wsdl.GetSparePartResponse;
import com.soaproject.inventory.wsdl.ReleaseReservationResponse;
import com.soaproject.inventory.wsdl.ReservePartResponse;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class InventoryService {

    private final SparePartRepository sparePartRepository;
    private final PartReservationRepository reservationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ReservationIdGenerator reservationIdGenerator;
    private final PurchaseOrderIdGenerator purchaseOrderIdGenerator;
    private final JdbcTemplate jdbcTemplate;

    public InventoryService(SparePartRepository sparePartRepository,
            PartReservationRepository reservationRepository,
            StockMovementRepository stockMovementRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            ReservationIdGenerator reservationIdGenerator,
            PurchaseOrderIdGenerator purchaseOrderIdGenerator,
            JdbcTemplate jdbcTemplate) {
        this.sparePartRepository = sparePartRepository;
        this.reservationRepository = reservationRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.reservationIdGenerator = reservationIdGenerator;
        this.purchaseOrderIdGenerator = purchaseOrderIdGenerator;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public GetSparePartResponse getSparePart(String partId) {
        SparePartEntity p = sparePartRepository.findById(partId)
                .orElseThrow(() -> new InventoryException("PART_NOT_FOUND",
                        "Part not found: " + partId));
        GetSparePartResponse r = new GetSparePartResponse();
        r.setPartId(p.getPartId());
        r.setSku(p.getSku());
        r.setName(p.getName());
        r.setDescription(p.getDescription());
        r.setUnit(p.getUnit());
        r.setAvailableQuantity(p.getQuantityOnHand());
        r.setMinimumStockLevel(p.getReorderLevel());
        return r;
    }

    @Transactional(readOnly = true)
    public CheckStockResponse checkStock(String partId, int quantity) {
        if (quantity <= 0) {
            throw new InventoryException("INVALID_QUANTITY",
                    "Quantity must be positive");
        }
        SparePartEntity p = sparePartRepository.findById(partId)
                .orElseThrow(() -> new InventoryException("PART_NOT_FOUND",
                        "Part not found: " + partId));

        CheckStockResponse r = new CheckStockResponse();
        r.setRequestedQuantity(quantity);
        r.setAvailableQuantity(p.getQuantityOnHand());
        boolean ok = p.getQuantityOnHand() >= quantity;
        r.setAvailable(ok);
        r.setMessage(ok ? "Sufficient stock" : "Insufficient stock");
        return r;
    }

    @Transactional
    public ReservePartResponse reservePart(String requestId, String partId,
            int quantity, String workOrderId) {
        if (quantity <= 0) {
            throw new InventoryException("INVALID_QUANTITY",
                    "Quantity must be positive");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new InventoryException("INVALID_REQUEST",
                    "requestId is required");
        }
        if (partId == null || partId.isBlank()) {
            throw new InventoryException("INVALID_REQUEST",
                    "partId is required");
        }
        if (workOrderId == null || workOrderId.isBlank()) {
            throw new InventoryException("INVALID_REQUEST",
                    "workOrderId is required");
        }

        lockRequestId(requestId);
        Optional<PartReservationEntity> existing = reservationRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            PartReservationEntity r = existing.get();
            if (!r.getPartId().equals(partId)
                    || r.getQuantity() != quantity
                    || !r.getWorkOrderId().equals(workOrderId)) {
                throw new InventoryException("IDEMPOTENCY_CONFLICT",
                        "requestId " + requestId + " already used with a different payload");
            }
            return buildReserveResponse(r);
        }

        SparePartEntity part = sparePartRepository.findWithLockByPartId(partId)
                .orElseThrow(() -> new InventoryException("PART_NOT_FOUND",
                        "Part not found: " + partId));

        if (part.getQuantityOnHand() < quantity) {
            throw new InventoryException("INSUFFICIENT_STOCK",
                    "Insufficient stock: requested " + quantity
                            + ", available " + part.getQuantityOnHand());
        }

        int newQty = part.getQuantityOnHand() - quantity;
        part.setQuantityOnHand(newQty);

        PartReservationEntity reservation = new PartReservationEntity();
        reservation.setReservationId(reservationIdGenerator.next());
        reservation.setPartId(partId);
        reservation.setQuantity(quantity);
        reservation.setRequestId(requestId);
        reservation.setWorkOrderId(workOrderId);
        reservation.setStatus("CONFIRMED");
        reservation = reservationRepository.saveAndFlush(reservation);

        StockMovementEntity movement = new StockMovementEntity();
        movement.setPartId(partId);
        movement.setMovementType("RESERVATION");
        movement.setQuantityDelta(-quantity);
        movement.setQuantityAfter(newQty);
        movement.setReferenceId(reservation.getReservationId());
        stockMovementRepository.save(movement);

        return buildReserveResponse(reservation);
    }

    private void lockRequestId(String requestId) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, requestId);
                statement.execute();
            }
            return null;
        });
    }

    @Transactional
    public ReleaseReservationResponse releaseReservation(String reservationId, String reason) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new InventoryException("INVALID_REQUEST",
                    "reservationId is required");
        }
        PartReservationEntity r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new InventoryException("RESERVATION_NOT_FOUND",
                        "Reservation not found: " + reservationId));

        if ("RELEASED".equals(r.getStatus())) {
            return buildReleaseResponse(r, r.getQuantity());
        }
        if (!"CONFIRMED".equals(r.getStatus())) {
            throw new InventoryException("INVALID_RESERVATION_STATE",
                    "Cannot release a " + r.getStatus() + " reservation");
        }

        SparePartEntity part = sparePartRepository.findWithLockByPartId(r.getPartId())
                .orElseThrow(() -> new InventoryException("PART_NOT_FOUND",
                        "Part not found: " + r.getPartId()));

        int newQty = part.getQuantityOnHand() + r.getQuantity();
        part.setQuantityOnHand(newQty);

        r.setStatus("RELEASED");
        r.setReleaseReason(reason);
        r.setReleasedAt(Instant.now());

        StockMovementEntity movement = new StockMovementEntity();
        movement.setPartId(r.getPartId());
        movement.setMovementType("RELEASE");
        movement.setQuantityDelta(r.getQuantity());
        movement.setQuantityAfter(newQty);
        movement.setReferenceId(r.getReservationId());
        stockMovementRepository.save(movement);

        return buildReleaseResponse(r, r.getQuantity());
    }

    @Transactional
    public CreatePurchaseOrderResponse createPurchaseOrder(String partId, int quantity,
            String workOrderId, String reason,
            String supplierReference) {
        if (quantity <= 0) {
            throw new InventoryException("INVALID_QUANTITY",
                    "Quantity must be positive");
        }
        if (!sparePartRepository.existsById(partId)) {
            throw new InventoryException("PART_NOT_FOUND",
                    "Part not found: " + partId);
        }

        PurchaseOrderEntity po = new PurchaseOrderEntity();
        po.setPurchaseOrderId(purchaseOrderIdGenerator.next());
        po.setPartId(partId);
        po.setQuantity(quantity);
        po.setWorkOrderId(workOrderId);
        po.setReason(reason);
        po.setSupplierReference(supplierReference);
        po.setStatus("CREATED");
        po = purchaseOrderRepository.saveAndFlush(po);

        CreatePurchaseOrderResponse response = new CreatePurchaseOrderResponse();
        response.setPurchaseOrderId(po.getPurchaseOrderId());
        response.setStatus(po.getStatus());
        response.setCreatedAt(toXmlDateTime(po.getCreatedAt()));
        return response;
    }

    // ---------- helpers ----------

    private ReservePartResponse buildReserveResponse(PartReservationEntity r) {
        ReservePartResponse resp = new ReservePartResponse();
        resp.setReservationId(r.getReservationId());
        resp.setStatus(r.getStatus());
        resp.setReservedQuantity(r.getQuantity());
        resp.setTimestamp(toXmlDateTime(r.getCreatedAt()));
        return resp;
    }

    private ReleaseReservationResponse buildReleaseResponse(PartReservationEntity r, int releasedQty) {
        ReleaseReservationResponse resp = new ReleaseReservationResponse();
        resp.setReservationId(r.getReservationId());
        resp.setStatus(r.getStatus());
        resp.setReleasedQuantity(releasedQty);
        resp.setMessage("RELEASED".equals(r.getStatus())
                ? "Reservation released"
                : "Reservation not released");
        return resp;
    }

    private static javax.xml.datatype.XMLGregorianCalendar toXmlDateTime(java.time.Instant instant) {
        return javax.xml.datatype.DatatypeFactory.newDefaultInstance()
                .newXMLGregorianCalendar(java.util.GregorianCalendar.from(
                        instant.atZone(java.time.ZoneOffset.UTC)));
    }
}