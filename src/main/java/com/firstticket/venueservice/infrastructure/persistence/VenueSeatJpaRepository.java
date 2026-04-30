package com.firstticket.venueservice.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.firstticket.venueservice.domain.VenueSeat;

import jakarta.transaction.Transactional;
import lombok.NonNull;

/**
 * Spring Data JPA Repository.
 * 도메인 계층에 직접 노출하지 않는다.
 * VenueSeatRepositoryImpl을 통해서만 접근한다.
 */
public interface VenueSeatJpaRepository extends JpaRepository<VenueSeat, UUID> {

    /**
     * soft delete 제외 단건 조회.
     * 기본 JpaRepository.findById()는 deletedAt을 무시하므로
     * 명시적 쿼리로 재정의한다.
     */
    @Query("""
        SELECT vs FROM VenueSeat vs
        WHERE vs.id = :id
          AND vs.deletedAt IS NULL
        """)
    Optional<VenueSeat> findActiveById(@Param("id") @NonNull UUID id);

    /**
     * 구역 ID로 전체 좌석 조회.
     * soft delete된 좌석 제외.
     */
    @Query("""
        SELECT vs FROM VenueSeat vs
        WHERE vs.sectionId = :sectionId
          AND vs.deletedAt IS NULL
        """)
    List<VenueSeat> findBySectionId(@Param("sectionId") UUID sectionId);

    /**
     * 구역 ID로 전체 좌석 일괄 삭제.
     * Section 삭제 시 연관 VenueSeat 처리.
     *
     * VenueSeat은 독립 애그리거트이므로 cascade 삭제 없이 명시적으로 처리한다.
     *
     * &#064;Transactional  필요 이유:
     * &#064;Modifying  쿼리는 반드시 트랜잭션 컨텍스트 안에서 실행되어야 한다.
     * 트랜잭션 없이 실행하면 TransactionRequiredException이 발생하고
     * 1차 캐시와 DB 상태가 불일치할 수 있다.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM VenueSeat vs WHERE vs.sectionId = :sectionId")
    void deleteAllBySectionId(@Param("sectionId") UUID sectionId);
}
