package com.metro.stationservice.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class FareSlabCreateDto {

    @PositiveOrZero(message = "Minimum distance cannot be negative")
    private double minDistanceKm;

    @PositiveOrZero(message = "Maximum distance cannot be negative")
    private double maxDistanceKm;

    @NotNull(message = "Fare is required")
    @PositiveOrZero(message = "Fare cannot be negative")
    private BigDecimal fare;
}