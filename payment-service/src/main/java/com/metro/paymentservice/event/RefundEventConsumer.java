package com.metro.paymentservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.metro.paymentservice.service.PaymentService;

@Component
public class RefundEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RefundEventConsumer.class);

    @Autowired
    private PaymentService paymentService;

    @KafkaListener(
        topics = "refund-required",
        properties = {
            "spring.json.value.default.type=com.metro.paymentservice.event.RefundRequiredEvent"
        })
    public void onRefundRequired(RefundRequiredEvent event) {
        if (event == null || event.getBookingId() == null) {
            log.warn("Skipping malformed RefundRequired event");
            return;
        }
        paymentService.refundForBooking(event.getBookingId(), event.getReason());
    }
}