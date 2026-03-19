package com.coworking.reservationservice.service;

import com.coworking.reservationservice.client.MemberClient;
import com.coworking.reservationservice.client.RoomClient;
import com.coworking.reservationservice.domain.Reservation;
import com.coworking.reservationservice.domain.ReservationStatus;
import com.coworking.reservationservice.dto.MemberResponse;
import com.coworking.reservationservice.dto.ReservationRequest;
import com.coworking.reservationservice.dto.ReservationResponse;
import com.coworking.reservationservice.dto.RoomResponse;
import com.coworking.reservationservice.event.MemberDeletedEvent;
import com.coworking.reservationservice.event.MemberSuspensionEvent;
import com.coworking.reservationservice.event.RoomDeletedEvent;
import com.coworking.reservationservice.pattern.CancelledState;
import com.coworking.reservationservice.pattern.CompletedState;
import com.coworking.reservationservice.pattern.ReservationContext;
import com.coworking.reservationservice.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomClient roomClient;
    private final MemberClient memberClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String MEMBER_SUSPENSION_TOPIC = "member-suspension-topic";

    @Transactional
    public ReservationResponse createReservation(ReservationRequest request) {
        // 1. Check member status and limits
        MemberResponse member = memberClient.getMemberById(request.getMemberId());
        if (member.isSuspended()) {
            throw new RuntimeException("Member is suspended and cannot make reservations.");
        }

        // 2. Check room availability
        RoomResponse room = roomClient.getRoomById(request.getRoomId());
        
        // Ensure no overlapping reservations for the room in CONFIRMED state
        boolean hasOverlap = reservationRepository.existsByRoomIdAndStatusAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                request.getRoomId(), ReservationStatus.CONFIRMED, request.getEndDateTime(), request.getStartDateTime());
        
        if (hasOverlap || !room.isAvailable()) {
            throw new RuntimeException("Room is not available for the requested time slot.");
        }

        // Lock room availability directly
        boolean locked = roomClient.lockAvailability(request.getRoomId());
        if (!locked) {
             throw new RuntimeException("Failed to lock room availability.");
        }

        // 3. Create reservation using the Context/State pattern
        Reservation reservation = Reservation.builder()
                .roomId(request.getRoomId())
                .memberId(request.getMemberId())
                .startDateTime(request.getStartDateTime())
                .endDateTime(request.getEndDateTime())
                .build();
        
        ReservationContext context = new ReservationContext(reservation);
        // Default state is ConfirmedState, sets status to CONFIRMED
        
        reservation = reservationRepository.save(context.getReservation());

        // 4. Check if member quota is reached
        checkAndPublishQuota(request.getMemberId(), member.getMaxConcurrentBookings());

        return mapToResponse(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + id));
        return mapToResponse(reservation);
    }

    @Transactional
    public ReservationResponse cancelReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + id));
        
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return mapToResponse(reservation); // Already cancelled
        }

        ReservationContext context = new ReservationContext(reservation);
        context.setState(new CancelledState());
        reservation = reservationRepository.save(context.getReservation());

        roomClient.releaseAvailability(reservation.getRoomId());

        // Check if member quota drops below limit
        MemberResponse member = memberClient.getMemberById(reservation.getMemberId());
        checkAndPublishQuota(reservation.getMemberId(), member.getMaxConcurrentBookings());

        return mapToResponse(reservation);
    }

    @Transactional
    public ReservationResponse completeReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + id));
        
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new RuntimeException("Only CONFIRMED reservations can be COMPLETED.");
        }

        ReservationContext context = new ReservationContext(reservation);
        context.setState(new CompletedState());
        reservation = reservationRepository.save(context.getReservation());

        roomClient.releaseAvailability(reservation.getRoomId());

        // Check if member quota drops below limit
        MemberResponse member = memberClient.getMemberById(reservation.getMemberId());
        checkAndPublishQuota(reservation.getMemberId(), member.getMaxConcurrentBookings());

        return mapToResponse(reservation);
    }

    // Kafka Listeners for async events

    @KafkaListener(topics = "room-deleted-topic", groupId = "reservation-group")
    @Transactional
    public void handleRoomDeleted(RoomDeletedEvent event) {
        log.info("Received RoomDeletedEvent for roomId: {}", event.getRoomId());
        List<Reservation> activeReservations = reservationRepository.findByRoomIdAndStatus(event.getRoomId(), ReservationStatus.CONFIRMED);
        
        for (Reservation reservation : activeReservations) {
            ReservationContext context = new ReservationContext(reservation);
            context.setState(new CancelledState());
            reservationRepository.save(context.getReservation());
            
            // Re-evaluate quota for the affected member
            try {
                MemberResponse member = memberClient.getMemberById(reservation.getMemberId());
                checkAndPublishQuota(reservation.getMemberId(), member.getMaxConcurrentBookings());
            } catch (Exception e) {
                log.error("Failed to re-evaluate quota for member {} after room deletion", reservation.getMemberId(), e);
            }
        }
    }

    @KafkaListener(topics = "member-deleted-topic", groupId = "reservation-group")
    @Transactional
    public void handleMemberDeleted(MemberDeletedEvent event) {
        log.info("Received MemberDeletedEvent for memberId: {}", event.getMemberId());
        List<Reservation> memberReservations = reservationRepository.findByMemberId(event.getMemberId());
        
        for(Reservation reservation : memberReservations) {
             if(reservation.getStatus() == ReservationStatus.CONFIRMED) {
                 roomClient.releaseAvailability(reservation.getRoomId());
             }
        }
        
        reservationRepository.deleteAll(memberReservations);
    }

    private void checkAndPublishQuota(Long memberId, int maxBookings) {
        long activeCount = reservationRepository.findByMemberIdAndStatus(memberId, ReservationStatus.CONFIRMED).size();
        boolean shouldSuspend = activeCount >= maxBookings;
        
        log.info("Member {} has {} active bookings (max: {}). Should suspend? {}", memberId, activeCount, maxBookings, shouldSuspend);
        
        kafkaTemplate.send(MEMBER_SUSPENSION_TOPIC, new MemberSuspensionEvent(memberId, shouldSuspend));
    }

    private ReservationResponse mapToResponse(Reservation reservation) {
        return ReservationResponse.builder()
                .id(reservation.getId())
                .roomId(reservation.getRoomId())
                .memberId(reservation.getMemberId())
                .startDateTime(reservation.getStartDateTime())
                .endDateTime(reservation.getEndDateTime())
                .status(reservation.getStatus())
                .build();
    }
}