package com.example.truckfleetsystem.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "truck_load", schema = "public")
public class TruckLoadEntity {
    @Column(name = "detail", nullable = false, length = 300)
    private String detail;
    @Column(name = "weight_kg", nullable = false)
    private Integer weightKg;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "truck_id", nullable = false)
    private TruckEntity truck;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Integer getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Integer weightKg) {
        this.weightKg = weightKg;
    }

    public TruckEntity getTruck() {
        return truck;
    }

    public void setTruck(TruckEntity truck) {
        this.truck = truck;
    }

    public Integer getId() {
        return id;
    }
}
