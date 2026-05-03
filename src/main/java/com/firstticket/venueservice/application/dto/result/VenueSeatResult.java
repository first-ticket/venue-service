package com.firstticket.venueservice.application.dto.result;

import java.util.UUID;

import com.firstticket.venueservice.domain.PhysicalStatus;
import com.firstticket.venueservice.domain.VenueSeat;

/**
 * 좌석 단건 조회 결과 DTO.
 */
public record VenueSeatResult(
    UUID id,
    UUID sectionId,
    int row,
    int col,
    PhysicalStatus physicalStatus
) {
    public static VenueSeatResult from(VenueSeat seat) {
        return new VenueSeatResult(
            seat.getId(),
            seat.getSectionId(),
            seat.getRow(),
            seat.getCol(),
            seat.getPhysicalStatus()
        );
    }
}
