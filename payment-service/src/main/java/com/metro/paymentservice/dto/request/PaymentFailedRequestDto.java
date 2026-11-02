package com.metro.paymentservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentFailedRequestDto {
    @NotBlank(message = "Razorpay order id is required")
    private String razorpayOrderId;

    @Size(max = 255, message = "Reason must be at most 255 characters")
    private String reason;
}
