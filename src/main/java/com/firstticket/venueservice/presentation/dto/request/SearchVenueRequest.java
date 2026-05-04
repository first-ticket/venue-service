package com.firstticket.venueservice.presentation.dto.request;

import com.firstticket.venueservice.application.dto.query.VenueSearchQuery;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 공연장 목록 조회 요청 DTO.
 * toQuery()로 Application 계층의 VenueSearchQuery로 변환한다.
 */
public record SearchVenueRequest(
    String keyword,

    @Pattern(
        regexp = "name|createdAt",
        message = "정렬 필드는 name 또는 createdAt만 허용됩니다"
    )
    String sort,

    @Pattern(
        regexp = "asc|desc",
        flags = Pattern.Flag.CASE_INSENSITIVE,
        message = "정렬 방향은 asc 또는 desc만 허용됩니다"
    )
    String direction,

    @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다")
    int page,

    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
    int size
) {
    public VenueSearchQuery toQuery() {
        return new VenueSearchQuery(
            keyword, sort, direction, page, size
        );
    }
}
