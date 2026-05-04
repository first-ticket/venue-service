package com.firstticket.venueservice.presentation.dto.request;

import java.util.UUID;

import com.firstticket.venueservice.application.dto.command.CreateSectionCommand;
import com.firstticket.venueservice.domain.SeatType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 구역 등록 요청 DTO.
 * 타입별 필수 파라미터:
 * - SEATED   : rowCount, colCount 필수
 * - STANDING : capacity 필수
 * - FREE     : capacity 필수
 * 타입별 파라미터 유효성은 도메인에서 검증한다.
 */
public record CreateSectionRequest(

    @NotBlank(message = "구역명은 필수입니다")
    String name,

    @NotNull(message = "구역 타입은 필수입니다")
    SeatType type,

    Integer rowCount,   // SEATED 전용
    Integer colCount,   // SEATED 전용
    Integer capacity    // STANDING·FREE 전용

) {
    public CreateSectionCommand toCommand(UUID venueId) {
        return new CreateSectionCommand(
            venueId, name, type, rowCount, colCount, capacity
        );
    }
}
