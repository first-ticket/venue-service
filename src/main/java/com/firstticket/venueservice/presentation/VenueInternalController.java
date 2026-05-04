package com.firstticket.venueservice.presentation;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.firstticket.common.response.ApiResponse;
import com.firstticket.venueservice.application.dto.result.SectionCapacityResult;
import com.firstticket.venueservice.application.service.VenueQueryService;

import lombok.RequiredArgsConstructor;

/**
 * Venue Service 내부 API Controller.
 * Program Service의 VenueClient(Feign)가 호출하는 내부 전용 엔드포인트.
 * 외부 사용자에게 노출하지 않는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/venues")
public class VenueInternalController {

    private final VenueQueryService venueQueryService;

    /**
     * 공연장 존재 여부 확인.
     * Program Service의 스케줄 등록 시 공연장 존재 여부를 확인한다.
     * 존재하면 200, 존재하지 않으면 404를 반환한다.
     */
    @GetMapping("/{venueId}/exists")
    public ResponseEntity<Void> checkVenueExists(@PathVariable UUID venueId) {
        venueQueryService.validateVenueExists(venueId);
        return ResponseEntity.ok().build();
    }

    /**
     * Section 수용 인원 상한 조회.
     * Program Service의 addSectionCapacity() 호출 전 상한 검증에 사용한다.
     */
    @GetMapping("/sections/{sectionId}/capacity")
    public ResponseEntity<ApiResponse<SectionCapacityResult>> getSectionCapacity(
        @PathVariable UUID sectionId) {
        SectionCapacityResult result = venueQueryService.getSectionCapacity(sectionId);
        return ApiResponse.success(VenueSuccessCode.SECTION_CAPACITY_OK, result);
    }
}
