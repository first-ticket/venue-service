package com.firstticket.venueservice.infrastructure.provider;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.firstticket.venueservice.domain.service.ProgramProvider;
import com.firstticket.venueservice.infrastructure.client.ProgramClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramProviderImpl implements ProgramProvider {

    private final ProgramClient programClient;

    /**
     * fail-fast 정책:
     * Program Service 호출 실패 시 true를 반환하여 삭제를 차단한다.
     * 네트워크 장애로 인해 프로그램이 있는 공연장이 삭제되는 것을 방지한다.
     */
    @Override
    public boolean hasProgramsForVenue(UUID venueId) {
        try {
            return programClient.hasProgramsForVenue(venueId);
        } catch (feign.FeignException.NotFound e) {
            log.warn("[ProgramProvider] 프로그램 존재 여부 조회 실패 — venueId: {}, error: {}",
                venueId, e.getMessage());
            return true;  // fail-fast: 조회 실패 시 삭제 차단
        } catch (feign.FeignException e) {
            // 그 외 Feign 오류 → 인프라 예외 propagate
            throw e;
        }
    }
}
