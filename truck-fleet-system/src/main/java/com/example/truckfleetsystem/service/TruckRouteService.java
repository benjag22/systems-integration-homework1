package com.example.truckfleetsystem.service;

import com.example.truckfleetsystem.entity.RouteEntity;
import com.example.truckfleetsystem.entity.TruckEntity;
import com.example.truckfleetsystem.entity.TruckRouteEntity;
import org.springframework.stereotype.Service;
import com.example.truckfleetsystem.repository.TruckRouteRepository;

import java.util.List;

@Service
public class TruckRouteService {
    TruckRouteRepository truckRouteRepository;
    public TruckRouteService(TruckRouteRepository truckRouteRepository) {
        this.truckRouteRepository = truckRouteRepository;
    }

    public List<RouteEntity> findByTruck(TruckEntity truck) {
        return truckRouteRepository.findByTruck(truck)
                .stream()
                .map(TruckRouteEntity::getRoute)
                .toList();
    }
}
