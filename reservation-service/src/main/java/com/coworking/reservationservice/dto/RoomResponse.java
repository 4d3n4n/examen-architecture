package com.coworking.reservationservice.dto;

import lombok.Data;

@Data
public class RoomResponse {
    private Long id;
    private boolean available;
}
