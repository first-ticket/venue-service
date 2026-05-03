package com.firstticket.venueservice.domain;

public enum SeatType {
    SEATED,    // 지정 좌석 — row, col 사용
    STANDING,  // 스탠딩 — Section.capacity로 인원 관리, VenueSeat 없음
    FREE       // 자유 입장 — Section.capacity로 인원 관리, VenueSeat 없음
}
