package com.cargasur.dispatch.shipment.repository;

import com.cargasur.dispatch.shipment.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Integer> {}