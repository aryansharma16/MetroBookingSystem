package com.metro.paymentservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.metro.paymentservice.entity.Payment;
import com.metro.paymentservice.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByBookingId(Long bookingId);

    Optional<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    List<Payment> findByStatusAndEventPublishedFalse(PaymentStatus status);

    List<Payment> findAllByOrderByCreatedAtDesc();

    List<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Payment p SET p.razorpayOrderId = :orderId " +
           "WHERE p.id = :id AND p.razorpayOrderId IS NULL")
    int attachOrderId(@Param("id") Long id, @Param("orderId") String orderId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Payment p SET p.status = :newStatus, p.razorpayPaymentId = :paymentId " +
           "WHERE p.id = :id AND p.status = :oldStatus")
    int markStatus(@Param("id") Long id,
                   @Param("paymentId") String paymentId,
                   @Param("oldStatus") PaymentStatus oldStatus,
                   @Param("newStatus") PaymentStatus newStatus);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Payment p SET p.eventPublished = true WHERE p.id = :id")
    int markEventPublished(@Param("id") Long id);

    // status change and failure reason in one compare-and-set, used when the user abandons checkout
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Payment p SET p.status = :newStatus, p.failureReason = :reason " +
           "WHERE p.id = :id AND p.status = :oldStatus")
    int transitionWithFailureReason(@Param("id") Long id,
                                    @Param("reason") String reason,
                                    @Param("oldStatus") PaymentStatus oldStatus,
                                    @Param("newStatus") PaymentStatus newStatus);

    // status-only compare-and-set, used by the refund flow
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Payment p SET p.status = :newStatus WHERE p.id = :id AND p.status = :oldStatus")
    int transition(@Param("id") Long id,
                   @Param("oldStatus") PaymentStatus oldStatus,
                   @Param("newStatus") PaymentStatus newStatus);
}