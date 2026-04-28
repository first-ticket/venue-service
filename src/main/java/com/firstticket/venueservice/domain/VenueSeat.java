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
 * SEATED 타입 전용 물리 고정 좌석.
 * STANDING·FREE는 Section.capacity 기반으로 BookingSeat을 생성하므로
 * VenueSeat이 필요하지 않다.
 *
 * 독립 애그리거트 분리 이유:
 * - 대형 공연장 기준 수천 개의 좌석을 Venue 로딩 시 함께 불러오면 성능 문제 발생
 * - 예매 선점(BookingSeat 생성) 시 개별 좌석 단위로 빠르게 조회·처리해야 함
 *
 * Section 참조:
 * - sectionId 값으로만 참조 (객체 참조 아님)
 * - 독립 애그리거트이므로 Section과 FK를 갖지 않는다
 *
 * 생성 시점:
 * - SEATED Section 등록 시 CreateSectionUseCase에서
 *   rowCount × colCount 개 일괄 생성 (V-02)
 * - 생성 후 physicalStatus 기본값은 AVAILABLE (V-05)
 */
@Entity
@Table(name = "p_venue_seat",
    uniqueConstraints = @UniqueConstraint(
        // SEATED 전용: 동일 구역 내 (row, col) 중복 방지 (S-12)
        name = "uk_seat_section_row_col",
        columnNames = {"section_id", "row_num", "col_num"}
    )
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
     * SEATED Section ID 참조 (값만 보관).
     * 독립 애그리거트이므로 Section 객체를 직접 참조하지 않는다.
     * 구역 단위 조회가 필요한 경우 sectionId로 쿼리한다.
     */
    @Column(nullable = false, columnDefinition = "uuid")
    private UUID sectionId;

    /**
     * 행 번호 (1부터 시작).
     * 컬럼명 row_num: PostgreSQL에서 row는 예약어이므로 변경.
     */
    @Column(name = "row_num", nullable = false)
    private int row;

    /** 열 번호 (1부터 시작). */
    @Column(name = "col_num", nullable = false)
    private int col;

    /**
     * 물리적 좌석 상태.
     * 예매 가능 여부(BookingSeat.status)와 별개로 관리
     * BROKEN 좌석은 Application 계층에서 isAvailable() 선검증으로
     * BookingSeat 생성 전에 예매 불가 처리됨
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PhysicalStatus physicalStatus;

    // ------ 정적 팩토리 메서드 -----------------------------

    /**
     * SEATED 좌석 생성.
     * Application 계층에서
     * rowCount × colCount 개 일괄 호출한다.
     *
     * @param sectionId SEATED Section의 ID
     * @param row       행 번호 (1 이상)
     * @param col       열 번호 (1 이상)
     */
    public static VenueSeat create(UUID sectionId, int row, int col) {
        if (sectionId == null) {
            throw new VenueException(VenueErrorCode.INVALID_SECTION_ID);
        }
        if (row <= 0 || col <= 0) {
            throw new VenueException(VenueErrorCode.INVALID_SEAT_POSITION);
        }
        return new VenueSeat(null, sectionId, row, col, PhysicalStatus.AVAILABLE);
    }

    // ---- 비즈니스 메서드 ----------------------------------------

    /**
     * 좌석을 파손 상태로 변경 (V-05).
     * BROKEN 상태의 좌석은 Application 계층에서 예매 불가 처리됨
     * isAvailable() 선검증으로 BookingSeat 생성을 차단
     *
     * @throws VenueException 이미 BROKEN 상태인 경우 SEAT_ALREADY_BROKEN
     */
    public void markBroken() {
        if (this.physicalStatus == PhysicalStatus.BROKEN) {
            throw new VenueException(VenueErrorCode.SEAT_ALREADY_BROKEN);
        }
        this.physicalStatus = PhysicalStatus.BROKEN;
    }

    /**
     * 파손된 좌석을 사용 가능 상태로 복구함
     *
     * @throws VenueException 이미 AVAILABLE 상태인 경우 SEAT_ALREADY_AVAILABLE
     */
    public void restore() {
        if (this.physicalStatus == PhysicalStatus.AVAILABLE) {
            throw new VenueException(VenueErrorCode.SEAT_ALREADY_AVAILABLE);
        }
        this.physicalStatus = PhysicalStatus.AVAILABLE;
    }

    /**
     * 예매 선점 전 BROKEN 여부 확인.
     * Application 계층에서 BookingSeat 생성 전에 반드시 호출해야 함
     *
     * @return physicalStatus가 AVAILABLE이면 true
     */
    public boolean isAvailable() {
        return this.physicalStatus == PhysicalStatus.AVAILABLE;
    }
}
