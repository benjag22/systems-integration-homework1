package com.example.truckfleetsystem.repository;

import com.example.truckfleetsystem.entity.TruckEntity;
import com.example.truckfleetsystem.entity.TruckRouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TruckRouteRepository extends JpaRepository<TruckRouteEntity, Integer> {

    List<TruckRouteEntity> findByTruck(TruckEntity truck);
}
