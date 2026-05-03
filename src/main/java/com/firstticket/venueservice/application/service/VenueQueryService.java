package com.firstticket.venueservice.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.firstticket.venueservice.application.dto.query.VenueSearchQuery;
import com.firstticket.venueservice.application.dto.result.SectionCapacityResult;
import com.firstticket.venueservice.application.dto.result.VenueResult;
import com.firstticket.venueservice.application.dto.result.VenueSeatResult;
import com.firstticket.venueservice.application.dto.result.VenueSummaryResult;
import com.firstticket.venueservice.domain.Section;
import com.firstticket.venueservice.domain.Venue;
import com.firstticket.venueservice.domain.VenueRepository;
import com.firstticket.venueservice.domain.VenueSeat;
import com.firstticket.venueservice.domain.VenueSeatRepository;
import com.firstticket.venueservice.domain.exception.VenueErrorCode;
import com.firstticket.venueservice.domain.exception.VenueException;
import com.firstticket.venueservice.domain.query.PagedResult;
import com.firstticket.venueservice.domain.query.VenueQueryRepository;
import com.firstticket.venueservice.infrastructure.persistence.SectionJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * 공연장 도메인 쿼리 서비스.
 * 조회 전용 서비스로 트랜잭션은 readOnly로 설정한다.
 * 모든 사용자(ALL)가 조회 가능하므로 별도 권한 검증이 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueQueryService {

    private final VenueRepository venueRepository;
    private final VenueSeatRepository venueSeatRepository;
    private final VenueQueryRepository venueQueryRepository;
    private final SectionJpaRepository sectionJpaRepository;

    /**
     * 공연장 단건 조회.
     * 공연장 기본 정보 + 구역 목록 반환.
     */
    public VenueResult getVenue(UUID venueId) {
        validateVenueId(venueId);

        Venue venue = venueRepository.findByIdWithSections(venueId)
            .orElseThrow(() -> new VenueException(VenueErrorCode.VENUE_NOT_FOUND));
        return VenueResult.from(venue);
    }

    /**
     * 공연장 목록 조회.
     * 이름·주소 키워드 검색, 정렬, 페이지네이션 지원.
     * 권한 제한 없음 — ALL.
     */
    public PagedResult<VenueSummaryResult> searchVenues(VenueSearchQuery query) {
        validateSearchQuery(query);

        PagedResult<com.firstticket.venueservice.domain.query.VenueSummaryData> pagedData =
            venueQueryRepository.findBySpec(query.toSpec());

        return PagedResult.of(
            pagedData.content().stream()
                .map(VenueSummaryResult::from)
                .toList(),
            pagedData.totalElements(),
            pagedData.pageNumber(),
            pagedData.pageSize()
        );
    }

    /**
     * 구역별 좌석 목록 조회.
     * SEATED 타입 구역의 물리 좌석 정보를 반환한다.
     * STANDING·FREE 타입은 VenueSeat이 없으므로 빈 리스트를 반환한다.
     */
    public List<VenueSeatResult> getSeatsBySection(UUID sectionId) {
        validateSectionId(sectionId);

        return venueSeatRepository.findBySectionId(sectionId).stream()
            .map(VenueSeatResult::from)
            .toList();
    }

    /**
     * 좌석 단건 조회.
     */
    public VenueSeatResult getSeat(UUID seatId) {
        validateSeatId(seatId);

        VenueSeat seat = venueSeatRepository.findById(seatId)
            .orElseThrow(() -> new VenueException(VenueErrorCode.SEAT_NOT_FOUND));
        return VenueSeatResult.from(seat);
    }

    /**
     * Section 수용 인원 상한 조회.
     * Program Service의 VenueClient가 호출하는 내부 API에서 사용한다.
     * SEATED: rowCount × colCount
     * STANDING·FREE: capacity
     */
    public SectionCapacityResult getSectionCapacity(UUID sectionId) {
        validateSectionId(sectionId);

        Section section = sectionJpaRepository.findById(sectionId)
            .orElseThrow(() -> new VenueException(VenueErrorCode.SECTION_NOT_FOUND));
        return SectionCapacityResult.from(section);
    }

    // ----- private 검증 메서드 ------------------------------------------

    /**
     * venueId null 검증.
     * 단건 조회 메서드 진입 시 호출한다.
     */
    private void validateVenueId(UUID venueId) {
        if (venueId == null) {
            throw new VenueException(VenueErrorCode.INVALID_VENUE_ID);
        }
    }

    /**
     * sectionId null 검증.
     */
    private void validateSectionId(UUID sectionId) {
        if (sectionId == null) {
            throw new VenueException(VenueErrorCode.INVALID_SECTION_ID);
        }
    }

    /**
     * seatId null 검증.
     */
    private void validateSeatId(UUID seatId) {
        if (seatId == null) {
            throw new VenueException(VenueErrorCode.SEAT_NOT_FOUND);
        }
    }

    /**
     * 목록 조회 쿼리 검증.
     * pageSize, pageNumber 범위 검증.
     */
    private void validateSearchQuery(VenueSearchQuery query) {
        if (query == null) {
            throw new VenueException(VenueErrorCode.INVALID_SEARCH_QUERY);
        }
        if (query.pageSize() <= 0) {
            throw new VenueException(VenueErrorCode.INVALID_PAGE_SIZE);
        }
        if (query.pageNumber() < 0) {
            throw new VenueException(VenueErrorCode.INVALID_PAGE_NUMBER);
        }
    }
}
