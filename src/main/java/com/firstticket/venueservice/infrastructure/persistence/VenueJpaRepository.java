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
     * soft delete 제외 존재 여부 확인.
     * 기본 existsById()는 deletedAt을 무시하므로 명시적 쿼리로 대체한다.
     * deletedAt이 있는 레코드는 존재하지 않는 것으로 간주한다.
     */
    @Query("""
        SELECT COUNT(v) > 0 FROM Venue v
        WHERE v.id = :id
          AND v.deletedAt IS NULL
        """)
    boolean existsActiveById(@Param("id") UUID id);

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
