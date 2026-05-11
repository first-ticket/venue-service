package com.firstticket.venueservice.application.dto.result;

/**
 * venue 검증 묶음 응답 DTO.
 * Program Service의 createSchedule() 호출 시
 * venue 존재 확인 + 해당 타입 전체 수용량을 한 번에 반환한다.
 *
 * totalCapacity:
 * - SEATED   : 해당 venue의 모든 SEATED 구역 rowCount × colCount 합계
 * - STANDING : 해당 venue의 모든 STANDING 구역 capacity 합계
 * - FREE     : 해당 venue의 모든 FREE 구역 capacity 합계
 */
public record VenueValidationResult(
    int totalCapacity
) {
}
