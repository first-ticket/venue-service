package com.firstticket.venueservice.domain;

import java.util.UUID;

import com.firstticket.common.persistence.BaseUserEntity;
import com.firstticket.venueservice.domain.exception.VenueErrorCode;
import com.firstticket.venueservice.domain.exception.VenueException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Section은 Venue 없이 독립적으로 존재할 수 없음
 * Venue.addSection()을 통해서만 생성되며,
 * SectionRepository를 별도로 두지 않음
 *
 * 수정 API 없음:
 * rowCount·colCount 변경은 VenueSeat 전체 재생성을 수반하므로
 * 삭제 후 재등록 방식으로 처리
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

    /**
     * Venue 애그리거트 루트 참조.
     * Section은 Venue 없이 독립적으로 존재할 수 없음
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * rowCount × colCount = VenueSeat 자동 생성 수 (V-02).
     * Section 수정(행·열 변경)은 VenueSeat 전체 재생성을 수반하므로
     * 수정 API 없이 삭제 후 재등록 방식으로 처리
     */
    @Column(nullable = false)
    private int rowCount;

    @Column(nullable = false)
    private int colCount;

    // --- 정적 팩토리 메서드--------
    /**
     * Package-private: Venue.addSection()을 통해서만 생성
     * 직접 호출 시 Venue 애그리거트 불변식이 깨질 수 있음
     */
    static Section create(Venue venue, String name, int rowCount, int colCount) {
        validateSectionInfo(name, rowCount, colCount);
        return new Section(null, venue, name, rowCount, colCount);
    }

    // --- 비즈니스 메서드 -------

    /** VenueSeat 자동 생성 수 계산 시 사용 (V-02). */
    public int getSeatCount() {
        return rowCount * colCount;
    }

    // --- private 검증 ------

    private static void validateSectionInfo(String name, int rowCount, int colCount) {
        if (name == null || name.isBlank()) {
            throw new VenueException(VenueErrorCode.INVALID_SECTION_NAME);
        }
        // rowCount, colCount는 1 이상이어야 한다
        if (rowCount <= 0 || colCount <= 0) {
            throw new VenueException(VenueErrorCode.INVALID_SEAT_COUNT);
        }
    }
}
