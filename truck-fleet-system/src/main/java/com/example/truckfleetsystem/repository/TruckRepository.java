package com.example.truckfleetsystem.repository;

import com.example.truckfleetsystem.entity.TruckEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TruckRepository extends JpaRepository<TruckEntity, Integer> {
}
