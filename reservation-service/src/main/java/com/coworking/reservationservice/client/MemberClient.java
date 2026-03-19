package com.coworking.reservationservice.client;

import com.coworking.reservationservice.dto.MemberResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "member-service")
public interface MemberClient {

    @GetMapping("/api/members/{id}")
    MemberResponse getMemberById(@PathVariable("id") Long id);
}
