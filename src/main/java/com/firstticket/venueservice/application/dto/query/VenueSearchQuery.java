package com.firstticket.venueservice.application.dto.query;

import com.firstticket.venueservice.domain.query.VenueSearchSpec;

/**
 * 공연장 목록 조회 쿼리 DTO.
 * Presentation 계층의 SearchVenueRequest.toQuery()로 생성된다.
 * toSpec()으로 도메인 계층의 VenueSearchSpec으로 변환한다.
 */
public record VenueSearchQuery(
    String keyword,
    String sortField,
    String direction,
    int pageNumber,
    int pageSize
) {
    public VenueSearchSpec toSpec() {
        return new VenueSearchSpec(
            keyword, sortField, direction, pageNumber, pageSize
        );
    }
}
