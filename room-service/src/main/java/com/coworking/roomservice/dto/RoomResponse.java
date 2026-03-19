package com.coworking.roomservice.dto;

import com.coworking.roomservice.domain.RoomType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RoomResponse {
    private Long id;
    private String name;
    private String city;
    private Integer capacity;
    private RoomType type;
    private BigDecimal hourlyRate;
    private boolean available;
}
