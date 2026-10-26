package com.example.truckfleetsystem.repository;

import com.example.truckfleetsystem.entity.TruckEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TruckRepository extends JpaRepository<TruckEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TruckEntity t where t.id = :id")
    Optional<TruckEntity> findByIdForUpdate(@Param("id") Integer id);
}
