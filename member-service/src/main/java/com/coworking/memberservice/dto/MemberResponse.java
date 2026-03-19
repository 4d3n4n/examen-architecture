package com.coworking.memberservice.dto;

import com.coworking.memberservice.domain.SubscriptionType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberResponse {
    private Long id;
    private String fullName;
    private String email;
    private SubscriptionType subscriptionType;
    private boolean suspended;
    private Integer maxConcurrentBookings;
}
