package com.coworking.reservationservice.client;

import com.coworking.reservationservice.dto.RoomResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "room-service")
public interface RoomClient {

    @GetMapping("/api/rooms/{id}")
    RoomResponse getRoomById(@PathVariable("id") Long id);

    @PostMapping("/api/rooms/{id}/lock-availability")
    boolean lockAvailability(@PathVariable("id") Long id);

    @PostMapping("/api/rooms/{id}/release-availability")
    void releaseAvailability(@PathVariable("id") Long id);
}
