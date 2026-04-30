package com.firstticket.venueservice.domain.query;

/**
 * 공연장 목록 조회 전용 Repository 인터페이스.
 * 도메인 계층에 위치하므로 Spring Data 의존성을 갖지 않는다.
 * Page 대신 순수 도메인 페이지네이션 VO(PagedResult)를 반환한다.
 */
public interface VenueQueryRepository {

    PagedResult<VenueSummaryData> findBySpec(VenueSearchSpec spec);
}
