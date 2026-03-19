package com.coworking.roomservice.dto;

import com.coworking.roomservice.domain.RoomType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RoomRequest {
    private String name;
    private String city;
    private Integer capacity;
    private RoomType type;
    private BigDecimal hourlyRate;
}
