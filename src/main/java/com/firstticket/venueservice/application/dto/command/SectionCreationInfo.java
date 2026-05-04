package com.firstticket.venueservice.application.dto.command;

import com.firstticket.venueservice.domain.SeatType;

/**
 * 공연장 일괄 생성 시 구역 정보를 담는 내부 DTO.
 * CreateSectionCommand와 달리 venueId를 포함하지 않는다.
 * venueId는 createVenueWithSections() 내부에서 venue 저장 후 자동으로 연결된다.
 */
public record SectionCreationInfo(
    String name,
    SeatType type,
    Integer rowCount,
    Integer colCount,
    Integer capacity
) {
}
