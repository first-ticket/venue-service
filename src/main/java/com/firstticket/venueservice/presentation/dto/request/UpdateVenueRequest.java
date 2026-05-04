package com.firstticket.venueservice.presentation.dto.request;

import java.util.UUID;

import com.firstticket.venueservice.application.dto.command.UpdateVenueCommand;

/**
 * 공연장 수정 요청 DTO.
 * null이면 기존 값 유지 (부분 업데이트).
 */
public record UpdateVenueRequest(
    String name,
    String address
) {
    public UpdateVenueCommand toCommand(UUID venueId) {
        return new UpdateVenueCommand(venueId, name, address);
    }
}
