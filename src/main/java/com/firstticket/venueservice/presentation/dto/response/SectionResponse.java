package com.firstticket.venueservice.presentation.dto.response;

import java.util.UUID;

import com.firstticket.venueservice.application.dto.result.SectionResult;
import com.firstticket.venueservice.domain.SeatType;

/**
 * 구역 조회 응답 DTO.
 */
public record SectionResponse(
    UUID id,
    String name,
    SeatType type,
    Integer rowCount,   // SEATED 전용
    Integer colCount,   // SEATED 전용
    Integer capacity,   // STANDING·FREE 전용
    int seatCount       // SEATED: rowCount × colCount / STANDING·FREE: capacity
) {
    public static SectionResponse from(SectionResult result) {
        return new SectionResponse(
            result.id(),
            result.name(),
            result.type(),
            result.rowCount(),
            result.colCount(),
            result.capacity(),
            result.type() == SeatType.SEATED
                ? result.rowCount() * result.colCount()
                : result.capacity()
        );
    }
}
