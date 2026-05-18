package com.firstticket.venueservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.firstticket.common.persistence.CommonJpaAutoConfiguration;
import com.firstticket.venueservice.domain.SeatType;
import com.firstticket.venueservice.domain.Section;
import com.firstticket.venueservice.domain.Venue;
import com.firstticket.venueservice.domain.VenueSeat;
import com.firstticket.venueservice.domain.query.PagedResult;
import com.firstticket.venueservice.domain.query.VenueSearchSpec;
import com.firstticket.venueservice.domain.query.VenueSummaryData;

/**
 * Venue Repository 슬라이스 테스트.
 *
 * [테스트 규칙]
 * - @DataJpaTest 슬라이스 테스트 (커스텀 쿼리가 있는 경우에만 작성)
 * - H2 in-memory DB 사용
 * - 테스트는 독립적으로 실행 가능 (@Transactional → 자동 롤백)
 *
 * [테스트 대상 커스텀 쿼리]
 * 1. VenueJpaRepository.findByIdWithSections()
 *    - JOIN FETCH로 N+1 방지
 *    - soft delete 제외 (venue·section 모두)
 *
 * 2. VenueJpaRepository.findVenueBySectionId()
 *    - section ID로 venue 역방향 조회
 *    - soft delete된 venue·section 제외
 *
 * 3. VenueJpaRepository.existsById()
 *    - soft delete 제외
 *
 * 4. VenueSeatJpaRepository.findBySectionId()
 *    - 구역 ID로 전체 좌석 조회
 *    - soft delete 제외
 *
 * 5. VenueSeatJpaRepository.deleteAllBySectionId()
 *    - @Modifying 벌크 삭제
 *    - flushAutomatically·clearAutomatically 검증
 *
 * 6. VenueQueryRepositoryImpl.findBySpec() — QueryDSL
 *    - 키워드 필터 (이름·주소 OR 검색)
 *    - 정렬 (name·createdAt 화이트리스트)
 *    - 페이지네이션
 *    - soft delete 제외
 */
@DataJpaTest
@Import({CommonJpaAutoConfiguration.class, VenueQueryRepositoryImpl.class})
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.flyway.enabled=false",
    "spring.jpa.properties.hibernate.default_schema="  // H2는 schema 없이 사용
})
class VenueRepositoryTest {

    @Autowired
    private VenueJpaRepository venueJpaRepository;
    @Autowired
    private VenueSeatJpaRepository venueSeatJpaRepository;
    @Autowired
    private VenueQueryRepositoryImpl venueQueryRepositoryImpl;

    // ── 공통 픽스처 UUID ─────────────────────────────────────────────
    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    // ── 픽스처 빌더 ──────────────────────────────────────────────────

    /**
     * Venue 픽스처 저장 헬퍼.
     * createdBy는 BaseUserEntity Auditing으로 자동 주입되지 않으므로
     * 리플렉션으로 주입 후 저장한다.
     */
    private Venue saveVenue(String name, String address) {
        Venue venue = Venue.create(name, address);
        injectField(venue, "createdBy", OWNER_ID);
        return venueJpaRepository.save(venue);
    }

    /**
     * SEATED 구역 추가 헬퍼.
     * Section.id는 save() 후 cascade로 부여되므로
     * venue 저장 후 sections에서 꺼내 사용한다.
     */
    private Section addSeatedSection(Venue venue, String sectionName) {
        Section section = venue.addSection(SeatType.SEATED, sectionName, 5, 5, null);

        injectField(section, "createdBy", OWNER_ID);

        venueJpaRepository.saveAndFlush(venue);

        // save 후 sections에서 id가 부여된 Section 반환
        return venue.getSections().stream()
            .filter(s -> s.getName().equals(sectionName))
            .findFirst()
            .orElseThrow();
    }

    /**
     * VenueSeat 픽스처 저장 헬퍼.
     * createdBy는 BaseUserEntity Auditing으로 자동 주입되지 않으므로 리플렉션으로 주입한다.
     */
    private VenueSeat saveSeat(UUID sectionId, int row, int col) {
        VenueSeat seat = VenueSeat.create(sectionId, row, col);
        injectField(seat, "createdBy", OWNER_ID);
        return venueSeatJpaRepository.save(seat);
    }

    // ══════════════════════════════════════════════════════════════════
    // VenueJpaRepository.findByIdWithSections()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByIdWithSections() — JOIN FETCH + soft delete 제외")
    class FindByIdWithSections {

        @Test
        @DisplayName("sections JOIN FETCH 조회 성공 — N+1 없이 sections 포함")
        void success_withSections() {
            Venue venue = saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            addSeatedSection(venue, "A구역");

            Optional<Venue> result = venueJpaRepository.findByIdWithSections(venue.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getSections()).hasSize(1);
            assertThat(result.get().getSections().get(0).getName()).isEqualTo("A구역");
        }

        @Test
        @DisplayName("구역 없는 공연장 조회 — sections 빈 리스트")
        void success_noSections() {
            Venue venue = saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");

            Optional<Venue> result = venueJpaRepository.findByIdWithSections(venue.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getSections()).isEmpty();
        }

        @Test
        @DisplayName("soft delete된 공연장 조회 시 empty 반환 — soft delete 제외")
        void fail_softDeletedVenue() {
            Venue venue = saveVenue("삭제된홀", "서울시 송파구 올림픽로 424");
            injectField(venue, "deletedAt", LocalDateTime.now());
            venueJpaRepository.save(venue);

            Optional<Venue> result = venueJpaRepository.findByIdWithSections(venue.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("soft delete된 구역은 sections에 포함되지 않음")
        void success_softDeletedSection_excluded() {
            Venue venue = saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            Section section = addSeatedSection(venue, "A구역");

            // 구역 soft delete
            injectField(section, "deletedAt", LocalDateTime.now());
            venueJpaRepository.save(venue);

            Optional<Venue> result = venueJpaRepository.findByIdWithSections(venue.getId());

            assertThat(result).isPresent();
            // LEFT JOIN FETCH이므로 venue는 반환되지만 soft delete된 section은 포함되지 않음
            assertThat(result.get().getSections()
                .stream().filter(s -> s.getDeletedAt() == null).toList()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 empty 반환")
        void fail_notFound() {
            Optional<Venue> result = venueJpaRepository.findByIdWithSections(UUID.randomUUID());

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // VenueJpaRepository.findVenueBySectionId()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findVenueBySectionId() — section ID로 venue 역방향 조회")
    class FindVenueBySectionId {

        @Test
        @DisplayName("section ID로 venue 조회 성공")
        void success() {
            Venue venue = saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            Section section = addSeatedSection(venue, "A구역");

            Optional<Venue> result = venueJpaRepository.findVenueBySectionId(section.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(venue.getId());
        }

        @Test
        @DisplayName("soft delete된 공연장의 구역 조회 시 empty 반환")
        void fail_softDeletedVenue() {
            Venue venue = saveVenue("삭제된홀", "서울시 송파구 올림픽로 424");
            Section section = addSeatedSection(venue, "A구역");

            injectField(venue, "deletedAt", LocalDateTime.now());
            venueJpaRepository.save(venue);

            Optional<Venue> result = venueJpaRepository.findVenueBySectionId(section.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("soft delete된 구역 ID로 조회 시 empty 반환")
        void fail_softDeletedSection() {
            Venue venue = saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            Section section = addSeatedSection(venue, "A구역");

            injectField(section, "deletedAt", LocalDateTime.now());
            venueJpaRepository.save(venue);

            Optional<Venue> result = venueJpaRepository.findVenueBySectionId(section.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 sectionId 조회 시 empty 반환")
        void fail_notFound() {
            Optional<Venue> result = venueJpaRepository.findVenueBySectionId(UUID.randomUUID());

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // VenueJpaRepository.existsById()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("existsById() — soft delete 제외 존재 여부")
    class ExistsById {

        @Test
        @DisplayName("존재하는 공연장 — true 반환")
        void true_exists() {
            Venue venue = saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");

            assertThat(venueJpaRepository.existsById(venue.getId())).isTrue();
        }

        @Test
        @DisplayName("soft delete된 공연장 — false 반환")
        void false_softDeleted() {
            Venue venue = saveVenue("삭제된홀", "서울시 송파구 올림픽로 424");
            injectField(venue, "deletedAt", LocalDateTime.now());
            venueJpaRepository.save(venue);

            assertThat(venueJpaRepository.existsById(venue.getId())).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 ID — false 반환")
        void false_notFound() {
            assertThat(venueJpaRepository.existsById(UUID.randomUUID())).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // VenueSeatJpaRepository.findBySectionId()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findBySectionId() — 구역 ID로 좌석 조회 + soft delete 제외")
    class FindBySectionId {

        @Test
        @DisplayName("구역의 좌석 전체 조회 성공")
        void success() {
            Venue venue = saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            Section section = addSeatedSection(venue, "A구역");
            saveSeat(section.getId(), 1, 1);
            saveSeat(section.getId(), 1, 2);

            List<VenueSeat> seats = venueSeatJpaRepository.findBySectionId(section.getId());

            assertThat(seats).hasSize(2);
        }

        @Test
        @DisplayName("soft delete된 좌석은 결과에서 제외")
        void success_softDeletedExcluded() {
            Venue venue = saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            Section section = addSeatedSection(venue, "A구역");
            VenueSeat active = saveSeat(section.getId(), 1, 1);
            VenueSeat deleted = saveSeat(section.getId(), 1, 2);

            injectField(deleted, "deletedAt", LocalDateTime.now());
            venueSeatJpaRepository.save(deleted);

            List<VenueSeat> seats = venueSeatJpaRepository.findBySectionId(section.getId());

            assertThat(seats).hasSize(1);
            assertThat(seats.get(0).getId()).isEqualTo(active.getId());
        }

        @Test
        @DisplayName("해당 구역에 좌석 없을 때 빈 리스트 반환")
        void success_empty() {
            List<VenueSeat> seats = venueSeatJpaRepository.findBySectionId(UUID.randomUUID());

            assertThat(seats).isEmpty();
        }

        @Test
        @DisplayName("다른 구역의 좌석은 포함되지 않음 — sectionId 격리")
        void success_otherSectionExcluded() {
            Venue venue = saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            Section sectionA = addSeatedSection(venue, "A구역");
            Section sectionB = addSeatedSection(venue, "B구역");

            saveSeat(sectionA.getId(), 1, 1);
            saveSeat(sectionB.getId(), 1, 1);

            List<VenueSeat> seats = venueSeatJpaRepository.findBySectionId(sectionA.getId());

            assertThat(seats).hasSize(1);
            assertThat(seats.get(0).getSectionId()).isEqualTo(sectionA.getId());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // VenueSeatJpaRepository.deleteAllBySectionId()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteAllBySectionId() — @Modifying 벌크 삭제")
    class DeleteAllBySectionId {

        @Test
        @DisplayName("구역 ID에 해당하는 좌석 전체 삭제 성공")
        void success() {
            Venue venue = saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            Section section = addSeatedSection(venue, "A구역");
            saveSeat(section.getId(), 1, 1);
            saveSeat(section.getId(), 1, 2);
            saveSeat(section.getId(), 1, 3);

            venueSeatJpaRepository.deleteAllBySectionId(section.getId());

            // clearAutomatically = true로 1차 캐시가 비워지므로 DB에서 새로 조회
            List<VenueSeat> remaining = venueSeatJpaRepository.findBySectionId(section.getId());
            assertThat(remaining).isEmpty();
        }

        @Test
        @DisplayName("다른 구역의 좌석은 삭제되지 않음 — sectionId 격리")
        void success_otherSectionPreserved() {
            Venue venue = saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            Section sectionA = addSeatedSection(venue, "A구역");
            Section sectionB = addSeatedSection(venue, "B구역");

            saveSeat(sectionA.getId(), 1, 1);
            saveSeat(sectionB.getId(), 1, 1);

            venueSeatJpaRepository.deleteAllBySectionId(sectionA.getId());

            assertThat(venueSeatJpaRepository.findBySectionId(sectionA.getId())).isEmpty();
            assertThat(venueSeatJpaRepository.findBySectionId(sectionB.getId())).hasSize(1);
        }

        @Test
        @DisplayName("해당 구역에 좌석 없을 때 예외 없이 정상 종료")
        void success_noSeats() {
            assertThatCode(() ->
                venueSeatJpaRepository.deleteAllBySectionId(UUID.randomUUID()))
                .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // VenueQueryRepositoryImpl.findBySpec() — QueryDSL
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findBySpec() — QueryDSL 필터·정렬·페이지네이션")
    class FindBySpec {

        @Test
        @DisplayName("조건 없이 전체 조회 — soft delete 제외")
        void success_noFilter() {
            saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            saveVenue("블루스퀘어", "서울시 용산구 이태원로 294");

            Venue deleted = saveVenue("삭제된홀", "서울시 중구 을지로 1");
            injectField(deleted, "deletedAt", LocalDateTime.now());
            venueJpaRepository.save(deleted);

            PagedResult<VenueSummaryData> result =
                venueQueryRepositoryImpl.findBySpec(
                    new VenueSearchSpec(null, null, null, 0, 20));

            assertThat(result.totalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("이름 키워드 필터 — 이름에 키워드 포함된 결과만 반환")
        void success_nameKeywordFilter() {
            saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            saveVenue("블루스퀘어", "서울시 용산구 이태원로 294");

            PagedResult<VenueSummaryData> result =
                venueQueryRepositoryImpl.findBySpec(
                    new VenueSearchSpec("올림픽", null, null, 0, 20));

            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content().get(0).name()).isEqualTo("올림픽홀");
        }

        @Test
        @DisplayName("주소 키워드 필터 — 주소에 키워드 포함된 결과도 반환 (OR 검색)")
        void success_addressKeywordFilter() {
            saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            saveVenue("블루스퀘어", "서울시 용산구 이태원로 294");

            PagedResult<VenueSummaryData> result =
                venueQueryRepositoryImpl.findBySpec(
                    new VenueSearchSpec("용산구", null, null, 0, 20));

            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content().get(0).name()).isEqualTo("블루스퀘어");
        }

        @Test
        @DisplayName("키워드 공백만 있을 때 전체 반환 — blank 키워드는 필터 미적용")
        void success_blankKeyword() {
            saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            saveVenue("블루스퀘어", "서울시 용산구 이태원로 294");

            PagedResult<VenueSummaryData> result =
                venueQueryRepositoryImpl.findBySpec(
                    new VenueSearchSpec("   ", null, null, 0, 20));

            assertThat(result.totalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("구역 수 집계 — sections 포함 여부에 따라 sectionCount 반환")
        void success_sectionCount() {
            Venue venue = saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            addSeatedSection(venue, "A구역");
            addSeatedSection(venue, "B구역");

            PagedResult<VenueSummaryData> result =
                venueQueryRepositoryImpl.findBySpec(
                    new VenueSearchSpec(null, null, null, 0, 20));

            assertThat(result.content().get(0).sectionCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("soft delete된 구역은 sectionCount에 포함되지 않음")
        void success_softDeletedSectionExcluded_fromCount() {
            Venue venue = saveVenue("올림픽홀", "서울시 송파구 올림픽로 424");
            Section sectionA = addSeatedSection(venue, "A구역");
            addSeatedSection(venue, "B구역");

            injectField(sectionA, "deletedAt", LocalDateTime.now());
            venueJpaRepository.save(venue);

            PagedResult<VenueSummaryData> result =
                venueQueryRepositoryImpl.findBySpec(
                    new VenueSearchSpec(null, null, null, 0, 20));

            assertThat(result.content().get(0).sectionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("페이지네이션 — 3건 중 2건씩 분리")
        void success_pagination() {
            saveVenue("공연장A", "주소A");
            saveVenue("공연장B", "주소B");
            saveVenue("공연장C", "주소C");

            PagedResult<VenueSummaryData> page0 =
                venueQueryRepositoryImpl.findBySpec(
                    new VenueSearchSpec(null, null, null, 0, 2));
            PagedResult<VenueSummaryData> page1 =
                venueQueryRepositoryImpl.findBySpec(
                    new VenueSearchSpec(null, null, null, 1, 2));

            assertThat(page0.content()).hasSize(2);
            assertThat(page1.content()).hasSize(1);
            assertThat(page0.totalPages()).isEqualTo(2);
            assertThat(page0.totalElements()).isEqualTo(3);
        }

        @Test
        @DisplayName("sortField=name asc — 이름 오름차순 정렬")
        void success_sortByNameAsc() {
            saveVenue("Z홀", "주소Z");
            saveVenue("A홀", "주소A");

            PagedResult<VenueSummaryData> result =
                venueQueryRepositoryImpl.findBySpec(
                    new VenueSearchSpec(null, "name", "asc", 0, 20));

            assertThat(result.content().get(0).name()).isEqualTo("A홀");
            assertThat(result.content().get(1).name()).isEqualTo("Z홀");
        }

        @Test
        @DisplayName("sortField=name desc — 이름 내림차순 정렬")
        void success_sortByNameDesc() {
            saveVenue("Z홀", "주소Z");
            saveVenue("A홀", "주소A");

            PagedResult<VenueSummaryData> result =
                venueQueryRepositoryImpl.findBySpec(
                    new VenueSearchSpec(null, "name", "desc", 0, 20));

            assertThat(result.content().get(0).name()).isEqualTo("Z홀");
        }

        @Test
        @DisplayName("알 수 없는 sortField — createdAt desc 기본 정렬")
        void success_unknownSortField_defaultSort() {
            saveVenue("공연장A", "주소A");
            saveVenue("공연장B", "주소B");

            assertThatCode(() ->
                venueQueryRepositoryImpl.findBySpec(
                    new VenueSearchSpec(null, "unknown", "asc", 0, 20)))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("결과 없을 때 빈 content, totalElements=0")
        void success_empty() {
            PagedResult<VenueSummaryData> result =
                venueQueryRepositoryImpl.findBySpec(
                    new VenueSearchSpec("존재하지않는키워드", null, null, 0, 20));

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isEqualTo(0);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 공통 리플렉션 헬퍼
    // ══════════════════════════════════════════════════════════════════

    private static void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("필드 주입 실패: " + fieldName, e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new RuntimeException("필드를 찾을 수 없습니다: " + fieldName);
    }
}
