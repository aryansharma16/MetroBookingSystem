package com.metro.bookingservice.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class BookingResponseDto {
    private Long id;
    private String ticketCode;
    private String fromStationName;
    private String toStationName;
    private BigDecimal fare;
    private double distanceKm;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime usedAt;
}