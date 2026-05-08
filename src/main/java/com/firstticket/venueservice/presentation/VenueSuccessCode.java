package com.firstticket.venueservice.presentation;

import org.springframework.http.HttpStatus;

import com.firstticket.common.response.SuccessCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VenueSuccessCode implements SuccessCode {

    // --- Venue ------------------------------------------
    VENUE_CREATED(HttpStatus.CREATED, "공연장이 등록되었습니다"),
    VENUE_UPDATED(HttpStatus.OK, "공연장이 수정되었습니다"),
    VENUE_DELETED(HttpStatus.NO_CONTENT, "공연장이 삭제되었습니다"),
    VENUE_FOUND(HttpStatus.OK, "공연장을 조회했습니다"),
    VENUE_LIST_FOUND(HttpStatus.OK, "공연장 목록을 조회했습니다"),

    // ---- Section ------------------------------------------
    SECTION_CREATED(HttpStatus.CREATED, "구역이 등록되었습니다"),
    SECTION_DELETED(HttpStatus.NO_CONTENT, "구역이 삭제되었습니다"),
    SECTION_FOUND(HttpStatus.OK, "구역을 조회했습니다"),
    SECTION_LIST_FOUND(HttpStatus.OK, "구역 목록을 조회했습니다"),

    // ----- VenueSeat ------------------------------------------
    SEAT_FOUND(HttpStatus.OK, "좌석을 조회했습니다"),
    SEAT_LIST_FOUND(HttpStatus.OK, "좌석 목록을 조회했습니다"),
    SEAT_STATUS_UPDATED(HttpStatus.OK, "좌석 상태가 변경되었습니다");

    private final HttpStatus status;
    private final String message;
}
