package com.example.truckfleetsystem.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "truck", schema = "public")
public class TruckEntity {
    @Column(name = "max_capacity_kg", nullable = false)
    private Integer maxCapacityKg;
    @Column(name = "license_plate", nullable = false, length = 10)
    private String licensePlate;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getMaxCapacityKg() {
        return maxCapacityKg;
    }

    public void setMaxCapacityKg(Integer maxCapacityKg) {
        this.maxCapacityKg = maxCapacityKg;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public Integer getId() {
        return id;
    }
}
