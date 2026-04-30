package com.firstticket.venueservice.infrastructure.persistence;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Repository;

import com.firstticket.venueservice.domain.QSection;
import com.firstticket.venueservice.domain.QVenue;
import com.firstticket.venueservice.domain.query.PagedResult;
import com.firstticket.venueservice.domain.query.VenueQueryRepository;
import com.firstticket.venueservice.domain.query.VenueSearchSpec;
import com.firstticket.venueservice.domain.query.VenueSummaryData;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * 공연장 목록 조회 QueryDSL 구현체.
 *
 * CVE-2024-49203 대응:
 * PathBuilder.get(userInput) 패턴 금지.
 * 정렬 필드는 toOrderSpecifier() switch 화이트리스트로만 처리한다.
 *
 * &#064;QueryProjection  미사용:
 * 도메인 DTO(VenueSummaryData)가 인프라 의존성을 갖지 않도록
 * Projections.constructor()로 인스턴스를 생성한다.
 */
@Repository
@RequiredArgsConstructor
public class VenueQueryRepositoryImpl implements VenueQueryRepository {

    private final JPAQueryFactory queryFactory;

    private static final QVenue venue = QVenue.venue;
    private static final QSection section = QSection.section;

    @Override
    public PagedResult<VenueSummaryData> findBySpec(VenueSearchSpec spec) {

        List<VenueSummaryData> content = buildBaseQuery(spec)
            .select(Projections.constructor(VenueSummaryData.class,
                venue.id,
                venue.name,
                venue.address,
                // soft delete된 구역 제외한 구역 수
                section.count().intValue()
            ))
            .groupBy(venue.id, venue.name, venue.address)
            .orderBy(toOrderSpecifier(spec.sortField(), spec.direction()))
            .offset(spec.getOffset())
            .limit(spec.pageSize())
            .fetch();

        // count 쿼리도 동일한 join·where 조건 사용
        // content 쿼리와 join/where가 일치해야 페이지 정보가 정확하다
        Long total = buildBaseQuery(spec)
            .select(venue.countDistinct())
            .fetchOne();

        return PagedResult.of(
            content,
            total != null ? total : 0L,
            spec.pageNumber(),
            spec.pageSize()
        );
    }

    // ------- 공통 베이스 쿼리 -----------------------------------

    /**
     * content 쿼리와 count 쿼리의 join·where 조건을 공통으로 관리한다.
     * 두 쿼리 간 조건 불일치로 인한 페이지 정보 오류를 방지한다.
     */
    private JPAQuery<?> buildBaseQuery(VenueSearchSpec spec) {
        return queryFactory
            .from(venue)
            .leftJoin(section)
            .on(section.venue.id.eq(venue.id)
                .and(section.deletedAt.isNull()))   // soft delete된 구역 제외
            .where(
                deletedAtIsNull(),
                keywordContains(spec.keyword())
            );
    }

    // ----- 정렬 — 화이트리스트 방식 (CVE-2024-49203 대응) -----------------

    /**
     * 허용된 필드만 switch 케이스로 정의.
     *
     * sortField null 처리:
     * VenueSearchSpec.sortField는 nullable이므로
     * null 입력 시 switch NPE 방지를 위해 null을 "createdAt"으로 정규화한다.
     *
     * 새 정렬 필드 추가 시 반드시 이 메서드에 케이스를 추가해야 한다.
     */
    private OrderSpecifier<?> toOrderSpecifier(String sortField, String direction) {
        // null 정규화 — switch에 null이 들어오면 NPE 발생
        String field = Objects.toString(sortField, "createdAt");
        boolean isAsc = "asc".equalsIgnoreCase(direction);

        return switch (field) {
            case "name" -> isAsc ? venue.name.asc() : venue.name.desc();
            case "createdAt" -> isAsc ? venue.createdAt.asc() : venue.createdAt.desc();
            // 알 수 없는 값은 최신순으로 fallback
            default -> venue.createdAt.desc();
        };
    }

    // -------- where 조건 헬퍼 -------------------

    /** soft delete된 공연장 제외 */
    private BooleanExpression deletedAtIsNull() {
        return venue.deletedAt.isNull();
    }

    /**
     * 이름·주소 키워드 검색.
     * 이름 또는 주소 중 하나라도 키워드를 포함하면 반환한다.
     */
    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null)
            return null;
        return venue.name.containsIgnoreCase(keyword)
            .or(venue.address.containsIgnoreCase(keyword));
    }
}
