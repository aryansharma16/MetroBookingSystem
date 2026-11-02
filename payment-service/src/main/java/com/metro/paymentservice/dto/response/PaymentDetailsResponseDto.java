package com.metro.paymentservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.metro.paymentservice.entity.PaymentStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentDetailsResponseDto {
    private Long id;
    private Long bookingId;
    private Long userId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String failureReason;
    private LocalDateTime createdAt;
}
