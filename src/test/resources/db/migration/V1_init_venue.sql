-- =====================================================
-- Venue Service — H2 테스트용 스키마
-- PostgreSQL 전용 문법을 H2 호환 문법으로 대체
--
-- 주요 차이점:
-- gen_random_uuid() → RANDOM_UUID()
-- partial index (WHERE절) → 일반 인덱스로 대체
-- =====================================================

CREATE TABLE p_venue
(
    id         UUID         NOT NULL DEFAULT RANDOM_UUID(),
    name       VARCHAR(255) NOT NULL,
    address    VARCHAR(255) NOT NULL,

    created_at TIMESTAMP    NOT NULL,
    created_by UUID         NOT NULL,
    updated_at TIMESTAMP,
    updated_by UUID,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT pk_venue PRIMARY KEY (id)
);

CREATE INDEX idx_venue_name ON program.p_venue (name);


CREATE TABLE p_section
(
    id         UUID         NOT NULL DEFAULT RANDOM_UUID(),
    venue_id   UUID         NOT NULL,
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(10)  NOT NULL
        CHECK (type IN ('SEATED', 'STANDING', 'FREE')),
    row_count  INTEGER,
    col_count  INTEGER,
    capacity   INTEGER,

    created_at TIMESTAMP    NOT NULL,
    created_by UUID         NOT NULL,
    updated_at TIMESTAMP,
    updated_by UUID,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT pk_section PRIMARY KEY (id),
    CONSTRAINT fk_section_venue
        FOREIGN KEY (venue_id) REFERENCES p_venue (id),
    CONSTRAINT chk_section_seated
        CHECK (type != 'SEATED'
            OR (row_count IS NOT NULL AND col_count IS NOT NULL
            AND row_count > 0 AND col_count > 0
            AND capacity IS NULL)
) ,
    CONSTRAINT chk_section_standing
        CHECK (type != 'STANDING'
            OR (capacity IS NOT NULL AND capacity > 0
                AND row_count IS NULL AND col_count IS NULL)),
    CONSTRAINT chk_section_free
        CHECK (type != 'FREE'
            OR (capacity IS NOT NULL AND capacity > 0
                AND row_count IS NULL AND col_count IS NULL))
);

CREATE INDEX idx_section_venue_id ON p_section (venue_id);


CREATE TABLE p_venue_seat
(
    id              UUID        NOT NULL DEFAULT RANDOM_UUID(),
    section_id      UUID        NOT NULL,
    row_num         INTEGER     NOT NULL CHECK (row_num > 0),
    col_num         INTEGER     NOT NULL CHECK (col_num > 0),
    physical_status VARCHAR(15) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (physical_status IN ('AVAILABLE', 'BROKEN')),

    created_at      TIMESTAMP   NOT NULL,
    created_by      UUID        NOT NULL,
    updated_at      TIMESTAMP,
    updated_by      UUID,
    deleted_at      TIMESTAMP,
    deleted_by      UUID,

    CONSTRAINT pk_venue_seat PRIMARY KEY (id),
    CONSTRAINT uk_seat_section_row_col
        UNIQUE (section_id, row_num, col_num)
);

CREATE INDEX idx_venue_seat_section_id ON p_venue_seat (section_id);
CREATE INDEX idx_venue_seat_physical_status ON p_venue_seat (physical_status);
