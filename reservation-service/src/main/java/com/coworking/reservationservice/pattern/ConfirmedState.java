package com.coworking.reservationservice.pattern;

import com.coworking.reservationservice.domain.ReservationStatus;

public class ConfirmedState implements ReservationState {
    @Override
    public void handle(ReservationContext context) {
        context.getReservation().setStatus(ReservationStatus.CONFIRMED);
    }

    @Override
    public ReservationStatus getStatus() {
        return ReservationStatus.CONFIRMED;
    }
}
