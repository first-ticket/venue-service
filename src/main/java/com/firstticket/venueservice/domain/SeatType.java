package com.firstticket.venueservice.domain;

public enum SeatType {
    SEATED,    // 지정 좌석 — row, col 사용
    STANDING,  // 스탠딩   — sequence 사용
    FREE       // 자유 입장 — row, col, sequence 모두 null
}
