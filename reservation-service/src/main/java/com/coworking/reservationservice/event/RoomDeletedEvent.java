package com.coworking.reservationservice.event;

import lombok.Data;

@Data
public class RoomDeletedEvent {
    private Long roomId;
}
