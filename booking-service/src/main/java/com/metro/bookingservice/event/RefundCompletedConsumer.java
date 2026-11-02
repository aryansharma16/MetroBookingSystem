package com.metro.bookingservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.metro.bookingservice.entity.BookingStatus;
import com.metro.bookingservice.repository.BookingRepository;

@Component
public class RefundCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(RefundCompletedConsumer.class);

    @Autowired
    private BookingRepository bookingRepository;

    @KafkaListener(
        topics = "refund-completed",
        properties = {
            "spring.json.value.default.type=com.metro.bookingservice.event.RefundCompletedEvent"
        })
    public void onRefundCompleted(RefundCompletedEvent event) {
        if (event == null || event.getBookingId() == null) {
            log.warn("Skipping malformed RefundCompleted event");
            return;
        }
        // CONFIRMED covers an admin manual refund done from payment-service
        int updated = bookingRepository.updateStatusIfCurrent(
                event.getBookingId(), BookingStatus.PAID_AFTER_EXPIRY, BookingStatus.REFUNDED);
        if (updated == 0) {
            updated = bookingRepository.updateStatusIfCurrent(
                    event.getBookingId(), BookingStatus.REFUND_REQUESTED, BookingStatus.REFUNDED);
        }
        if (updated == 0) {
            updated = bookingRepository.updateStatusIfCurrent(
                    event.getBookingId(), BookingStatus.CONFIRMED, BookingStatus.REFUNDED);
        }
        if (updated == 1) {
            log.info("Booking {} marked REFUNDED", event.getBookingId());
        } else {
            log.info("RefundCompleted for booking {} ignored (not PAID_AFTER_EXPIRY)", event.getBookingId());
        }
    }
}