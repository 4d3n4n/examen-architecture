package com.coworking.memberservice.dto;

import com.coworking.memberservice.domain.SubscriptionType;
import lombok.Data;

@Data
public class MemberRequest {
    private String fullName;
    private String email;
    private SubscriptionType subscriptionType;
}
