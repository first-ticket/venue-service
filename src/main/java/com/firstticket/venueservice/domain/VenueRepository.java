package com.firstticket.venueservice.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Venue 애그리거트 Repository 인터페이스.
 * 도메인 계층에 위치하므로 JPA·Spring 의존성을 갖지 않는다.
 * 구현체는 infrastructure/persistence/VenueRepositoryImpl에 위치한다.
 */
public interface VenueRepository {

    /** 공연장 저장 (생성·수정) */
    Venue save(Venue venue);

    /**
     * ID로 공연장 단건 조회.
     * soft delete된 공연장은 반환하지 않는다.
     */
    Optional<Venue> findById(UUID id);

    /**
     * ID로 공연장 단건 조회 — sections 컬렉션 함께 로딩.
     * addSection·removeSection 등 구역 수정이 필요한 유스케이스에서 사용한다.
     * N+1 방지를 위해 JOIN FETCH로 구현한다.
     */
    Optional<Venue> findByIdWithSections(UUID id);

    /**
     * sectionId로 해당 구역이 속한 공연장을 조회한다.
     * 좌석 소유자 검증 시 VenueSeat → Section → Venue 경로로 사용한다.
     */
    Optional<Venue> findVenueBySectionId(UUID sectionId);

    /** 공연장 물리 삭제 */
    void delete(Venue venue);

    /** ID 존재 여부 확인 */
    boolean existsById(UUID id);
}
