package com.firstticket.venueservice.application.dto.command;

import java.util.UUID;

/**
 * 좌석 물리 상태 변경 커맨드.
 * markBroken() 또는 restore() 호출에 사용한다.
 */
public record UpdateVenueSeatStatusCommand(
    UUID seatId,
    boolean broken  // true: markBroken(), false: restore()
) {
}
