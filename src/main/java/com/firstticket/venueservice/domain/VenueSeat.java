package com.firstticket.venueservice.domain;

import java.util.UUID;

import com.firstticket.common.persistence.BaseUserEntity;
import com.firstticket.venueservice.domain.exception.VenueErrorCode;
import com.firstticket.venueservice.domain.exception.VenueException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공연장 좌석(VenueSeat) 도메인 엔티티 — 독립 Aggregate Root
 *
 * 분리 이유:
 * - 대형 공연장 기준 수천 개의 좌석을 Venue 로딩 시 함께 불러오면 성능 문제 발생
 * - 예매 선점(BookingSeat 생성) 시 개별 좌석 단위로 빠르게 조회·처리해야 함
 *
 * Section 참조:
 * - sectionId 값으로만 참조 (객체 참조 아님)
 * - 독립 애그리거트이므로 Section FK를 두지 않음
 *
 * 생성 시점:
 * - Section 등록 시 CreateSectionUseCase에서
 *   rowCount × colCount 개 일괄 생성 (V-02)
 * - 생성 후 physicalStatus 기본값은 AVAILABLE (V-05)
 *
 * 타입별 필드 사용 규칙:
 * - SEATED   : row, col 필수 / sequence null
 * - STANDING : sequence 필수 / row, col null
 * - FREE     : row, col, sequence 모두 null
 */
@Entity
@Table(name = "p_venue_seat",
    uniqueConstraints = {
        // SEATED: 동일 구역 내 (row, col) 중복 방지 (S-12)
        @UniqueConstraint(
            name = "uk_seat_section_row_col",
            columnNames = {"section_id", "row_num", "col_num"}),
        // STANDING: 동일 구역 내 sequence 중복 방지
        @UniqueConstraint(
            name = "uk_seat_section_sequence",
            columnNames = {"section_id", "sequence"})
    }
)
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VenueSeat extends BaseUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    /**
     * Section ID 참조 (값만 보관).
     * VenueSeat은 독립 애그리거트이므로 Section 객체를 직접 참조하지 않음
     * 구역 단위 조회가 필요한 경우 sectionId로 쿼리
     */
    @Column(nullable = false, columnDefinition = "uuid")
    private UUID sectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeatType type;

    /**
     * SEATED 전용: 행 번호 (1부터 시작).
     * 컬럼명 row_num: PostgreSQL에서 row는 예약어이므로 변경.
     * STANDING, FREE 타입은 null.
     */
    @Column(name = "row_num")
    private Integer row;

    /**
     * SEATED 전용: 열 번호 (1부터 시작).
     * STANDING, FREE 타입은 null.
     */
    @Column(name = "col_num")
    private Integer col;

    /**
     * STANDING 전용: 입장 순번 (1부터 시작).
     * SEATED, FREE 타입은 null.
     */
    @Column
    private Integer sequence;

    /**
     * 물리적 좌석 상태. 예매 가능 여부(BookingSeat.status)와 별개로 관리
     * BROKEN 좌석은 BookingSeat 생성 시 예매 불가 처리
     * (Application 계층에서 isAvailable() 선검증).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PhysicalStatus physicalStatus;

    // ----- 타입별 정적 팩토리 메서드 -------

    /**
     * SEATED 좌석 생성: row, col 필수. sequence는 null.
     * row, col은 1부터 시작하며 0 이하는 차단한다.
     */
    public static VenueSeat createSeated(UUID sectionId, int row, int col) {
        validateSectionId(sectionId);
        if (row <= 0 || col <= 0) {
            throw new VenueException(VenueErrorCode.INVALID_SEAT_POSITION);
        }
        return new VenueSeat(
            null, sectionId, SeatType.SEATED,
            row, col, null,
            PhysicalStatus.AVAILABLE
        );
    }

    /**
     * STANDING 좌석 생성: sequence 필수. row, col은 null.
     * sequence는 1부터 시작하며 0 이하는 차단한다.
     */
    public static VenueSeat createStanding(UUID sectionId, int sequence) {
        validateSectionId(sectionId);
        if (sequence <= 0) {
            throw new VenueException(VenueErrorCode.INVALID_SEAT_SEQUENCE);
        }
        return new VenueSeat(
            null, sectionId, SeatType.STANDING,
            null, null, sequence,
            PhysicalStatus.AVAILABLE
        );
    }

    /**
     * FREE 좌석 생성: row, col, sequence 모두 null.
     * 수량 기반으로만 관리된다.
     */
    public static VenueSeat createFree(UUID sectionId) {
        validateSectionId(sectionId);
        return new VenueSeat(
            null, sectionId, SeatType.FREE,
            null, null, null,
            PhysicalStatus.AVAILABLE
        );
    }

    // --- 비즈니스 메서드 ----------

    /**
     * 좌석을 파손 상태로 변경 (V-05).
     * BROKEN 상태의 좌석은 Application 계층에서 예매 불가 처리
     */
    public void markBroken() {
        if (this.physicalStatus == PhysicalStatus.BROKEN) {
            throw new VenueException(VenueErrorCode.SEAT_ALREADY_BROKEN);
        }
        this.physicalStatus = PhysicalStatus.BROKEN;
    }

    /** 파손된 좌석을 사용 가능 상태로 복구 */
    public void restore() {
        if (this.physicalStatus == PhysicalStatus.AVAILABLE) {
            throw new VenueException(VenueErrorCode.SEAT_ALREADY_AVAILABLE);
        }
        this.physicalStatus = PhysicalStatus.AVAILABLE;
    }

    /**
     * 예매 선점 전 BROKEN 여부를 확인할 때 사용
     * Application 계층에서 BookingSeat 생성 전에 반드시 호출해야 함
     */
    public boolean isAvailable() {
        return this.physicalStatus == PhysicalStatus.AVAILABLE;
    }

    //---- private 검증 --------

    /**
     * sectionId null 검증.
     * 모든 타입의 VenueSeat은 반드시 구역에 속해야 함
     */
    private static void validateSectionId(UUID sectionId) {
        if (sectionId == null) {
            throw new VenueException(VenueErrorCode.INVALID_SECTION_ID);
        }
    }
}
