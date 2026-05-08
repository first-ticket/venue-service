package com.firstticket.venueservice.application.dto.result;

import com.firstticket.venueservice.domain.SeatType;

/**
 * section 검증 묶음 응답 DTO.
 * Program Service의 addPriceGrade() / addSectionCapacity() 호출 시
 * section 소속 확인 + seatType + capacity를 한 번에 반환한다.
 *
 * seatType : section의 SeatType (SEATED·STANDING·FREE)
 * capacity :
 * - SEATED: rowCount × colCount
 * - STANDING·FREE: Section.capacity
 */
public record SectionValidationResult(
    SeatType seatType,
    int capacity
) {
}
