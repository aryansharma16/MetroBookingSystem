package com.metro.bookingservice.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.metro.bookingservice.entity.Booking;
import com.metro.bookingservice.entity.BookingStatus;
import com.metro.bookingservice.event.RefundEventPublisher;
import com.metro.bookingservice.exception.EventPublishException;
import com.metro.bookingservice.repository.BookingRepository;

@Component
public class RefundRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefundRetryScheduler.class);

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RefundEventPublisher refundEventPublisher;

    // every 5 minutes by default
    @Scheduled(fixedDelayString = "${booking.refund-retry.rate-ms:300000}")
    public void retryRefunds() {
        retryRefunds(BookingStatus.PAID_AFTER_EXPIRY, "BOOKING_EXPIRED_AFTER_PAYMENT");
        retryRefunds(BookingStatus.REFUND_REQUESTED, "USER_CANCELLED");
    }

    private void retryRefunds(BookingStatus status, String reason) {
        for (Booking b : bookingRepository.findByStatus(status)) {
            try {
                refundEventPublisher.publishRefundRequired(b.getId(), reason);
            } catch (EventPublishException e) {
                log.warn("Kafka unavailable, refund retry deferred");
                break;
            } catch (RuntimeException e) {
                log.error("Refund retry failed for booking {}", b.getId(), e);
            }
        }
    }
}