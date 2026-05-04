package com.firstticket.venueservice.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.firstticket.common.exception.BusinessException;
import com.firstticket.common.response.ApiResponse;
import com.firstticket.common.response.CommonErrorCode;
import com.firstticket.common.web.AuthContext;
import com.firstticket.common.web.UserRole;
import com.firstticket.venueservice.application.dto.command.UpdateVenueSeatStatusCommand;
import com.firstticket.venueservice.application.dto.result.VenueResult;
import com.firstticket.venueservice.application.dto.result.VenueSeatResult;
import com.firstticket.venueservice.application.dto.result.VenueSummaryResult;
import com.firstticket.venueservice.application.service.VenueCommandService;
import com.firstticket.venueservice.application.service.VenueQueryService;
import com.firstticket.venueservice.domain.query.PagedResult;
import com.firstticket.venueservice.presentation.dto.request.CreateSectionRequest;
import com.firstticket.venueservice.presentation.dto.request.CreateVenueRequest;
import com.firstticket.venueservice.presentation.dto.request.SearchVenueRequest;
import com.firstticket.venueservice.presentation.dto.request.UpdateSeatStatusRequest;
import com.firstticket.venueservice.presentation.dto.request.UpdateVenueRequest;
import com.firstticket.venueservice.presentation.dto.response.PagedResponse;
import com.firstticket.venueservice.presentation.dto.response.SectionResponse;
import com.firstticket.venueservice.presentation.dto.response.VenueResponse;
import com.firstticket.venueservice.presentation.dto.response.VenueSeatResponse;
import com.firstticket.venueservice.presentation.dto.response.VenueSummaryResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 공연장 도메인 Controller.
 *
 * 인가 처리:
 * Gateway가 주입한 X-User-Id, X-User-Role 헤더를
 * AuthContext로 추출하여 역할 검증을 수행한다.
 * HOST·ADMIN 검증은 Controller 계층에서 처리한다.
 * 소유자(createdBy) 검증은 Application 계층에서 처리한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/venues")
public class VenueController {

    private final VenueCommandService venueCommandService;
    private final VenueQueryService venueQueryService;

    // ----- 공연장 CRUD --------------------------------------

    /**
     * 공연장 등록 (POST /api/venues).
     * 권한: ADMIN, HOST
     * sections 배열을 함께 전달하면 구역과 VenueSeat이 자동 생성된다 (V-01, V-02).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<VenueResponse>> createVenue(
        @RequestBody @Valid CreateVenueRequest request) {
        checkHostOrAdmin();
        UUID requesterId = AuthContext.getUserId();

        // 공연장 먼저 생성
        VenueResult venueResult = venueCommandService.createVenue(
            requesterId, request.toCommand()
        );

        // sections가 있으면 구역 추가 (V-01, V-02)
        if (request.sections() != null && !request.sections().isEmpty()) {
            for (CreateVenueRequest.SectionRequest sectionRequest : request.sections()) {
                venueCommandService.createSection(
                    requesterId,
                    sectionRequest.toCommand(venueResult.id())
                );
            }
            // 구역 추가 후 sections 포함된 Venue 재조회
            venueResult = venueQueryService.getVenue(venueResult.id());
        }

        return ApiResponse.success(
            VenueSuccessCode.VENUE_CREATED,
            VenueResponse.from(venueResult)
        );
    }

    /**
     * 공연장 목록 조회 (GET /api/venues).
     * 권한: ALL
     * 이름·주소 키워드 검색, 정렬, 페이지네이션 지원.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<VenueSummaryResponse>>> searchVenues(
        @ModelAttribute SearchVenueRequest request) {
        PagedResult<VenueSummaryResult> pagedResult =
            venueQueryService.searchVenues(request.toQuery());

        return ApiResponse.success(
            VenueSuccessCode.VENUE_LIST_FOUND,
            PagedResponse.from(pagedResult, VenueSummaryResponse::from)
        );
    }

    /**
     * 공연장 상세 조회 (GET /api/venues/{venueId}).
     * 권한: ALL
     * 공연장 기본 정보 + 구역 목록 반환.
     */
    @GetMapping("/{venueId}")
    public ResponseEntity<ApiResponse<VenueResponse>> getVenue(
        @PathVariable UUID venueId) {
        VenueResult result = venueQueryService.getVenue(venueId);
        return ApiResponse.success(
            VenueSuccessCode.VENUE_FOUND,
            VenueResponse.from(result)
        );
    }

    /**
     * 공연장 수정 (PATCH /api/venues/{venueId}).
     * 권한: ADMIN, HOST
     * null이면 기존 값 유지 (부분 업데이트).
     */
    @PatchMapping("/{venueId}")
    public ResponseEntity<ApiResponse<VenueResponse>> updateVenue(
        @PathVariable UUID venueId,
        @RequestBody UpdateVenueRequest request) {
        checkHostOrAdmin();
        UUID requesterId = AuthContext.getUserId();

        VenueResult result = venueCommandService.updateVenue(
            requesterId, request.toCommand(venueId)
        );
        return ApiResponse.success(
            VenueSuccessCode.VENUE_UPDATED,
            VenueResponse.from(result)
        );
    }

    /**
     * 공연장 삭제 (DELETE /api/venues/{venueId}).
     * 권한: ADMIN
     * 사전 조건: 해당 공연장에 등록된 프로그램이 없어야 한다.
     */
    @DeleteMapping("/{venueId}")
    public ResponseEntity<ApiResponse<Void>> deleteVenue(
        @PathVariable UUID venueId) {
        checkAdmin();
        UUID requesterId = AuthContext.getUserId();

        venueCommandService.deleteVenue(requesterId, venueId);
        return ApiResponse.success(VenueSuccessCode.VENUE_DELETED);
    }

    // ---- 구역 -----------------------------------------------------

    /**
     * 구역 등록 (POST /api/venues/{venueId}/sections).
     * 권한: ADMIN, HOST
     * SEATED 타입: VenueSeat 자동 생성 (V-02).
     */
    @PostMapping("/{venueId}/sections")
    public ResponseEntity<ApiResponse<VenueResponse>> createSection(
        @PathVariable UUID venueId,
        @RequestBody @Valid CreateSectionRequest request) {
        checkHostOrAdmin();
        UUID requesterId = AuthContext.getUserId();

        VenueResult result = venueCommandService.createSection(
            requesterId, request.toCommand(venueId)
        );
        return ApiResponse.success(
            VenueSuccessCode.SECTION_CREATED,
            VenueResponse.from(result)
        );
    }

    /**
     * 구역 목록 조회 (GET /api/venues/{venueId}/sections).
     * 권한: ALL
     * 공연장 상세 조회에 sections가 포함되므로 getVenue()로 대체한다.
     */
    @GetMapping("/{venueId}/sections")
    public ResponseEntity<ApiResponse<List<SectionResponse>>> getSections(
        @PathVariable UUID venueId) {
        VenueResult result = venueQueryService.getVenue(venueId);
        return ApiResponse.success(
            VenueSuccessCode.SECTION_LIST_FOUND,
            result.sections().stream()
                .map(SectionResponse::from)
                .toList()
        );
    }

    /**
     * 구역 상세 조회 (GET /api/venues/{venueId}/sections/{sectionId}).
     * 권한: ALL
     */
    @GetMapping("/{venueId}/sections/{sectionId}")
    public ResponseEntity<ApiResponse<SectionResponse>> getSection(
        @PathVariable UUID venueId,
        @PathVariable UUID sectionId) {
        VenueResult venue = venueQueryService.getVenue(venueId);
        SectionResponse section = venue.sections().stream()
            .filter(s -> s.id().equals(sectionId))
            .map(SectionResponse::from)
            .findFirst()
            .orElseThrow(() ->
                new com.firstticket.venueservice.domain.exception.VenueException(
                    com.firstticket.venueservice.domain.exception.VenueErrorCode.SECTION_NOT_FOUND
                ));
        return ApiResponse.success(VenueSuccessCode.SECTION_FOUND, section);
    }

    /**
     * 구역 삭제 (DELETE /api/venues/{venueId}/sections/{sectionId}).
     * 권한: ADMIN
     * SEATED 타입: 연관 VenueSeat 일괄 삭제 후 구역 삭제.
     * 수정 API 없음 — 삭제 후 재등록 방식 사용.
     */
    @DeleteMapping("/{venueId}/sections/{sectionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSection(
        @PathVariable UUID venueId,
        @PathVariable UUID sectionId) {
        checkAdmin();
        UUID requesterId = AuthContext.getUserId();

        venueCommandService.deleteSection(requesterId, venueId, sectionId);
        return ApiResponse.success(VenueSuccessCode.SECTION_DELETED);
    }

    // ---- 좌석 -----------------------------------------------------

    /**
     * 구역 내 좌석 목록 조회 (GET /api/venues/{venueId}/sections/{sectionId}/seats).
     * 권한: ALL
     * SEATED 타입만 VenueSeat이 존재한다.
     * STANDING·FREE 타입은 빈 리스트를 반환한다.
     */
    @GetMapping("/{venueId}/sections/{sectionId}/seats")
    public ResponseEntity<ApiResponse<List<VenueSeatResponse>>> getSeats(
        @PathVariable UUID venueId,
        @PathVariable UUID sectionId) {
        List<VenueSeatResult> results = venueQueryService.getSeatsBySection(sectionId);
        return ApiResponse.success(
            VenueSuccessCode.SEAT_LIST_FOUND,
            results.stream().map(VenueSeatResponse::from).toList()
        );
    }

    /**
     * 좌석 상세 조회 (GET /api/venues/{venueId}/sections/{sectionId}/seats/{seatId}).
     * 권한: ALL
     */
    @GetMapping("/{venueId}/sections/{sectionId}/seats/{seatId}")
    public ResponseEntity<ApiResponse<VenueSeatResponse>> getSeat(
        @PathVariable UUID venueId,
        @PathVariable UUID sectionId,
        @PathVariable UUID seatId) {
        VenueSeatResult result = venueQueryService.getSeat(seatId);
        return ApiResponse.success(
            VenueSuccessCode.SEAT_FOUND,
            VenueSeatResponse.from(result)
        );
    }

    /**
     * 좌석 물리 상태 변경 (PATCH /api/venues/{venueId}/sections/{sectionId}/seats/{seatId}/status).
     * 권한: ADMIN
     * AVAILABLE ↔ BROKEN 전환.
     * BROKEN 상태의 좌석은 예매 불가 처리된다 (V-05).
     */
    @PatchMapping("/{venueId}/sections/{sectionId}/seats/{seatId}/status")
    public ResponseEntity<ApiResponse<VenueSeatResponse>> updateSeatStatus(
        @PathVariable UUID venueId,
        @PathVariable UUID sectionId,
        @PathVariable UUID seatId,
        @RequestBody @Valid UpdateSeatStatusRequest request) {
        checkAdmin();
        UUID requesterId = AuthContext.getUserId();

        UpdateVenueSeatStatusCommand command = request.toCommand(seatId);
        VenueSeatResult result = command.broken()
            ? venueCommandService.markSeatBroken(requesterId, command)
            : venueCommandService.restoreSeat(requesterId, command);

        return ApiResponse.success(
            VenueSuccessCode.SEAT_STATUS_UPDATED,
            VenueSeatResponse.from(result)
        );
    }

    // --- 권한 검증 헬퍼 -----------------------------------

    /**
     * HOST 또는 ADMIN 역할인지 검증한다.
     */
    private void checkHostOrAdmin() {
        UserRole role = AuthContext.getRole();
        if (role != UserRole.HOST && role != UserRole.ADMIN) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    /**
     * ADMIN 역할인지 검증한다.
     * 공연장 삭제·구역 삭제·좌석 상태 변경은 ADMIN만 허용한다.
     */
    private void checkAdmin() {
        UserRole role = AuthContext.getRole();
        if (role != UserRole.ADMIN) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
