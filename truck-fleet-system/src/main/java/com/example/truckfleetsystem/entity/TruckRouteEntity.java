package com.example.truckfleetsystem.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity(name = "truck_route")
public class TruckRouteEntity {
    @MapsId
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "route_id", nullable = false)
    private RouteEntity route;
    @MapsId
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "truck_id", nullable = false)
    private TruckEntity truck;
    @EmbeddedId
    private int id;

    public void setId(int id) {
        this.id = id;
    }

    public RouteEntity getRoute() {
        return route;
    }

    public void setRoute(RouteEntity route) {
        this.route = route;
    }

    public TruckEntity getTruck() {
        return truck;
    }

    public void setTruck(TruckEntity truck) {
        this.truck = truck;
    }

    public int getId() {
        return id;
    }
}
