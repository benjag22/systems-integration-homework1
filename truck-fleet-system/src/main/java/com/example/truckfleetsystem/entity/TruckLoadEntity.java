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

    public TruckLoadEntity() {

    }

    public void setId(Integer id) {
        this.id = id;
    }
    public TruckLoadEntity(String detail, int weightKg, TruckEntity truck){
        this.detail = detail;
        this.weightKg = weightKg;
        this.truck = truck;
    }

    public String getDetail() {
        return detail;
    }

    public Integer getWeightKg() {
        return weightKg;
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
