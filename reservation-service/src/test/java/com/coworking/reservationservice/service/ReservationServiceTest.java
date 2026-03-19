package com.coworking.reservationservice.service;

import com.coworking.events.MemberSuspensionEvent;
import com.coworking.reservationservice.client.MemberClient;
import com.coworking.reservationservice.client.RoomClient;
import com.coworking.reservationservice.domain.Reservation;
import com.coworking.reservationservice.domain.ReservationStatus;
import com.coworking.reservationservice.dto.MemberResponse;
import com.coworking.reservationservice.dto.ReservationRequest;
import com.coworking.reservationservice.dto.ReservationResponse;
import com.coworking.reservationservice.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RoomClient roomClient;

    @Mock
    private MemberClient memberClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void createReservationRejectsOverlappingSlot() {
        ReservationRequest request = buildRequest(
                1L,
                2L,
                LocalDateTime.now().plusMinutes(5),
                LocalDateTime.now().plusMinutes(65));

        when(reservationRepository.findByStatusAndEndDateTimeLessThanEqual(
                eq(ReservationStatus.CONFIRMED), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(memberClient.getMemberById(2L)).thenReturn(member(false, 5));
        when(reservationRepository.existsByRoomIdAndStatusAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                eq(1L),
                eq(ReservationStatus.CONFIRMED),
                eq(request.getEndDateTime()),
                eq(request.getStartDateTime())))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.createReservation(request));

        assertEquals("Room is not available for the requested time slot.", exception.getReason());
        verify(roomClient).getRoomById(1L);
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void createFutureReservationKeepsRoomCurrentlyAvailable() {
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
        ReservationRequest request = buildRequest(3L, 4L, tomorrow, tomorrow.plusHours(2));

        when(reservationRepository.findByStatusAndEndDateTimeLessThanEqual(
                eq(ReservationStatus.CONFIRMED), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            if (reservation.getId() == null) {
                reservation.setId(99L);
            }
            return reservation;
        });
        when(memberClient.getMemberById(4L)).thenReturn(member(false, 5));
        when(reservationRepository.existsByRoomIdAndStatusAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                eq(3L),
                eq(ReservationStatus.CONFIRMED),
                eq(request.getEndDateTime()),
                eq(request.getStartDateTime())))
                .thenReturn(false);
        when(reservationRepository.findByMemberIdAndStatus(4L, ReservationStatus.CONFIRMED))
                .thenReturn(List.of(Reservation.builder().id(99L).status(ReservationStatus.CONFIRMED).build()));
        when(reservationRepository.existsByRoomIdAndStatusAndStartDateTimeLessThanEqualAndEndDateTimeGreaterThan(
                eq(3L),
                eq(ReservationStatus.CONFIRMED),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(false);

        ReservationResponse response = reservationService.createReservation(request);

        assertEquals(ReservationStatus.CONFIRMED, response.getStatus());
        verify(roomClient).releaseAvailability(3L);
        verify(roomClient, never()).lockAvailability(3L);
    }

    @Test
    void completeExpiredReservationsMarksReservationCompletedAndReleasesRoom() {
        LocalDateTime pastStart = LocalDateTime.now().minusHours(2);
        LocalDateTime pastEnd = LocalDateTime.now().minusMinutes(1);
        Reservation expiredReservation = Reservation.builder()
                .id(7L)
                .roomId(8L)
                .memberId(9L)
                .startDateTime(pastStart)
                .endDateTime(pastEnd)
                .status(ReservationStatus.CONFIRMED)
                .build();

        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.findByStatusAndEndDateTimeLessThanEqual(
                eq(ReservationStatus.CONFIRMED), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredReservation));
        when(reservationRepository.existsByRoomIdAndStatusAndStartDateTimeLessThanEqualAndEndDateTimeGreaterThan(
                eq(8L),
                eq(ReservationStatus.CONFIRMED),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(false);
        when(memberClient.getMemberById(9L)).thenReturn(member(false, 2));
        when(reservationRepository.findByMemberIdAndStatus(9L, ReservationStatus.CONFIRMED))
                .thenReturn(List.of());

        reservationService.completeExpiredReservations();

        assertEquals(ReservationStatus.COMPLETED, expiredReservation.getStatus());
        verify(roomClient).releaseAvailability(8L);

        ArgumentCaptor<MemberSuspensionEvent> captor = ArgumentCaptor.forClass(MemberSuspensionEvent.class);
        verify(kafkaTemplate).send(eq("member-suspension-topic"), captor.capture());
        assertEquals(9L, captor.getValue().getMemberId());
        assertEquals(false, captor.getValue().isSuspend());
    }

    private ReservationRequest buildRequest(Long roomId, Long memberId, LocalDateTime start, LocalDateTime end) {
        ReservationRequest request = new ReservationRequest();
        request.setRoomId(roomId);
        request.setMemberId(memberId);
        request.setStartDateTime(start);
        request.setEndDateTime(end);
        return request;
    }

    private MemberResponse member(boolean suspended, int maxConcurrentBookings) {
        MemberResponse response = new MemberResponse();
        response.setId(1L);
        response.setSuspended(suspended);
        response.setMaxConcurrentBookings(maxConcurrentBookings);
        return response;
    }
}
