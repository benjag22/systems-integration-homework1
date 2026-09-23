package com.example.truckfleetsystem.service;

import com.example.truckfleetsystem.entity.TruckEntity;
import com.example.truckfleetsystem.entity.TruckLoadEntity;
import org.springframework.stereotype.Service;
import com.example.truckfleetsystem.repository.TruckLoadRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TruckLoadService {
    TruckLoadRepository truckLoadRepository;

    public TruckLoadService(TruckLoadRepository truckLoadRepository) {
        this.truckLoadRepository = truckLoadRepository;
    }

    public List<TruckLoadEntity> findAllByTruck(TruckEntity truck) {
        return truckLoadRepository.findByTruck(truck);
    }
    public void createLoad(TruckEntity truck, String detail, int weight){
        TruckLoadEntity createdLoad = new TruckLoadEntity(detail, weight, truck);
        truckLoadRepository.save(createdLoad);
    }

    public Optional<TruckLoadEntity> findById(int id){
        return truckLoadRepository.findById(id);
    }
    public void unload(TruckLoadEntity load){
        truckLoadRepository.delete(load);
    }
}
