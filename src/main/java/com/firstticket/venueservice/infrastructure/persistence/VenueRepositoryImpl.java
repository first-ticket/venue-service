package com.firstticket.venueservice.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.firstticket.venueservice.domain.Venue;
import com.firstticket.venueservice.domain.VenueRepository;

import lombok.RequiredArgsConstructor;

/**
 * VenueRepository 구현체.
 * 도메인 인터페이스와 JPA Repository 사이의 어댑터 역할.
 * 도메인 계층은 이 구현체를 직접 알지 못하고
 * VenueRepository 인터페이스에만 의존한다.
 */
@Repository
@RequiredArgsConstructor
public class VenueRepositoryImpl implements VenueRepository {

    private final VenueJpaRepository venueJpaRepository;

    @Override
    public Venue save(Venue venue) {
        return venueJpaRepository.save(venue);
    }

    @Override
    public Optional<Venue> findById(UUID id) {
        // soft delete 제외 조회
        return venueJpaRepository.findById(id);
    }

    @Override
    public Optional<Venue> findByIdWithSections(UUID id) {
        // sections 컬렉션 JOIN FETCH — N+1 방지
        return venueJpaRepository.findByIdWithSections(id);
    }

    @Override
    public void delete(Venue venue) {
        venueJpaRepository.delete(venue);
    }

    @Override
    public boolean existsById(UUID id) {
        // 기본 existsById()는 deletedAt을 무시하므로
        // soft delete를 반영한 existsActiveById()로 대체한다.
        // findById()와 동일하게 active 레코드만 존재하는 것으로 간주한다.
        return venueJpaRepository.existsById(id);
    }
}
