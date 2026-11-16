package com.soaproject.maintenance.controller;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system")
public class SystemController {

    public record SystemInfoResponse(String service, String apiVersion, String status, Instant serverTime) {}

    @GetMapping("/info")
    public SystemInfoResponse info() {
        return new SystemInfoResponse("maintenance-service", "v1", "UP", Instant.now());
    }
}