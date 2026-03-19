package com.coworking.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberSuspensionEvent {
    private Long memberId;
    private boolean suspend;
}
