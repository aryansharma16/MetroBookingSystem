package com.metro.bookingservice.entity;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED,
    EXPIRED,
    PAID_AFTER_EXPIRY,   // money taken, booking dead, refund requested
    REFUNDED,            // refund accepted by Razorpay; bank credit takes days
    USED,                // ticket scanned at the gate
    REFUND_REQUESTED     // user cancelled a CONFIRMED booking, refund asked from Payment
}