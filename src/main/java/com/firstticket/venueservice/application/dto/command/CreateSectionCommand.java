package com.firstticket.venueservice.application.dto.command;

import java.util.UUID;

import com.firstticket.venueservice.domain.SeatType;

/**
 * 구역 추가 커맨드.
 * 타입별 필수 파라미터:
 * - SEATED   : rowCount, colCount 필수 / capacity null
 * - STANDING : capacity 필수 / rowCount, colCount null
 * - FREE     : capacity 필수 / rowCount, colCount null
 */
public record CreateSectionCommand(
    UUID venueId,
    String name,
    SeatType type,
    Integer rowCount,
    Integer colCount,
    Integer capacity
) {
}
