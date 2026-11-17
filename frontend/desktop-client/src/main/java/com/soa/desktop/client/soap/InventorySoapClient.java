package com.soa.desktop.client.soap;

import com.soa.desktop.client.soap.generated.*;
import com.soa.desktop.util.ActivityLogger;
import com.soa.desktop.ui.topology.TrafficEventBus;
import java.net.URL;
import jakarta.xml.ws.BindingProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class InventorySoapClient {

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "soap-worker");
        t.setDaemon(true);
        return t;
    });

    private InventoryPort port = null;
    private String initError = null;

    public InventorySoapClient() {
        this(System.getenv("INVENTORY_API_KEY"),
                System.getenv().getOrDefault("INVENTORY_WSDL_URL",
                        "http://localhost:8082/services/inventory.wsdl"));
    }

    public InventorySoapClient(String apiKey, String wsdlLocation) {
        try {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("INVENTORY_API_KEY is not configured");
            }
            URL wsdlUrl = new URL(wsdlLocation);
            InventoryPortService service = new InventoryPortService(wsdlUrl);
            this.port = service.getInventoryPortSoap11();

            var bindingProvider = (BindingProvider) port;
            var binding = bindingProvider.getBinding();
            List<jakarta.xml.ws.handler.Handler> handlerChain = new ArrayList<>();
            handlerChain.add(new ApiKeyHandler(apiKey));
            binding.setHandlerChain(handlerChain);
            bindingProvider.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                    wsdlLocation.replaceFirst("\\.wsdl(?:\\?.*)?$", ""));

        } catch (Exception e) {
            this.port = null;
            this.initError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            System.err.println("WARNING: SOAP service unavailable at startup: " + initError);
            ActivityLogger.log("SOAP", "INIT", "FAILED", "Service unavailable");
        }
    }

    public CompletableFuture<GetSparePartResponse> getSparePart(String partId) {
        if (port == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "SOAP client not initialized: " + initError));
        }
        return CompletableFuture.supplyAsync(() -> {
            String traceId = UUID.randomUUID().toString();
            long startedAt = System.nanoTime();
            ActivityLogger.log("SOAP", "getSparePart", "PENDING", "Part: " + partId, traceId, null, null);
            TrafficEventBus.publish("SOAP", "REQUEST", "PENDING");
            try {
                GetSparePartRequest request = new GetSparePartRequest();
                request.setPartId(partId);
                GetSparePartResponse response = port.getSparePart(request);
                TrafficEventBus.publish("SOAP", "RESPONSE", "SUCCESS");
                ActivityLogger.log("SOAP", "getSparePart", "SUCCESS",
                        "Available: " + response.getAvailableQuantity(), traceId, null,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                return response;
            } catch (Exception exception) {
                TrafficEventBus.publish("SOAP", "RESPONSE", "FAILED");
                ActivityLogger.log("SOAP", "getSparePart", "FAILED", exception.getMessage(), traceId, null,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                throw new RuntimeException("Inventory getSparePart failed: " + exception.getMessage(), exception);
            }
        }, executor);
    }

    public CompletableFuture<GetSparePartResponse> checkAuthenticatedService() {
        return getSparePart("PART-001");
    }

    public CompletableFuture<CheckStockResponse> checkStock(String partId, int quantity) {
        if (port == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "SOAP client not initialized: " + initError));
        }
        return CompletableFuture.supplyAsync(() -> {
            String requestId = UUID.randomUUID().toString();
            long startedAt = System.nanoTime();
            ActivityLogger.log("SOAP", "checkStock", "PENDING", "Part: " + partId, requestId, null, null);
            TrafficEventBus.publish("SOAP", "REQUEST", "PENDING");
            try {
                CheckStockRequest request = new CheckStockRequest();
                request.setPartId(partId);
                request.setQuantity(quantity);
                CheckStockResponse resp = port.checkStock(request);
                TrafficEventBus.publish("SOAP", "RESPONSE", "SUCCESS");
                ActivityLogger.log("SOAP", "checkStock", "SUCCESS", "Available: " + resp.getAvailableQuantity(),
                        requestId, null, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                return resp;
            } catch (Exception e) {
                TrafficEventBus.publish("SOAP", "RESPONSE", "FAILED");
                ActivityLogger.log("SOAP", "checkStock", "FAILED", e.getMessage(), requestId, null,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                throw e;
            }
        }, executor);
    }

    public CompletableFuture<ReservePartResponse> reservePart(String partId, int quantity, String requestId, String workOrderId) {
        if (port == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "SOAP client not initialized: " + initError));
        }
        return CompletableFuture.supplyAsync(() -> {
            long startedAt = System.nanoTime();
            ActivityLogger.log("SOAP", "reservePart", "PENDING", "Reserving " + partId, requestId, null, null);
            TrafficEventBus.publish("SOAP", "REQUEST", "PENDING");
            try {
                ReservePartRequest request = new ReservePartRequest();
                request.setPartId(partId);
                request.setQuantity(quantity);
                request.setRequestId(requestId);
                request.setWorkOrderId(workOrderId);
                ReservePartResponse resp = port.reservePart(request);
                TrafficEventBus.publish("SOAP", "RESPONSE", "SUCCESS");
                ActivityLogger.log("SOAP", "reservePart", "SUCCESS", "Res ID: " + resp.getReservationId(),
                        requestId, null, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                return resp;
            } catch (Exception e) {
                TrafficEventBus.publish("SOAP", "RESPONSE", "FAILED");
                ActivityLogger.log("SOAP", "reservePart", "FAILED", e.getMessage(), requestId, null,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                throw e;
            }
        }, executor);
    }

    public void close() {
        executor.shutdown();
    }
}