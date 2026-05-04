package com.firstticket.venueservice.application.dto.command;

import java.util.UUID;

/**
 * 공연장 수정 커맨드.
 * null이면 기존 값 유지 (부분 업데이트).
 */
public record UpdateVenueCommand(
    UUID venueId,
    String name,
    String address
) {
}
