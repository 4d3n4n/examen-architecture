package com.coworking.roomservice.controller;

import com.coworking.roomservice.dto.RoomRequest;
import com.coworking.roomservice.dto.RoomResponse;
import com.coworking.roomservice.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@RequestBody RoomRequest request) {
        return new ResponseEntity<>(roomService.createRoom(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoomById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomResponse> updateRoom(@PathVariable Long id, @RequestBody RoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }

    // Inter-service endpoints
    @PostMapping("/{id}/lock-availability")
    public ResponseEntity<Boolean> lockAvailability(@PathVariable Long id) {
        boolean success = roomService.checkAndLockAvailability(id);
        return ResponseEntity.ok(success);
    }

    @PostMapping("/{id}/release-availability")
    public ResponseEntity<Void> releaseAvailability(@PathVariable Long id) {
        roomService.releaseAvailability(id);
        return ResponseEntity.ok().build();
    }
}
