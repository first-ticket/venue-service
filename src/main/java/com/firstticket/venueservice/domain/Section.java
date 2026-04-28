package com.firstticket.venueservice.domain;

import java.util.UUID;

import com.firstticket.common.persistence.BaseUserEntity;
import com.firstticket.venueservice.domain.exception.VenueErrorCode;
import com.firstticket.venueservice.domain.exception.VenueException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_section")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Section extends BaseUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 구역 타입.
     * - SEATED   : 물리 고정 좌석 구역 → rowCount, colCount 사용
     * - STANDING : 스탠딩 구역 → capacity 사용 (프로그램마다 인원 달라짐)
     * - FREE     : 자유 입장 구역 → capacity 사용 (프로그램마다 인원 달라짐)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeatType type;

    /**
     * SEATED 전용: 행 수.
     * VenueSeat 자동 생성 시 사용 (rowCount × colCount).
     * STANDING·FREE는 null.
     */
    @Column
    private Integer rowCount;

    /**
     * SEATED 전용: 열 수.
     * VenueSeat 자동 생성 시 사용 (rowCount × colCount).
     * STANDING·FREE는 null.
     */
    @Column
    private Integer colCount;

    /**
     * STANDING·FREE 전용: 최대 수용 인원 상한선.
     * 프로그램 등록 시 이 값을 초과하는 인원 지정 불가.
     * SEATED는 rowCount × colCount로 계산하므로 null.
     */
    @Column
    private Integer capacity;

    // -------- 타입별 정적 팩토리 메서드 ---------------------------

    /**
     * Package-private: Venue.addSection()을 통해서만 생성.
     * SEATED 구역: rowCount × colCount 개의 VenueSeat이 자동 생성된다 (V-02).
     */
    static Section createSeated(Venue venue, String name, int rowCount, int colCount) {
        validateVenue(venue);
        validateSectionName(name);
        if (rowCount <= 0 || colCount <= 0) {
            throw new VenueException(VenueErrorCode.INVALID_SEAT_COUNT);
        }
        return new Section(null, venue, name, SeatType.SEATED, rowCount, colCount, null);
    }

    /**
     * Package-private: Venue.addSection()을 통해서만 생성.
     * STANDING 구역: VenueSeat 없이 capacity만 관리.
     * 프로그램 등록 시 이 capacity를 초과할 수 없다.
     */
    static Section createStanding(Venue venue, String name, int capacity) {
        validateVenue(venue);
        validateSectionName(name);
        if (capacity <= 0) {
            throw new VenueException(VenueErrorCode.INVALID_CAPACITY);
        }
        return new Section(null, venue, name, SeatType.STANDING, null, null, capacity);
    }

    /**
     * Package-private: Venue.addSection()을 통해서만 생성.
     * FREE 구역: STANDING과 동일하게 capacity만 관리.
     * STANDING과 구현이 동일하지만 타입별 확장 가능성을 위해 분리 유지.
     * - PriceGrade에서 FREE 타입은 sectionId를 null로 설정한다.
     */
    static Section createFree(Venue venue, String name, int capacity) {
        validateVenue(venue);
        validateSectionName(name);
        if (capacity <= 0) {
            throw new VenueException(VenueErrorCode.INVALID_CAPACITY);
        }
        return new Section(null, venue, name, SeatType.FREE, null, null, capacity);
    }

    // --- 비즈니스 메서드 ----------------------------

    /**
     * 구역 내 좌석 수 반환.
     * - SEATED   : rowCount × colCount
     * - STANDING·FREE : capacity
     */
    public int getSeatCount() {
        return switch (type) {
            case SEATED -> {
                // rowCount, colCount는 createSeated()에서 보장되지만 방어적으로 검증
                if (rowCount == null || colCount == null) {
                    throw new VenueException(VenueErrorCode.INVALID_SEAT_COUNT);
                }
                yield rowCount * colCount;
            }
            case STANDING, FREE -> {
                if (capacity == null) {
                    throw new VenueException(VenueErrorCode.INVALID_CAPACITY);
                }
                yield capacity;
            }
        };
    }

    /**
     * 프로그램 등록 시 요청한 인원이 구역 수용 상한을 초과하는지 검증.
     * SEATED는 고정 좌석이므로 이 검증 대상이 아님
     */
    public void validateCapacityLimit(int requestedCapacity) {
        if (type == SeatType.SEATED)
            return;

        // 데이터 무결성 문제 — createStanding/createFree에서 보장되지만 방어적 검증
        if (this.capacity == null) {
            throw new VenueException(VenueErrorCode.INVALID_CAPACITY);
        }

        // requestedCapacity <= 0: 입력값 오류
        if (requestedCapacity <= 0) {
            throw new VenueException(VenueErrorCode.INVALID_CAPACITY);
        }

        // requestedCapacity > this.capacity: 공연장 상한 초과
        if (requestedCapacity > this.capacity) {
            throw new VenueException(VenueErrorCode.CAPACITY_EXCEEDED);
        }
    }

    // ------ private 검증 -------------

    private static void validateSectionName(String name) {
        if (name == null || name.isBlank()) {
            throw new VenueException(VenueErrorCode.INVALID_SECTION_NAME);
        }
    }

    private static void validateVenue(Venue venue) {
        if (venue == null) {
            throw new VenueException(VenueErrorCode.INVALID_VENUE);
        }
    }
}
