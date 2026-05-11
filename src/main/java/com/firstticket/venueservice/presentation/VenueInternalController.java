package com.firstticket.venueservice.presentation;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.firstticket.venueservice.application.dto.result.SectionValidationResult;
import com.firstticket.venueservice.application.dto.result.VenueInfoResult;
import com.firstticket.venueservice.application.dto.result.VenueValidationResult;
import com.firstticket.venueservice.application.service.VenueQueryService;
import com.firstticket.venueservice.domain.SeatType;

import lombok.RequiredArgsConstructor;

/**
 * Venue Service 내부 API Controller.
 * Program Service의 VenueClient(Feign)가 호출하는 내부 전용 엔드포인트.
 * 외부 사용자에게 노출하지 않는다.
 *
 * 기존 개별 엔드포인트 (/exists, /capacity, /sections/{id}/capacity, /sections/{id}/type)를
 * 검증 묶음 2개로 통합하여 서비스 간 Feign 호출 수를 줄인다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/venues")
public class VenueInternalController {

    private final VenueQueryService venueQueryService;

    /**
     * venue 검증 묶음 (venue 존재 확인 + 타입별 전체 수용량).
     * Program Service의 createSchedule() 에서 호출한다.
     *
     * venue가 존재하지 않으면 404를 반환한다.
     * 존재하면 해당 seatType 구역의 전체 수용량 합산을 반환한다.
     *
     * @param venueId  공연장 ID
     * @param seatType 프로그램 타입에 대응하는 구역 타입 (SEATED·STANDING·FREE)
     */
    @GetMapping("/{venueId}/validation")
    public VenueValidationResult getVenueValidation(
        @PathVariable UUID venueId,
        @RequestParam SeatType seatType) {
        return venueQueryService.getVenueValidation(venueId, seatType);
    }

    /**
     * section 검증 묶음 (소속 확인 + seatType + capacity).
     * Program Service의 addPriceGrade() / addSectionCapacity() 에서 호출한다.
     *
     * sectionId가 venueId 소속이 아니면 404를 반환한다.
     * 소속이 맞으면 seatType과 capacity를 함께 반환한다.
     *
     * @param venueId   소속 공연장 ID
     * @param sectionId 검증할 구역 ID
     */
    @GetMapping("/{venueId}/sections/{sectionId}/validation")
    public SectionValidationResult getSectionValidation(
        @PathVariable UUID venueId,
        @PathVariable UUID sectionId) {
        return venueQueryService.getSectionValidation(venueId, sectionId);
    }

    /**
     * 공연장 기본 정보 조회 (내부 전용).
     * Program Service의 getScheduleBookingInfo() 에서
     * venueName·venueAddress 조회를 위해 호출한다.
     *
     * @param venueId 조회할 공연장 ID
     */
    @GetMapping("/{venueId}/info")
    public VenueInfoResult getVenueInfo(@PathVariable UUID venueId) {
        return venueQueryService.getVenueInfo(venueId);
    }
}
