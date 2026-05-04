package com.firstticket.venueservice.presentation.dto.response;

import java.util.UUID;

import com.firstticket.venueservice.application.dto.result.VenueSummaryResult;

/**
 * 공연장 목록 조회 응답 DTO.
 */
public record VenueSummaryResponse(
    UUID id,
    String name,
    String address,
    int sectionCount
) {
    public static VenueSummaryResponse from(VenueSummaryResult result) {
        return new VenueSummaryResponse(
            result.id(),
            result.name(),
            result.address(),
            result.sectionCount()
        );
    }
}
