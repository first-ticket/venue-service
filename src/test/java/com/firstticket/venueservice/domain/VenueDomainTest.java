package com.firstticket.venueservice.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.firstticket.venueservice.domain.exception.VenueErrorCode;
import com.firstticket.venueservice.domain.exception.VenueException;
import com.firstticket.venueservice.domain.query.PagedResult;
import com.firstticket.venueservice.domain.query.VenueSearchSpec;

/**
 * Venue 서비스 도메인 단위 테스트.
 *
 * [테스트 규칙]
 * - JUnit5 단위 테스트 (Spring 컨텍스트 없음)
 * - 비즈니스 핵심 로직 검증
 * - 도메인 규칙 위반 케이스 반드시 작성
 * - 동일 결과의 중복 케이스 하나만 작성
 *
 * [엔티티 생성 전략]
 * - Venue   : @NoArgsConstructor(PROTECTED), @AllArgsConstructor(PRIVATE)
 *             → Venue.create()로 생성
 * - Section : 정적 팩토리가 package-private
 *             → venue.addSection()을 통해 간접 생성
 * - VenueSeat: public static VenueSeat.create() 직접 사용 가능
 */
class VenueDomainTest {

    // ── 공통 픽스처 ──────────────────────────────────────────────────

    private static final UUID SECTION_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    /**
     * Section 픽스처 헬퍼.
     * Section.id는 @GeneratedValue(UUID)로 JPA 영속화 시점에 부여된다.
     * 단위 테스트에서는 DB가 없으므로 id가 null인 채로 생성된다.
     * removeSection()이 sectionId.equals(s.getId())로 비교하므로
     * 리플렉션으로 고정 UUID를 주입하여 null 비교 실패를 방지한다.
     */
    private Section addSeatedSection(Venue venue) {
        Section section = venue.addSection(SeatType.SEATED, "A구역", 10, 12, null);
        injectSectionId(section, SECTION_ID);
        return section;
    }

    private static void injectSectionId(Section section, UUID id) {
        try {
            java.lang.reflect.Field field = findField(section.getClass());
            field.setAccessible(true);
            field.set(section, id);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Section.id 주입 실패", e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField("id");
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new RuntimeException("필드를 찾을 수 없습니다: " + "id");
    }

    /** 기본 Venue 픽스처 */
    private Venue venue() {
        return Venue.create("올림픽홀", "서울시 송파구 올림픽로 424");
    }

    // ══════════════════════════════════════════════════════════════════
    // Venue.create()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Venue.create() — 공연장 생성")
    class CreateVenue {

        @Test
        @DisplayName("정상 생성 — sections 빈 컬렉션으로 초기화")
        void success() {
            Venue venue = venue();

            assertThat(venue.getName()).isEqualTo("올림픽홀");
            assertThat(venue.getAddress()).isEqualTo("서울시 송파구 올림픽로 424");
            assertThat(venue.getSections()).isEmpty();
        }

        @Test
        @DisplayName("이름 null 시 INVALID_VENUE_NAME 예외 — 입력값 유효성 오류")
        void fail_nullName() {
            assertThatThrownBy(() -> Venue.create(null, "서울시 송파구 올림픽로 424"))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_VENUE_NAME);
        }

        @Test
        @DisplayName("이름 빈 문자열 시 INVALID_VENUE_NAME 예외 — null과 동일 결과이므로 대표 케이스만")
        void fail_blankName() {
            assertThatThrownBy(() -> Venue.create("  ", "서울시 송파구 올림픽로 424"))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_VENUE_NAME);
        }

        @Test
        @DisplayName("주소 null 시 INVALID_VENUE_ADDRESS 예외")
        void fail_nullAddress() {
            assertThatThrownBy(() -> Venue.create("올림픽홀", null))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_VENUE_ADDRESS);
        }

        @Test
        @DisplayName("주소 빈 문자열 시 INVALID_VENUE_ADDRESS 예외")
        void fail_blankAddress() {
            assertThatThrownBy(() -> Venue.create("올림픽홀", "  "))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_VENUE_ADDRESS);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Venue.update()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Venue.update() — 공연장 수정")
    class UpdateVenue {

        @Test
        @DisplayName("이름만 수정 — null 필드는 기존 값 유지")
        void success_nameOnly() {
            Venue venue = venue();

            venue.update("새로운홀", null);

            assertThat(venue.getName()).isEqualTo("새로운홀");
            assertThat(venue.getAddress()).isEqualTo("서울시 송파구 올림픽로 424");
        }

        @Test
        @DisplayName("주소만 수정 — null 필드는 기존 값 유지")
        void success_addressOnly() {
            Venue venue = venue();

            venue.update(null, "부산시 해운대구 해운대로 1");

            assertThat(venue.getName()).isEqualTo("올림픽홀");
            assertThat(venue.getAddress()).isEqualTo("부산시 해운대구 해운대로 1");
        }

        @Test
        @DisplayName("전체 필드 수정 성공")
        void success_allFields() {
            Venue venue = venue();

            venue.update("새로운홀", "부산시 해운대구 해운대로 1");

            assertThat(venue.getName()).isEqualTo("새로운홀");
            assertThat(venue.getAddress()).isEqualTo("부산시 해운대구 해운대로 1");
        }

        @Test
        @DisplayName("수정 결과 이름이 blank가 되면 INVALID_VENUE_NAME 예외 — 도메인 규칙 위반")
        void fail_nameBecomesBlank() {
            Venue venue = venue();

            assertThatThrownBy(() -> venue.update("  ", null))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_VENUE_NAME);
        }

        @Test
        @DisplayName("수정 결과 주소가 blank가 되면 INVALID_VENUE_ADDRESS 예외 — 도메인 규칙 위반")
        void fail_addressBecomesBlank() {
            Venue venue = venue();

            assertThatThrownBy(() -> venue.update(null, "  "))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_VENUE_ADDRESS);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Venue.addSection()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Venue.addSection() — 구역 추가")
    class AddSection {

        @Test
        @DisplayName("SEATED 타입 구역 추가 성공")
        void success_seated() {
            Venue venue = venue();

            Section section = venue.addSection(SeatType.SEATED, "A구역", 10, 12, null);

            assertThat(venue.getSections()).hasSize(1);
            assertThat(section.getType()).isEqualTo(SeatType.SEATED);
            assertThat(section.getRowCount()).isEqualTo(10);
            assertThat(section.getColCount()).isEqualTo(12);
            assertThat(section.getCapacity()).isNull();
        }

        @Test
        @DisplayName("STANDING 타입 구역 추가 성공")
        void success_standing() {
            Venue venue = venue();

            Section section = venue.addSection(SeatType.STANDING, "스탠딩구역", null, null, 300);

            assertThat(venue.getSections()).hasSize(1);
            assertThat(section.getType()).isEqualTo(SeatType.STANDING);
            assertThat(section.getCapacity()).isEqualTo(300);
            assertThat(section.getRowCount()).isNull();
        }

        @Test
        @DisplayName("FREE 타입 구역 추가 성공")
        void success_free() {
            Venue venue = venue();

            Section section = venue.addSection(SeatType.FREE, "자유구역", null, null, 200);

            assertThat(section.getType()).isEqualTo(SeatType.FREE);
            assertThat(section.getCapacity()).isEqualTo(200);
        }

        @Test
        @DisplayName("타입 null 시 INVALID_SECTION_TYPE 예외 — 입력값 유효성 오류")
        void fail_nullType() {
            Venue venue = venue();

            assertThatThrownBy(() -> venue.addSection(null, "A구역", 10, 12, null))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_SECTION_TYPE);
        }

        @Test
        @DisplayName("SEATED 타입에 rowCount null 시 INVALID_SEAT_COUNT 예외 — NPE 방어")
        void fail_seated_nullRowCount() {
            Venue venue = venue();

            assertThatThrownBy(() -> venue.addSection(SeatType.SEATED, "A구역", null, 12, null))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_SEAT_COUNT);
        }

        @Test
        @DisplayName("SEATED 타입에 colCount null 시 INVALID_SEAT_COUNT 예외 — NPE 방어")
        void fail_seated_nullColCount() {
            Venue venue = venue();

            assertThatThrownBy(() -> venue.addSection(SeatType.SEATED, "A구역", 10, null, null))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_SEAT_COUNT);
        }

        @Test
        @DisplayName("SEATED 타입에 rowCount 0 이하 시 INVALID_SEAT_COUNT 예외 — 경계값")
        void fail_seated_zeroRowCount() {
            Venue venue = venue();

            assertThatThrownBy(() -> venue.addSection(SeatType.SEATED, "A구역", 0, 12, null))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_SEAT_COUNT);
        }

        @Test
        @DisplayName("STANDING 타입에 capacity null 시 INVALID_CAPACITY 예외 — NPE 방어")
        void fail_standing_nullCapacity() {
            Venue venue = venue();

            assertThatThrownBy(() -> venue.addSection(SeatType.STANDING, "스탠딩구역", null, null, null))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_CAPACITY);
        }

        @Test
        @DisplayName("STANDING 타입에 capacity 0 이하 시 INVALID_CAPACITY 예외 — 경계값")
        void fail_standing_zeroCapacity() {
            Venue venue = venue();

            assertThatThrownBy(() -> venue.addSection(SeatType.STANDING, "스탠딩구역", null, null, 0))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_CAPACITY);
        }

        @Test
        @DisplayName("구역명 null 시 INVALID_SECTION_NAME 예외")
        void fail_nullSectionName() {
            Venue venue = venue();

            assertThatThrownBy(() -> venue.addSection(SeatType.SEATED, null, 10, 12, null))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_SECTION_NAME);
        }

        @Test
        @DisplayName("구역명 빈 문자열 시 INVALID_SECTION_NAME 예외")
        void fail_blankSectionName() {
            Venue venue = venue();

            assertThatThrownBy(() -> venue.addSection(SeatType.SEATED, "  ", 10, 12, null))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_SECTION_NAME);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Venue.removeSection()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Venue.removeSection() — 구역 삭제")
    class RemoveSection {

        @Test
        @DisplayName("존재하는 구역 삭제 성공")
        void success() {
            Venue venue = venue();
            addSeatedSection(venue); // id 주입 포함 — SECTION_ID로 고정

            venue.removeSection(SECTION_ID);

            assertThat(venue.getSections()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 sectionId 삭제 시 SECTION_NOT_FOUND 예외 — 외부 의존성 실패")
        void fail_sectionNotFound() {
            Venue venue = venue();
            addSeatedSection(venue); // SECTION_ID로 id 주입
            UUID unknownId = UUID.randomUUID(); // SECTION_ID와 다른 UUID

            assertThatThrownBy(() -> venue.removeSection(unknownId))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.SECTION_NOT_FOUND);
        }

        @Test
        @DisplayName("sectionId null 시 INVALID_SECTION_ID 예외")
        void fail_nullSectionId() {
            Venue venue = venue();

            assertThatThrownBy(() -> venue.removeSection(null))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_SECTION_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Section.getSeatCount()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Section.getSeatCount() — 좌석 수 반환")
    class GetSeatCount {

        @Test
        @DisplayName("SEATED 타입: rowCount × colCount 반환")
        void seated_rowTimesCol() {
            Venue venue = venue();
            Section section = venue.addSection(SeatType.SEATED, "A구역", 10, 12, null);

            assertThat(section.getSeatCount()).isEqualTo(120);
        }

        @Test
        @DisplayName("STANDING 타입: capacity 반환")
        void standing_capacity() {
            Venue venue = venue();
            Section section = venue.addSection(SeatType.STANDING, "스탠딩구역", null, null, 300);

            assertThat(section.getSeatCount()).isEqualTo(300);
        }

        @Test
        @DisplayName("FREE 타입: capacity 반환")
        void free_capacity() {
            Venue venue = venue();
            Section section = venue.addSection(SeatType.FREE, "자유구역", null, null, 200);

            assertThat(section.getSeatCount()).isEqualTo(200);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Section.validateCapacityLimit()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Section.validateCapacityLimit() — 프로그램 인원 상한 검증")
    class ValidateCapacityLimit {

        @Test
        @DisplayName("SEATED 타입은 검증 대상이 아님 — 예외 없이 통과")
        void seated_skipsValidation() {
            Venue venue = venue();
            Section section = venue.addSection(SeatType.SEATED, "A구역", 10, 12, null);

            // SEATED는 capacity 기반이 아니므로 어떤 값이라도 통과
            assertThatCode(() -> section.validateCapacityLimit(99_999))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("STANDING: 수용 상한 이하 요청 — 정상 통과")
        void standing_withinLimit() {
            Venue venue = venue();
            Section section = venue.addSection(SeatType.STANDING, "스탠딩구역", null, null, 300);

            assertThatCode(() -> section.validateCapacityLimit(300))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("STANDING: 수용 상한 초과 요청 시 CAPACITY_EXCEEDED 예외 — 도메인 규칙 위반")
        void standing_exceedsCapacity() {
            Venue venue = venue();
            Section section = venue.addSection(SeatType.STANDING, "스탠딩구역", null, null, 300);

            assertThatThrownBy(() -> section.validateCapacityLimit(301))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.CAPACITY_EXCEEDED);
        }

        @Test
        @DisplayName("요청 인원 0 이하 시 INVALID_CAPACITY 예외 — 경계값")
        void fail_zeroRequestedCapacity() {
            Venue venue = venue();
            Section section = venue.addSection(SeatType.STANDING, "스탠딩구역", null, null, 300);

            assertThatThrownBy(() -> section.validateCapacityLimit(0))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_CAPACITY);
        }

        @Test
        @DisplayName("요청 인원 정확히 상한과 같을 때 — 경계값 통과")
        void standing_exactlyAtLimit() {
            Venue venue = venue();
            Section section = venue.addSection(SeatType.FREE, "자유구역", null, null, 100);

            assertThatCode(() -> section.validateCapacityLimit(100))
                .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // VenueSeat.create()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("VenueSeat.create() — 좌석 생성")
    class CreateVenueSeat {

        @Test
        @DisplayName("정상 생성 — 초기 상태 AVAILABLE")
        void success_initialStatusAvailable() {
            VenueSeat seat = VenueSeat.create(SECTION_ID, 1, 3);

            assertThat(seat.getSectionId()).isEqualTo(SECTION_ID);
            assertThat(seat.getRow()).isEqualTo(1);
            assertThat(seat.getCol()).isEqualTo(3);
            assertThat(seat.getPhysicalStatus()).isEqualTo(PhysicalStatus.AVAILABLE);
        }

        @Test
        @DisplayName("sectionId null 시 INVALID_SECTION_ID 예외")
        void fail_nullSectionId() {
            assertThatThrownBy(() -> VenueSeat.create(null, 1, 1))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_SECTION_ID);
        }

        @Test
        @DisplayName("row 0 이하 시 INVALID_SEAT_POSITION 예외 — 경계값")
        void fail_zeroRow() {
            assertThatThrownBy(() -> VenueSeat.create(SECTION_ID, 0, 1))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_SEAT_POSITION);
        }

        @Test
        @DisplayName("col 0 이하 시 INVALID_SEAT_POSITION 예외 — 경계값")
        void fail_zeroCol() {
            assertThatThrownBy(() -> VenueSeat.create(SECTION_ID, 1, 0))
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.INVALID_SEAT_POSITION);
        }

        @Test
        @DisplayName("row, col 모두 1일 때 정상 생성 — 경계값 최솟값")
        void success_minPosition() {
            assertThatCode(() -> VenueSeat.create(SECTION_ID, 1, 1))
                .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // VenueSeat.markBroken() / restore() / isAvailable()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("VenueSeat 상태 변경 — markBroken / restore / isAvailable")
    class VenueSeatStatus {

        @Test
        @DisplayName("AVAILABLE → BROKEN 변경 성공")
        void markBroken_success() {
            VenueSeat seat = VenueSeat.create(SECTION_ID, 1, 1);

            seat.markBroken();

            assertThat(seat.getPhysicalStatus()).isEqualTo(PhysicalStatus.BROKEN);
            assertThat(seat.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("이미 BROKEN인 좌석에 markBroken 시 SEAT_ALREADY_BROKEN 예외 — 도메인 규칙 위반")
        void markBroken_alreadyBroken() {
            VenueSeat seat = VenueSeat.create(SECTION_ID, 1, 1);
            seat.markBroken();

            assertThatThrownBy(seat::markBroken)
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.SEAT_ALREADY_BROKEN);
        }

        @Test
        @DisplayName("BROKEN → AVAILABLE 복구 성공")
        void restore_success() {
            VenueSeat seat = VenueSeat.create(SECTION_ID, 1, 1);
            seat.markBroken();

            seat.restore();

            assertThat(seat.getPhysicalStatus()).isEqualTo(PhysicalStatus.AVAILABLE);
            assertThat(seat.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("이미 AVAILABLE인 좌석에 restore 시 SEAT_ALREADY_AVAILABLE 예외 — 도메인 규칙 위반")
        void restore_alreadyAvailable() {
            VenueSeat seat = VenueSeat.create(SECTION_ID, 1, 1);

            assertThatThrownBy(seat::restore)
                .isInstanceOf(VenueException.class)
                .extracting(e -> ((VenueException)e).getErrorCode())
                .isEqualTo(VenueErrorCode.SEAT_ALREADY_AVAILABLE);
        }

        @Test
        @DisplayName("isAvailable — 생성 직후 true 반환")
        void isAvailable_trueOnCreate() {
            VenueSeat seat = VenueSeat.create(SECTION_ID, 2, 5);

            assertThat(seat.isAvailable()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // PagedResult compact constructor
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PagedResult — compact constructor 검증")
    class PagedResultValidation {

        @Test
        @DisplayName("정상 생성 성공")
        void success() {
            assertThatCode(() -> new PagedResult<>(List.of(), 0L, 0, 0, 1))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("content null 시 IllegalArgumentException")
        void fail_nullContent() {
            assertThatThrownBy(() -> new PagedResult<>(null, 0L, 0, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("totalElements 음수 시 IllegalArgumentException — 경계값")
        void fail_negativeTotalElements() {
            assertThatThrownBy(() -> new PagedResult<>(List.of(), -1L, 0, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("pageNumber 음수 시 IllegalArgumentException — 경계값")
        void fail_negativePageNumber() {
            assertThatThrownBy(() -> new PagedResult<>(List.of(), 0L, 0, -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("pageSize 0 시 IllegalArgumentException — 경계값")
        void fail_zeroPageSize() {
            assertThatThrownBy(() -> new PagedResult<>(List.of(), 0L, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("content는 불변 복사 — 외부 수정이 내부에 반영되지 않는다")
        void success_contentIsDefensivelyCopied() {
            List<String> mutable = new ArrayList<>();
            mutable.add("a");
            PagedResult<String> result = new PagedResult<>(mutable, 1L, 1, 0, 10);

            mutable.add("b");

            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("PagedResult.of() — totalPages 자동 계산")
        void success_ofFactoryCalculatesTotalPages() {
            PagedResult<String> result = PagedResult.of(List.of("a", "b"), 25L, 0, 10);

            assertThat(result.totalPages()).isEqualTo(3); // ceil(25 / 10)
            assertThat(result.totalElements()).isEqualTo(25L);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // VenueSearchSpec compact constructor
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("VenueSearchSpec — compact constructor 검증")
    class VenueSearchSpecValidation {

        @Test
        @DisplayName("정상 생성 성공")
        void success() {
            assertThatCode(() -> new VenueSearchSpec(null, null, null, 0, 20))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("pageNumber 음수 시 IllegalArgumentException — 경계값")
        void fail_negativePageNumber() {
            assertThatThrownBy(() -> new VenueSearchSpec(null, null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("pageSize 0 시 IllegalArgumentException — 경계값")
        void fail_zeroPageSize() {
            assertThatThrownBy(() -> new VenueSearchSpec(null, null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("getOffset() — pageNumber × pageSize 계산 검증")
        void success_offsetCalculation() {
            VenueSearchSpec spec = new VenueSearchSpec(null, null, null, 3, 20);

            assertThat(spec.getOffset()).isEqualTo(60L);
        }
    }
}
