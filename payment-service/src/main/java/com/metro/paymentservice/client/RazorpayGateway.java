package com.metro.paymentservice.client;

import java.math.BigDecimal;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.metro.paymentservice.entity.Payment;
import com.metro.paymentservice.exception.PaymentGatewayException;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

@Component
public class RazorpayGateway {

    private final RazorpayClient client;
    private final String keySecret;
    private final String webhookSecret;

    public RazorpayGateway(@Value("${razorpay.key-id}") String keyId,
                           @Value("${razorpay.key-secret}") String keySecret,
                           @Value("${razorpay.webhook-secret}") String webhookSecret) throws RazorpayException {
        this.client = new RazorpayClient(keyId, keySecret);
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
    }

    public String createOrder(Payment payment) {
        try {
            long paise = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();

            JSONObject options = new JSONObject();
            options.put("amount", paise);
            options.put("currency", "INR");
            options.put("receipt", "booking_" + payment.getBookingId());

            Order order = client.orders.create(options);
            return order.get("id");
        } catch (RazorpayException | ArithmeticException e) {
            throw new PaymentGatewayException("Could not create Razorpay order", e);
        }
    }

    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            JSONObject attrs = new JSONObject();
            attrs.put("razorpay_order_id", orderId);
            attrs.put("razorpay_payment_id", paymentId);
            attrs.put("razorpay_signature", signature);
            return com.razorpay.Utils.verifyPaymentSignature(attrs, keySecret);
        } catch (RazorpayException e) {
            return false;
        }
    }

    // Checks the signature over the raw webhook body. Must be called before the body is parsed.
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            return com.razorpay.Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException e) {
            return false;
        }
    }

    // Refunds the full amount of a captured payment.
    public void refund(Payment payment, String reason) {
        try {
            long paise = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();

            JSONObject notes = new JSONObject();
            notes.put("reason", reason == null ? "BOOKING_EXPIRED_AFTER_PAYMENT" : reason);
            notes.put("bookingId", String.valueOf(payment.getBookingId()));

            JSONObject options = new JSONObject();
            options.put("amount", paise);
            options.put("notes", notes);

            client.payments.refund(payment.getRazorpayPaymentId(), options);
        } catch (RazorpayException | ArithmeticException e) {
            throw new PaymentGatewayException("Could not refund payment", e);
        }
    }
}