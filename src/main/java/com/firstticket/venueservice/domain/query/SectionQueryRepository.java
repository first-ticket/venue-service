package com.firstticket.venueservice.domain.query;

import java.util.Optional;
import java.util.UUID;

import com.firstticket.venueservice.domain.Section;

/**
 * Section 단건 조회 전용 도메인 인터페이스.
 * Section은 Venue 애그리거트 하위 엔티티이므로 도메인 계층에
 * SectionRepository를 두지 않는다.
 * 내부 API(VenueInternalController)에서 Section 단건 조회가 필요한 경우에만 사용한다.
 */
public interface SectionQueryRepository {

    /**
     * soft delete 제외 단건 조회.
     */
    Optional<Section> findById(UUID sectionId);
}
