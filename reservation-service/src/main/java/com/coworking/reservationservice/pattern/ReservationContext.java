package com.coworking.reservationservice.pattern;

import com.coworking.reservationservice.domain.Reservation;

public class ReservationContext {
    private ReservationState state;
    private final Reservation reservation;

    public ReservationContext(Reservation reservation) {
        this.reservation = reservation;
        // Default state is confirmed on creation
        this.setState(new ConfirmedState());
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
