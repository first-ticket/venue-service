package com.firstticket.venueservice.application.dto.command;

/**
 * 공연장 생성 커맨드.
 * Presentation 계층의 CreateVenueRequest.toCommand()로 생성된다.
 */
public record CreateVenueCommand(
    String name,
    String address
) {
}
