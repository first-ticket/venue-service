-- =====================================================
-- Venue Service — V1 초기 스키마
-- schema: program
-- Spring 설정: spring.jpa.properties.hibernate.default_schema=program
-- =====================================================


-- ── p_venue ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS p_venue
(
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    address    VARCHAR(255) NOT NULL,

    -- BaseUserEntity Auditing
    created_at TIMESTAMP    NOT NULL,
    created_by UUID         NOT NULL,
    updated_at TIMESTAMP,
    updated_by UUID,
    deleted_at TIMESTAMP, -- soft delete
    deleted_by UUID,

    CONSTRAINT pk_venue PRIMARY KEY (id)
    );

-- 공연장명 검색 인덱스
-- partial index: soft delete된 레코드 제외
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid WHERE c.relname = 'idx_venue_name' AND n.nspname = 'program') THEN
CREATE INDEX idx_venue_name ON program.p_venue (name) WHERE deleted_at IS NULL;
END IF;
END $$;


-- ── p_section ─────────────────────────────────────────
-- Section은 Venue 애그리거트 하위 엔티티
-- SectionRepository를 별도로 두지 않음
-- 수정 API 없음: 삭제 후 재등록 방식 사용
CREATE TABLE IF NOT EXISTS p_section
(
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    venue_id   UUID         NOT NULL,
    name       VARCHAR(100) NOT NULL,

    -- 구역 타입: SEATED | STANDING | FREE
    -- SEATED   : rowCount, colCount 사용 / capacity null
    -- STANDING : capacity 사용 / rowCount, colCount null
    -- FREE     : capacity 사용 / rowCount, colCount null
    type       VARCHAR(10)  NOT NULL,

    -- SEATED 전용: VenueSeat 자동 생성 시 사용 (rowCount × colCount)
    row_count  INTEGER,
    col_count  INTEGER,

    -- STANDING·FREE 전용: 최대 수용 인원 상한선
    -- 프로그램 등록 시 이 값을 초과하는 인원 지정 불가
    capacity   INTEGER,

    -- BaseUserEntity Auditing
    created_at TIMESTAMP    NOT NULL,
    created_by UUID         NOT NULL,
    updated_at TIMESTAMP,
    updated_by UUID,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT pk_section PRIMARY KEY (id),
    CONSTRAINT fk_section_venue
    FOREIGN KEY (venue_id) REFERENCES p_venue (id),
    CONSTRAINT chk_section_type
    CHECK (type IN ('SEATED', 'STANDING', 'FREE')),

    -- 타입별 필드 유효성 검증
    -- SEATED: rowCount, colCount 필수 / capacity null
    CONSTRAINT chk_section_seated
    CHECK (type != 'SEATED'
           OR (row_count IS NOT NULL AND col_count IS NOT NULL
           AND row_count > 0 AND col_count > 0
           AND capacity IS NULL)
    ) ,

    -- STANDING: capacity 필수 / rowCount, colCount null
    CONSTRAINT chk_section_standing
    CHECK (type != 'STANDING'
           OR (capacity IS NOT NULL AND capacity > 0
           AND row_count IS NULL AND col_count IS NULL)),

    -- FREE: capacity 필수 / rowCount, colCount null
    CONSTRAINT chk_section_free
    CHECK (type != 'FREE'
           OR (capacity IS NOT NULL AND capacity > 0
           AND row_count IS NULL AND col_count IS NULL))
    );

-- 공연장별 구역 조회 인덱스
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid WHERE c.relname = 'idx_section_venue_id' AND n.nspname = 'program') THEN
CREATE INDEX idx_section_venue_id ON program.p_section (venue_id) WHERE deleted_at IS NULL;
END IF;
END $$;


-- ── p_venue_seat ──────────────────────────────────────
-- SEATED 타입 전용 물리 고정 좌석
-- STANDING·FREE는 Section.capacity + Redis로 재고 관리
--
-- 독립 애그리거트: section_id를 값으로만 참조 (FK 없음)
-- 이유: VenueSeat은 Venue 애그리거트와 분리된 독립 애그리거트
--       Section과 FK를 두면 애그리거트 경계가 깨짐
CREATE TABLE IF NOT EXISTS p_venue_seat
(
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),

    -- Section ID 참조 (값만 보관, FK 없음 — 독립 애그리거트)
    section_id      UUID        NOT NULL,

    -- 행 번호 (1부터 시작)
    -- 컬럼명 row_num: PostgreSQL에서 row는 예약어
    row_num         INTEGER     NOT NULL,

    -- 열 번호 (1부터 시작)
    col_num         INTEGER     NOT NULL,

    -- 물리적 좌석 상태
    -- 예매 가능 여부(BookingSeat.status)와 별개로 관리
    -- BROKEN 좌석은 Application 계층에서 예매 불가 처리
    physical_status VARCHAR(15) NOT NULL DEFAULT 'AVAILABLE',

    -- BaseUserEntity Auditing
    created_at      TIMESTAMP   NOT NULL,
    created_by      UUID        NOT NULL,
    updated_at      TIMESTAMP,
    updated_by      UUID,
    deleted_at      TIMESTAMP,
    deleted_by      UUID,

    CONSTRAINT pk_venue_seat PRIMARY KEY (id),
    CONSTRAINT chk_venue_seat_physical_status
    CHECK (physical_status IN ('AVAILABLE', 'BROKEN')),
    CONSTRAINT chk_venue_seat_row_num
    CHECK (row_num > 0),
    CONSTRAINT chk_venue_seat_col_num
    CHECK (col_num > 0),

    -- SEATED: 동일 구역 내 (row, col) 중복 방지 (S-12)
    CONSTRAINT uk_seat_section_row_col
    UNIQUE (section_id, row_num, col_num)
    );

-- 구역별 좌석 조회 인덱스, 물리 상태별 필터 인덱스
-- CreateSectionUseCase에서 VenueSeat 일괄 생성 후 조회 시 사용
-- isAvailable() 선검증 쿼리에서 사용
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid WHERE c.relname = 'idx_venue_seat_section_id' AND n.nspname = 'program') THEN
CREATE INDEX idx_venue_seat_section_id ON program.p_venue_seat (section_id) WHERE deleted_at IS NULL;
END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid WHERE c.relname = 'idx_venue_seat_physical_status' AND n.nspname = 'program') THEN
CREATE INDEX idx_venue_seat_physical_status ON program.p_venue_seat (physical_status) WHERE deleted_at IS NULL;
END IF;
END $$;
