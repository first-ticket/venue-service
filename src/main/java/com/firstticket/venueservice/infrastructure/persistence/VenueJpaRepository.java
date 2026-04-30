package com.firstticket.venueservice.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.firstticket.venueservice.domain.Venue;

/**
 * Spring Data JPA Repository.
 * 도메인 계층에 직접 노출하지 않는다.
 * VenueRepositoryImpl을 통해서만 접근한다.
 */
public interface VenueJpaRepository extends JpaRepository<Venue, UUID> {

    /**
     * soft delete 제외 단건 조회.
     */
    @Query("SELECT v FROM Venue v WHERE v.id = :id AND v.deletedAt IS NULL")
    Optional<Venue> findActiveById(@Param("id") UUID id);

    /**
     * sections 컬렉션 JOIN FETCH 조회.
     * N+1 방지. addSection·removeSection 유스케이스에서 사용.
     *
     * DISTINCT 필요 이유:
     * LEFT JOIN FETCH로 컬렉션을 로딩하면 Section 수만큼 Venue 행이 복제된다.
     * DISTINCT로 Venue 레벨 중복을 제거해야 단건 반환이 보장된다.
     */
    @Query("""
        SELECT DISTINCT v FROM Venue v
        LEFT JOIN FETCH v.sections s
        WHERE v.id = :id
          AND v.deletedAt IS NULL
        """)
    Optional<Venue> findByIdWithSections(@Param("id") UUID id);
}
