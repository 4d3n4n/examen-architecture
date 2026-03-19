package com.coworking.roomservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String city;
    private Integer capacity;
    
    @Enumerated(EnumType.STRING)
    private RoomType type;
    
    private BigDecimal hourlyRate;
    
    @Builder.Default
    private boolean available = true;
}
