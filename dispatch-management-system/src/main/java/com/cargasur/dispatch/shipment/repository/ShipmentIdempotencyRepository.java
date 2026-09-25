package com.cargasur.dispatch.shipment.repository;

import com.cargasur.dispatch.shipment.model.ShipmentIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentIdempotencyRepository extends JpaRepository<ShipmentIdempotency, String> {

    @Modifying
    @Query(value = """
            INSERT INTO shipment_idempotency (idempotency_key, request_hash, shipment_id)
            VALUES (:key, :requestHash, NULL)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int claimKey(@Param("key") String key, @Param("requestHash") String requestHash);
}
