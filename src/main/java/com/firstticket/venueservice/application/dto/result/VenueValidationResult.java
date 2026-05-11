package com.firstticket.venueservice.application.dto.result;

import java.util.List;
import java.util.UUID;

import com.firstticket.venueservice.domain.Section;

/**
 * venue 검증 묶음 응답 DTO.
 * Program Service의 createSchedule() 호출 시
 * venue 존재 확인 + 해당 타입 전체 수용량 + 구역 목록을 한 번에 반환한다.
 *
 * sections: ScheduleCreatedEvent의 seatTemplates 구성에 사용한다.
 * totalCapacity: totalCapacity 상한 검증에 사용한다.
 */
public record VenueValidationResult(
    int totalCapacity,
    List<SectionInfo> sections  // 추가
) {

    /**
     * 구역 정보 내부 DTO.
     * seatTemplates 구성에 필요한 필드만 포함한다.
     */
    public record SectionInfo(
        UUID sectionId,
        String sectionName,
        String seatType,        // SEATED·STANDING·FREE
        Integer rowCount,       // SEATED 전용
        Integer colCount,       // SEATED 전용
        Integer capacity        // STANDING·FREE 전용
    ) {
        public static SectionInfo from(Section section) {
            return new SectionInfo(
                section.getId(),
                section.getName(),
                section.getType().name(),
                section.getRowCount(),
                section.getColCount(),
                section.getCapacity()
            );
        }
    }
}
