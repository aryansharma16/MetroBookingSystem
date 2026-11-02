package com.metro.bookingservice.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.metro.bookingservice.client.StationServiceClient;
import com.metro.bookingservice.dto.request.CreateBookingRequestDto;
import com.metro.bookingservice.dto.response.BookingResponseDto;
import com.metro.bookingservice.dto.response.PathStepDto;
import com.metro.bookingservice.dto.response.RouteResponseDto;
import com.metro.bookingservice.entity.Booking;
import com.metro.bookingservice.entity.BookingStatus;
import com.metro.bookingservice.event.BookingEventPublisher;
import com.metro.bookingservice.event.PaymentFailedEvent;
import com.metro.bookingservice.event.PaymentSuccessEvent;
import com.metro.bookingservice.event.RefundEventPublisher;
import com.metro.bookingservice.exception.BookingNotFoundException;
import com.metro.bookingservice.exception.EventPublishException;
import com.metro.bookingservice.exception.InvalidBookingRequestException;
import com.metro.bookingservice.exception.InvalidBookingStatusException;
import com.metro.bookingservice.exception.RouteNotFoundException;
import com.metro.bookingservice.exception.StationServiceUnavailableException;
import com.metro.bookingservice.exception.TicketAlreadyUsedException;
import com.metro.bookingservice.repository.BookingRepository;

import feign.FeignException;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    @Autowired
    public StationServiceClient stationServiceClient;

    @Autowired
    public BookingRepository bookingRepository;

    @Autowired
    private BookingEventPublisher bookingEventPublisher;

    @Autowired
    private RefundEventPublisher refundEventPublisher;

    public BookingResponseDto createBooking(CreateBookingRequestDto request, Long userId) {
        if (request.getFromStationId().equals(request.getToStationId())) {
            throw new InvalidBookingRequestException("Source and destination stations must be different");
        }
        RouteResponseDto route;
        try {
            route = stationServiceClient.getRoute(request.getFromStationId(), request.getToStationId());
        } catch (FeignException e) {
            if (e.status() >= 400 && e.status() < 500) {
                throw new RouteNotFoundException("No route found for the given stations");
            }
            throw new StationServiceUnavailableException("Station service is currently unavailable");
        }

        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setFromStationId(request.getFromStationId());
        booking.setToStationId(request.getToStationId());
        booking.setFromStationName(extractStationName(route, true));
        booking.setToStationName(extractStationName(route, false));
        booking.setFare(route.getFare());
        booking.setDistanceKm(route.getTotalDistanceKm());
        booking.setStatus(BookingStatus.PENDING);

        Booking saved = bookingRepository.save(booking);

        bookingEventPublisher.publishBookingCreated(saved);   // after the save, outside any transaction

        return toResponseDto(saved);
    }

    private String extractStationName(RouteResponseDto route, boolean isFrom) {
        if (route.getPath() == null || route.getPath().isEmpty()) {
            throw new RouteNotFoundException("Route data is incomplete for the given stations");
        }
        PathStepDto step = isFrom ? route.getPath().get(0) : route.getPath().get(route.getPath().size() - 1);
        return step.getStation().getName();
    }

    public BookingResponseDto cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        if (!booking.getUserId().equals(userId)) {
            throw new BookingNotFoundException("Booking not found");
        }

        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStatusException(
                    "Only PENDING or CONFIRMED bookings can be cancelled. Current status: " + booking.getStatus());
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            // paid ticket: ask Payment for a refund, the refund consumer marks it REFUNDED later
            int requested = bookingRepository.updateStatusIfCurrent(
                    bookingId, BookingStatus.CONFIRMED, BookingStatus.REFUND_REQUESTED);
            if (requested == 0) {
                BookingStatus now = bookingRepository.findById(bookingId).orElseThrow().getStatus();
                throw new InvalidBookingStatusException(
                        "Only PENDING or CONFIRMED bookings can be cancelled. Current status: " + now);
            }
            try {
                refundEventPublisher.publishRefundRequired(bookingId, "USER_CANCELLED");
            } catch (EventPublishException e) {
                // the refund retry scheduler will publish it later
                log.warn("Refund request for booking {} not published now, will retry", bookingId);
            }
            return toResponseDto(bookingRepository.findById(bookingId).orElseThrow());
        }

        // compare-and-set: if the payment consumer confirmed it in the meantime, this changes 0 rows
        int updated = bookingRepository.updateStatusIfCurrent(
                bookingId, BookingStatus.PENDING, BookingStatus.CANCELLED);
        if (updated == 0) {
            BookingStatus now = bookingRepository.findById(bookingId).orElseThrow().getStatus();
            if (now == BookingStatus.CONFIRMED) {
                // payment confirmed it just now, so run the cancel again and take the refund path
                return cancelBooking(bookingId, userId);
            }
            throw new InvalidBookingStatusException(
                    "Only PENDING or CONFIRMED bookings can be cancelled. Current status: " + now);
        }

        return toResponseDto(bookingRepository.findById(bookingId).orElseThrow());
    }

    public List<BookingResponseDto> getMyBookings(Long userId) {
        List<Booking> bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<BookingResponseDto> result = new ArrayList<>();
        for (Booking booking : bookings) {
            result.add(toResponseDto(booking));
        }
        return result;
    }

    // frontend polls this after payment
    public BookingResponseDto getBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        if (!booking.getUserId().equals(userId)) {
            throw new BookingNotFoundException("Booking not found");
        }
        return toResponseDto(booking);
    }

    // Called by the Kafka consumer. Expected cases (mismatch, duplicate) return quietly
    // so Kafka does not retry them forever. Real failures (DB/Kafka down) propagate.
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        Booking booking = bookingRepository.findById(event.getBookingId()).orElse(null);

        if (booking == null) {
            log.error("PaymentSuccess for unknown booking {}", event.getBookingId());
            return;
        }
        if (!booking.getUserId().equals(event.getUserId())
                || booking.getFare().compareTo(event.getAmount()) != 0) {
            log.error("PaymentSuccess mismatch for booking {} (user or amount), needs manual review",
                    booking.getId());
            return;
        }

        int confirmed = bookingRepository.updateStatusIfCurrent(
                booking.getId(), BookingStatus.PENDING, BookingStatus.CONFIRMED);
        if (confirmed == 1) {
            log.info("Booking {} confirmed", booking.getId());
            return;
        }

        BookingStatus current = bookingRepository.findById(booking.getId()).orElseThrow().getStatus();

        if (current == BookingStatus.CANCELLED || current == BookingStatus.EXPIRED
                || current == BookingStatus.FAILED) {
            bookingRepository.updateStatusIfCurrent(
                    booking.getId(), current, BookingStatus.PAID_AFTER_EXPIRY);
            current = BookingStatus.PAID_AFTER_EXPIRY;
        }

        if (current == BookingStatus.PAID_AFTER_EXPIRY) {
            // published on every delivery; Payment's compare-and-set makes repeats harmless
            log.warn("Booking {} was dead when payment {} arrived, requesting refund",
                    booking.getId(), event.getRazorpayPaymentId());
            refundEventPublisher.publishRefundRequired(booking.getId(), "BOOKING_EXPIRED_AFTER_PAYMENT");
            return;
        }

        log.info("Duplicate PaymentSuccess for booking {} (already {}), ignored",
                booking.getId(), current);
    }

    // admin: gate device looks up a ticket by its QR code
    public BookingResponseDto getBookingByTicketCode(String ticketCode) {
        Booking booking = bookingRepository.findByTicketCode(ticketCode).orElse(null);
        if (booking == null) {
            throw new BookingNotFoundException("Ticket not found");
        }
        return toResponseDto(booking);
    }

    // admin: gate device marks a ticket as used when it is scanned
    public BookingResponseDto useTicket(String ticketCode) {
        Booking booking = bookingRepository.findByTicketCode(ticketCode).orElse(null);
        if (booking == null) {
            throw new BookingNotFoundException("Ticket not found");
        }
        if (booking.getStatus() == BookingStatus.USED) {
            throw new TicketAlreadyUsedException("Ticket already used at " + booking.getUsedAt());
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStatusException("Ticket is not valid. Current status: " + booking.getStatus());
        }

        // compare-and-set: a second scan at the same time changes 0 rows
        int updated = bookingRepository.markTicketUsed(
                booking.getId(), BookingStatus.CONFIRMED, BookingStatus.USED, LocalDateTime.now());
        if (updated == 0) {
            Booking current = bookingRepository.findById(booking.getId()).orElse(null);
            if (current == null) {
                throw new BookingNotFoundException("Ticket not found");
            }
            if (current.getStatus() == BookingStatus.USED) {
                throw new TicketAlreadyUsedException("Ticket already used at " + current.getUsedAt());
            }
            throw new InvalidBookingStatusException("Ticket is not valid. Current status: " + current.getStatus());
        }

        return toResponseDto(bookingRepository.findById(booking.getId()).orElseThrow());
    }

    // admin: all bookings, optionally filtered by status
    public List<BookingResponseDto> getAllBookings(String status) {
        List<Booking> bookings;
        if (status == null || status.isBlank()) {
            bookings = bookingRepository.findAllByOrderByCreatedAtDesc();
        } else {
            BookingStatus filter;
            try {
                filter = BookingStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new InvalidBookingRequestException("Invalid status: " + status);
            }
            bookings = bookingRepository.findByStatusOrderByCreatedAtDesc(filter);
        }

        List<BookingResponseDto> result = new ArrayList<>();
        for (Booking booking : bookings) {
            result.add(toResponseDto(booking));
        }
        return result;
    }

    // Called by the Kafka consumer. Payment failed for this booking, so it can no longer be paid
    // on time. A late success is still handled by handlePaymentSuccess (FAILED -> PAID_AFTER_EXPIRY).
    public void handlePaymentFailed(PaymentFailedEvent event) {
        Booking booking = bookingRepository.findById(event.getBookingId()).orElse(null);

        if (booking == null) {
            log.error("PaymentFailed for unknown booking {}", event.getBookingId());
            return;
        }
        if (!booking.getUserId().equals(event.getUserId())) {
            log.error("PaymentFailed user mismatch for booking {}, needs manual review", booking.getId());
            return;
        }

        int failed = bookingRepository.updateStatusIfCurrent(
                booking.getId(), BookingStatus.PENDING, BookingStatus.FAILED);
        if (failed == 1) {
            log.info("Booking {} marked FAILED", booking.getId());
        } else {
            log.info("PaymentFailed for booking {} ignored (not PENDING)", booking.getId());
        }
    }

    private BookingResponseDto toResponseDto(Booking booking) {
        BookingResponseDto dto = new BookingResponseDto();
        dto.setId(booking.getId());
        dto.setTicketCode(booking.getTicketCode());
        dto.setFromStationName(booking.getFromStationName());
        dto.setToStationName(booking.getToStationName());
        dto.setFare(booking.getFare());
        dto.setDistanceKm(booking.getDistanceKm());
        dto.setStatus(booking.getStatus().name());
        dto.setCreatedAt(booking.getCreatedAt());
        dto.setUsedAt(booking.getUsedAt());
        return dto;
    }
}