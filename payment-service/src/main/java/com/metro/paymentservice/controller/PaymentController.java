package com.metro.paymentservice.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.metro.paymentservice.dto.request.PaymentFailedRequestDto;
import com.metro.paymentservice.dto.request.VerifyPaymentRequestDto;
import com.metro.paymentservice.dto.response.PaymentDetailsResponseDto;
import com.metro.paymentservice.dto.response.PaymentOrderResponseDto;
import com.metro.paymentservice.service.PaymentService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<PaymentOrderResponseDto> getPaymentForBooking(
            @PathVariable Long bookingId, HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok(paymentService.getPaymentForBooking(bookingId, userId));
    }
    
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(
            @Valid @RequestBody VerifyPaymentRequestDto request, HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        paymentService.verifyPayment(request, userId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    // Public: Razorpay calls this without a JWT. The service checks the signature first.
    // Raw bytes, so the body is exactly what Razorpay signed (no charset guessing).
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> webhook(
            @RequestBody byte[] body,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        String payload = new String(body, StandardCharsets.UTF_8);
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    @PostMapping("/failed")
    public ResponseEntity<Map<String, String>> markFailed(
            @Valid @RequestBody PaymentFailedRequestDto request, HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        paymentService.markPaymentFailed(request, userId);
        return ResponseEntity.ok(Map.of("status", "FAILED"));
    }

    @GetMapping("/admin")
    public ResponseEntity<List<PaymentDetailsResponseDto>> getAllPayments(
            @RequestParam(required = false) String status, HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        String role = (String) httpRequest.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(paymentService.getPaymentsForAdmin(status));
    }

    @PostMapping("/admin/booking/{bookingId}/refund")
    public ResponseEntity<Map<String, String>> adminRefund(
            @PathVariable Long bookingId, HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        String role = (String) httpRequest.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        paymentService.adminRefund(bookingId);
        return ResponseEntity.ok(Map.of("status", "REFUND_REQUESTED"));
    }
}