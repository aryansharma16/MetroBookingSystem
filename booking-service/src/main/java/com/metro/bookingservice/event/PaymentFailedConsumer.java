package com.metro.bookingservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.metro.bookingservice.service.BookingService;

@Component
public class PaymentFailedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentFailedConsumer.class);

    @Autowired
    private BookingService bookingService;

    @KafkaListener(
        topics = "payment-failed",
        properties = {
            "spring.json.value.default.type=com.metro.bookingservice.event.PaymentFailedEvent"
        })
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (event == null || event.getBookingId() == null || event.getUserId() == null) {
            log.warn("Skipping malformed PaymentFailed event");
            return;
        }
        bookingService.handlePaymentFailed(event);
    }
}
