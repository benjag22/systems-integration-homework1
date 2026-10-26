package com.cargasur.dispatch.truck.controller;

import com.cargasur.dispatch.truck.dto.TruckAvailabilityResponse;
import com.cargasur.dispatch.truck.service.TruckAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/camiones")
@RequiredArgsConstructor
public class TruckController {

    private final TruckAvailabilityService truckAvailabilityService;

    @GetMapping("/{id}/disponibilidad")
    public ResponseEntity<TruckAvailabilityResponse> getAvailability(@PathVariable Integer id) {
        return ResponseEntity.ok(truckAvailabilityService.getAvailability(id));
    }
}
