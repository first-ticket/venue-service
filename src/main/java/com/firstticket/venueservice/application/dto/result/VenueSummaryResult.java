package com.firstticket.venueservice.application.dto.result;

import java.util.UUID;

import com.firstticket.venueservice.domain.query.VenueSummaryData;

/**
 * 공연장 목록 조회 결과 DTO.
 * Application 계층에서 도메인 Projection(VenueSummaryData)을 이 DTO로 변환한다.
 */
public record VenueSummaryResult(
    UUID id,
    String name,
    String address,
    int sectionCount
) {
    public static VenueSummaryResult from(VenueSummaryData data) {
        return new VenueSummaryResult(
            data.id(),
            data.name(),
            data.address(),
            data.sectionCount()
        );
    }
}
