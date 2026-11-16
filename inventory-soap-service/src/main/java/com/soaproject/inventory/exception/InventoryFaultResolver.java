package com.soaproject.inventory.exception;

import org.springframework.stereotype.Component;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointExceptionResolver;
import org.springframework.ws.soap.SoapBody;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.SoapFaultDetailElement;
import org.springframework.ws.soap.SoapMessage;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.dom.DOMResult;
import java.util.Locale;

@Component
public class InventoryFaultResolver implements EndpointExceptionResolver {

    private static final String NAMESPACE = "http://soaproject.com/inventory";

    @Override
    public boolean resolveException(MessageContext messageContext, Object endpoint, Exception ex) {
        if (!(ex instanceof InventoryException ie)) {
            return false;
        }

        SoapMessage response = (SoapMessage) messageContext.getResponse();
        SoapBody body = response.getSoapBody();
        SoapFault fault = body.addClientOrSenderFault(ie.getMessage(), Locale.ENGLISH);

        SoapFaultDetail detail = fault.addFaultDetail();
        writeDetailText(detail, "code", ie.getCode());
        writeDetailText(detail, "message", ie.getMessage());
        return true;
    }

    /**
     * Adds an empty &lt;inv:{localName}/&gt; to the SOAP fault &lt;detail&gt; and
     * sets its text content. Spring WS 4.x returns a {@link SoapFaultDetailElement}
     * whose {@link SoapFaultDetailElement#getResult()} is a {@link DOMResult} wrapping
     * the underlying DOM element; {@code setTextContent} sets the text and lets the DOM
     * handle XML escaping.
     */
    private static void writeDetailText(SoapFaultDetail detail, String localName, String value) {
        SoapFaultDetailElement element =
                detail.addFaultDetailElement(new QName(NAMESPACE, localName, "inv"));
        Result result = element.getResult();
        if (!(result instanceof DOMResult domResult)) {
            throw new IllegalStateException(
                    "Unexpected SoapFaultDetailElement result type: " + result.getClass());
        }
        if (!(domResult.getNode() instanceof Element domElement)) {
            throw new IllegalStateException(
                    "Unexpected DOMResult node: " + domResult.getNode());
        }
        domElement.setTextContent(value == null ? "" : value);
    }
}