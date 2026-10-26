package com.example.truckfleetsystem.service;

public class LoadTruckMismatchException extends RuntimeException {
    public LoadTruckMismatchException(int loadId, int truckId) {
        super("la carga " + loadId + " no pertenece al camión " + truckId);
    }
}
