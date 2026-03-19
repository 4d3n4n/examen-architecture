package com.coworking.reservationservice.repository;

import com.coworking.reservationservice.domain.Reservation;
import com.coworking.reservationservice.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByMemberIdAndStatus(Long memberId, ReservationStatus status);
    List<Reservation> findByRoomIdAndStatus(Long roomId, ReservationStatus status);
    List<Reservation> findByMemberId(Long memberId);
    
    // Check for overlapping reservations for a given room
    boolean existsByRoomIdAndStatusAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
            Long roomId, ReservationStatus status, LocalDateTime endDateTime, LocalDateTime startDateTime);

    boolean existsByRoomIdAndStatusAndStartDateTimeLessThanEqualAndEndDateTimeGreaterThan(
            Long roomId, ReservationStatus status, LocalDateTime currentDateTime, LocalDateTime sameCurrentDateTime);

    List<Reservation> findByStatusAndEndDateTimeLessThanEqual(ReservationStatus status, LocalDateTime endDateTime);
}
