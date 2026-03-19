package com.coworking.reservationservice.pattern;

import com.coworking.reservationservice.domain.Reservation;
import com.coworking.reservationservice.domain.ReservationStatus;

public interface ReservationState {
    void handle(ReservationContext context);
    ReservationStatus getStatus();
}
