package com.soa.desktop.client.soap;

import com.soa.desktop.client.soap.generated.*;
import com.soa.desktop.util.ActivityLogger;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventorySoapClient {

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "soap-worker");
        t.setDaemon(true);
        return t;
    });

    private InventoryPort port = null;
    private String initError = null;

    public InventorySoapClient() {
        try {
            URL wsdlUrl = new URL("http://localhost:8082/services/inventory.wsdl");
            InventoryPortService service = new InventoryPortService(wsdlUrl);
            this.port = service.getInventoryPortSoap11();

            var bindingProvider = (jakarta.xml.ws.BindingProvider) port;
            var binding = bindingProvider.getBinding();
            List<jakarta.xml.ws.handler.Handler> handlerChain = new ArrayList<>();
            handlerChain.add(new ApiKeyHandler("96dc2f3b58041ea7"));
            binding.setHandlerChain(handlerChain);

        } catch (Exception e) {
            this.initError = e.getMessage();
            System.err.println("WARNING: SOAP service unavailable at startup: " + initError);
            ActivityLogger.log("SOAP", "INIT", "FAILED", "Service unavailable");
        }
    }

    public CompletableFuture<CheckStockResponse> checkStock(String partId, int quantity) {
        if (port == null) {
            return CompletableFuture.failedFuture(new RuntimeException("SOAP client not initialized: " + initError));
        }
        return CompletableFuture.supplyAsync(() -> {
            ActivityLogger.log("SOAP", "checkStock", "PENDING", "Part: " + partId);
            try {
                CheckStockRequest request = new CheckStockRequest();
                request.setPartId(partId);
                request.setQuantity(quantity);
                CheckStockResponse resp = port.checkStock(request);
                ActivityLogger.log("SOAP", "checkStock", "SUCCESS", "Available: " + resp.getAvailableQuantity());
                return resp;
            } catch (Exception e) {
                ActivityLogger.log("SOAP", "checkStock", "FAILED", e.getMessage());
                throw e;
            }
        }, executor);
    }

    public CompletableFuture<ReservePartResponse> reservePart(String partId, int quantity, String requestId, String workOrderId) {
        if (port == null) {
            return CompletableFuture.failedFuture(new RuntimeException("SOAP client not initialized: " + initError));
        }
        return CompletableFuture.supplyAsync(() -> {
            ActivityLogger.log("SOAP", "reservePart", "PENDING", "Reserving " + partId);
            try {
                ReservePartRequest request = new ReservePartRequest();
                request.setPartId(partId);
                request.setQuantity(quantity);
                request.setRequestId(requestId);
                request.setWorkOrderId(workOrderId);
                ReservePartResponse resp = port.reservePart(request);
                ActivityLogger.log("SOAP", "reservePart", "SUCCESS", "Res ID: " + resp.getReservationId());
                return resp;
            } catch (Exception e) {
                ActivityLogger.log("SOAP", "reservePart", "FAILED", e.getMessage());
                throw e;
            }
        }, executor);
    }

    public void close() {
        executor.shutdown();
    }
}