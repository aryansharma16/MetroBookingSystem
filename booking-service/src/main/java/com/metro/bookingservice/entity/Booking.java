package com.metro.bookingservice.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "bookings", indexes = {
    @Index(name = "idx_booking_status_created", columnList = "status, created_at"),
    @Index(name = "idx_booking_user_created", columnList = "user_id, created_at")
})
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, updatable = false)
    private String ticketCode; // random, unguessable — used in the QR code, not the DB id
    private Long userId; // reference — who booked it

    private Long fromStationId; // reference — technical pointer
    private Long toStationId;

    private String fromStationName; // frozen — for display, survives renames/deletion
    private String toStationName;

    @Column(precision = 10, scale = 2)
    private BigDecimal fare;       // frozen — what was actually paid
    private double distanceKm; // frozen — trip fact at that time

    // varchar, not a MySQL ENUM column, so adding a new status does not need a schema change
    @Enumerated(EnumType.STRING)
    @Column(length = 30, columnDefinition = "varchar(30)")
    private BookingStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime usedAt;   // set when the ticket is scanned at the gate
    
    @PrePersist                 //PrePersist — a JPA life cycle hook. It means "run this method automatically, right before this entity is saved to the database for the first time.
    public void prePersist() {
        if (this.ticketCode == null) {
            this.ticketCode = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
