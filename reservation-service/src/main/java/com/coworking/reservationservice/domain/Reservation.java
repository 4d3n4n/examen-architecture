package com.coworking.reservationservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long roomId;
    private Long memberId;
    
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
}
