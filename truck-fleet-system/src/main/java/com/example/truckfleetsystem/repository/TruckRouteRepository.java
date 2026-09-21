package com.example.truckfleetsystem.repository;

import com.example.truckfleetsystem.entity.RouteEntity;
import com.example.truckfleetsystem.entity.TruckEntity;
import com.example.truckfleetsystem.entity.TruckRouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TruckRouteRepository extends JpaRepository<TruckRouteEntity, Integer> {

    @Query("""
        select r
        from TruckRouteEntity tr
        join tr.route r
        where tr.truck.id = :truckId
    """)
    List<RouteEntity> findRoutesByTruckId(@Param("truckId") int truckId);
}
