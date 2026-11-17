package com.soa.desktop.client.soap;

import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.handler.soap.SOAPMessageContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyHandlerTest {

    @Test
    void attachesConfiguredApiKeyWithoutMutatingExistingHeaderMap() {
        ApiKeyHandler handler = new ApiKeyHandler("configured-secret");
        SOAPMessageContext context = mock(SOAPMessageContext.class);
        Map<String, List<String>> existing = Map.of("Accept", List.of("text/xml"));
        when(context.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY)).thenReturn(Boolean.TRUE);
        when(context.get(MessageContext.HTTP_REQUEST_HEADERS)).thenReturn(existing);

        assertTrue(handler.handleMessage(context));

        ArgumentCaptor<Map> headers = ArgumentCaptor.forClass(Map.class);
        verify(context).put(eq(MessageContext.HTTP_REQUEST_HEADERS), headers.capture());
        assertEquals(List.of("configured-secret"), headers.getValue().get("X-Inventory-Api-Key"));
        assertEquals(List.of("text/xml"), existing.get("Accept"));
    }

    @Test
    void rejectsMissingApiKeyConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new ApiKeyHandler(" "));
    }
}
