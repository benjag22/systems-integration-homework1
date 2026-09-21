package com.example.truckfleetsystem.service;

import com.example.grpc.proto.GetTruckRequest;
import com.example.truckfleetsystem.entity.TruckEntity;
import org.springframework.stereotype.Service;
import com.example.truckfleetsystem.repository.TruckRepository;

import java.util.Optional;

@Service
public class TruckService {
    TruckRepository truckRepository;

    public TruckService(TruckRepository truckRepository) {
        this.truckRepository = truckRepository;
    }

    public Optional<TruckEntity> getTruck(GetTruckRequest request) {
        int id = request.getTruckId();
        return truckRepository.findById(id);
    }
}
