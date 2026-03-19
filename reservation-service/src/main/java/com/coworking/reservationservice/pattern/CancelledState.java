package com.coworking.reservationservice.pattern;

import com.coworking.reservationservice.domain.ReservationStatus;

public class CancelledState implements ReservationState {
    @Override
    public void handle(ReservationContext context) {
        context.getReservation().setStatus(ReservationStatus.CANCELLED);
    }

    @Override
    public ReservationStatus getStatus() {
        return ReservationStatus.CANCELLED;
    }
}
