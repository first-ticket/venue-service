package com.firstticket.venueservice.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.firstticket.venueservice.domain.VenueSeat;
import com.firstticket.venueservice.domain.VenueSeatRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * VenueSeatRepository 구현체.
 * 도메인 인터페이스와 JPA Repository 사이의 어댑터 역할.
 */
@Repository
@RequiredArgsConstructor
public class VenueSeatRepositoryImpl implements VenueSeatRepository {

    private final VenueSeatJpaRepository venueSeatJpaRepository;

    @Override
    public VenueSeat save(VenueSeat venueSeat) {
        return venueSeatJpaRepository.save(venueSeat);
    }

    @Override
    public List<VenueSeat> saveAll(List<VenueSeat> venueSeats) {
        return venueSeatJpaRepository.saveAll(venueSeats);
    }

    @Override
    public Optional<VenueSeat> findById(UUID id) {
        return venueSeatJpaRepository.findById(id);
    }

    @Override
    public List<VenueSeat> findBySectionId(UUID sectionId) {
        return venueSeatJpaRepository.findBySectionId(sectionId);
    }

    @Override
    @Transactional
    public void deleteAllBySectionId(UUID sectionId) {
        // @Modifying 쿼리는 트랜잭션 컨텍스트 안에서 실행되어야 한다.
        // JpaRepository 레벨에도 @Transactional이 있지만
        // 구현체 레벨에서도 명시하여 호출 경로 전체의 트랜잭션을 보장한다.
        venueSeatJpaRepository.deleteAllBySectionId(sectionId);
    }
}
