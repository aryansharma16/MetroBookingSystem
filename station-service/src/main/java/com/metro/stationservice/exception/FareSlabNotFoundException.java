package com.metro.stationservice.exception;

public class FareSlabNotFoundException extends RuntimeException {
    public FareSlabNotFoundException(Long id) {
        super("Fare slab not found with id: " + id);
    }
}
