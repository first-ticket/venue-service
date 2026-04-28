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

/**
 * 구역(Section) 도메인 엔티티 — Venue 애그리거트 하위 엔티티
 *
 * Section은 Venue 없이 독립적으로 존재할 수 없다.
 * 반드시 Venue.addSection()을 통해서만 생성되며,
 * SectionRepository를 별도로 두지 않는다.
 *
 * 수정 API 없음:
 * rowCount·colCount 변경은 VenueSeat 전체 재생성을 수반하므로
 * 삭제 후 재등록 방식으로 처리
 *
 * 타입별 필드 사용 규칙:
 * - SEATED   : rowCount, colCount 사용 / capacity null
 *              Section 등록 시 rowCount × colCount 개 VenueSeat 자동 생성 (V-02)
 * - STANDING : capacity 사용 / rowCount, colCount null
 *              프로그램 등록 시 ScheduleSectionCapacity로 회차별 인원 관리
 * - FREE     : capacity 사용 / rowCount, colCount null
 *              STANDING과 동일한 방식
 */
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
     * SEATED 구역 생성.
     * Package-private: Venue.addSection()을 통해서만 생성
     * 직접 호출 시 Venue 애그리거트 불변식이 깨질 수 있음에 주의!!!!
     *
     * 이후 Application 계층에서
     * Section 저장 후 rowCount × colCount 개의 VenueSeat을 일괄 생성한다 (V-02).
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
     * STANDING 구역 생성.
     * Package-private: Venue.addSection()을 통해서만 생성
     *
     * VenueSeat을 생성하지 않음
     * 프로그램 등록 시 ScheduleSectionCapacity로 회차별 인원을 지정하며,
     * 이 capacity가 상한선 역할을 함
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
     * FREE 구역 생성.
     * Package-private: Venue.addSection()을 통해서만 생성
     *
     * STANDING과 구현이 동일하지만 타입별 확장 가능성을 위해 분리 유지.
     * PriceGrade에서 FREE 타입은 sectionId를 null로 설정
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
     * - STANDING·FREE : capacity (상한선 기준)
     *
     * rowCount·colCount·capacity는 각 정적 팩토리 생성 메서드에서 보장되지만
     * 방어적으로 추가 null 검증을 수행한다.
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
     * 프로그램 등록 시 요청 인원이 구역 수용 상한을 초과하는지 검증
     * SEATED는 고정 좌석 기반이므로 해당 검증 대상 아님!!!
     *
     * 호출처: Application 계층
     * VenueClient를 통해 Section 정보를 조회한 뒤 이 메서드로 검증
     *
     * @param requestedCapacity 프로그램에서 요청하는 구역별 인원
     * @throws VenueException requestedCapacity <= 0 이면 INVALID_CAPACITY
     * @throws VenueException requestedCapacity > this.capacity 이면 CAPACITY_EXCEEDED
     */
    public void validateCapacityLimit(int requestedCapacity) {
        // SEATED는 VenueSeat 기반이므로 인원 상한 검증 불필요
        if (type == SeatType.SEATED)
            return;

        // 데이터 무결성 문제 — createStanding/createFree에서 보장되지만 방어적 검증
        if (this.capacity == null) {
            throw new VenueException(VenueErrorCode.INVALID_CAPACITY);
        }

        // requestedCapacity <= 0: 입력값 오류, 요청 인원 0 이하 차단
        if (requestedCapacity <= 0) {
            throw new VenueException(VenueErrorCode.INVALID_CAPACITY);
        }

        // requestedCapacity > this.capacity: 공연장 구역 수용 상한 초과 차단
        if (requestedCapacity > this.capacity) {
            throw new VenueException(VenueErrorCode.CAPACITY_EXCEEDED);
        }
    }

    // ------ private 검증 -------------

    /**
     * 구역명 null/blank 검증.
     * 세 팩토리 메서드에서 공통으로 사용
     */
    private static void validateSectionName(String name) {
        if (name == null || name.isBlank()) {
            throw new VenueException(VenueErrorCode.INVALID_SECTION_NAME);
        }
    }

    /**
     * venue null 검증.
     * Section은 반드시 Venue에 속해야 한다는 불변식을 생성 시점에 강제
     * DB flush 시점이 아닌 세 팩토리 메서드 호출 시점에 즉시 차단
     */
    private static void validateVenue(Venue venue) {
        if (venue == null) {
            throw new VenueException(VenueErrorCode.INVALID_VENUE);
        }
    }
}
