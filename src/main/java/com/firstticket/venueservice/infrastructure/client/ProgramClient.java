package com.firstticket.venueservice.infrastructure.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// infrastructure/client/ProgramClient.java
@FeignClient(name = "program-service", url = "${feign.program-service.url}")
public interface ProgramClient {

    @GetMapping("/internal/v1/programs/venues/{venueId}/exists")
    boolean hasProgramsForVenue(@PathVariable("venueId") UUID venueId);
}
