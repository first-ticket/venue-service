package com.firstticket.venueservice.presentation.dto.response;

import java.util.UUID;

import com.firstticket.venueservice.application.dto.result.VenueSeatResult;
import com.firstticket.venueservice.domain.PhysicalStatus;

/**
 * 좌석 조회 응답 DTO.
 */
public record VenueSeatResponse(
    UUID id,
    UUID sectionId,
    int row,
    int col,
    PhysicalStatus physicalStatus
) {
    public static VenueSeatResponse from(VenueSeatResult result) {
        return new VenueSeatResponse(
            result.id(),
            result.sectionId(),
            result.row(),
            result.col(),
            result.physicalStatus()
        );
    }
}
