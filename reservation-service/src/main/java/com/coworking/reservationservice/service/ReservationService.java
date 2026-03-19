package com.coworking.reservationservice.service;

import com.coworking.reservationservice.client.MemberClient;
import com.coworking.reservationservice.client.RoomClient;
import com.coworking.reservationservice.domain.Reservation;
import com.coworking.reservationservice.domain.ReservationStatus;
import com.coworking.reservationservice.dto.MemberResponse;
import com.coworking.reservationservice.dto.ReservationRequest;
import com.coworking.reservationservice.dto.ReservationResponse;
import com.coworking.events.MemberDeletedEvent;
import com.coworking.events.MemberSuspensionEvent;
import com.coworking.events.RoomDeletedEvent;
import com.coworking.reservationservice.pattern.ConfirmedState;
import com.coworking.reservationservice.pattern.CancelledState;
import com.coworking.reservationservice.pattern.CompletedState;
import com.coworking.reservationservice.pattern.ReservationContext;
import com.coworking.reservationservice.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        processExpiredReservations();

        if (request.getRoomId() == null || request.getMemberId() == null
                || request.getStartDateTime() == null || request.getEndDateTime() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "roomId, memberId, startDateTime and endDateTime are required.");
        }

        if (!request.getEndDateTime().isAfter(request.getStartDateTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDateTime must be after startDateTime.");
        }

        MemberResponse member = memberClient.getMemberById(request.getMemberId());
        if (member.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Member is suspended and cannot make reservations.");
        }

        long activeBookings = reservationRepository.findByMemberIdAndStatus(
                request.getMemberId(), ReservationStatus.CONFIRMED).size();
        if (activeBookings >= member.getMaxConcurrentBookings()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Member has reached the maximum number of active reservations.");
        }

        roomClient.getRoomById(request.getRoomId());

        boolean hasOverlap = reservationRepository.existsByRoomIdAndStatusAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                request.getRoomId(), ReservationStatus.CONFIRMED, request.getEndDateTime(), request.getStartDateTime());

        if (hasOverlap) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is not available for the requested time slot.");
        }

        Reservation reservation = Reservation.builder()
                .roomId(request.getRoomId())
                .memberId(request.getMemberId())
                .startDateTime(request.getStartDateTime())
                .endDateTime(request.getEndDateTime())
                .build();

        ReservationContext context = new ReservationContext(reservation);
        context.setState(new ConfirmedState());
        reservation = reservationRepository.save(context.getReservation());

        checkAndPublishQuota(request.getMemberId(), member.getMaxConcurrentBookings());
        syncRoomAvailability(request.getRoomId());

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

        syncRoomAvailability(reservation.getRoomId());

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

        syncRoomAvailability(reservation.getRoomId());

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

        for (Reservation reservation : memberReservations) {
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                reservation.setStatus(ReservationStatus.CANCELLED);
                reservationRepository.save(reservation);
                syncRoomAvailability(reservation.getRoomId());
            }
        }

        reservationRepository.deleteAll(memberReservations);
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void completeExpiredReservations() {
        processExpiredReservations();
    }

    private void checkAndPublishQuota(Long memberId, int maxBookings) {
        long activeCount = reservationRepository.findByMemberIdAndStatus(memberId, ReservationStatus.CONFIRMED).size();
        boolean shouldSuspend = activeCount >= maxBookings;
        
        log.info("Member {} has {} active bookings (max: {}). Should suspend? {}", memberId, activeCount, maxBookings, shouldSuspend);
        
        kafkaTemplate.send(MEMBER_SUSPENSION_TOPIC, new MemberSuspensionEvent(memberId, shouldSuspend));
    }

    private void syncRoomAvailability(Long roomId) {
        LocalDateTime now = LocalDateTime.now();
        boolean hasOngoingReservation = reservationRepository
                .existsByRoomIdAndStatusAndStartDateTimeLessThanEqualAndEndDateTimeGreaterThan(
                        roomId, ReservationStatus.CONFIRMED, now, now);

        if (hasOngoingReservation) {
            roomClient.lockAvailability(roomId);
            return;
        }

        roomClient.releaseAvailability(roomId);
    }

    private void processExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> expiredReservations = reservationRepository
                .findByStatusAndEndDateTimeLessThanEqual(ReservationStatus.CONFIRMED, now);

        if (expiredReservations.isEmpty()) {
            return;
        }

        Set<Long> impactedRoomIds = new LinkedHashSet<>();
        Set<Long> impactedMemberIds = new LinkedHashSet<>();

        for (Reservation reservation : expiredReservations) {
            ReservationContext context = new ReservationContext(reservation);
            context.setState(new CompletedState());
            reservationRepository.save(context.getReservation());
            impactedRoomIds.add(reservation.getRoomId());
            impactedMemberIds.add(reservation.getMemberId());
        }

        for (Long roomId : impactedRoomIds) {
            syncRoomAvailability(roomId);
        }

        for (Long memberId : impactedMemberIds) {
            MemberResponse member = memberClient.getMemberById(memberId);
            checkAndPublishQuota(memberId, member.getMaxConcurrentBookings());
        }
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
