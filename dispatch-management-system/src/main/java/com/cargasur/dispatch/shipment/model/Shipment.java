package com.cargasur.dispatch.shipment.model;

import com.cargasur.dispatch.client.model.Client;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipment")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "truck_id", nullable = false)
    private Integer truckId;

    @Column(name = "load_id")
    private Integer loadId;

    @Column(name = "weight_kg", nullable = false)
    private Integer weightKg;

    @Column(nullable = false, length = 300)
    private String detail;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
