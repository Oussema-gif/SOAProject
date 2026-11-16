package com.soa.desktop.client.rest.dto;

public class ServiceError extends RuntimeException {
    public final int status;
    public final String errorCode;
    public final String body;

    public ServiceError(int status, String errorCode, String message, String body) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.body = body;
    }
}