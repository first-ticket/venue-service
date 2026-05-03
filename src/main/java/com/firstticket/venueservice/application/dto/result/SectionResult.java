package com.firstticket.venueservice.application.dto.result;

import java.util.UUID;

import com.firstticket.venueservice.domain.SeatType;
import com.firstticket.venueservice.domain.Section;

/**
 * 구역 조회 결과 DTO.
 */
public record SectionResult(
    UUID id,
    String name,
    SeatType type,
    Integer rowCount,   // SEATED 전용
    Integer colCount,   // SEATED 전용
    Integer capacity    // STANDING·FREE 전용
) {
    public static SectionResult from(Section section) {
        return new SectionResult(
            section.getId(),
            section.getName(),
            section.getType(),
            section.getRowCount(),
            section.getColCount(),
            section.getCapacity()
        );
    }
}
