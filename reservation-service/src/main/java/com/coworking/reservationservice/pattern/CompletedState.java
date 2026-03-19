package com.coworking.reservationservice.pattern;

import com.coworking.reservationservice.domain.ReservationStatus;

public class CompletedState implements ReservationState {
    @Override
    public void handle(ReservationContext context) {
        context.getReservation().setStatus(ReservationStatus.COMPLETED);
    }

    @Override
    public ReservationStatus getStatus() {
        return ReservationStatus.COMPLETED;
    }
}
