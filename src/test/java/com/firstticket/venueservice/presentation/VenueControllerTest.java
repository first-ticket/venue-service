package com.firstticket.venueservice.presentation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.restdocs.headers.HeaderDocumentation.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.*;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.firstticket.common.exception.GlobalExceptionHandler;
import com.firstticket.venueservice.application.dto.result.SectionResult;
import com.firstticket.venueservice.application.dto.result.VenueResult;
import com.firstticket.venueservice.application.dto.result.VenueSeatResult;
import com.firstticket.venueservice.application.dto.result.VenueSummaryResult;
import com.firstticket.venueservice.application.service.VenueCommandService;
import com.firstticket.venueservice.application.service.VenueQueryService;
import com.firstticket.venueservice.domain.PhysicalStatus;
import com.firstticket.venueservice.domain.SeatType;
import com.firstticket.venueservice.domain.exception.VenueErrorCode;
import com.firstticket.venueservice.domain.exception.VenueException;
import com.firstticket.venueservice.domain.query.PagedResult;

/**
 * VenueController 슬라이스 테스트.
 *
 * [테스트 규칙]
 * - @WebMvcTest 슬라이스 테스트 (@SpringBootTest 미사용)
 * - @MockitoBean 사용 (Spring Boot 3.4+ — @MockBean deprecated)
 * - REST Docs 스니펫 생성 필수 (표현 계층 규칙)
 * - 독립 실행 가능
 *
 * [AuthContext 처리 전략] — README 기준
 * AuthContext는 HttpServletRequest에서 X-User-Id, X-User-Role 헤더를 직접 읽는다.
 * - X-User-Id  누락 → 401 UNAUTHORIZED
 * - X-User-Role 누락 → 401 UNAUTHORIZED
 * - X-User-Role 값이 UserRole enum에 없는 값 → 401 UNAUTHORIZED
 * - X-User-Role = "CUSTOMER" → UserRole.CUSTOMER 파싱 성공 → checkHostOrAdmin()이 403 던짐
 * 따라서 403 케이스는 X-User-Id 헤더도 반드시 함께 전달해야 한다.
 *
 * [응답 구조] — common ApiResponse ex.
 * {
 *   "success": true,
 *   "code": "VENUE_FOUND",
 *   "message": "공연장을 조회했습니다",
 *   "timestamp": "...",
 *   "data": { ... }
 * }
 */
@ExtendWith({RestDocumentationExtension.class, SpringExtension.class})
@WebMvcTest({VenueController.class, GlobalExceptionHandler.class})
@TestPropertySource(
    properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
    })
class VenueControllerTest {

    private MockMvc mockMvc;

    @MockitoBean
    private VenueCommandService venueCommandService;

    @MockitoBean
    private VenueQueryService venueQueryService;

    // ── 공통 픽스처 UUID ─────────────────────────────────────────────
    private static final UUID VENUE_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SECTION_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID SEAT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @BeforeEach
    void setUp(WebApplicationContext context, RestDocumentationContextProvider restDoc) {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new VenueController(venueCommandService, venueQueryService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .apply(documentationConfiguration(restDoc))
            .build();
    }

    // ── 픽스처 빌더 ──────────────────────────────────────────────────

    /** SEATED 구역 결과 픽스처 (rowCount=10, colCount=12 → seatCount=120) */
    private SectionResult seatedSection() {
        return new SectionResult(SECTION_ID, "A구역", SeatType.SEATED, 10, 12, null);
    }

    /** 공연장 단건 결과 픽스처 */
    private VenueResult venueResult() {
        return new VenueResult(VENUE_ID, "올림픽홀", "서울시 송파구 올림픽로 424", List.of(seatedSection()));
    }

    private VenueResult updatedVenueResult() {
        return new VenueResult(VENUE_ID, "새로운홀", "서울시 송파구 올림픽로 424", List.of(seatedSection()));
    }

    /** 공연장 목록 요약 결과 픽스처 */
    private VenueSummaryResult venueSummaryResult() {
        return new VenueSummaryResult(VENUE_ID, "올림픽홀", "서울시 송파구 올림픽로 424", 1);
    }

    /** 좌석 결과 픽스처 */
    private VenueSeatResult seatResult(PhysicalStatus status) {
        return new VenueSeatResult(SEAT_ID, SECTION_ID, 1, 3, status);
    }

    // ── 공통 REST Docs 응답 필드 (ApiResponse wrapper) ───────────────

    private static org.springframework.restdocs.payload.FieldDescriptor[] apiResponseFields(
        org.springframework.restdocs.payload.FieldDescriptor... dataFields) {
        var base = List.of(fieldWithPath("success").description("성공 여부"), fieldWithPath("code").description("응답 코드"),
            fieldWithPath("message").description("응답 메시지"), fieldWithPath("timestamp").description("응답 시각"));

        var all = new java.util.ArrayList<>(base);

        if (dataFields.length > 0) {
            all.addAll(List.of(dataFields));
        } else {
            // 인자가 하나도 없는 에러 케이스(400, 403 등)일 경우:
            // 혹시 모를 'data' 키 포함 여부를 방어하기 위해 optional과 타입을 명시적으로 지정
            all.add(fieldWithPath("data").type(org.springframework.restdocs.payload.JsonFieldType.OBJECT)
                .description("응답 데이터 (에러 발생 시 null 혹은 미포함)")
                .optional());
        }

        return all.toArray(new org.springframework.restdocs.payload.FieldDescriptor[0]);
    }

    // ══════════════════════════════════════════════════════════════════
    // 공연장 (Venue)
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/venues — 공연장 등록")
    class CreateVenue {

        @Test
        @DisplayName("구역 포함 공연장 등록 성공")
        void success_withSections() throws Exception {
            given(venueCommandService.createVenue(any(), any(), any())).willReturn(venueResult());

            mockMvc.perform(post("/api/v1/venues").header("X-User-Id", USER_ID)
                    .header("X-User-Role", "ADMIN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "올림픽홀",
                          "address": "서울시 송파구 올림픽로 424",
                          "sections": [
                            { "name": "A구역", "type": "SEATED", "rowCount": 10, "colCount": 12 }
                          ]
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(VENUE_ID.toString()))
                .andExpect(jsonPath("$.data.name").value("올림픽홀"))
                .andExpect(jsonPath("$.data.sections[0].type").value("SEATED"))
                .andDo(document("venue/create",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Id").description("Gateway가 주입하는 사용자 UUID"),
                        headerWithName("X-User-Role").description("사용자 역할 (ADMIN | HOST)")),
                    requestFields(
                        fieldWithPath("name").description("공연장 이름"),
                        fieldWithPath("address").description("공연장 주소"),
                        fieldWithPath("sections").description("구역 목록 (생략 가능)").optional(),
                        fieldWithPath("sections[].name").description("구역명"),
                        fieldWithPath("sections[].type").description("SEATED | STANDING | FREE"),
                        fieldWithPath("sections[].rowCount").type(JsonFieldType.NUMBER).description("행 수 (SEATED 필수)"),
                        fieldWithPath("sections[].colCount").type(JsonFieldType.NUMBER).description("열 수 (SEATED 필수)"),
                        fieldWithPath("sections[].capacity").type(JsonFieldType.NUMBER)
                            .description("수용 인원 (STANDING·FREE 필수)")
                            .optional()), responseFields(
                        apiResponseFields(
                            fieldWithPath("data.id").description("생성된 공연장 UUID"),
                            fieldWithPath("data.name").description("공연장 이름"),
                            fieldWithPath("data.address").description("공연장 주소"),
                            fieldWithPath("data.sections[].id").description("구역 UUID"),
                            fieldWithPath("data.sections[].name").description("구역명"),
                            fieldWithPath("data.sections[].type").description("구역 타입"),
                            fieldWithPath("data.sections[].rowCount").type(JsonFieldType.NUMBER)
                                .description("행 수 (SEATED 전용)"),
                            fieldWithPath("data.sections[].colCount").type(JsonFieldType.NUMBER)
                                .description("열 수 (SEATED 전용)"),
                            fieldWithPath("data.sections[].capacity").type(JsonFieldType.NUMBER)
                                .description("수용 인원 (STANDING·FREE 전용)")
                                .optional(), fieldWithPath("data.sections[].seatCount").type(JsonFieldType.NUMBER)
                                .description("총 좌석 수")))));
        }

        @Test
        @DisplayName("공연장 이름 누락 시 400 — 입력값 유효성 오류")
        void fail_missingName() throws Exception {
            mockMvc.perform(post("/api/v1/venues").header("X-User-Id", USER_ID)
                    .header("X-User-Role", "ADMIN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "address": "서울시 송파구 올림픽로 424" }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andDo(
                    document("venue/create-400",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(apiResponseFields())));
        }

        @Test
        @DisplayName("CUSTOMER 역할로 요청 시 403 — 권한 오류")
        void fail_customerRole() throws Exception {
            // X-User-Id 누락 시 AuthContext가 401을 먼저 던지므로 반드시 함께 전달
            // X-User-Role = "CUSTOMER" → UserRole.CUSTOMER 파싱 → checkHostOrAdmin()이 403

            String body = """
                { "name": "올림픽홀", "address": "서울시 송파구 올림픽로 424" }
                """;

            mockMvc.perform(post("/api/v1/venues").header("X-User-Id", USER_ID)
                    .header("X-User-Role", "CUSTOMER") // 이 헤더를 AuthContext가 읽는다면 OK
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isForbidden()) // 이제 Handler가 예외를 잡아 403을 줍니다.
                .andDo(
                    document("venue/create-403",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(apiResponseFields())));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/venues — 공연장 목록 조회")
    class SearchVenues {

        @Test
        @DisplayName("키워드·정렬·페이지네이션으로 목록 조회 성공")
        void success() throws Exception {
            PagedResult<VenueSummaryResult> paged = new PagedResult<>(List.of(venueSummaryResult()), 1L, 1, 0, 20);
            given(venueQueryService.searchVenues(any())).willReturn(paged);

            mockMvc.perform(get("/api/v1/venues").param("keyword", "올림픽")
                    .param("sort", "name")
                    .param("direction", "asc")
                    .param("page", "0")
                    .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(VENUE_ID.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andDo(document("venue/search",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    queryParameters(parameterWithName("keyword").description("이름 키워드 (선택)").optional(),
                        parameterWithName("sort").description("정렬 필드 — name | createdAt (선택)").optional(),
                        parameterWithName("direction").description("asc | desc (선택)").optional(),
                        parameterWithName("page").description("페이지 번호 (0부터)"),
                        parameterWithName("size").description("페이지 크기")),
                    responseFields(
                        apiResponseFields(fieldWithPath("data.content[].id").description("공연장 UUID"),
                            fieldWithPath("data.content[].name").description("공연장 이름"),
                            fieldWithPath("data.content[].address").description("공연장 주소"),
                            fieldWithPath("data.content[].sectionCount").description("구역 수"),
                            fieldWithPath("data.totalElements").description("전체 요소 수"),
                            fieldWithPath("data.totalPages").description("전체 페이지 수"),
                            fieldWithPath("data.size").description("페이지 크기"),
                            fieldWithPath("data.number").description("현재 페이지 번호")
                        ))));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/venues/{venueId} — 공연장 상세 조회")
    class GetVenue {

        @Test
        @DisplayName("공연장 상세 조회 성공")
        void success() throws Exception {
            given(venueQueryService.getVenue(VENUE_ID)).willReturn(venueResult());

            mockMvc.perform(get("/api/v1/venues/{venueId}", VENUE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(VENUE_ID.toString()))
                .andExpect(jsonPath("$.data.sections[0].id").value(SECTION_ID.toString()))
                .andDo(document("venue/get",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("venueId").description("조회할 공연장 UUID")),
                    responseFields(
                        apiResponseFields(
                            fieldWithPath("data.id").description("공연장 UUID"),
                            fieldWithPath("data.name").description("공연장 이름"),
                            fieldWithPath("data.address").description("공연장 주소"),
                            fieldWithPath("data.sections[].id").description("구역 UUID"),
                            fieldWithPath("data.sections[].name").description("구역명"),
                            fieldWithPath("data.sections[].type").description("구역 타입"),
                            fieldWithPath("data.sections[].rowCount").description("행 수 (SEATED)").optional(),
                            fieldWithPath("data.sections[].colCount").description("열 수 (SEATED)").optional(),
                            fieldWithPath("data.sections[].capacity").description("수용 인원 (STANDING·FREE)").optional(),
                            fieldWithPath("data.sections[].seatCount").description("총 좌석 수")
                        ))));
        }

        @Test
        @DisplayName("존재하지 않는 공연장 조회 시 404 — 외부 의존성 실패")
        void fail_notFound() throws Exception {
            given(venueQueryService.getVenue(VENUE_ID))
                .willThrow(new VenueException(VenueErrorCode.VENUE_NOT_FOUND));

            mockMvc.perform(get("/api/v1/venues/{venueId}", VENUE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andDo(document("venue/get-404",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("venueId").description("조회할 공연장 UUID")),
                    responseFields(apiResponseFields())));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/venues/{venueId} — 공연장 수정")
    class UpdateVenue {

        @Test
        @DisplayName("이름만 수정 성공 — null 필드는 기존 값 유지")
        void success_partialUpdate() throws Exception {
            VenueResult expectedResult = updatedVenueResult();

            given(venueCommandService.updateVenue(any(), any()))
                .willReturn(expectedResult);

            mockMvc.perform(patch("/api/v1/venues/{venueId}", expectedResult.id())
                    .header("X-User-Id", USER_ID)
                    .header("X-User-Role", "ADMIN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "새로운홀" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(expectedResult.id().toString()))
                .andExpect(jsonPath("$.data.name").value(expectedResult.name()))
                .andExpect(jsonPath("$.data.address").value(expectedResult.address()))
                .andDo(document("venue/update",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Id").description("사용자 UUID"),
                        headerWithName("X-User-Role").description("ADMIN | HOST")),
                    pathParameters(parameterWithName("venueId").description("수정할 공연장 UUID")),
                    requestFields(
                        fieldWithPath("name").description("새 이름 (null이면 기존 값 유지)")
                            .type(JsonFieldType.STRING)
                            .optional(),
                        fieldWithPath("address").description("새 주소 (null이면 기존 값 유지)")
                            .type(JsonFieldType.STRING)
                            .optional()
                    ),
                    responseFields(
                        apiResponseFields(
                            fieldWithPath("data.id").description("공연장 UUID"),
                            fieldWithPath("data.name").description("공연장 이름"),
                            fieldWithPath("data.address").description("공연장 주소"),
                            fieldWithPath("data.sections[].id").description("구역 UUID"),
                            fieldWithPath("data.sections[].name").description("구역명"),
                            fieldWithPath("data.sections[].type").description("구역 타입"),
                            fieldWithPath("data.sections[].rowCount").optional().description("행 수"),
                            fieldWithPath("data.sections[].colCount").optional().description("열 수"),
                            fieldWithPath("data.sections[].capacity").optional().description("수용 인원"),
                            fieldWithPath("data.sections[].seatCount").description("총 좌석 수")
                        ))));
        }

        @Test
        @DisplayName("존재하지 않는 공연장 수정 시 404 — 외부 의존성 실패")
        void fail_notFound() throws Exception {
            willThrow(new VenueException(VenueErrorCode.VENUE_NOT_FOUND)).given(venueCommandService)
                .updateVenue(any(), any());

            mockMvc.perform(patch("/api/v1/venues/{venueId}", VENUE_ID).header("X-User-Id", USER_ID)
                    .header("X-User-Role", "ADMIN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "없는홀" }
                        """))
                .andExpect(status().isNotFound())
                .andDo(document("venue/update-404",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("venueId").description("공연장 UUID")),
                    responseFields(apiResponseFields())));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/venues/{venueId} — 공연장 삭제")
    class DeleteVenue {

        @Test
        @DisplayName("공연장 삭제 성공 — 204 No Content")
        void success() throws Exception {
            willDoNothing().given(venueCommandService)
                .deleteVenue(any(), eq(VENUE_ID));

            mockMvc.perform(
                    delete("/api/v1/venues/{venueId}", VENUE_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent())
                .andDo(document("venue/delete", preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Id").description("사용자 UUID"),
                        headerWithName("X-User-Role").description("ADMIN 전용")),
                    pathParameters(
                        parameterWithName("venueId").description("삭제할 공연장 UUID"))));
        }

        @Test
        @DisplayName("활성 프로그램이 있는 공연장 삭제 시 409 — 도메인 규칙 위반")
        void fail_hasActivePrograms() throws Exception {
            willThrow(new VenueException(VenueErrorCode.VENUE_HAS_PROGRAMS))
                .given(venueCommandService)
                .deleteVenue(any(), eq(VENUE_ID));

            mockMvc.perform(
                    delete("/api/v1/venues/{venueId}", VENUE_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andDo(
                    document("venue/delete-409",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("venueId").description("공연장 UUID")),
                        responseFields(
                            fieldWithPath("success").description("false"),
                            fieldWithPath("code").description("에러 코드"),
                            fieldWithPath("message").description("오류 메시지"),
                            fieldWithPath("timestamp").description("응답 시각"))));
        }

        @Test
        @DisplayName("HOST 역할로 삭제 시 403 — 권한 오류 (ADMIN 전용)")
        void fail_hostRole() throws Exception {
            mockMvc.perform(
                    delete("/api/v1/venues/{venueId}", VENUE_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isForbidden())
                .andDo(
                    document("venue/delete-403",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("venueId").description("공연장 UUID"))));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 구역 (Section)
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/venues/{venueId}/sections — 구역 등록")
    class CreateSection {

        @Test
        @DisplayName("SEATED 타입 구역 등록 성공")
        void success_seated() throws Exception {
            given(venueCommandService.createSection(any(), any()))
                .willReturn(venueResult());

            mockMvc.perform(
                    post("/api/v1/venues/{venueId}/sections", VENUE_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "name": "A구역", "type": "SEATED", "rowCount": 10, "colCount": 12 }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sections[0].type").value("SEATED"))
                .andDo(
                    document("venue/section/create-seated",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(
                            headerWithName("X-User-Id").description("사용자 UUID"),
                            headerWithName("X-User-Role").description("ADMIN | HOST")),
                        pathParameters(
                            parameterWithName("venueId").description("구역을 추가할 공연장 UUID")),
                        requestFields(
                            fieldWithPath("name").description("구역명"),
                            fieldWithPath("type").description("SEATED | STANDING | FREE"),
                            fieldWithPath("rowCount").description("행 수 (SEATED 필수)").optional(),
                            fieldWithPath("colCount").description("열 수 (SEATED 필수)").optional(),
                            fieldWithPath("capacity").description("수용 인원 (STANDING·FREE 필수)")
                                .type(JsonFieldType.NUMBER)
                                .optional())));
        }

        @Test
        @DisplayName("STANDING 타입 구역 등록 성공")
        void success_standing() throws Exception {
            given(venueCommandService.createSection(any(), any()))
                .willReturn(venueResult());

            mockMvc.perform(post("/api/v1/venues/{venueId}/sections", VENUE_ID).header("X-User-Id", USER_ID)
                    .header("X-User-Role", "HOST")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "스탠딩구역", "type": "STANDING", "capacity": 300 }
                        """))
                .andExpect(status().isCreated())
                .andDo(document("venue/section/create-standing",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("venueId").description("공연장 UUID"))));
        }

        @Test
        @DisplayName("구역명 누락 시 400 — 입력값 유효성 오류")
        void fail_missingName() throws Exception {
            mockMvc.perform(
                    post("/api/v1/venues/{venueId}/sections", VENUE_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "type": "SEATED", "rowCount": 10, "colCount": 12 }
                            """))
                .andExpect(status().isBadRequest())
                .andDo(
                    document("venue/section/create-400",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("venueId").description("공연장 UUID"))));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/venues/{venueId}/sections — 구역 목록 조회")
    class GetSections {

        @Test
        @DisplayName("구역 목록 조회 성공")
        void success() throws Exception {
            given(venueQueryService.getVenue(VENUE_ID)).willReturn(venueResult());

            mockMvc.perform(
                    get("/api/v1/venues/{venueId}/sections", VENUE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(SECTION_ID.toString()))
                .andDo(
                    document("venue/section/list",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("venueId").description("공연장 UUID")),
                        responseFields(
                            apiResponseFields(
                                fieldWithPath("data[].id").description("구역 UUID"),
                                fieldWithPath("data[].name").description("구역명"),
                                fieldWithPath("data[].type").description("구역 타입"),
                                fieldWithPath("data[].rowCount").optional().description("행 수 (SEATED)"),
                                fieldWithPath("data[].colCount").optional().description("열 수 (SEATED)"),
                                fieldWithPath("data[].capacity").optional().description("수용 인원 (STANDING·FREE)"),
                                fieldWithPath("data[].seatCount").description("총 좌석 수")))));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/venues/{venueId}/sections/{sectionId} — 구역 상세 조회")
    class GetSection {

        @Test
        @DisplayName("구역 상세 조회 성공")
        void success() throws Exception {
            given(venueQueryService.getVenue(VENUE_ID))
                .willReturn(venueResult());

            mockMvc.perform(
                    get("/api/v1/venues/{venueId}/sections/{sectionId}",
                        VENUE_ID,
                        SECTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(SECTION_ID.toString()))
                .andDo(
                    document("venue/section/get",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("venueId").description("공연장 UUID"),
                            parameterWithName("sectionId").description("조회할 구역 UUID")),
                        responseFields(
                            apiResponseFields(
                                fieldWithPath("data.id").description("구역 UUID"),
                                fieldWithPath("data.name").description("구역명"),
                                fieldWithPath("data.type").description("구역 타입"),
                                fieldWithPath("data.rowCount").optional().description("행 수 (SEATED)"),
                                fieldWithPath("data.colCount").optional().description("열 수 (SEATED)"),
                                fieldWithPath("data.capacity").optional().description("수용 인원 (STANDING·FREE)"),
                                fieldWithPath("data.seatCount").description("총 좌석 수")))));
        }

        @Test
        @DisplayName("공연장에 속하지 않는 구역 조회 시 404 — 도메인 규칙 위반")
        void fail_sectionNotInVenue() throws Exception {
            UUID unknownSectionId = UUID.randomUUID();

            given(venueQueryService.getVenue(VENUE_ID))
                .willReturn(venueResult());

            mockMvc.perform(
                    get("/api/v1/venues/{venueId}/sections/{sectionId}",
                        VENUE_ID,
                        unknownSectionId))
                .andExpect(status().isNotFound())
                .andDo(
                    document("venue/section/get-404",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("venueId").description("공연장 UUID"),
                            parameterWithName("sectionId").description("구역 UUID"))));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/venues/{venueId}/sections/{sectionId} — 구역 삭제")
    class DeleteSection {

        @Test
        @DisplayName("구역 삭제 성공 — 204 No Content")
        void success() throws Exception {
            willDoNothing().given(venueCommandService)
                .deleteSection(any(), eq(VENUE_ID), eq(SECTION_ID));

            mockMvc.perform(
                    delete("/api/v1/venues/{venueId}/sections/{sectionId}",
                        VENUE_ID,
                        SECTION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent())
                .andDo(
                    document("venue/section/delete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(
                            headerWithName("X-User-Id").description("사용자 UUID"),
                            headerWithName("X-User-Role").description("ADMIN 전용")),
                        pathParameters(
                            parameterWithName("venueId").description("공연장 UUID"),
                            parameterWithName("sectionId").description("삭제할 구역 UUID"))));
        }

        @Test
        @DisplayName("HOST 역할로 구역 삭제 시 403 — 권한 오류 (ADMIN 전용)")
        void fail_hostRole() throws Exception {
            mockMvc.perform(
                    delete("/api/v1/venues/{venueId}/sections/{sectionId}",
                        VENUE_ID,
                        SECTION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isForbidden())
                .andDo(
                    document("venue/section/delete-403",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("venueId").description("공연장 UUID"),
                            parameterWithName("sectionId").description("구역 UUID"))));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 좌석 (VenueSeat)
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/venues/{venueId}/sections/{sectionId}/seats — 좌석 목록 조회")
    class GetSeats {

        @Test
        @DisplayName("좌석 목록 조회 성공")
        void success() throws Exception {
            willDoNothing().given(venueQueryService)
                .validateSectionBelongsToVenue(VENUE_ID, SECTION_ID);

            given(venueQueryService.getSeatsBySection(SECTION_ID))
                .willReturn(
                    List.of(seatResult(PhysicalStatus.AVAILABLE)));

            mockMvc.perform(
                    get("/api/v1/venues/{venueId}/sections/{sectionId}/seats",
                        VENUE_ID,
                        SECTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(SEAT_ID.toString()))
                .andExpect(jsonPath("$.data[0].row").value(1))
                .andExpect(jsonPath("$.data[0].col").value(3))
                .andExpect(jsonPath("$.data[0].physicalStatus").value("AVAILABLE"))
                .andDo(document("venue/seat/list",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("venueId").description("공연장 UUID"),
                        parameterWithName("sectionId").description("구역 UUID")),
                    responseFields(
                        apiResponseFields(
                            fieldWithPath("data[].id").description("좌석 UUID"),
                            fieldWithPath("data[].sectionId").description("소속 구역 UUID"),
                            fieldWithPath("data[].row").description("행 번호"),
                            fieldWithPath("data[].col").description("열 번호"),
                            fieldWithPath("data[].physicalStatus").description("AVAILABLE | BROKEN")))));
        }

        @Test
        @DisplayName("공연장에 속하지 않는 구역으로 요청 시 404 — 외부 의존성 실패")
        void fail_sectionNotInVenue() throws Exception {
            willThrow(new VenueException(VenueErrorCode.SECTION_NOT_FOUND))
                .given(venueQueryService)
                .validateSectionBelongsToVenue(VENUE_ID, SECTION_ID);

            mockMvc.perform(
                    get("/api/v1/venues/{venueId}/sections/{sectionId}/seats",
                        VENUE_ID,
                        SECTION_ID))
                .andExpect(status().isNotFound())
                .andDo(
                    document("venue/seat/list-404",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("venueId").description("공연장 UUID"),
                            parameterWithName("sectionId").description("구역 UUID"))));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/venues/{venueId}/sections/{sectionId}/seats/{seatId} — 좌석 상세 조회")
    class GetSeat {

        @Test
        @DisplayName("좌석 상세 조회 성공")
        void success() throws Exception {
            willDoNothing().given(venueQueryService)
                .validateSectionBelongsToVenue(VENUE_ID, SECTION_ID);

            willDoNothing().given(venueQueryService)
                .validateSeatBelongsToSection(SECTION_ID, SEAT_ID);

            given(venueQueryService.getSeat(SEAT_ID))
                .willReturn(seatResult(PhysicalStatus.AVAILABLE));

            mockMvc.perform(
                    get("/api/v1/venues/{venueId}/sections/{sectionId}/seats/{seatId}",
                        VENUE_ID,
                        SECTION_ID,
                        SEAT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(SEAT_ID.toString()))
                .andExpect(jsonPath("$.data.row").value(1))
                .andExpect(jsonPath("$.data.col").value(3))
                .andDo(
                    document("venue/seat/get",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("venueId").description("공연장 UUID"),
                            parameterWithName("sectionId").description("구역 UUID"),
                            parameterWithName("seatId").description("좌석 UUID")), responseFields(
                            apiResponseFields(
                                fieldWithPath("data.id").description("좌석 UUID"),
                                fieldWithPath("data.sectionId").description("소속 구역 UUID"),
                                fieldWithPath("data.row").description("행 번호"),
                                fieldWithPath("data.col").description("열 번호"),
                                fieldWithPath("data.physicalStatus").description("AVAILABLE | BROKEN")))));
        }
    }

    @Nested
    @DisplayName("PATCH .../seats/{seatId}/status — 좌석 상태 변경")
    class UpdateSeatStatus {

        @Test
        @DisplayName("AVAILABLE → BROKEN 변경 성공")
        void success_markBroken() throws Exception {
            willDoNothing().given(venueQueryService)
                .validateSectionBelongsToVenue(VENUE_ID, SECTION_ID);

            willDoNothing().given(venueQueryService)
                .validateSeatBelongsToSection(SECTION_ID, SEAT_ID);

            given(venueCommandService.markSeatBroken(any(), any()))
                .willReturn(seatResult(PhysicalStatus.BROKEN));

            mockMvc.perform(
                    patch("/api/v1/venues/{venueId}/sections/{sectionId}/seats/{seatId}/status",
                        VENUE_ID,
                        SECTION_ID,
                        SEAT_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "physicalStatus": "BROKEN" }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.physicalStatus").value("BROKEN"))
                .andDo(
                    document("venue/seat/update-broken",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(
                            headerWithName("X-User-Id").description("사용자 UUID"),
                            headerWithName("X-User-Role").description("ADMIN 전용")),
                        pathParameters(
                            parameterWithName("venueId").description("공연장 UUID"),
                            parameterWithName("sectionId").description("구역 UUID"),
                            parameterWithName("seatId").description("좌석 UUID")),
                        requestFields(
                            fieldWithPath("physicalStatus").description("AVAILABLE | BROKEN")),
                        responseFields(
                            apiResponseFields(
                                fieldWithPath("data.id").description("좌석 UUID"),
                                fieldWithPath("data.sectionId").description("구역 UUID"),
                                fieldWithPath("data.row").description("행 번호"),
                                fieldWithPath("data.col").description("열 번호"),
                                fieldWithPath("data.physicalStatus").description("변경된 상태")))));
        }

        @Test
        @DisplayName("BROKEN → AVAILABLE 복구 성공")
        void success_restoreSeat() throws Exception {
            willDoNothing().given(venueQueryService)
                .validateSectionBelongsToVenue(VENUE_ID, SECTION_ID);

            willDoNothing().given(venueQueryService)
                .validateSeatBelongsToSection(SECTION_ID, SEAT_ID);

            given(venueCommandService.restoreSeat(any(), any()))
                .willReturn(seatResult(PhysicalStatus.AVAILABLE));

            mockMvc.perform(
                    patch("/api/v1/venues/{venueId}/sections/{sectionId}/seats/{seatId}/status",
                        VENUE_ID,
                        SECTION_ID,
                        SEAT_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "physicalStatus": "AVAILABLE" }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.physicalStatus").value("AVAILABLE"))
                .andDo(document("venue/seat/update-available",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("venueId").description("공연장 UUID"),
                        parameterWithName("sectionId").description("구역 UUID"),
                        parameterWithName("seatId").description("좌석 UUID"))));
        }

        @Test
        @DisplayName("이미 BROKEN인 좌석에 BROKEN 요청 시 400 — 도메인 규칙 위반")
        void fail_alreadyBroken() throws Exception {
            willDoNothing().given(venueQueryService)
                .validateSectionBelongsToVenue(VENUE_ID, SECTION_ID);

            willDoNothing().given(venueQueryService)
                .validateSeatBelongsToSection(SECTION_ID, SEAT_ID);

            willThrow(new VenueException(VenueErrorCode.SEAT_ALREADY_BROKEN))
                .given(venueCommandService)
                .markSeatBroken(any(), any());

            mockMvc.perform(
                    patch("/api/v1/venues/{venueId}/sections/{sectionId}/seats/{seatId}/status",
                        VENUE_ID,
                        SECTION_ID,
                        SEAT_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "physicalStatus": "BROKEN" }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andDo(
                    document("venue/seat/update-400-already-broken",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("venueId").description("공연장 UUID"),
                            parameterWithName("sectionId").description("구역 UUID"),
                            parameterWithName("seatId").description("좌석 UUID")),
                        responseFields(
                            fieldWithPath("success").description("false"),
                            fieldWithPath("code").description("에러 코드"),
                            fieldWithPath("message").description("오류 메시지"),
                            fieldWithPath("timestamp").description("응답 시각"))));
        }

        @Test
        @DisplayName("physicalStatus 누락 시 400 — 입력값 유효성 오류")
        void fail_missingPhysicalStatus() throws Exception {
            mockMvc.perform(
                    patch("/api/v1/venues/{venueId}/sections/{sectionId}/seats/{seatId}/status",
                        VENUE_ID,
                        SECTION_ID,
                        SEAT_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andDo(
                    document("venue/seat/update-400-missing",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("venueId").description("공연장 UUID"),
                            parameterWithName("sectionId").description("구역 UUID"),
                            parameterWithName("seatId").description("좌석 UUID"))));
        }
    }
}
