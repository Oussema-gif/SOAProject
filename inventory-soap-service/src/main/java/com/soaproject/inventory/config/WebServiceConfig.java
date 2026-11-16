package com.soaproject.inventory.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

import java.util.List;

@EnableWs
@Configuration
public class WebServiceConfig {

    public static final String NAMESPACE = "http://soaproject.com/inventory";

    @Bean
    public MessageDispatcherServlet messageDispatcherServlet(ApplicationContext ctx) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(ctx);
        servlet.setTransformWsdlLocations(true);
        return servlet;
    }

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcher(
            MessageDispatcherServlet servlet) {
        return new ServletRegistrationBean<>(servlet, "/services/*");
    }

    @Bean(name = "inventory")
    public DefaultWsdl11Definition inventoryWsdl(XsdSchema inventorySchema) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("InventoryPort");
        wsdl.setLocationUri("/services/inventory");
        wsdl.setTargetNamespace(NAMESPACE);
        wsdl.setSchema(inventorySchema);
        return wsdl;
    }

    @Bean
    public XsdSchema inventorySchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/inventory.xsd"));
    }
}