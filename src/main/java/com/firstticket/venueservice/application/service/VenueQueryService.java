package com.firstticket.venueservice.application.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.firstticket.venueservice.application.dto.query.VenueSearchQuery;
import com.firstticket.venueservice.application.dto.result.SectionValidationResult;
import com.firstticket.venueservice.application.dto.result.VenueResult;
import com.firstticket.venueservice.application.dto.result.VenueSeatResult;
import com.firstticket.venueservice.application.dto.result.VenueSummaryResult;
import com.firstticket.venueservice.application.dto.result.VenueValidationResult;
import com.firstticket.venueservice.domain.SeatType;
import com.firstticket.venueservice.domain.Section;
import com.firstticket.venueservice.domain.Venue;
import com.firstticket.venueservice.domain.VenueRepository;
import com.firstticket.venueservice.domain.VenueSeat;
import com.firstticket.venueservice.domain.VenueSeatRepository;
import com.firstticket.venueservice.domain.exception.VenueErrorCode;
import com.firstticket.venueservice.domain.exception.VenueException;
import com.firstticket.venueservice.domain.query.PagedResult;
import com.firstticket.venueservice.domain.query.SectionQueryRepository;
import com.firstticket.venueservice.domain.query.VenueQueryRepository;

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
    private final SectionQueryRepository sectionQueryRepository;

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
            throw new VenueException(VenueErrorCode.INVALID_SEAT_ID);
        }
    }

    /**
     * 구역이 해당 공연장에 속하는지 검증한다.
     * getSeats, getSeat, updateSeatStatus 호출 전 계층 관계를 확인한다.
     */
    public void validateSectionBelongsToVenue(UUID venueId, UUID sectionId) {
        VenueResult venue = getVenue(venueId);
        boolean belongs = venue.sections().stream()
            .anyMatch(s -> s.id().equals(sectionId));
        if (!belongs) {
            throw new VenueException(VenueErrorCode.SECTION_NOT_FOUND);
        }
    }

    /**
     * 좌석이 해당 구역에 속하는지 검증한다.
     */
    public void validateSeatBelongsToSection(UUID sectionId, UUID seatId) {
        VenueSeatResult seat = getSeat(seatId);
        if (!seat.sectionId().equals(sectionId)) {
            throw new VenueException(VenueErrorCode.SEAT_NOT_FOUND);
        }
    }

    /**
     * 공연장 존재 여부 확인.
     * VenueInternalController에서 사용한다.
     * Presentation 계층이 VenueRepository에 직접 의존하지 않도록 격리한다.
     */
    public void validateVenueExists(UUID venueId) {
        if (!venueRepository.existsById(venueId)) {
            throw new VenueException(VenueErrorCode.VENUE_NOT_FOUND);
        }
    }

    /**
     * venue 검증 묶음 조회.
     * venue 존재 여부 확인 + 해당 seatType 구역의 전체 수용량 합산을 한 번에 처리한다.
     * Program Service의 createSchedule() 에서 Feign 호출 수를 줄이기 위해 도입한다.
     *
     * venue가 존재하지 않으면 VENUE_NOT_FOUND 예외를 던진다.
     * seatType이 입력되지 않으면 INVALID_SECTION_TYPE 예외를 던진다.
     * 해당 seatType의 구역이 하나도 없으면 totalCapacity = 0을 반환한다.
     *
     * @param venueId  검증할 공연장 ID
     * @param seatType 프로그램 타입에 대응하는 구역 타입 (SEATED·STANDING·FREE)
     *
     */
    public VenueValidationResult getVenueValidation(UUID venueId, SeatType seatType) {
        validateVenueId(venueId);

        if (Objects.isNull(seatType))
            throw new VenueException(VenueErrorCode.INVALID_SECTION_TYPE);

        // venue 존재 확인 + sections 함께 로딩 (N+1 방지)
        Venue venue = venueRepository.findByIdWithSections(venueId)
            .orElseThrow(() -> new VenueException(VenueErrorCode.VENUE_NOT_FOUND));

        // 해당 seatType 구역의 전체 수용량 합산
        // SEATED   : rowCount × colCount (getSeatCount() 내부에서 처리)
        // STANDING·FREE : capacity (getSeatCount() 내부에서 처리)
        int totalCapacity = venue.getSections().stream()
            .filter(s -> s.getType() == seatType)
            .mapToInt(Section::getSeatCount)
            .sum();

        return new VenueValidationResult(totalCapacity);
    }

    /**
     * section 검증 묶음 조회.
     * section 소속 venue 확인 + seatType + capacity를 한 번에 처리한다.
     * Program Service의 addPriceGrade() / addSectionCapacity() 에서
     * Feign 호출 수를 줄이기 위해 도입한다.
     *
     * sectionId가 venueId 소속이 아니면 SECTION_NOT_FOUND 예외를 던진다.
     *
     * @param venueId   소속 공연장 ID
     * @param sectionId 검증할 구역 ID
     */
    public SectionValidationResult getSectionValidation(UUID venueId, UUID sectionId) {
        validateVenueId(venueId);
        validateSectionId(sectionId);

        // venue + sections 함께 로딩 후 소속 여부 확인
        Venue venue = venueRepository.findByIdWithSections(venueId)
            .orElseThrow(() -> new VenueException(VenueErrorCode.VENUE_NOT_FOUND));

        Section section = venue.getSections().stream()
            .filter(s -> s.getId().equals(sectionId))
            .findFirst()
            .orElseThrow(() -> new VenueException(VenueErrorCode.SECTION_NOT_FOUND));

        // getSeatCount(): SEATED → rowCount × colCount / STANDING·FREE → capacity
        return new SectionValidationResult(section.getType(), section.getSeatCount());
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
