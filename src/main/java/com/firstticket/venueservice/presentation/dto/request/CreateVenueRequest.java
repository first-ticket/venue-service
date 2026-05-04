package com.firstticket.venueservice.presentation.dto.request;

import java.util.List;

import com.firstticket.venueservice.application.dto.command.CreateSectionCommand;
import com.firstticket.venueservice.application.dto.command.CreateVenueCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 공연장 등록 요청 DTO.
 * sections 배열을 함께 전달하면 구역과 VenueSeat이 자동 생성된다 (V-01, V-02).
 */
public record CreateVenueRequest(

    @NotBlank(message = "공연장 이름은 필수입니다")
    String name,

    @NotBlank(message = "공연장 주소는 필수입니다")
    String address,

    @Valid
    List<SectionRequest> sections  // null 허용 — 구역 없이 공연장만 생성 가능

) {
    public CreateVenueCommand toCommand() {
        return new CreateVenueCommand(name, address);
    }

    /**
     * 구역 등록 요청 내부 DTO.
     * 타입별 필수 파라미터:
     * - SEATED   : rowCount, colCount 필수 / capacity null
     * - STANDING : capacity 필수 / rowCount, colCount null
     * - FREE     : capacity 필수 / rowCount, colCount null
     */
    public record SectionRequest(

        @NotBlank(message = "구역명은 필수입니다")
        String name,

        com.firstticket.venueservice.domain.SeatType type,

        Integer rowCount,   // SEATED 전용
        Integer colCount,   // SEATED 전용
        Integer capacity    // STANDING·FREE 전용

    ) {
        public CreateSectionCommand toCommand(java.util.UUID venueId) {
            return new CreateSectionCommand(
                venueId, name, type, rowCount, colCount, capacity
            );
        }
    }
}
