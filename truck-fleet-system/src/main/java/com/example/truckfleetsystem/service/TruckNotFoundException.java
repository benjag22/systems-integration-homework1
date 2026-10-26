package com.example.truckfleetsystem.service;

public class TruckNotFoundException extends RuntimeException {
    public TruckNotFoundException(int truckId) {
        super("no se encontró el camión con id: " + truckId);
    }
}
