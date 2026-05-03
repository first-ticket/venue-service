package com.firstticket.venueservice.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.firstticket.venueservice.domain.Section;

/**
 * Section 조회 전용 JPA Repository.
 * Section은 Venue 애그리거트 하위 엔티티이므로
 * 도메인 계층에 SectionRepository를 두지 않는다.
 * 내부 API(VenueInternalController)에서 Section 단건 조회가 필요한 경우에만 사용한다.
 */
public interface SectionJpaRepository extends JpaRepository<Section, UUID> {

    /**
     * soft delete 제외 단건 조회.
     */
    @NonNull
    @Override
    @Query("SELECT s FROM Section s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<Section> findById(@NonNull @Param("id") UUID id);
}
