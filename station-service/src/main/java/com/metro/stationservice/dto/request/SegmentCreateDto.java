package com.metro.stationservice.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

//SegmentCreateDto.java — request, raw ids since client is choosing from existing stations/lines
@Data
public class SegmentCreateDto {
    @NotNull(message = "Station A id is required")
	private Long stationAId;

    @NotNull(message = "Station B id is required")
	private Long stationBId;

    @NotNull(message = "Line id is required")
	private Long lineId;

    @Positive(message = "Distance must be greater than 0")
	private double distanceKm;
    
}