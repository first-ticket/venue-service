package com.firstticket.venueservice.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * VenueSeat 독립 애그리거트 Repository 인터페이스.
 * VenueSeat은 Venue 애그리거트와 분리된 독립 애그리거트이므로
 * 별도 Repository를 둔다.
 */
public interface VenueSeatRepository {

    /** 좌석 단건 저장 */
    VenueSeat save(VenueSeat venueSeat);

    /**
     * 좌석 일괄 저장.
     * SEATED Section 등록 시 rowCount × colCount 개 일괄 생성에 사용한다.
     */
    List<VenueSeat> saveAll(List<VenueSeat> venueSeats);

    /** ID로 단건 조회. soft delete된 좌석은 반환하지 않는다. */
    Optional<VenueSeat> findById(UUID id);

    /**
     * 구역 ID로 해당 구역의 모든 좌석 조회.
     * soft delete된 좌석은 제외한다.
     */
    List<VenueSeat> findBySectionId(UUID sectionId);

    /**
     * 구역 ID로 해당 구역의 모든 좌석 삭제.
     * Section 삭제 시 연관 VenueSeat 일괄 삭제에 사용한다.
     * VenueSeat은 독립 애그리거트이므로 cascade 삭제가 없어
     * 명시적으로 처리해야 한다.
     */
    void deleteAllBySectionId(UUID sectionId);
}
