package com.example.truckfleetsystem.service;

import com.example.truckfleetsystem.entity.RouteEntity;
import org.springframework.stereotype.Service;
import com.example.truckfleetsystem.repository.TruckRouteRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TruckRouteService {
    TruckRouteRepository repository;
    public TruckRouteService(TruckRouteRepository truckRouteRepository) {
        this.repository = truckRouteRepository;
    }

    @Transactional(readOnly = true)
    public List<RouteEntity> findByTruckId(int truckId) {
        return repository.findRoutesByTruckId(truckId);
    }
}
