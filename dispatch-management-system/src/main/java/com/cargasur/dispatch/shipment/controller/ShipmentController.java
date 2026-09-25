package com.cargasur.dispatch.shipment.controller;

import com.cargasur.dispatch.shipment.dto.CreateShipmentRequest;
import com.cargasur.dispatch.shipment.dto.ShipmentResponse;
import com.cargasur.dispatch.shipment.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/despachos")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @PostMapping
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        ShipmentResponse response = shipmentService.createShipment(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/{id}/revertir")
    public ResponseEntity<ShipmentResponse> revertShipment(@PathVariable Integer id) {
        return ResponseEntity.ok(shipmentService.revertShipment(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShipmentResponse> getShipmentById(@PathVariable Integer id) {
        return ResponseEntity.ok(shipmentService.getShipmentById(id));
    }

    @GetMapping
    public ResponseEntity<List<ShipmentResponse>> getAllShipments() {
        return ResponseEntity.ok(shipmentService.getAllShipments());
    }
}
