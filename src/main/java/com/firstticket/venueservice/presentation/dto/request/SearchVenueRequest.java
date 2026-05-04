package com.firstticket.venueservice.presentation.dto.request;

import com.firstticket.venueservice.application.dto.query.VenueSearchQuery;

/**
 * 공연장 목록 조회 요청 DTO.
 * toQuery()로 Application 계층의 VenueSearchQuery로 변환한다.
 */
public record SearchVenueRequest(
    String keyword,
    String sort,
    String direction,
    int page,
    int size
) {
    public VenueSearchQuery toQuery() {
        return new VenueSearchQuery(
            keyword, sort, direction, page, size
        );
    }
}
