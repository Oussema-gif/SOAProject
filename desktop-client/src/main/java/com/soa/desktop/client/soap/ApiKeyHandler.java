package com.soa.desktop.client.soap;

import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.handler.soap.SOAPHandler;
import jakarta.xml.ws.handler.soap.SOAPMessageContext;

import javax.xml.namespace.QName;
import java.util.*;

public class ApiKeyHandler implements SOAPHandler<SOAPMessageContext> {
    private final String apiKey;

    public ApiKeyHandler(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Set<QName> getHeaders() { return Collections.emptySet(); }

    @Override
    @SuppressWarnings("unchecked")
    public boolean handleMessage(SOAPMessageContext context) {
        Boolean outbound = (Boolean) context.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
        if (outbound != null && outbound) {
            try {
                Map<String, List<String>> requestHeaders =
                        (Map<String, List<String>>) context.get(MessageContext.HTTP_REQUEST_HEADERS);
                if (requestHeaders == null) {
                    requestHeaders = new HashMap<>();
                    context.put(MessageContext.HTTP_REQUEST_HEADERS, requestHeaders);
                }
                requestHeaders.put("X-Inventory-Api-Key", Collections.singletonList(apiKey));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public boolean handleFault(SOAPMessageContext context) { return true; }

    @Override
    public void close(MessageContext context) { }
}