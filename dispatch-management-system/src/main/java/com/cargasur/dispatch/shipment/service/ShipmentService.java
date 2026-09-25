package com.cargasur.dispatch.shipment.service;

import com.cargasur.dispatch.client.model.Client;
import com.cargasur.dispatch.client.repository.ClientRepository;
import com.cargasur.dispatch.config.exception.ResourceNotFoundException;
import com.cargasur.dispatch.shipment.client.FleetCapacityClient;
import com.cargasur.dispatch.shipment.dto.CreateShipmentRequest;
import com.cargasur.dispatch.shipment.dto.ShipmentLink;
import com.cargasur.dispatch.shipment.dto.ShipmentResponse;
import com.cargasur.dispatch.shipment.model.Shipment;
import com.cargasur.dispatch.shipment.model.ShipmentIdempotency;
import com.cargasur.dispatch.shipment.repository.ShipmentIdempotencyRepository;
import com.cargasur.dispatch.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentIdempotencyRepository idempotencyRepository;
    private final ClientRepository clientRepository;
    private final FleetCapacityClient fleetCapacityClient;

    @Transactional
    public ShipmentResponse createShipment(String idempotencyKey, CreateShipmentRequest request) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 255) {
            throw new IllegalArgumentException("Idempotency-Key es obligatorio y debe tener hasta 255 caracteres");
        }

        String requestHash = hashRequest(request);
        int claimed = idempotencyRepository.claimKey(key, requestHash);
        if (claimed == 0) {
            ShipmentIdempotency previous = idempotencyRepository.findById(key)
                    .orElseThrow(() -> new IllegalStateException("No se pudo recuperar la solicitud idempotente"));
            if (!previous.getRequestHash().equals(requestHash)) {
                throw new IllegalStateException("El Idempotency-Key ya fue utilizado con otros datos");
            }
            if (previous.getShipmentId() == null) {
                throw new IllegalStateException("La solicitud idempotente todavía no tiene un despacho asociado");
            }
            return shipmentRepository.findById(previous.getShipmentId())
                    .map(this::mapToResponse)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Despacho idempotente no encontrado id: " + previous.getShipmentId()));
        }

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
        ShipmentIdempotency idempotency = idempotencyRepository.findById(key)
                .orElseThrow(() -> new IllegalStateException("No se pudo guardar la clave idempotente"));
        idempotency.setShipmentId(saved.getId());
        idempotencyRepository.save(idempotency);
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
        String shipmentUrl = "/v1/despachos/" + s.getId();
        Map<String, ShipmentLink> links = new LinkedHashMap<>();
        links.put("self", new ShipmentLink(shipmentUrl));
        links.put("collection", new ShipmentLink("/v1/despachos"));
        if ("REGISTERED".equals(s.getStatus())) {
            links.put("revert", new ShipmentLink(shipmentUrl + "/revertir"));
        }

        return new ShipmentResponse(
                s.getId(),
                s.getClient().getId(),
                s.getTruckId(),
                s.getLoadId(),
                s.getWeightKg(),
                s.getDetail(),
                s.getStatus(),
                s.getCreatedAt(),
                Map.copyOf(links)
        );
    }

    private String hashRequest(CreateShipmentRequest request) {
        String canonicalRequest = request.clientId() + "|" + request.truckId() + "|"
                + request.weightKg() + "|" + request.detail().trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
