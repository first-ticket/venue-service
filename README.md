# VenueService

> 공연장·구역·좌석을 관리하는 마이크로서비스

공연장(Venue)과 구역(Section), 좌석(VenueSeat)의 물리적 정보를 관리합니다.
SEATED 타입 구역 등록 시 VenueSeat을 자동 생성하고,
프로그램 서비스의 스케줄 등록 시 공연장 수용량 검증을 제공합니다.

---

## 🛠️ 개발 환경

| 항목 | 버전 |
|------|------|
| Java | 21 (Temurin) |
| Spring Boot | 3.5.x |
| Spring Cloud | 2025.x |
| PostgreSQL | 15 |
| QueryDSL | 5.1.0 (Jakarta) |
| Flyway | 최신 (PostgreSQL 전용) |

---

## ⚙️ 개발 환경 구축

### 1. 사전 요구사항

- Docker Desktop 설치
- GitHub Personal Access Token (`read:packages` 권한)

### 2. 저장소 클론

```bash
git clone https://github.com/first-ticket/venue-service.git
cd venue-service
```

### 3. 환경 변수 설정

루트 디렉토리에 `.env` 파일을 생성합니다.

```bash
cp .env.example .env
```

```
# .env
GITHUB_USER=깃허브유저명
GITHUB_TOKEN=ghp_xxxxxxxxxxxx

CONFIG_SERVER_USERNAME=
CONFIG_SERVER_PASSWORD=

SPRING_PROFILES_ACTIVE=prod

# PostgreSQL
DB_HOST=
DB_USERNAME=
DB_PASSWORD=
DB_NAME=

EUREKA_SERVER_URL=

ZIPKIN_ENDPOINT=
```

> `GITHUB_TOKEN`은 GitHub → Settings → Developer settings → Personal access tokens → `read:packages` 권한으로 발급합니다.

---

## 🔨 빌드 및 실행

### 로컬 빌드

```bash
./gradlew build
```

### 테스트 실행

```bash
./gradlew test
```

> `@DataJpaTest`는 H2 in-memory DB를 사용합니다.
> Testcontainers 테스트는 Docker가 실행 중이어야 합니다.

### Docker 실행

```bash
# 1. 빌드 먼저 실행 (REST Docs 포함)
./gradlew build

# 2. 컨테이너 실행
docker-compose up --build
```
---

## 🌐 API 목록

### 공연장 (Venue)

| 메서드 | 경로 | 권한 | 설명 |
|--------|------|------|------|
| `POST` | `/api/v1/venues` | HOST·ADMIN | 공연장 등록 |
| `GET` | `/api/v1/venues` | ALL | 목록 조회 |
| `GET` | `/api/v1/venues/{venueId}` | ALL | 상세 조회 |
| `PATCH` | `/api/v1/venues/{venueId}` | HOST·ADMIN | 수정 |
| `DELETE` | `/api/v1/venues/{venueId}` | ADMIN | 삭제 |

### 구역 (Section)

| 메서드 | 경로 | 권한 | 설명 |
|--------|------|------|------|
| `POST` | `/api/v1/venues/{venueId}/sections` | HOST·ADMIN | 구역 등록 |
| `GET` | `/api/v1/venues/{venueId}/sections` | ALL | 구역 목록 조회 |
| `GET` | `/api/v1/venues/{venueId}/sections/{sectionId}` | ALL | 구역 상세 조회 |
| `DELETE` | `/api/v1/venues/{venueId}/sections/{sectionId}` | ADMIN | 구역 삭제 |

### 좌석 (VenueSeat)

| 메서드 | 경로 | 권한 | 설명 |
|--------|------|------|------|
| `GET` | `/api/v1/venues/{venueId}/sections/{sectionId}/seats` | ALL | 좌석 목록 조회 |
| `GET` | `/api/v1/venues/{venueId}/sections/{sectionId}/seats/{seatId}` | ALL | 좌석 상세 조회 |
| `PATCH` | `/api/v1/venues/{venueId}/sections/{sectionId}/seats/{seatId}/status` | ADMIN | 좌석 상태 변경 |

### 내부 API (Feign 전용)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/v1/venues/{venueId}/validation` | 수용량 + seatTemplates 조회 |
| `GET` | `/api/v1/venues/{venueId}/sections/{sectionId}/validation` | 구역 타입·수용량 조회 |
| `GET` | `/api/v1/venues/{venueId}/info` | 공연장 이름·주소 조회 |

> REST Docs 문서: 서버 실행 후 `/docs/venue-api.html` 에서 확인할 수 있습니다.

---

## 🗄️ 도메인 설계

### 구역 타입 (SeatType)

| 타입 | 필수 필드 | VenueSeat 자동 생성 |
|------|----------|---------------------|
| `SEATED` | `rowCount`, `colCount` | rowCount × colCount |
| `STANDING` | `capacity` | 생성 없음 |
| `FREE` | `capacity` | 생성 없음 |

### 좌석 상태 (PhysicalStatus)

| 상태 | 설명 |
|------|------|
| `AVAILABLE` | 사용 가능 (초기값) |
| `BROKEN` | 파손 처리 |

### 주요 제약

- 활성 프로그램(`CANCELLED`·`CLOSED` 제외)이 있는 공연장은 삭제 불가
- SEATED 타입 구역 삭제 시 연관 VenueSeat 일괄 삭제

---

## 📋 응답 형식

### ✅ 성공 응답

```json
{
  "success": true,
  "code": "VENUE_CREATED",
  "message": "공연장이 등록되었습니다",
  "timestamp": "2027-01-01T00:00:00",
  "data": {
    "id": "aaaaaaaa-0000-0000-0000-000000000001",
    "name": "올림픽홀",
    "address": "서울시 송파구 올림픽로 424",
    "sections": []
  }
}
```

### ❌ 에러 응답

```json
{
  "success": false,
  "code": "VENUE_HAS_PROGRAMS",
  "message": "해당 공연장에 등록된 프로그램이 있어 삭제할 수 없습니다",
  "timestamp": "2027-01-01T00:00:00"
}
```

---

## 🔴 에러 코드

### Venue

| 코드 | HTTP | 메시지 |
|------|------|--------|
| `VENUE_NOT_FOUND` | 404 | 공연장을 찾을 수 없습니다 |
| `VENUE_ALREADY_DELETED` | 400 | 삭제된 공연장입니다 |
| `INVALID_VENUE_NAME` | 400 | 공연장 이름은 필수입니다 |
| `INVALID_VENUE_ADDRESS` | 400 | 공연장 주소는 필수입니다 |
| `INVALID_VENUE_ID` | 400 | 공연장 ID는 필수입니다 |
| `VENUE_HAS_PROGRAMS` | 409 | 해당 공연장에 등록된 프로그램이 있어 삭제할 수 없습니다 |
| `VENUE_TIME_CONFLICT` | 409 | 해당 공연장에 이미 예약된 일정이 있습니다 |

### Section

| 코드 | HTTP | 메시지 |
|------|------|--------|
| `SECTION_NOT_FOUND` | 404 | 구역을 찾을 수 없습니다 |
| `INVALID_SECTION_NAME` | 400 | 구역명은 필수입니다 |
| `INVALID_SECTION_ID` | 400 | 구역 ID는 필수입니다 |
| `INVALID_SECTION_TYPE` | 400 | 구역 타입은 필수입니다 |
| `INVALID_SEAT_COUNT` | 400 | 행과 열 수는 1 이상이어야 합니다 |
| `INVALID_CAPACITY` | 400 | 수용 인원은 1명 이상이어야 합니다 |
| `CAPACITY_EXCEEDED` | 400 | 요청 인원이 구역 최대 수용 인원을 초과합니다 |
| `INVALID_SECTION_FIELD_COMBINATION` | 400 | 구역 타입과 필드 조합이 올바르지 않습니다 |

### VenueSeat

| 코드 | HTTP | 메시지 |
|------|------|--------|
| `SEAT_NOT_FOUND` | 404 | 좌석을 찾을 수 없습니다 |
| `INVALID_SEAT_ID` | 400 | 좌석 ID는 필수입니다 |
| `SEAT_ALREADY_BROKEN` | 400 | 이미 파손 처리된 좌석입니다 |
| `SEAT_ALREADY_AVAILABLE` | 400 | 이미 사용 가능한 좌석입니다 |
| `INVALID_SEAT_POSITION` | 400 | 좌석 행·열 번호는 1 이상이어야 합니다 |

---

## 🟢 성공 코드

| 코드 | HTTP | 메시지 |
|------|------|--------|
| `VENUE_CREATED` | 201 | 공연장이 등록되었습니다 |
| `VENUE_FOUND` | 200 | 공연장을 조회했습니다 |
| `VENUE_LIST_FOUND` | 200 | 공연장 목록을 조회했습니다 |
| `VENUE_UPDATED` | 200 | 공연장이 수정되었습니다 |
| `VENUE_DELETED` | 204 | 공연장이 삭제되었습니다 |
| `SECTION_CREATED` | 201 | 구역이 등록되었습니다 |
| `SECTION_FOUND` | 200 | 구역을 조회했습니다 |
| `SECTION_LIST_FOUND` | 200 | 구역 목록을 조회했습니다 |
| `SECTION_DELETED` | 204 | 구역이 삭제되었습니다 |
| `SEAT_FOUND` | 200 | 좌석을 조회했습니다 |
| `SEAT_LIST_FOUND` | 200 | 좌석 목록을 조회했습니다 |
| `SEAT_STATUS_UPDATED` | 200 | 좌석 상태가 변경되었습니다 |

---
