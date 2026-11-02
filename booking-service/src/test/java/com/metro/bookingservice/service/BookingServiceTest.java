package com.metro.bookingservice.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.metro.bookingservice.entity.Booking;
import com.metro.bookingservice.entity.BookingStatus;
import com.metro.bookingservice.event.PaymentFailedEvent;
import com.metro.bookingservice.event.RefundEventPublisher;
import com.metro.bookingservice.exception.EventPublishException;
import com.metro.bookingservice.exception.InvalidBookingStatusException;
import com.metro.bookingservice.exception.TicketAlreadyUsedException;
import com.metro.bookingservice.repository.BookingRepository;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RefundEventPublisher refundEventPublisher;

    @InjectMocks
    private BookingService bookingService;

    private Booking booking(Long id, Long userId, BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setUserId(userId);
        booking.setTicketCode("ticket-" + id);
        booking.setStatus(status);
        return booking;
    }

    @Test
    void cancelConfirmedBookingRequestsRefund() {
        Booking booking = booking(1L, 10L, BookingStatus.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.updateStatusIfCurrent(1L, BookingStatus.CONFIRMED, BookingStatus.REFUND_REQUESTED))
                .thenReturn(1);

        bookingService.cancelBooking(1L, 10L);

        verify(refundEventPublisher).publishRefundRequired(1L, "USER_CANCELLED");
    }

    @Test
    void cancelConfirmedBookingStillSucceedsWhenRefundEventFails() {
        Booking booking = booking(1L, 10L, BookingStatus.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.updateStatusIfCurrent(1L, BookingStatus.CONFIRMED, BookingStatus.REFUND_REQUESTED))
                .thenReturn(1);
        doThrow(new EventPublishException("Could not publish RefundRequired event"))
                .when(refundEventPublisher).publishRefundRequired(1L, "USER_CANCELLED");

        assertDoesNotThrow(() -> bookingService.cancelBooking(1L, 10L));
    }

    @Test
    void cancelPendingThatGetsConfirmedMeanwhileRequestsRefund() {
        Booking pending = booking(1L, 10L, BookingStatus.PENDING);
        Booking confirmed = booking(1L, 10L, BookingStatus.CONFIRMED);
        // 1st read: PENDING, re-read after the failed cancel: CONFIRMED, then the retry and final reads
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(pending), Optional.of(confirmed));
        when(bookingRepository.updateStatusIfCurrent(1L, BookingStatus.PENDING, BookingStatus.CANCELLED))
                .thenReturn(0);
        when(bookingRepository.updateStatusIfCurrent(1L, BookingStatus.CONFIRMED, BookingStatus.REFUND_REQUESTED))
                .thenReturn(1);

        bookingService.cancelBooking(1L, 10L);

        verify(refundEventPublisher).publishRefundRequired(1L, "USER_CANCELLED");
    }

    @Test
    void cancelUsedTicketIsRejected() {
        Booking booking = booking(1L, 10L, BookingStatus.USED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(InvalidBookingStatusException.class, () -> bookingService.cancelBooking(1L, 10L));

        verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
        verify(refundEventPublisher, never()).publishRefundRequired(any(), any());
    }

    @Test
    void useConfirmedTicketMarksItUsed() {
        Booking booking = booking(1L, 10L, BookingStatus.CONFIRMED);
        when(bookingRepository.findByTicketCode("ticket-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.markTicketUsed(eq(1L), eq(BookingStatus.CONFIRMED), eq(BookingStatus.USED), any()))
                .thenReturn(1);
        Booking used = booking(1L, 10L, BookingStatus.USED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(used));

        bookingService.useTicket("ticket-1");

        verify(bookingRepository).markTicketUsed(eq(1L), eq(BookingStatus.CONFIRMED), eq(BookingStatus.USED), any());
    }

    @Test
    void useAlreadyUsedTicketIsRejected() {
        Booking booking = booking(1L, 10L, BookingStatus.USED);
        when(bookingRepository.findByTicketCode("ticket-1")).thenReturn(Optional.of(booking));

        assertThrows(TicketAlreadyUsedException.class, () -> bookingService.useTicket("ticket-1"));

        verify(bookingRepository, never()).markTicketUsed(any(), any(), any(), any());
    }

    @Test
    void handlePaymentFailedMovesPendingToFailed() {
        Booking booking = booking(1L, 10L, BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.updateStatusIfCurrent(1L, BookingStatus.PENDING, BookingStatus.FAILED))
                .thenReturn(1);

        bookingService.handlePaymentFailed(paymentFailedEvent(1L, 10L));

        verify(bookingRepository).updateStatusIfCurrent(1L, BookingStatus.PENDING, BookingStatus.FAILED);
    }

    @Test
    void handlePaymentFailedWithWrongUserDoesNothing() {
        Booking booking = booking(1L, 10L, BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        bookingService.handlePaymentFailed(paymentFailedEvent(1L, 99L));

        verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
    }

    private PaymentFailedEvent paymentFailedEvent(Long bookingId, Long userId) {
        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setEventType("PAYMENT_FAILED");
        event.setBookingId(bookingId);
        event.setUserId(userId);
        event.setReason("USER_CANCELLED_CHECKOUT");
        return event;
    }
}
