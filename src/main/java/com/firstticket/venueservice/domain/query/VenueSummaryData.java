package com.firstticket.venueservice.domain.query;

import java.util.UUID;

/**
 * 공연장 목록 조회 결과 도메인 DTO.
 *
 * &#064;QueryProjection  미사용 이유:
 * 도메인 계층이 QueryDSL 인프라 의존성을 갖지 않아야 한다.
 * 인스턴스 생성은 infrastructure 계층에서
 * Projections.constructor()로 처리한다.
 */
public record VenueSummaryData(
    UUID id,
    String name,
    String address,
    int sectionCount    // 등록된 구역 수
) {
}
