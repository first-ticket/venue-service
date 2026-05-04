package com.firstticket.venueservice.presentation.dto.response;

import java.util.List;
import java.util.UUID;

import com.firstticket.venueservice.application.dto.result.VenueResult;

/**
 * 공연장 단건 조회 응답 DTO.
 */
public record VenueResponse(
    UUID id,
    String name,
    String address,
    List<SectionResponse> sections
) {
    public static VenueResponse from(VenueResult result) {
        return new VenueResponse(
            result.id(),
            result.name(),
            result.address(),
            result.sections().stream()
                .map(SectionResponse::from)
                .toList()
        );
    }
}
