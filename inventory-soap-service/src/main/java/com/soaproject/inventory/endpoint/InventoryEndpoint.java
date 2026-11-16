package com.soaproject.inventory.endpoint;

import com.soaproject.inventory.service.InventoryService;
import com.soaproject.inventory.wsdl.CheckStockRequest;
import com.soaproject.inventory.wsdl.CheckStockResponse;
import com.soaproject.inventory.wsdl.CreatePurchaseOrderRequest;
import com.soaproject.inventory.wsdl.CreatePurchaseOrderResponse;
import com.soaproject.inventory.wsdl.GetSparePartRequest;
import com.soaproject.inventory.wsdl.GetSparePartResponse;
import com.soaproject.inventory.wsdl.ReleaseReservationRequest;
import com.soaproject.inventory.wsdl.ReleaseReservationResponse;
import com.soaproject.inventory.wsdl.ReservePartRequest;
import com.soaproject.inventory.wsdl.ReservePartResponse;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
public class InventoryEndpoint {

    private static final String NAMESPACE = "http://soaproject.com/inventory";

    private final InventoryService service;

    public InventoryEndpoint(InventoryService service) {
        this.service = service;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "getSparePartRequest")
    @ResponsePayload
    public GetSparePartResponse getSparePart(@RequestPayload GetSparePartRequest req) {
        return service.getSparePart(req.getPartId());
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "checkStockRequest")
    @ResponsePayload
    public CheckStockResponse checkStock(@RequestPayload CheckStockRequest req) {
        return service.checkStock(req.getPartId(), req.getQuantity());
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "reservePartRequest")
    @ResponsePayload
    public ReservePartResponse reservePart(@RequestPayload ReservePartRequest req) {
        return service.reservePart(req.getRequestId(), req.getPartId(),
                req.getQuantity(), req.getWorkOrderId());
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "releaseReservationRequest")
    @ResponsePayload
    public ReleaseReservationResponse releaseReservation(@RequestPayload ReleaseReservationRequest req) {
        return service.releaseReservation(req.getReservationId(), req.getReason());
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "createPurchaseOrderRequest")
    @ResponsePayload
    public CreatePurchaseOrderResponse createPurchaseOrder(@RequestPayload CreatePurchaseOrderRequest req) {
        return service.createPurchaseOrder(req.getPartId(), req.getQuantity(),
                req.getWorkOrderId(), req.getReason(), req.getSupplierReference());
    }
}