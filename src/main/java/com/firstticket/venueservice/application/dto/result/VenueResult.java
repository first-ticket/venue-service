package com.firstticket.venueservice.application.dto.result;

import java.util.List;
import java.util.UUID;

import com.firstticket.venueservice.domain.Venue;

/**
 * 공연장 단건 조회 결과 DTO.
 * Application 계층에서 도메인 객체를 이 DTO로 변환하여 반환한다.
 */
public record VenueResult(
    UUID id,
    String name,
    String address,
    List<SectionResult> sections
) {
    public static VenueResult from(Venue venue) {
        return new VenueResult(
            venue.getId(),
            venue.getName(),
            venue.getAddress(),
            venue.getSections().stream()
                .map(SectionResult::from)
                .toList()
        );
    }
}
