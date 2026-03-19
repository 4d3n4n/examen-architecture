package com.coworking.memberservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String fullName;
    private String email;
    
    @Enumerated(EnumType.STRING)
    private SubscriptionType subscriptionType;
    
    @Builder.Default
    private boolean suspended = false;
    
    private Integer maxConcurrentBookings;
    
    @PrePersist
    @PreUpdate
    private void preSave() {
        if (subscriptionType != null) {
            this.maxConcurrentBookings = subscriptionType.getMaxConcurrentBookings();
        }
    }
}
