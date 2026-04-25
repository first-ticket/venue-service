package com.firstticket.venueservice.domain.exception;

import org.springframework.http.HttpStatus;

import com.firstticket.common.response.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VenueErrorCode implements ErrorCode {

    // ---- Venue -------
    VENUE_NOT_FOUND(HttpStatus.NOT_FOUND,
        "공연장을 찾을 수 없습니다"),
    VENUE_ALREADY_DELETED(HttpStatus.BAD_REQUEST,
        "삭제된 공연장입니다"),
    INVALID_VENUE_NAME(HttpStatus.BAD_REQUEST,
        "공연장 이름은 필수입니다"),
    INVALID_VENUE_ADDRESS(HttpStatus.BAD_REQUEST,
        "공연장 주소는 필수입니다"),

    // --- Section -----
    SECTION_NOT_FOUND(HttpStatus.NOT_FOUND,
        "구역을 찾을 수 없습니다"),
    INVALID_SECTION_NAME(HttpStatus.BAD_REQUEST,
        "구역명은 필수입니다"),
    INVALID_SECTION_ID(HttpStatus.BAD_REQUEST,
        "구역 ID는 필수입니다"),
    INVALID_SEAT_COUNT(HttpStatus.BAD_REQUEST,
        "행과 열 수는 1 이상이어야 합니다"),

    // ---- VenueSeat --------
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND,
        "좌석을 찾을 수 없습니다"),
    SEAT_ALREADY_BROKEN(HttpStatus.BAD_REQUEST,
        "이미 파손 처리된 좌석입니다"),
    SEAT_ALREADY_AVAILABLE(HttpStatus.BAD_REQUEST,
        "이미 사용 가능한 좌석입니다"),
    INVALID_SEAT_POSITION(HttpStatus.BAD_REQUEST,
        "좌석 행·열 번호는 1 이상이어야 합니다"),
    INVALID_SEAT_SEQUENCE(HttpStatus.BAD_REQUEST,
        "좌석 순번은 1 이상이어야 합니다"),
    VENUE_TIME_CONFLICT(HttpStatus.CONFLICT,
        "해당 공연장에 이미 예약된 일정이 있습니다");

    private final HttpStatus status;
    private final String message;
}
