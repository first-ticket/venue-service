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
     */
    @Column(nullable = false, columnDefinition = "uuid")
    private UUID sectionId;

    /**
     * 행 번호 (1부터 시작).
     * 컬럼명 row_num: PostgreSQL에서 row는 예약어.
     */
    @Column(name = "row_num", nullable = false)
    private int row;

    /** 열 번호 (1부터 시작). */
    @Column(name = "col_num", nullable = false)
    private int col;

    /**
     * 물리적 좌석 상태.
     * 예매 가능 여부(BookingSeat.status)와 별개로 관리한다.
     * BROKEN 좌석은 Application 계층에서 isAvailable() 선검증으로 예매 불가 처리된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PhysicalStatus physicalStatus;

    // ── 정적 팩토리 메서드 ──────────────────────────────────────────────

    /**
     * SEATED 좌석 생성.
     * CreateSectionUseCase에서 rowCount × colCount 개 일괄 호출한다.
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

    // ── 비즈니스 메서드 ─────────────────────────────────────────────────

    /**
     * 좌석을 파손 상태로 변경한다 (V-05).
     * BROKEN 상태의 좌석은 Application 계층에서 예매 불가 처리된다.
     */
    public void markBroken() {
        if (this.physicalStatus == PhysicalStatus.BROKEN) {
            throw new VenueException(VenueErrorCode.SEAT_ALREADY_BROKEN);
        }
        this.physicalStatus = PhysicalStatus.BROKEN;
    }

    /** 파손된 좌석을 사용 가능 상태로 복구한다. */
    public void restore() {
        if (this.physicalStatus == PhysicalStatus.AVAILABLE) {
            throw new VenueException(VenueErrorCode.SEAT_ALREADY_AVAILABLE);
        }
        this.physicalStatus = PhysicalStatus.AVAILABLE;
    }

    /**
     * 예매 선점 전 BROKEN 여부 확인.
     * Application 계층에서 BookingSeat 생성 전에 반드시 호출해야 한다.
     */
    public boolean isAvailable() {
        return this.physicalStatus == PhysicalStatus.AVAILABLE;
    }
}
