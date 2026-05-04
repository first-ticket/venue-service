package com.firstticket.venueservice.presentation.dto.request;

import java.util.UUID;

import com.firstticket.venueservice.application.dto.command.UpdateVenueSeatStatusCommand;
import com.firstticket.venueservice.domain.PhysicalStatus;

import jakarta.validation.constraints.NotNull;

/**
 * 좌석 물리 상태 변경 요청 DTO.
 * API 설계 문서: { physicalStatus: "AVAILABLE" | "BROKEN" }
 */
public record UpdateSeatStatusRequest(

    @NotNull(message = "물리 상태는 필수입니다")
    PhysicalStatus physicalStatus

) {
    public UpdateVenueSeatStatusCommand toCommand(UUID seatId) {
        return new UpdateVenueSeatStatusCommand(
            seatId,
            physicalStatus == PhysicalStatus.BROKEN
        );
    }
}
