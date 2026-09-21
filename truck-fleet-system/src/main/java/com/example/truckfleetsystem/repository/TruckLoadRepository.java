package com.example.truckfleetsystem.repository;

import com.example.truckfleetsystem.entity.TruckEntity;
import com.example.truckfleetsystem.entity.TruckLoadEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TruckLoadRepository extends JpaRepository<TruckLoadEntity,Long> {

    List<TruckLoadEntity> findByTruck(TruckEntity truck);
}
