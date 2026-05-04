package com.firstticket.venueservice.presentation.dto.response;

import java.util.List;
import java.util.function.Function;

import com.firstticket.venueservice.domain.query.PagedResult;

/**
 * 페이지네이션 응답 DTO.
 *
 * PagedResult 의존성:
 * 현재 PagedResult는 domain/query에 위치하나
 * presentation → domain 직접 의존을 피하려면 application 계층
 * PagedResult를 별도로 두거나 공통 모듈로 분리한다.
 */
public record PagedResponse<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int size,
    int number
) {
    public static <S, T> PagedResponse<T> from(PagedResult<S> pagedResult,
        Function<S, T> mapper) {
        return new PagedResponse<>(
            pagedResult.content().stream().map(mapper).toList(),
            pagedResult.totalElements(),
            pagedResult.totalPages(),
            pagedResult.pageSize(),
            pagedResult.pageNumber()
        );
    }
}
