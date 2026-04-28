package com.firstticket.venueservice.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.firstticket.common.persistence.BaseUserEntity;
import com.firstticket.venueservice.domain.exception.VenueErrorCode;
import com.firstticket.venueservice.domain.exception.VenueException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공연장(Venue) 도메인 엔티티 — Aggregate Root
 *
 * Venue 애그리거트는 Venue + Section으로 구성.
 * Section은 반드시 Venue를 통해서만 추가·삭제할 수 있으며,
 * SectionRepository를 별도로 두지 않음
 *
 * VenueSeat은 독립 애그리거트로 분리되어 있으므로
 * Venue 애그리거트에 포함되지 않음
 * VenueSeat 자동 생성(V-02)은 Application 계층에서 처리
 */
@Entity
@Table(name = "p_venue")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue extends BaseUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    /**
     * Venue 애그리거트 루트가 Section 컬렉션을 직접 관리한다.
     * Section은 Venue를 통해서만 추가·삭제되어야 하며,
     * 외부에서 SectionRepository로 직접 저장하지 않는다.
     *
     * orphanRemoval = true: sections에서 제거된 Section은 DB에서도 자동 삭제된다.
     * cascade = ALL: Venue 저장 시 Section도 함께 저장된다.
     */
    @OneToMany(mappedBy = "venue",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY)
    private List<Section> sections;

    // ---- 정적 팩토리 메서드 ----------

    /**
     * 공연장 생성.
     * name, address는 필수값이므로 null/blank 시 즉시 예외를 던진다.
     * sections는 빈 컬렉션으로 초기화된다.
     */
    public static Venue create(String name, String address) {
        validateVenueInfo(name, address);
        return new Venue(null, name, address, new ArrayList<>());
    }

    // ---- 비즈니스 메서드 -------------

    /**
     * 구역 추가.
     * 타입에 따라 Section 생성 방식이 달라진다.
     * - SEATED   : rowCount, colCount 필수
     *              Application 계층에서 VenueSeat 자동 생성 (V-02)
     * - STANDING : capacity 필수 (프로그램 등록 시 이 값을 초과할 수 없다)
     * - FREE     : capacity 필수 (STANDING과 동일한 방식)
     *
     * 타입별 필수 파라미터 null 검증을 먼저 수행하여
     * Section 팩토리의 언박싱(Integer → int) 시 NPE를 방지한다.
     */
    public Section addSection(SeatType type, String name,
        Integer rowCount, Integer colCount,
        Integer capacity) {
        validateNotDeleted();
        // type null 선검증
        if (type == null) {
            throw new VenueException(VenueErrorCode.INVALID_SECTION_TYPE);
        }

        // 타입별 필수 파라미터 null 선검증
        // 언박싱(Integer → int) 시 NPE 방지
        if (type == SeatType.SEATED && (rowCount == null || colCount == null)) {
            throw new VenueException(VenueErrorCode.INVALID_SEAT_COUNT);
        }
        if ((type == SeatType.STANDING || type == SeatType.FREE) && capacity == null) {
            throw new VenueException(VenueErrorCode.INVALID_CAPACITY);
        }

        Section section = switch (type) {
            case SEATED -> Section.createSeated(this, name, rowCount, colCount);
            case STANDING -> Section.createStanding(this, name, capacity);
            case FREE -> Section.createFree(this, name, capacity);
        };
        sections.add(section);
        return section;
    }

    /**
     * 구역 삭제.
     * orphanRemoval = true이므로 컬렉션에서 제거 시 DB에서도 자동 삭제
     *
     * 주의: Application 계층에서
     * 해당 Section을 참조하는 VenueSeat 존재 여부를 사전 검증 후 호출해야 함!!!!!!!!
     * VenueSeat이 남아있는 상태에서 Section을 삭제하면 데이터 정합성이 깨짐!!!!
     */
    public void removeSection(UUID sectionId) {
        validateNotDeleted();

        if (sectionId == null) {
            throw new VenueException(VenueErrorCode.INVALID_SECTION_ID);
        }
        boolean removed = sections.removeIf(
            s -> sectionId.equals(s.getId())
        );
        if (!removed) {
            throw new VenueException(VenueErrorCode.SECTION_NOT_FOUND);
        }
    }

    /**
     * 공연장 정보 수정.
     * null이면 기존 값 유지 (부분 업데이트).
     * 수정 후 최종값으로 검증을 수행
     */
    public void update(String name, String address) {
        validateNotDeleted();

        String nextName = (name != null) ? name : this.name;
        String nextAddress = (address != null) ? address : this.address;

        // 최종값으로 재검증 — null로 덮어쓰는 케이스 방지
        validateVenueInfo(nextName, nextAddress);

        this.name = nextName;
        this.address = nextAddress;
    }

    /**
     * 외부에서 sections 컬렉션을 직접 수정하지 못하도록
     * UnmodifiableList로 감싸서 반환
     */
    public List<Section> getSections() {
        return Collections.unmodifiableList(sections);
    }

    // --------- private 검증 -------------

    /**
     * 소프트 삭제 여부 확인.
     * BaseUserEntity.getDeletedAt()이 null이 아니면 삭제된 공연장으로 간주함
     * 수정·삭제 진입점에서 공통으로 호출
     */
    private void validateNotDeleted() {
        if (getDeletedAt() != null) {
            throw new VenueException(VenueErrorCode.VENUE_ALREADY_DELETED);
        }
    }

    /**
     * 공연장 필수 정보 검증.
     * create()와 update() 양쪽에서 호출하여 일관된 검증을 보장한다.
     */
    private static void validateVenueInfo(String name, String address) {
        if (name == null || name.isBlank()) {
            throw new VenueException(VenueErrorCode.INVALID_VENUE_NAME);
        }
        if (address == null || address.isBlank()) {
            throw new VenueException(VenueErrorCode.INVALID_VENUE_ADDRESS);
        }
    }
}
