package com.coworking.reservationservice.event;

import lombok.Data;

@Data
public class MemberDeletedEvent {
    private Long memberId;
}
