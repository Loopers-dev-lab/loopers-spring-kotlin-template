# API 설계 결정사항: 주간/월간 랭킹 시스템

## 1. 핵심 설계 결정과 배경

### 1.1 비즈니스 컨텍스트

현재 시스템은 실시간(시간대별) 랭킹과 일간 랭킹만 제공하고 있어, 순간적인 트렌드는 파악할 수 있지만 꾸준히 인기 있는 상품을 파악하기 어렵다. 구매자가 거시적인 트렌드를 확인하고 검증된 인기 상품을 탐색할 수 있도록 주간(최근 7일) 및 월간(최근 30일) 랭킹을 제공하고자 한다.

추가로, 관리자가 배치 실패 후 수동으로 재실행하거나 특정 날짜 기준으로 재집계할 수 있는 기능을 제공한다.

### 1.2 주요 설계 결정

#### 기존 랭킹 조회 API 확장
- **결정**: 새로운 API를 추가하지 않고 기존 `GET /api/v1/rankings`의 period 파라미터에 `weekly`, `monthly` 값을 추가
- **배경**: 실시간/일간/주간/월간 랭킹은 동일한 도메인 개념이며, 응답 구조도 동일함
- **전략적 의미**: 하위 호환성 유지, 클라이언트 코드 변경 최소화

#### 배치 실행 결과의 일관된 응답
- **결정**: 배치 실행이 완료되면 비즈니스 로직 성공/실패와 관계없이 HTTP 200 OK를 반환하고, `status` 필드로 결과를 구분
- **배경**: Spring Batch REST API 베스트 프랙티스를 따름. "배치 실행 자체의 성공/실패"와 "비즈니스 로직의 성공/실패"를 구분
- **전략적 의미**: 클라이언트에서 일관된 응답 처리 가능, 실패 사유는 `exitDescription` 필드로 확인

#### 배치 수동 실행의 동기 방식
- **결정**: 배치 수동 실행 API는 배치 완료까지 대기 후 응답 반환
- **배경**: 관리자가 배치 실행 결과를 즉시 확인해야 하며, 트래픽이 낮아 긴 응답 시간 허용
- **전략적 의미**: 별도의 상태 조회 API 불필요, 구현 단순화

#### period를 Path Variable로 설계
- **결정**: 배치 수동 실행 API에서 period를 `/api/v1/admin/batch/rankings/{period}` 형태로 설계
- **배경**: period는 필수 값이며, RESTful 관점에서 리소스를 명확히 식별
- **전략적 의미**: URL만으로 어떤 배치를 실행하는지 명확히 파악 가능

---

## 2. API 명세

### 2.1 랭킹 조회 API (변경)

**엔드포인트**: `GET /api/v1/rankings`

**설명**: 기간 타입별 인기 상품 랭킹을 조회한다. 주간/월간 랭킹 조회를 위해 period 파라미터에 `weekly`, `monthly` 값이 추가되었다.

**요청 파라미터**:

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `period` | String | N | hourly | 기간 타입 (hourly/daily/weekly/monthly) |
| `date` | String | N | 오늘 | 기준일. hourly는 yyyyMMddHH, 나머지는 yyyyMMdd |
| `page` | Int | N | 0 | 페이지 번호 (0-based) |
| `size` | Int | N | 10 | 페이지 크기 (1~100) |

**응답**:

```json
{
  "meta": {
    "result": "SUCCESS",
    "errorCode": null,
    "message": null
  },
  "data": {
    "rankings": [
      {
        "rank": 1,
        "productId": 123,
        "name": "인기 상품 A",
        "price": 29900,
        "stock": 100,
        "brandId": 1,
        "brandName": "브랜드A",
        "likeCount": 1520
      }
    ],
    "hasNext": true
  }
}
```

**비즈니스 규칙**:

| 규칙 | 설명 |
|------|------|
| TOP 100 제한 | 모든 기간 타입에서 상위 100개 상품만 제공. 100위 이후 요청 시 빈 목록 반환 |
| 미래 날짜 처리 | 미래 날짜 지정 시 오늘 날짜로 대체 |
| Fallback 정책 | 당일 기준 랭킹이 없고 offset=0인 경우, 전일 기준 랭킹을 자동으로 조회하여 반환 |
| 기본 period | period 미지정 시 hourly(실시간) 랭킹 제공 |

**기간 타입별 동작**:

| period | date 형식 | 데이터 소스 | TOP 제한 |
|--------|----------|------------|---------|
| hourly | yyyyMMddHH | Redis | 100 |
| daily | yyyyMMdd | Redis | 100 |
| weekly | yyyyMMdd | RDB + Cache | 100 |
| monthly | yyyyMMdd | RDB + Cache | 100 |

**에러 케이스**:

| 상황 | HTTP 상태 | 에러 코드 | 메시지 |
|------|----------|----------|--------|
| 유효하지 않은 period 값 | 400 | INVALID_PERIOD | 유효하지 않은 기간 타입입니다: {period} |
| 유효하지 않은 date 형식 | 400 | INVALID_DATE_FORMAT | 날짜 형식이 올바르지 않습니다 |
| size가 허용 범위 초과 | 400 | INVALID_SIZE | size는 1~100 사이여야 합니다 |

---

### 2.2 배치 수동 실행 API (신규)

**엔드포인트**: `POST /api/v1/admin/batch/rankings/{period}`

**설명**: 관리자가 랭킹 배치를 수동으로 실행한다. 배치 완료까지 동기로 대기하며, 실행 결과를 반환한다.

**경로 파라미터**:

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `period` | String | Y | 배치 타입 (weekly/monthly). 실시간(hourly)/일간(daily) 랭킹은 Redis에만 저장되므로 수동 실행 대상이 아님 |

**요청 바디**:

```json
{
  "baseDate": "20250102"
}
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|------|------|------|--------|------|
| `baseDate` | String | N | 오늘 | 집계 기준일. hourly는 yyyyMMddHH, 나머지는 yyyyMMdd |

**응답 (성공)**:

```json
{
  "meta": {
    "result": "SUCCESS",
    "errorCode": null,
    "message": null
  },
  "data": {
    "jobName": "WeeklyRankingJob",
    "baseDate": "20250102",
    "status": "COMPLETED",
    "startTime": "2025-01-02T03:00:00",
    "endTime": "2025-01-02T03:00:13",
    "readCount": 15000,
    "writeCount": 100,
    "exitDescription": ""
  }
}
```

**응답 (배치 실패)**:

```json
{
  "meta": {
    "result": "SUCCESS",
    "errorCode": null,
    "message": null
  },
  "data": {
    "jobName": "WeeklyRankingJob",
    "baseDate": "20250102",
    "status": "FAILED",
    "startTime": "2025-01-02T03:00:00",
    "endTime": "2025-01-02T03:00:05",
    "readCount": 5000,
    "writeCount": 0,
    "exitDescription": "Redis connection timeout"
  }
}
```

**응답 필드 설명**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `jobName` | String | 실행된 Job 이름 (weeklyRankingJob, monthlyRankingJob) |
| `baseDate` | String | 집계 기준일 |
| `status` | String | 실행 결과 (COMPLETED/FAILED) |
| `startTime` | String | 시작 시간 (ISO 8601) |
| `endTime` | String | 종료 시간 (ISO 8601) |
| `readCount` | Long | 읽은 건수 |
| `writeCount` | Long | 쓴 건수 |
| `exitDescription` | String | 종료 설명. 실패 시 실패 사유 포함 |

**비즈니스 규칙**:

| 규칙 | 설명 |
|------|------|
| 미래 날짜 처리 | 미래 날짜 지정 시 오늘 날짜로 대체 |
| 중복 실행 방지 | 동일 배치가 이미 실행 중이면 409 Conflict 반환 |
| 재집계 | 이미 완료된 날짜를 다시 실행하면 기존 데이터를 덮어쓰고 재집계 |
| 동기 실행 | 배치 완료까지 대기 후 응답 반환 |

**에러 케이스**:

| 상황 | HTTP 상태 | 에러 코드 | 메시지 |
|------|----------|----------|--------|
| 유효하지 않은 period 값 (weekly/monthly 외) | 400 | INVALID_PERIOD | 유효하지 않은 기간 타입입니다: {period} |
| 유효하지 않은 date 형식 | 400 | INVALID_DATE_FORMAT | 날짜 형식이 올바르지 않습니다 |
| 동일 배치 실행 중 | 409 | JOB_ALREADY_RUNNING | 해당 배치가 이미 실행 중입니다 |

---

## 3. API 변경 사항

### 3.1 추가되는 API

| HTTP 메서드 | 경로 | 설명 | 영향 |
|------------|------|------|------|
| POST | `/api/v1/admin/batch/rankings/{period}` | 랭킹 배치 수동 실행 | 기존 API와 충돌 없음 |

### 3.2 변경되는 API

- **변경 대상**: `GET /api/v1/rankings`
- **변경 유형**: 파라미터 값 추가
- **변경 내용**: `period` 파라미터에 `weekly`, `monthly` 값 추가 지원
- **변경 이유**: 주간/월간 랭킹 조회 기능 추가 (US-1, US-2 요구사항)
- **하위 호환성**: 유지 (기존 hourly/daily 동작 변경 없음)
- **마이그레이션 기간**: 없음 (추가만 있고 삭제 없음)
