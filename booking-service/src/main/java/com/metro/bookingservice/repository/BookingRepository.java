package com.metro.bookingservice.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.metro.bookingservice.entity.Booking;
import com.metro.bookingservice.entity.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Booking> findByTicketCode(String ticketCode);

    List<Booking> findByStatus(BookingStatus status);

    List<Booking> findAllByOrderByCreatedAtDesc();

    List<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status);

    @Modifying
    @Query("UPDATE Booking b SET b.status = :newStatus " +
           "WHERE b.status = :oldStatus AND b.createdAt < :cutoff")
    int expireOldBookings(
            @Param("oldStatus") BookingStatus oldStatus,
            @Param("newStatus") BookingStatus newStatus,
            @Param("cutoff") LocalDateTime cutoff);

    // compare-and-set: changes the row only if it is still in oldStatus
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Booking b SET b.status = :newStatus WHERE b.id = :id AND b.status = :oldStatus")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("oldStatus") BookingStatus oldStatus,
                              @Param("newStatus") BookingStatus newStatus);

    // gate scan: only a CONFIRMED ticket can be used, and only once
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Booking b SET b.status = :newStatus, b.usedAt = :usedAt " +
           "WHERE b.id = :id AND b.status = :oldStatus")
    int markTicketUsed(@Param("id") Long id,
                       @Param("oldStatus") BookingStatus oldStatus,
                       @Param("newStatus") BookingStatus newStatus,
                       @Param("usedAt") LocalDateTime usedAt);
}