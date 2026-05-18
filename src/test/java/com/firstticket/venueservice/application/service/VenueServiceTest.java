package com.firstticket.venueservice.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.firstticket.common.exception.BusinessException;
import com.firstticket.common.response.CommonErrorCode;
import com.firstticket.venueservice.application.dto.command.CreateSectionCommand;
import com.firstticket.venueservice.application.dto.command.CreateVenueCommand;
import com.firstticket.venueservice.application.dto.command.SectionCreationInfo;
import com.firstticket.venueservice.application.dto.command.UpdateVenueCommand;
import com.firstticket.venueservice.application.dto.command.UpdateVenueSeatStatusCommand;
import com.firstticket.venueservice.application.dto.query.VenueSearchQuery;
import com.firstticket.venueservice.application.dto.result.VenueResult;
import com.firstticket.venueservice.application.dto.result.VenueSeatResult;
import com.firstticket.venueservice.domain.PhysicalStatus;
import com.firstticket.venueservice.domain.SeatType;
import com.firstticket.venueservice.domain.Section;
import com.firstticket.venueservice.domain.Venue;
import com.firstticket.venueservice.domain.VenueRepository;
import com.firstticket.venueservice.domain.VenueSeat;
import com.firstticket.venueservice.domain.VenueSeatRepository;
import com.firstticket.venueservice.domain.exception.VenueErrorCode;
import com.firstticket.venueservice.domain.exception.VenueException;
import com.firstticket.venueservice.domain.query.PagedResult;
import com.firstticket.venueservice.domain.query.SectionQueryRepository;
import com.firstticket.venueservice.domain.query.VenueQueryRepository;
import com.firstticket.venueservice.domain.service.ProgramProvider;

/**
 * VenueCommandService, VenueQueryService 단위 테스트.
 *
 * [테스트 규칙]
 * - JUnit5 + Mockito 단위 테스트 (Spring 컨텍스트 없음)
 * - 외부 의존성(Repository, ProgramProvider)은 Mock 처리
 * - Service 예외 케이스 반드시 작성
 * - 동일 결과의 중복 케이스 하나만 작성
 *
 * [Section.id null 문제 — VenueDomainTest와 동일]
 * Section.id는 @GeneratedValue(UUID)로 JPA 영속화 전 null.
 * deleteSection()이 s.getId().equals(sectionId)로 비교하므로
 * 리플렉션으로 Section.id를 주입한다.
 *
 * [VenueSeat.sectionId]
 * VenueSeat.create()는 public이므로 직접 생성 가능.
 * VenueSeat.id도 영속화 전 null이므로 필요 시 리플렉션으로 주입한다.
 */
@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    // ── 공통 픽스처 UUID ─────────────────────────────────────────────
    private static final UUID VENUE_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SECTION_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID SEAT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID OWNER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID OTHER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");

    // ══════════════════════════════════════════════════════════════════
    // VenueCommandService
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("VenueCommandService")
    class CommandServiceTest {

        @Mock
        private VenueRepository venueRepository;
        @Mock
        private VenueSeatRepository venueSeatRepository;
        @Mock
        private ProgramProvider programProvider;

        @InjectMocks
        private VenueCommandService venueCommandService;

        // ── 픽스처 빌더 ────────────────────────────────────────────

        /** SEATED 구역이 포함된 Venue 픽스처 — Section.id 주입 포함 */
        private Venue venueWithSeatedSection() {
            Venue venue = Venue.create("올림픽홀", "서울시 송파구 올림픽로 424");
            injectField(venue, "id", VENUE_ID);
            injectField(venue, "createdBy", OWNER_ID);
            Section section = venue.addSection(SeatType.SEATED, "A구역", 10, 12, null);
            injectField(section, "id", SECTION_ID);
            return venue;
        }

        /** 구역 없는 기본 Venue 픽스처 */
        private Venue emptyVenue() {
            Venue venue = Venue.create("올림픽홀", "서울시 송파구 올림픽로 424");
            injectField(venue, "id", VENUE_ID);
            injectField(venue, "createdBy", OWNER_ID);
            return venue;
        }

        // ── createVenue ─────────────────────────────────────────────

        @Nested
        @DisplayName("createVenue() — 공연장 생성")
        class CreateVenue {

            @Test
            @DisplayName("구역 없이 공연장만 생성 성공")
            void success_noSections() {
                Venue venue = emptyVenue();
                given(venueRepository.save(any())).willReturn(venue);

                VenueResult result = venueCommandService.createVenue(
                    OWNER_ID,
                    new CreateVenueCommand("올림픽홀", "서울시 송파구 올림픽로 424"),
                    List.of()
                );

                assertThat(result.name()).isEqualTo("올림픽홀");
                then(venueSeatRepository).shouldHaveNoInteractions();
            }

            @Test
            @DisplayName("SEATED 구역 포함 생성 — VenueSeat 일괄 생성")
            void success_withSeatedSection() {
                // 서비스 흐름: 1차 save(venue만) → 2차 save(섹션 추가 후)
                // 2차 save() 호출 시점에 cascade로 Section.id가 할당되는 것을 시뮬레이션
                // willAnswer로 2차 save() 시 sections의 첫 번째 Section에 id 주입
                given(venueRepository.save(any())).willAnswer(invocation -> {
                    Venue v = invocation.getArgument(0);
                    if (!v.getSections().isEmpty()) {
                        injectField(v.getSections().getFirst(), "id", SECTION_ID);
                    }
                    return v;
                });
                given(venueSeatRepository.saveAll(any())).willReturn(List.of());

                VenueResult result = venueCommandService.createVenue(
                    OWNER_ID,
                    new CreateVenueCommand("올림픽홀", "서울시 송파구 올림픽로 424"),
                    List.of(new SectionCreationInfo("A구역", SeatType.SEATED, 10, 12, null))
                );

                assertThat(result.sections()).hasSize(1);
                // SEATED 10×12 = 120석 — VenueSeat saveAll 호출 검증
                then(venueSeatRepository).should().saveAll(argThat(
                    seats -> ((List<?>)seats).size() == 120));
            }

            @Test
            @DisplayName("STANDING 구역 포함 생성 — VenueSeat 생성 없음")
            void success_withStandingSection_noSeats() {
                Venue venue = emptyVenue();
                venue.addSection(SeatType.STANDING, "스탠딩구역", null, null, 300);
                given(venueRepository.save(any())).willReturn(venue);

                venueCommandService.createVenue(
                    OWNER_ID,
                    new CreateVenueCommand("올림픽홀", "서울시 송파구 올림픽로 424"),
                    List.of(new SectionCreationInfo("스탠딩구역", SeatType.STANDING, null, null, 300))
                );

                then(venueSeatRepository).shouldHaveNoInteractions();
            }

            @Test
            @DisplayName("requesterId null 시 UNAUTHORIZED 예외")
            void fail_nullRequesterId() {
                assertThatThrownBy(() -> venueCommandService.createVenue(
                    null,
                    new CreateVenueCommand("올림픽홀", "서울시 송파구 올림픽로 424"),
                    List.of()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException)e).getErrorCode())
                    .isEqualTo(CommonErrorCode.UNAUTHORIZED);
            }

            @Test
            @DisplayName("공연장 이름 null 시 INVALID_VENUE_NAME 예외 — 입력값 유효성 오류")
            void fail_nullVenueName() {
                assertThatThrownBy(() -> venueCommandService.createVenue(
                    OWNER_ID,
                    new CreateVenueCommand(null, "서울시 송파구 올림픽로 424"),
                    List.of()))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.INVALID_VENUE_NAME);
            }

            @Test
            @DisplayName("SEATED 구역에 capacity 함께 전달 시 INVALID_SECTION_FIELD_COMBINATION 예외 — 도메인 규칙 위반")
            void fail_seatedWithCapacity() {
                assertThatThrownBy(() -> venueCommandService.createVenue(
                    OWNER_ID,
                    new CreateVenueCommand("올림픽홀", "서울시 송파구 올림픽로 424"),
                    List.of(new SectionCreationInfo("A구역", SeatType.SEATED, 10, 12, 100))))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.INVALID_SECTION_FIELD_COMBINATION);
            }
        }

        // ── updateVenue ──────────────────────────────────────────────

        @Nested
        @DisplayName("updateVenue() — 공연장 수정")
        class UpdateVenue {

            @Test
            @DisplayName("이름만 수정 성공")
            void success_nameOnly() {
                Venue venue = emptyVenue();
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.of(venue));

                VenueResult result = venueCommandService.updateVenue(
                    OWNER_ID, new UpdateVenueCommand(VENUE_ID, "새로운홀", null));

                assertThat(result.name()).isEqualTo("새로운홀");
            }

            @Test
            @DisplayName("존재하지 않는 venueId 수정 시 VENUE_NOT_FOUND 예외 — 외부 의존성 실패")
            void fail_notFound() {
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.empty());

                assertThatThrownBy(() -> venueCommandService.updateVenue(
                    OWNER_ID, new UpdateVenueCommand(VENUE_ID, "새로운홀", null)))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.VENUE_NOT_FOUND);
            }

            @Test
            @DisplayName("소유자가 아닌 사용자 수정 시 FORBIDDEN 예외 — 권한 오류")
            void fail_notOwner() {
                Venue venue = emptyVenue();
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.of(venue));

                assertThatThrownBy(() -> venueCommandService.updateVenue(
                    OTHER_ID, new UpdateVenueCommand(VENUE_ID, "새로운홀", null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException)e).getErrorCode())
                    .isEqualTo(CommonErrorCode.FORBIDDEN);
            }
        }

        // ── deleteVenue ──────────────────────────────────────────────

        @Nested
        @DisplayName("deleteVenue() — 공연장 삭제")
        class DeleteVenue {

            @Test
            @DisplayName("활성 프로그램 없을 때 삭제 성공")
            void success() {
                Venue venue = emptyVenue();
                given(venueRepository.findById(VENUE_ID)).willReturn(Optional.of(venue));
                given(programProvider.hasProgramsForVenue(VENUE_ID)).willReturn(false);
                willDoNothing().given(venueRepository).delete(venue);

                assertThatCode(() -> venueCommandService.deleteVenue(OWNER_ID, VENUE_ID))
                    .doesNotThrowAnyException();

                then(venueRepository).should().delete(venue);
            }

            @Test
            @DisplayName("활성 프로그램이 있는 공연장 삭제 시 VENUE_HAS_PROGRAMS 예외 — 도메인 규칙 위반")
            void fail_hasActivePrograms() {
                Venue venue = emptyVenue();
                given(venueRepository.findById(VENUE_ID)).willReturn(Optional.of(venue));
                given(programProvider.hasProgramsForVenue(VENUE_ID)).willReturn(true);

                assertThatThrownBy(() -> venueCommandService.deleteVenue(OWNER_ID, VENUE_ID))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.VENUE_HAS_PROGRAMS);
            }

            @Test
            @DisplayName("존재하지 않는 venueId 삭제 시 VENUE_NOT_FOUND 예외 — 외부 의존성 실패")
            void fail_notFound() {
                given(venueRepository.findById(VENUE_ID)).willReturn(Optional.empty());

                assertThatThrownBy(() -> venueCommandService.deleteVenue(OWNER_ID, VENUE_ID))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.VENUE_NOT_FOUND);
            }

            @Test
            @DisplayName("소유자가 아닌 사용자 삭제 시 FORBIDDEN 예외 — 권한 오류")
            void fail_notOwner() {
                Venue venue = emptyVenue();
                given(venueRepository.findById(VENUE_ID)).willReturn(Optional.of(venue));

                assertThatThrownBy(() -> venueCommandService.deleteVenue(OTHER_ID, VENUE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException)e).getErrorCode())
                    .isEqualTo(CommonErrorCode.FORBIDDEN);
            }
        }

        // ── createSection ────────────────────────────────────────────

        @Nested
        @DisplayName("createSection() — 구역 추가")
        class CreateSection {

            @Test
            @DisplayName("SEATED 구역 추가 성공 — VenueSeat 일괄 생성")
            void success_seated() {
                Venue venue = emptyVenue();
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.of(venue));
                // save() 호출 시 cascade로 Section.id가 할당되는 것을 시뮬레이션
                given(venueRepository.save(any())).willAnswer(invocation -> {
                    Venue v = invocation.getArgument(0);
                    if (!v.getSections().isEmpty()) {
                        injectField(v.getSections().getFirst(), "id", SECTION_ID);
                    }
                    return v;
                });
                given(venueSeatRepository.saveAll(any())).willReturn(List.of());

                venueCommandService.createSection(OWNER_ID,
                    new CreateSectionCommand(VENUE_ID, "A구역", SeatType.SEATED, 10, 12, null));

                then(venueSeatRepository).should().saveAll(any());
            }

            @Test
            @DisplayName("STANDING 구역 추가 성공 — VenueSeat 생성 없음")
            void success_standing_noSeats() {
                Venue venue = emptyVenue();
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.of(venue));
                given(venueRepository.save(any())).willReturn(venue);

                venueCommandService.createSection(OWNER_ID,
                    new CreateSectionCommand(VENUE_ID, "스탠딩구역", SeatType.STANDING, null, null, 300));

                then(venueSeatRepository).shouldHaveNoInteractions();
            }

            @Test
            @DisplayName("존재하지 않는 venueId 시 VENUE_NOT_FOUND 예외 — 외부 의존성 실패")
            void fail_notFound() {
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.empty());

                assertThatThrownBy(() -> venueCommandService.createSection(OWNER_ID,
                    new CreateSectionCommand(VENUE_ID, "A구역", SeatType.SEATED, 10, 12, null)))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.VENUE_NOT_FOUND);
            }

            @Test
            @DisplayName("STANDING 구역에 rowCount 함께 전달 시 INVALID_SECTION_FIELD_COMBINATION 예외 — 도메인 규칙 위반")
            void fail_standingWithRowCount() {
                assertThatThrownBy(() -> venueCommandService.createSection(OWNER_ID,
                    new CreateSectionCommand(VENUE_ID, "스탠딩구역", SeatType.STANDING, 10, null, 300)))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.INVALID_SECTION_FIELD_COMBINATION);
            }
        }

        // ── deleteSection ────────────────────────────────────────────

        @Nested
        @DisplayName("deleteSection() — 구역 삭제")
        class DeleteSection {

            @Test
            @DisplayName("SEATED 구역 삭제 성공 — VenueSeat 먼저 삭제")
            void success_seated() {
                Venue venue = venueWithSeatedSection(); // SECTION_ID 주입된 SEATED 구역 보유
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.of(venue));
                willDoNothing().given(venueSeatRepository).deleteAllBySectionId(SECTION_ID);

                venueCommandService.deleteSection(OWNER_ID, VENUE_ID, SECTION_ID);

                then(venueSeatRepository).should().deleteAllBySectionId(SECTION_ID);
                assertThat(venue.getSections()).isEmpty();
            }

            @Test
            @DisplayName("존재하지 않는 sectionId 삭제 시 SECTION_NOT_FOUND 예외 — 외부 의존성 실패")
            void fail_sectionNotFound() {
                Venue venue = venueWithSeatedSection(); // SECTION_ID만 보유
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.of(venue));
                UUID unknownSectionId = UUID.randomUUID();

                assertThatThrownBy(() ->
                    venueCommandService.deleteSection(OWNER_ID, VENUE_ID, unknownSectionId))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.SECTION_NOT_FOUND);
            }

            @Test
            @DisplayName("소유자가 아닌 사용자 삭제 시 FORBIDDEN 예외 — 권한 오류")
            void fail_notOwner() {
                Venue venue = venueWithSeatedSection();
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.of(venue));

                assertThatThrownBy(() ->
                    venueCommandService.deleteSection(OTHER_ID, VENUE_ID, SECTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException)e).getErrorCode())
                    .isEqualTo(CommonErrorCode.FORBIDDEN);
            }
        }

        // ── markSeatBroken ───────────────────────────────────────────

        @Nested
        @DisplayName("markSeatBroken() — 좌석 파손 처리")
        class MarkSeatBroken {

            /** VenueSeat 픽스처 — id, createdBy 주입 */
            private VenueSeat availableSeat() {
                VenueSeat seat = VenueSeat.create(SECTION_ID, 1, 1);
                injectField(seat, "id", SEAT_ID);
                injectField(seat, "createdBy", OWNER_ID);
                return seat;
            }

            @Test
            @DisplayName("AVAILABLE → BROKEN 변경 성공")
            void success() {
                VenueSeat seat = availableSeat();
                Venue venue = emptyVenue();
                given(venueSeatRepository.findById(SEAT_ID)).willReturn(Optional.of(seat));
                // checkSeatOwner() → venueRepository.findVenueBySectionId(sectionId)
                given(venueRepository.findVenueBySectionId(SECTION_ID)).willReturn(Optional.of(venue));

                VenueSeatResult result = venueCommandService.markSeatBroken(
                    OWNER_ID, new UpdateVenueSeatStatusCommand(SEAT_ID, true));

                assertThat(result.physicalStatus()).isEqualTo(PhysicalStatus.BROKEN);
            }

            @Test
            @DisplayName("이미 BROKEN인 좌석 파손 처리 시 SEAT_ALREADY_BROKEN 예외 — 도메인 규칙 위반")
            void fail_alreadyBroken() {
                VenueSeat seat = availableSeat();
                seat.markBroken();
                Venue venue = emptyVenue();
                given(venueSeatRepository.findById(SEAT_ID)).willReturn(Optional.of(seat));
                given(venueRepository.findVenueBySectionId(SECTION_ID)).willReturn(Optional.of(venue));

                assertThatThrownBy(() -> venueCommandService.markSeatBroken(
                    OWNER_ID, new UpdateVenueSeatStatusCommand(SEAT_ID, true)))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.SEAT_ALREADY_BROKEN);
            }

            @Test
            @DisplayName("존재하지 않는 seatId 시 SEAT_NOT_FOUND 예외 — 외부 의존성 실패")
            void fail_notFound() {
                given(venueSeatRepository.findById(SEAT_ID)).willReturn(Optional.empty());

                assertThatThrownBy(() -> venueCommandService.markSeatBroken(
                    OWNER_ID, new UpdateVenueSeatStatusCommand(SEAT_ID, true)))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.SEAT_NOT_FOUND);
            }

            @Test
            @DisplayName("소유자가 아닌 사용자 요청 시 FORBIDDEN 예외 — 권한 오류")
            void fail_notOwner() {
                VenueSeat seat = availableSeat();
                Venue venue = emptyVenue();
                given(venueSeatRepository.findById(SEAT_ID)).willReturn(Optional.of(seat));
                given(venueRepository.findVenueBySectionId(SECTION_ID)).willReturn(Optional.of(venue));

                assertThatThrownBy(() -> venueCommandService.markSeatBroken(
                    OTHER_ID, new UpdateVenueSeatStatusCommand(SEAT_ID, true)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException)e).getErrorCode())
                    .isEqualTo(CommonErrorCode.FORBIDDEN);
            }
        }

        // ── restoreSeat ──────────────────────────────────────────────

        @Nested
        @DisplayName("restoreSeat() — 좌석 복구")
        class RestoreSeat {

            /** BROKEN 상태 VenueSeat 픽스처 */
            private VenueSeat brokenSeat() {
                VenueSeat seat = VenueSeat.create(SECTION_ID, 1, 1);
                injectField(seat, "id", SEAT_ID);
                injectField(seat, "createdBy", OWNER_ID);
                seat.markBroken();
                return seat;
            }

            @Test
            @DisplayName("BROKEN → AVAILABLE 복구 성공")
            void success() {
                VenueSeat seat = brokenSeat();
                Venue venue = emptyVenue();
                given(venueSeatRepository.findById(SEAT_ID)).willReturn(Optional.of(seat));
                given(venueRepository.findVenueBySectionId(SECTION_ID)).willReturn(Optional.of(venue));

                VenueSeatResult result = venueCommandService.restoreSeat(
                    OWNER_ID, new UpdateVenueSeatStatusCommand(SEAT_ID, false));

                assertThat(result.physicalStatus()).isEqualTo(PhysicalStatus.AVAILABLE);
            }

            @Test
            @DisplayName("이미 AVAILABLE인 좌석 복구 시 SEAT_ALREADY_AVAILABLE 예외 — 도메인 규칙 위반")
            void fail_alreadyAvailable() {
                VenueSeat seat = VenueSeat.create(SECTION_ID, 1, 1);
                injectField(seat, "id", SEAT_ID);
                injectField(seat, "createdBy", OWNER_ID);
                Venue venue = emptyVenue();
                given(venueSeatRepository.findById(SEAT_ID)).willReturn(Optional.of(seat));
                given(venueRepository.findVenueBySectionId(SECTION_ID)).willReturn(Optional.of(venue));

                assertThatThrownBy(() -> venueCommandService.restoreSeat(
                    OWNER_ID, new UpdateVenueSeatStatusCommand(SEAT_ID, false)))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.SEAT_ALREADY_AVAILABLE);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // VenueQueryService
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("VenueQueryService")
    class QueryServiceTest {

        @Mock
        private VenueRepository venueRepository;
        @Mock
        private VenueSeatRepository venueSeatRepository;
        @Mock
        private VenueQueryRepository venueQueryRepository;
        @Mock
        private SectionQueryRepository sectionQueryRepository;

        @InjectMocks
        private VenueQueryService venueQueryService;

        private Venue venueWithSeatedSection() {
            Venue venue = Venue.create("올림픽홀", "서울시 송파구 올림픽로 424");
            injectField(venue, "id", VENUE_ID);
            injectField(venue, "createdBy", OWNER_ID);
            Section section = venue.addSection(SeatType.SEATED, "A구역", 10, 12, null);
            injectField(section, "id", SECTION_ID);
            return venue;
        }

        // ── getVenue ─────────────────────────────────────────────────

        @Nested
        @DisplayName("getVenue() — 공연장 단건 조회")
        class GetVenue {

            @Test
            @DisplayName("정상 조회 성공")
            void success() {
                Venue venue = venueWithSeatedSection();
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.of(venue));

                VenueResult result = venueQueryService.getVenue(VENUE_ID);

                assertThat(result.id()).isEqualTo(VENUE_ID);
                assertThat(result.sections()).hasSize(1);
            }

            @Test
            @DisplayName("존재하지 않는 venueId 시 VENUE_NOT_FOUND 예외 — 외부 의존성 실패")
            void fail_notFound() {
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.empty());

                assertThatThrownBy(() -> venueQueryService.getVenue(VENUE_ID))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.VENUE_NOT_FOUND);
            }

            @Test
            @DisplayName("venueId null 시 INVALID_VENUE_ID 예외 — 입력값 유효성 오류")
            void fail_nullVenueId() {
                assertThatThrownBy(() -> venueQueryService.getVenue(null))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.INVALID_VENUE_ID);
            }
        }

        // ── getSeat ──────────────────────────────────────────────────

        @Nested
        @DisplayName("getSeat() — 좌석 단건 조회")
        class GetSeat {

            @Test
            @DisplayName("정상 조회 성공")
            void success() {
                VenueSeat seat = VenueSeat.create(SECTION_ID, 1, 1);
                injectField(seat, "id", SEAT_ID);
                given(venueSeatRepository.findById(SEAT_ID)).willReturn(Optional.of(seat));

                VenueSeatResult result = venueQueryService.getSeat(SEAT_ID);

                assertThat(result.id()).isEqualTo(SEAT_ID);
                assertThat(result.physicalStatus()).isEqualTo(PhysicalStatus.AVAILABLE);
            }

            @Test
            @DisplayName("존재하지 않는 seatId 시 SEAT_NOT_FOUND 예외 — 외부 의존성 실패")
            void fail_notFound() {
                given(venueSeatRepository.findById(SEAT_ID)).willReturn(Optional.empty());

                assertThatThrownBy(() -> venueQueryService.getSeat(SEAT_ID))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.SEAT_NOT_FOUND);
            }

            @Test
            @DisplayName("seatId null 시 INVALID_SEAT_ID 예외")
            void fail_nullSeatId() {
                assertThatThrownBy(() -> venueQueryService.getSeat(null))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.INVALID_SEAT_ID);
            }
        }

        // ── validateSectionBelongsToVenue ────────────────────────────

        @Nested
        @DisplayName("validateSectionBelongsToVenue() — 구역 소속 검증")
        class ValidateSectionBelongsToVenue {

            @Test
            @DisplayName("구역이 공연장에 속할 때 정상 통과")
            void success() {
                Venue venue = venueWithSeatedSection(); // SECTION_ID 포함
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.of(venue));

                assertThatCode(() ->
                    venueQueryService.validateSectionBelongsToVenue(VENUE_ID, SECTION_ID))
                    .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("공연장에 속하지 않는 구역 시 SECTION_NOT_FOUND 예외 — 도메인 규칙 위반")
            void fail_sectionNotInVenue() {
                Venue venue = venueWithSeatedSection(); // SECTION_ID만 보유
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.of(venue));
                UUID unknownSectionId = UUID.randomUUID();

                assertThatThrownBy(() ->
                    venueQueryService.validateSectionBelongsToVenue(VENUE_ID, unknownSectionId))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.SECTION_NOT_FOUND);
            }
        }

        // ── validateSeatBelongsToSection ─────────────────────────────

        @Nested
        @DisplayName("validateSeatBelongsToSection() — 좌석 소속 검증")
        class ValidateSeatBelongsToSection {

            @Test
            @DisplayName("좌석이 구역에 속할 때 정상 통과")
            void success() {
                VenueSeat seat = VenueSeat.create(SECTION_ID, 1, 1);
                injectField(seat, "id", SEAT_ID);
                given(venueSeatRepository.findById(SEAT_ID)).willReturn(Optional.of(seat));

                assertThatCode(() ->
                    venueQueryService.validateSeatBelongsToSection(SECTION_ID, SEAT_ID))
                    .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("좌석이 다른 구역 소속일 때 SEAT_NOT_FOUND 예외 — 도메인 규칙 위반")
            void fail_seatNotInSection() {
                UUID otherSectionId = UUID.randomUUID();
                VenueSeat seat = VenueSeat.create(otherSectionId, 1, 1); // 다른 구역
                injectField(seat, "id", SEAT_ID);
                given(venueSeatRepository.findById(SEAT_ID)).willReturn(Optional.of(seat));

                assertThatThrownBy(() ->
                    venueQueryService.validateSeatBelongsToSection(SECTION_ID, SEAT_ID))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.SEAT_NOT_FOUND);
            }
        }

        // ── searchVenues ─────────────────────────────────────────────

        @Nested
        @DisplayName("searchVenues() — 목록 조회")
        class SearchVenues {

            @Test
            @DisplayName("정상 조회")
            void success() {
                given(venueQueryRepository.findBySpec(any()))
                    .willReturn(new PagedResult<>(List.of(), 0L, 0, 0, 20));

                assertThatCode(() -> venueQueryService.searchVenues(
                    new VenueSearchQuery(null, null, null, 0, 20)))
                    .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("pageSize 0 이하 시 INVALID_PAGE_SIZE 예외 — 경계값")
            void fail_zeroPageSize() {
                assertThatThrownBy(() -> venueQueryService.searchVenues(
                    new VenueSearchQuery(null, null, null, 0, 0)))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.INVALID_PAGE_SIZE);
            }

            @Test
            @DisplayName("pageNumber 음수 시 INVALID_PAGE_NUMBER 예외 — 경계값")
            void fail_negativePageNumber() {
                assertThatThrownBy(() -> venueQueryService.searchVenues(
                    new VenueSearchQuery(null, null, null, -1, 20)))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.INVALID_PAGE_NUMBER);
            }

            @Test
            @DisplayName("query null 시 INVALID_SEARCH_QUERY 예외")
            void fail_nullQuery() {
                assertThatThrownBy(() -> venueQueryService.searchVenues(null))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.INVALID_SEARCH_QUERY);
            }
        }

        // ── getVenueValidation ───────────────────────────────────────

        @Nested
        @DisplayName("getVenueValidation() — venue 검증 묶음 조회")
        class GetVenueValidation {

            @Test
            @DisplayName("SEATED 타입 — totalCapacity = rowCount × colCount 합산")
            void success_seated() {
                Venue venue = venueWithSeatedSection(); // 10×12 = 120석
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.of(venue));

                var result = venueQueryService.getVenueValidation(VENUE_ID, SeatType.SEATED);

                assertThat(result.totalCapacity()).isEqualTo(120);
                assertThat(result.sections()).hasSize(1);
            }

            @Test
            @DisplayName("해당 타입 구역이 없을 때 totalCapacity = 0, sections = []")
            void success_noMatchingSection() {
                Venue venue = venueWithSeatedSection(); // SEATED 구역만 보유
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.of(venue));

                var result = venueQueryService.getVenueValidation(VENUE_ID, SeatType.STANDING);

                assertThat(result.totalCapacity()).isEqualTo(0);
                assertThat(result.sections()).isEmpty();
            }

            @Test
            @DisplayName("seatType null 시 INVALID_SECTION_TYPE 예외")
            void fail_nullSeatType() {
                assertThatThrownBy(() ->
                    venueQueryService.getVenueValidation(VENUE_ID, null))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.INVALID_SECTION_TYPE);
            }

            @Test
            @DisplayName("존재하지 않는 venueId 시 VENUE_NOT_FOUND 예외")
            void fail_notFound() {
                given(venueRepository.findByIdWithSections(VENUE_ID))
                    .willReturn(Optional.empty());

                assertThatThrownBy(() ->
                    venueQueryService.getVenueValidation(VENUE_ID, SeatType.SEATED))
                    .isInstanceOf(VenueException.class)
                    .extracting(e -> ((VenueException)e).getErrorCode())
                    .isEqualTo(VenueErrorCode.VENUE_NOT_FOUND);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 공통 리플렉션 헬퍼
    // ══════════════════════════════════════════════════════════════════

    /**
     * 리플렉션으로 객체 필드에 값을 주입한다.
     * 사용처:
     * - Venue.id, createdBy      : @GeneratedValue / BaseUserEntity 필드, 영속화 전 null
     * - Section.id               : @GeneratedValue, 영속화 전 null
     *                              → deleteSection()의 s.getId().equals() NPE 방지
     * - VenueSeat.id, createdBy  : @GeneratedValue / BaseUserEntity 필드, 영속화 전 null
     *                              → checkSeatOwner() NPE 방지
     * 프로덕션 코드 변경 없이 단위 테스트 픽스처 구성에만 사용한다.
     */
    private static void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("필드 주입 실패: " + fieldName, e);
        }
    }

    /**
     * 상속 계층을 포함하여 필드를 탐색한다.
     * BaseUserEntity.createdBy, BaseEntity.id 등 부모 클래스 필드 대응.
     */
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
