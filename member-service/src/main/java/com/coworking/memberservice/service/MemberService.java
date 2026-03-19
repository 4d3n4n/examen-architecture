package com.coworking.memberservice.service;

import com.coworking.memberservice.domain.Member;
import com.coworking.memberservice.dto.MemberRequest;
import com.coworking.memberservice.dto.MemberResponse;
import com.coworking.memberservice.event.MemberDeletedEvent;
import com.coworking.memberservice.event.MemberSuspensionEvent;
import com.coworking.memberservice.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String MEMBER_DELETED_TOPIC = "member-deleted-topic";

    @Transactional
    public MemberResponse createMember(MemberRequest request) {
        Member member = Member.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .subscriptionType(request.getSubscriptionType())
                .suspended(false)
                .build();
        member = memberRepository.save(member);
        return mapToResponse(member);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getAllMembers() {
        return memberRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MemberResponse getMemberById(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member not found: " + id));
        return mapToResponse(member);
    }

    @Transactional
    public MemberResponse updateMember(Long id, MemberRequest request) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member not found: " + id));
        
        member.setFullName(request.getFullName());
        member.setEmail(request.getEmail());
        member.setSubscriptionType(request.getSubscriptionType());
        
        member = memberRepository.save(member);
        return mapToResponse(member);
    }

    @Transactional
    public void deleteMember(Long id) {
        if (!memberRepository.existsById(id)) {
            throw new RuntimeException("Member not found: " + id);
        }
        memberRepository.deleteById(id);
        
        // Publish Kafka event
        kafkaTemplate.send(MEMBER_DELETED_TOPIC, new MemberDeletedEvent(id));
    }

    @KafkaListener(topics = "member-suspension-topic", groupId = "member-group")
    @Transactional
    public void handleMemberSuspension(MemberSuspensionEvent event) {
        memberRepository.findById(event.getMemberId()).ifPresent(member -> {
            member.setSuspended(event.isSuspend());
            memberRepository.save(member);
        });
    }

    private MemberResponse mapToResponse(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .fullName(member.getFullName())
                .email(member.getEmail())
                .subscriptionType(member.getSubscriptionType())
                .suspended(member.isSuspended())
                .maxConcurrentBookings(member.getMaxConcurrentBookings())
                .build();
    }
}
