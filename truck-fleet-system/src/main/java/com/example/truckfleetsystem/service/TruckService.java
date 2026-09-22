package com.example.truckfleetsystem.service;

import com.example.truckfleetsystem.entity.TruckEntity;
import com.example.truckfleetsystem.entity.TruckLoadEntity;
import org.springframework.stereotype.Service;
import com.example.truckfleetsystem.repository.TruckRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TruckService {
    TruckRepository truckRepository;
    TruckLoadService truckLoadService;

    public TruckService(TruckRepository truckRepository, TruckLoadService truckLoadService) {
        this.truckRepository = truckRepository;
        this.truckLoadService = truckLoadService;
    }

    public Optional<TruckEntity> getTruck(int truckId) {
        return truckRepository.findById(truckId);
    }
    public List<TruckEntity> getAll(){
        return truckRepository.findAll();
    }

    public TruckEntity loadTruck(TruckEntity truck, String detail, int weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("El peso debe ser mayor que cero");
        }

        List<TruckLoadEntity> currentLoads =
                truckLoadService.findAllByTruck(truck);

        int currentCapacity = currentLoads.stream()
                .mapToInt(TruckLoadEntity::getWeightKg)
                .sum();

        if (currentCapacity + weight > truck.getMaxCapacityKg()) {
            throw new IllegalStateException(
                    "El camión no tiene capacidad suficiente"
            );
        }

        truckLoadService.createLoad(truck, detail, weight);

        return truck;
    }
}
