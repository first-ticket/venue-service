package com.firstticket.venueservice.application.dto.result;

import com.firstticket.venueservice.domain.Section;

/**
 * Section 수용 인원 상한 조회 결과 DTO.
 * Program Service의 VenueClient가 수신하는 응답 구조.
 */
public record SectionCapacityResult(
    int capacity
) {
    public static SectionCapacityResult from(Section section) {
        return new SectionCapacityResult(section.getSeatCount());
    }
}
