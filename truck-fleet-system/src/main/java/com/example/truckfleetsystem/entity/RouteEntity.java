package com.example.truckfleetsystem.entity;
import jakarta.persistence.*;

@Entity
@Table(name = "route", schema = "public")
public class RouteEntity {
    @Column(name = "destination", nullable = false, length = 100)
    private String destination;
    @Column(name = "origin", nullable = false, length = 100)
    private String origin;
    @Column(name = "distance_km", nullable = false)
    private Integer distanceKm;
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public Integer getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Integer distanceKm) {
        this.distanceKm = distanceKm;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }
}
