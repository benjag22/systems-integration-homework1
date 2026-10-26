package com.example.truckfleetsystem.service;

public class LoadNotFoundException extends RuntimeException {
    public LoadNotFoundException(int loadId) {
        super("no se encontró la carga con id: " + loadId);
    }
}
