package com.metro.paymentservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.metro.paymentservice.client.RazorpayGateway;
import com.metro.paymentservice.dto.request.PaymentFailedRequestDto;
import com.metro.paymentservice.dto.request.VerifyPaymentRequestDto;
import com.metro.paymentservice.entity.Payment;
import com.metro.paymentservice.entity.PaymentStatus;
import com.metro.paymentservice.event.PaymentEventPublisher;
import com.metro.paymentservice.event.RefundEventPublisher;
import com.metro.paymentservice.exception.EventPublishException;
import com.metro.paymentservice.exception.InvalidPaymentStateException;
import com.metro.paymentservice.exception.InvalidSignatureException;
import com.metro.paymentservice.exception.PaymentNotFoundException;
import com.metro.paymentservice.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String ORDER_ID = "order_1";
    private static final String RAZORPAY_PAYMENT_ID = "pay_1";
    private static final Long PAYMENT_ID = 1L;
    private static final Long BOOKING_ID = 7L;
    private static final Long USER_ID = 3L;

    @Mock
    private RazorpayGateway razorpayGateway;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @Mock
    private RefundEventPublisher refundEventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void webhookWithInvalidSignatureIsRejectedBeforeAnythingElse() {
        String payload = capturedPayload(ORDER_ID, 10000);
        when(razorpayGateway.verifyWebhookSignature(payload, "bad")).thenReturn(false);

        try {
            paymentService.handleWebhook(payload, "bad");
            fail("Expected InvalidSignatureException");
        } catch (InvalidSignatureException e) {
            assertEquals("Invalid webhook signature", e.getMessage());
        }

        verifyNoInteractions(paymentRepository, paymentEventPublisher);
    }

    @Test
    void webhookWithoutSignatureIsRejected() {
        try {
            paymentService.handleWebhook(capturedPayload(ORDER_ID, 10000), null);
            fail("Expected InvalidSignatureException");
        } catch (InvalidSignatureException e) {
            assertEquals("Invalid webhook signature", e.getMessage());
        }

        verifyNoInteractions(razorpayGateway, paymentRepository);
    }

    @Test
    void capturedWebhookRecordsSuccessfulPaymentAndPublishes() {
        String payload = capturedPayload(ORDER_ID, 10000);
        when(razorpayGateway.verifyWebhookSignature(payload, "sig")).thenReturn(true);
        Payment created = payment(PaymentStatus.CREATED);
        Payment success = payment(PaymentStatus.SUCCESS);
        success.setRazorpayPaymentId(RAZORPAY_PAYMENT_ID);
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(created));
        when(paymentRepository.markStatus(PAYMENT_ID, RAZORPAY_PAYMENT_ID,
                PaymentStatus.CREATED, PaymentStatus.SUCCESS)).thenReturn(1);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(success));

        paymentService.handleWebhook(payload, "sig");

        verify(paymentEventPublisher).publishPaymentSuccess(success, RAZORPAY_PAYMENT_ID);
        verify(paymentRepository).markEventPublished(PAYMENT_ID);
    }

    @Test
    void capturedWebhookForFailedPaymentRecordsSuccess() {
        String payload = capturedPayload(ORDER_ID, 10000);
        when(razorpayGateway.verifyWebhookSignature(payload, "sig")).thenReturn(true);
        Payment failed = payment(PaymentStatus.FAILED);
        Payment success = payment(PaymentStatus.SUCCESS);
        success.setRazorpayPaymentId(RAZORPAY_PAYMENT_ID);
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(failed));
        when(paymentRepository.markStatus(PAYMENT_ID, RAZORPAY_PAYMENT_ID,
                PaymentStatus.FAILED, PaymentStatus.SUCCESS)).thenReturn(1);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(success));

        paymentService.handleWebhook(payload, "sig");

        verify(paymentEventPublisher).publishPaymentSuccess(success, RAZORPAY_PAYMENT_ID);
    }

    @Test
    void capturedWebhookForUnknownOrderDoesNothing() {
        String payload = capturedPayload("order_other_app", 10000);
        when(razorpayGateway.verifyWebhookSignature(payload, "sig")).thenReturn(true);
        when(paymentRepository.findByRazorpayOrderId("order_other_app")).thenReturn(Optional.empty());

        paymentService.handleWebhook(payload, "sig");

        verify(paymentRepository, never()).markStatus(any(), any(), any(), any());
        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    void capturedWebhookWithAmountMismatchIsNotRecorded() {
        String payload = capturedPayload(ORDER_ID, 9900);
        when(razorpayGateway.verifyWebhookSignature(payload, "sig")).thenReturn(true);
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment(PaymentStatus.CREATED)));

        paymentService.handleWebhook(payload, "sig");

        verify(paymentRepository, never()).markStatus(any(), any(), any(), any());
        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    void capturedWebhookForAlreadyRefundedPaymentIsAcknowledged() {
        String payload = capturedPayload(ORDER_ID, 10000);
        when(razorpayGateway.verifyWebhookSignature(payload, "sig")).thenReturn(true);
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment(PaymentStatus.REFUNDED)));

        paymentService.handleWebhook(payload, "sig");

        verify(paymentRepository, never()).markStatus(any(), any(), any(), any());
        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    void otherWebhookEventIsIgnored() {
        String payload = "{\"event\":\"payment.failed\",\"payload\":{}}";
        when(razorpayGateway.verifyWebhookSignature(payload, "sig")).thenReturn(true);

        paymentService.handleWebhook(payload, "sig");

        verifyNoInteractions(paymentRepository, paymentEventPublisher);
    }

    @Test
    void malformedWebhookBodyWithValidSignatureIsIgnored() {
        String payload = "not json";
        when(razorpayGateway.verifyWebhookSignature(payload, "sig")).thenReturn(true);

        paymentService.handleWebhook(payload, "sig");

        verifyNoInteractions(paymentRepository, paymentEventPublisher);
    }

    @Test
    void verifyPaymentRejectsRefundedPayment() {
        VerifyPaymentRequestDto req = verifyRequest();
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment(PaymentStatus.REFUNDED)));
        when(razorpayGateway.verifySignature(ORDER_ID, RAZORPAY_PAYMENT_ID, "sig")).thenReturn(true);

        try {
            paymentService.verifyPayment(req, USER_ID);
            fail("Expected InvalidPaymentStateException");
        } catch (InvalidPaymentStateException e) {
            assertEquals("Payment already REFUNDED", e.getMessage());
        }

        verify(paymentRepository, never()).markStatus(any(), any(), any(), any());
    }

    @Test
    void markPaymentFailedRecordsReasonAndPublishes() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment(PaymentStatus.CREATED)));
        when(paymentRepository.transitionWithFailureReason(PAYMENT_ID, "USER_CANCELLED_CHECKOUT",
                PaymentStatus.CREATED, PaymentStatus.FAILED)).thenReturn(1);

        paymentService.markPaymentFailed(failedRequest(null), USER_ID);

        verify(paymentEventPublisher).publishPaymentFailed(any(Payment.class), eq("USER_CANCELLED_CHECKOUT"));
    }

    @Test
    void markPaymentFailedOnAlreadyFailedPaymentDoesNothing() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment(PaymentStatus.FAILED)));

        paymentService.markPaymentFailed(failedRequest("retry"), USER_ID);

        verify(paymentRepository, never()).transitionWithFailureReason(any(), any(), any(), any());
        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    void markPaymentFailedForAnotherUsersPaymentIsNotFound() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment(PaymentStatus.CREATED)));
        PaymentFailedRequestDto req = failedRequest("retry");

        try {
            paymentService.markPaymentFailed(req, USER_ID + 1);
            fail("Expected PaymentNotFoundException");
        } catch (PaymentNotFoundException e) {
            assertEquals("Payment not found", e.getMessage());
        }

        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    void markPaymentFailedOnSuccessfulPaymentIsRejected() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment(PaymentStatus.SUCCESS)));
        PaymentFailedRequestDto req = failedRequest("retry");

        try {
            paymentService.markPaymentFailed(req, USER_ID);
            fail("Expected InvalidPaymentStateException");
        } catch (InvalidPaymentStateException e) {
            assertEquals("Payment already SUCCESS", e.getMessage());
        }
        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    void markPaymentFailedStillSucceedsWhenKafkaIsDown() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment(PaymentStatus.CREATED)));
        when(paymentRepository.transitionWithFailureReason(PAYMENT_ID, "retry",
                PaymentStatus.CREATED, PaymentStatus.FAILED)).thenReturn(1);
        doThrow(new EventPublishException("down")).when(paymentEventPublisher)
                .publishPaymentFailed(any(Payment.class), eq("retry"));

        paymentService.markPaymentFailed(failedRequest("retry"), USER_ID);
    }

    @Test
    void adminRefundOnCreatedPaymentIsRejected() {
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(payment(PaymentStatus.CREATED)));

        try {
            paymentService.adminRefund(BOOKING_ID);
            fail("Expected InvalidPaymentStateException");
        } catch (InvalidPaymentStateException e) {
            assertEquals("Only successful payments can be refunded. Current status: CREATED", e.getMessage());
        }

        verifyNoInteractions(razorpayGateway, refundEventPublisher);
    }

    @Test
    void adminRefundOfSuccessfulPaymentUsesAdminReason() {
        Payment success = payment(PaymentStatus.SUCCESS);
        success.setRazorpayPaymentId(RAZORPAY_PAYMENT_ID);
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(success));
        when(paymentRepository.transition(PAYMENT_ID, PaymentStatus.SUCCESS, PaymentStatus.REFUND_PENDING))
                .thenReturn(1);

        paymentService.adminRefund(BOOKING_ID);

        verify(razorpayGateway).refund(success, "ADMIN_MANUAL_REFUND");
        verify(refundEventPublisher).publishRefundCompleted(BOOKING_ID);
    }

    @Test
    void getPaymentsForAdminRejectsUnknownStatus() {
        try {
            paymentService.getPaymentsForAdmin("BOGUS");
            fail("Expected InvalidPaymentStateException");
        } catch (InvalidPaymentStateException e) {
            assertEquals("Invalid status: BOGUS", e.getMessage());
        }
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(PAYMENT_ID);
        payment.setBookingId(BOOKING_ID);
        payment.setUserId(USER_ID);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setStatus(status);
        payment.setRazorpayOrderId(ORDER_ID);
        return payment;
    }

    private VerifyPaymentRequestDto verifyRequest() {
        VerifyPaymentRequestDto req = new VerifyPaymentRequestDto();
        req.setRazorpayOrderId(ORDER_ID);
        req.setRazorpayPaymentId(RAZORPAY_PAYMENT_ID);
        req.setRazorpaySignature("sig");
        return req;
    }

    private PaymentFailedRequestDto failedRequest(String reason) {
        PaymentFailedRequestDto req = new PaymentFailedRequestDto();
        req.setRazorpayOrderId(ORDER_ID);
        req.setReason(reason);
        return req;
    }

    private String capturedPayload(String orderId, long amountPaise) {
        return "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"id\":\"" + RAZORPAY_PAYMENT_ID + "\","
                + "\"order_id\":\"" + orderId + "\","
                + "\"amount\":" + amountPaise
                + "}}}}";
    }
}
