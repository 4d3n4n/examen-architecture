package com.coworking.reservationservice.dto;

import lombok.Data;

@Data
public class MemberResponse {
    private Long id;
    private boolean suspended;
    private Integer maxConcurrentBookings;
}
