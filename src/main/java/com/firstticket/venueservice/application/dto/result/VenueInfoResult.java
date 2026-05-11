package com.firstticket.venueservice.application.dto.result;

import com.firstticket.venueservice.domain.Venue;

/**
 * 공연장 기본 정보 응답 DTO (내부 전용).
 * Program Service의 ScheduleBookingInfoResponse 구성에 사용한다.
 */
public record VenueInfoResult(
    String name,
    String address
) {
    public static VenueInfoResult from(Venue venue) {
        return new VenueInfoResult(venue.getName(), venue.getAddress());
    }
}
