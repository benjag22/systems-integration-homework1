package com.example.truckfleetsystem.service;

import com.example.truckfleetsystem.entity.TruckEntity;
import com.example.truckfleetsystem.entity.TruckLoadEntity;
import org.springframework.stereotype.Service;
import com.example.truckfleetsystem.repository.TruckRepository;
import org.springframework.transaction.annotation.Transactional;

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

    public List<TruckEntity> getAll() {
        return truckRepository.findAll();
    }

    @Transactional
    public TruckLoadEntity loadTruck(int truckId, String detail, int weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("El peso debe ser mayor que cero");
        }
        if (detail == null || detail.isBlank() || detail.length() > 300) {
            throw new IllegalArgumentException("El detalle es obligatorio y admite hasta 300 caracteres");
        }

        TruckEntity truck = truckRepository.findByIdForUpdate(truckId)
                .orElseThrow(() -> new TruckNotFoundException(truckId));

        long loaded = truckLoadService.findAllByTruck(truck).stream()
                .mapToLong(load -> load.getWeightKg())
                .sum();
        long available = truck.getMaxCapacityKg() - loaded;

        if (weight > available) {
            throw new InsufficientCapacityException(String.format(
                    "El camión %d no tiene capacidad suficiente: disponible %d kg, solicitado %d kg",
                    truckId, available, weight));
        }

        return truckLoadService.createLoad(truck, detail, weight);
    }

    @Transactional
    public TruckEntity unloadTruck(int truckId, int loadId) {
        TruckEntity truck = truckRepository.findByIdForUpdate(truckId)
                .orElseThrow(() -> new TruckNotFoundException(truckId));
        TruckLoadEntity load = truckLoadService.findById(loadId)
                .orElseThrow(() -> new LoadNotFoundException(loadId));

        if (!load.getTruck().getId().equals(truck.getId())) {
            throw new LoadTruckMismatchException(loadId, truckId);
        }

        truckLoadService.unload(load);
        return truck;
    }
}
