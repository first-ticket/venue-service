package com.firstticket.venueservice.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.firstticket.common.exception.BusinessException;
import com.firstticket.common.response.CommonErrorCode;
import com.firstticket.venueservice.application.dto.command.CreateSectionCommand;
import com.firstticket.venueservice.application.dto.command.CreateVenueCommand;
import com.firstticket.venueservice.application.dto.command.UpdateVenueCommand;
import com.firstticket.venueservice.application.dto.command.UpdateVenueSeatStatusCommand;
import com.firstticket.venueservice.application.dto.result.VenueResult;
import com.firstticket.venueservice.application.dto.result.VenueSeatResult;
import com.firstticket.venueservice.domain.SeatType;
import com.firstticket.venueservice.domain.Section;
import com.firstticket.venueservice.domain.Venue;
import com.firstticket.venueservice.domain.VenueRepository;
import com.firstticket.venueservice.domain.VenueSeat;
import com.firstticket.venueservice.domain.VenueSeatRepository;
import com.firstticket.venueservice.domain.exception.VenueErrorCode;
import com.firstticket.venueservice.domain.exception.VenueException;

import lombok.RequiredArgsConstructor;

/**
 * 공연장 도메인 커맨드 서비스.
 * 트랜잭션·흐름을 조율하며 도메인 메서드를 호출한다.
 * 도메인 로직은 도메인 객체에 위임하고, 이 서비스는 흐름만 조율한다.
 *
 * 권한 검증:
 * HOST·ADMIN 역할 검증은 Controller 계층(AuthContext)에서 처리한다.
 * 이 서비스에서는 공연장 소유자(createdBy) 검증만 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VenueCommandService {

    private final VenueRepository venueRepository;
    private final VenueSeatRepository venueSeatRepository;

    // ---- 공연장 생성 -------------------------------------

    /**
     * 공연장 생성.
     * 새로 생성된 Venue는 sections가 빈 리스트로 초기화되므로
     * findByIdWithSections() 없이 바로 반환한다.
     */
    public VenueResult createVenue(UUID requesterId, CreateVenueCommand command) {
        Venue venue = Venue.create(command.name(), command.address());
        return VenueResult.from(venueRepository.save(venue));
    }

    // -------- 공연장 수정 -----------------------------------------

    /**
     * 공연장 정보 수정.
     * null이면 기존 값 유지 (부분 업데이트).
     * VenueResult에 sections가 포함되므로
     * findByIdWithSections()로 조회한다.
     */
    public VenueResult updateVenue(UUID requesterId, UpdateVenueCommand command) {
        Venue venue = findVenueWithSectionsOrThrow(command.venueId());
        checkOwner(venue, requesterId);

        venue.update(command.name(), command.address());
        return VenueResult.from(venue);
    }

    // -------- 공연장 삭제 -------------------------------------------

    /**
     * 공연장 삭제.
     * 반환값이 없으므로 findVenueOrThrow() 사용.
     * 사전 조건:
     * - 해당 공연장에 등록된 프로그램이 없어야 한다.
     * - 사전 조건 강제 방식 — 이벤트 없음.
     * TODO: Program Service와 연동하여 등록된 프로그램 존재 여부 검증 추가
     */
    public void deleteVenue(UUID requesterId, UUID venueId) {
        Venue venue = findVenueOrThrow(venueId);
        checkOwner(venue, requesterId);
        venueRepository.delete(venue);
    }

    // ------ 구역 관련 -------------------------------------------

    /**
     * 구역 추가.
     * SEATED 타입: VenueSeat 자동 생성 (rowCount × colCount 개).
     * STANDING·FREE 타입: VenueSeat 생성 없음.
     *
     * VenueResult에 sections가 포함되므로
     * findByIdWithSections()로 조회한다.
     */
    public VenueResult createSection(UUID requesterId, CreateSectionCommand command) {
        Venue venue = findVenueWithSectionsOrThrow(command.venueId());
        checkOwner(venue, requesterId);

        Section section = venue.addSection(
            command.type(),
            command.name(),
            command.rowCount(),
            command.colCount(),
            command.capacity()
        );
        venueRepository.save(venue);

        // SEATED 타입: rowCount × colCount 개 VenueSeat 일괄 생성 (V-02)
        if (command.type() == SeatType.SEATED) {
            List<VenueSeat> seats = createSeats(section);
            venueSeatRepository.saveAll(seats);
        }
        // STANDING·FREE: VenueSeat 생성 없음
        // Section.capacity + Redis로 재고 관리

        return VenueResult.from(venue);
    }

    /**
     * 구역 삭제.
     * SEATED 타입: 연관 VenueSeat 일괄 삭제 후 구역 삭제.
     * VenueSeat은 독립 애그리거트이므로 cascade 삭제가 없어 명시적으로 처리한다.
     * 반환값이 없으므로 findVenueWithSectionsOrThrow() 사용
     * (removeSection()이 sections 컬렉션을 직접 수정하기 때문).
     */
    public void deleteSection(UUID requesterId, UUID venueId, UUID sectionId) {
        Venue venue = findVenueWithSectionsOrThrow(venueId);
        checkOwner(venue, requesterId);

        // SEATED 타입이면 연관 VenueSeat 먼저 삭제
        // VenueSeat은 독립 애그리거트 — cascade 삭제 없음
        venue.getSections().stream()
            .filter(s -> s.getId().equals(sectionId))
            .findFirst()
            .ifPresent(section -> {
                if (section.getType() == SeatType.SEATED) {
                    venueSeatRepository.deleteAllBySectionId(sectionId);
                }
            });

        venue.removeSection(sectionId);
    }

    // --- VenueSeat 관련 ----------------------------------------------

    /**
     * 좌석 파손 처리 (V-05).
     * BROKEN 상태의 좌석은 예매 불가 처리된다.
     */
    public VenueSeatResult markSeatBroken(UUID requesterId,
        UpdateVenueSeatStatusCommand command) {
        VenueSeat seat = findSeatOrThrow(command.seatId());
        seat.markBroken();
        return VenueSeatResult.from(seat);
    }

    /**
     * 좌석 복구 처리 (V-05).
     * BROKEN → AVAILABLE 상태로 복구한다.
     */
    public VenueSeatResult restoreSeat(UUID requesterId,
        UpdateVenueSeatStatusCommand command) {
        VenueSeat seat = findSeatOrThrow(command.seatId());
        seat.restore();
        return VenueSeatResult.from(seat);
    }

    // ------------- private 헬퍼 ------------------------------

    /**
     * 공연장 단건 조회 — sections 미로딩.
     * 사용처: 반환값이 없고 sections가 불필요한 경우
     *   - deleteVenue: soft delete만 처리
     */
    private Venue findVenueOrThrow(UUID venueId) {
        return venueRepository.findById(venueId)
            .orElseThrow(() -> new VenueException(VenueErrorCode.VENUE_NOT_FOUND));
    }

    /**
     * 공연장 단건 조회 — sections JOIN FETCH.
     * 사용처:
     *   - VenueResult 반환하는 모든 메서드 (sections 포함)
     *   - sections 컬렉션을 직접 수정하는 메서드
     *     (addSection, removeSection)
     */
    private Venue findVenueWithSectionsOrThrow(UUID venueId) {
        return venueRepository.findByIdWithSections(venueId)
            .orElseThrow(() -> new VenueException(VenueErrorCode.VENUE_NOT_FOUND));
    }

    private VenueSeat findSeatOrThrow(UUID seatId) {
        return venueSeatRepository.findById(seatId)
            .orElseThrow(() -> new VenueException(VenueErrorCode.SEAT_NOT_FOUND));
    }

    /**
     * SEATED 구역의 VenueSeat 일괄 생성.
     * rowCount × colCount 개의 VenueSeat을 생성한다.
     */
    private List<VenueSeat> createSeats(Section section) {
        List<VenueSeat> seats = new java.util.ArrayList<>();
        for (int r = 1; r <= section.getRowCount(); r++) {
            for (int c = 1; c <= section.getColCount(); c++) {
                seats.add(VenueSeat.create(section.getId(), r, c));
            }
        }
        return seats;
    }

    /**
     * 공연장 소유자 검증.
     * HOST는 자신이 생성한 공연장만 수정·삭제할 수 있다.
     * ADMIN은 Controller에서 이미 통과했으므로 여기선 createdBy만 비교한다.
     */
    private void checkOwner(Venue venue, UUID requesterId) {
        if (!venue.getCreatedBy().equals(requesterId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
