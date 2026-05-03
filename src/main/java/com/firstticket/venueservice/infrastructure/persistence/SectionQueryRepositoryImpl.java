package com.firstticket.venueservice.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.firstticket.venueservice.domain.Section;
import com.firstticket.venueservice.domain.query.SectionQueryRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SectionQueryRepositoryImpl implements SectionQueryRepository {

    private final SectionJpaRepository sectionJpaRepository;

    @Override
    public Optional<Section> findById(UUID sectionId) {
        return sectionJpaRepository.findById(sectionId);
    }
}
