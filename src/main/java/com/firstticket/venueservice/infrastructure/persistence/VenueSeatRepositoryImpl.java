package com.firstticket.venueservice.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.firstticket.venueservice.domain.VenueSeat;
import com.firstticket.venueservice.domain.VenueSeatRepository;

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
    public void deleteAllBySectionId(UUID sectionId) {
        venueSeatJpaRepository.deleteAllBySectionId(sectionId);
    }
}
