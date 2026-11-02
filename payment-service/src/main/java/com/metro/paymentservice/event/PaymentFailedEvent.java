package com.metro.paymentservice.event;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentFailedEvent {
    private String eventId;
    private String eventType;     // "PAYMENT_FAILED"
    private Instant occurredAt;
    private Long bookingId;
    private Long userId;
    private String reason;
}
