package com.cargasur.dispatch.shipment.service;

import com.cargasur.dispatch.client.model.Client;
import com.cargasur.dispatch.client.repository.ClientRepository;
import com.cargasur.dispatch.config.exception.ResourceNotFoundException;
import com.cargasur.dispatch.shipment.client.FleetCapacityClient;
import com.cargasur.dispatch.shipment.dto.CreateShipmentRequest;
import com.cargasur.dispatch.shipment.dto.ShipmentResponse;
import com.cargasur.dispatch.shipment.model.Shipment;
import com.cargasur.dispatch.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ClientRepository clientRepository;
    private final FleetCapacityClient fleetCapacityClient;

    @Transactional
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado id: " + request.clientId()));

        var reservation = fleetCapacityClient.verifyAndReserveCapacity(request.truckId(), request.weightKg());
        if (!reservation.success()) {
            throw new IllegalStateException("No hay capacidad disponible en el camión: " + reservation.message());
        }

        Shipment shipment = Shipment.builder()
                .client(client)
                .truckId(request.truckId())
                .loadId(reservation.loadId())
                .weightKg(request.weightKg())
                .detail(request.detail().trim())
                .status("REGISTERED")
                .build();

        Shipment saved = shipmentRepository.save(shipment);
        return mapToResponse(saved);
    }

    @Transactional
    public ShipmentResponse revertShipment(Integer id) {
        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Despacho no encontrado id: " + id));

        if ("REVERTED".equals(shipment.getStatus())) {
            throw new IllegalStateException("El despacho ya se encuentra revertido");
        }

        fleetCapacityClient.releaseCapacity(shipment.getTruckId(), shipment.getLoadId(), shipment.getWeightKg());

        shipment.setStatus("REVERTED");
        Shipment updated = shipmentRepository.save(shipment);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentById(Integer id) {
        return shipmentRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Despacho no encontrado id: " + id));
    }

    @Transactional(readOnly = true)
    public List<ShipmentResponse> getAllShipments() {
        return shipmentRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ShipmentResponse mapToResponse(Shipment s) {
        return new ShipmentResponse(
                s.getId(),
                s.getClient().getId(),
                s.getTruckId(),
                s.getLoadId(),
                s.getWeightKg(),
                s.getDetail(),
                s.getStatus(),
                s.getCreatedAt()
        );
    }
}