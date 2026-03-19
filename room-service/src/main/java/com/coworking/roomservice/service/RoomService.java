package com.coworking.roomservice.service;

import com.coworking.roomservice.domain.Room;
import com.coworking.roomservice.dto.RoomRequest;
import com.coworking.roomservice.dto.RoomResponse;
import com.coworking.events.RoomDeletedEvent;
import com.coworking.roomservice.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String ROOM_DELETED_TOPIC = "room-deleted-topic";

    @Transactional
    public RoomResponse createRoom(RoomRequest request) {
        Room room = Room.builder()
                .name(request.getName())
                .city(request.getCity())
                .capacity(request.getCapacity())
                .type(request.getType())
                .hourlyRate(request.getHourlyRate())
                .available(true)
                .build();
        room = roomRepository.save(room);
        return mapToResponse(room);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getAllRooms() {
        return roomRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoomById(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room not found: " + id));
        return mapToResponse(room);
    }

    @Transactional
    public RoomResponse updateRoom(Long id, RoomRequest request) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room not found: " + id));
        
        room.setName(request.getName());
        room.setCity(request.getCity());
        room.setCapacity(request.getCapacity());
        room.setType(request.getType());
        room.setHourlyRate(request.getHourlyRate());
        
        room = roomRepository.save(room);
        return mapToResponse(room);
    }

    @Transactional
    public void deleteRoom(Long id) {
        if (!roomRepository.existsById(id)) {
            throw new RuntimeException("Room not found: " + id);
        }
        roomRepository.deleteById(id);
        
        // Publish Kafka event
        kafkaTemplate.send(ROOM_DELETED_TOPIC, new RoomDeletedEvent(id));
    }

    // Endpoints called by ReservationService to check and update availability
    @Transactional
    public boolean checkAndLockAvailability(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room not found: " + id));
        room.setAvailable(false);
        roomRepository.save(room);
        return true;
    }

    @Transactional
    public void releaseAvailability(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room not found: " + id));
        room.setAvailable(true);
        roomRepository.save(room);
    }

    private RoomResponse mapToResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .city(room.getCity())
                .capacity(room.getCapacity())
                .type(room.getType())
                .hourlyRate(room.getHourlyRate())
                .available(room.isAvailable())
                .build();
    }
}
