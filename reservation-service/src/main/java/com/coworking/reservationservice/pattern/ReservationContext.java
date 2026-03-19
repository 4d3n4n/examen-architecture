package com.coworking.reservationservice.pattern;

import com.coworking.reservationservice.domain.Reservation;

public class ReservationContext {
    private ReservationState state;
    private final Reservation reservation;

    public ReservationContext(Reservation reservation) {
        this.reservation = reservation;
    }

    public void setState(ReservationState state) {
        this.state = state;
        this.state.handle(this);
    }

    public ReservationState getState() {
        return state;
    }

    public Reservation getReservation() {
        return reservation;
    }
}
