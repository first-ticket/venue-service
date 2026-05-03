package com.firstticket.venueservice.domain.service;

import java.util.UUID;

/**
 * Program Service로부터 공연장 연관 프로그램 존재 여부를 확인하는 도메인 서비스 인터페이스.
 * 구현체는 infrastructure/provider/ProgramProviderImpl에 위치한다.
 */
public interface ProgramProvider {

    /**
     * 해당 공연장에 등록된 프로그램이 존재하는지 확인한다.
     * 존재하면 true, 없으면 false를 반환한다.
     * Program Service 호출 실패 시 true를 반환하여 삭제를 차단한다 (fail-fast).
     */
    boolean hasProgramsForVenue(UUID venueId);
}
