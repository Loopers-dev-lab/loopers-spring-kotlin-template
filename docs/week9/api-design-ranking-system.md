# API 설계 결정사항: 랭킹 시스템

## 1. 핵심 설계 결정과 배경

### 1.1 비즈니스 컨텍스트

사용자 행동(조회, 좋아요, 주문)을 종합하여 인기 상품 랭킹을 제공함으로써 구매 전환율을 향상시키고자 한다. 구매자는 인기 상품을 쉽게 탐색할 수 있고, 관리자는 랭킹 정책(Weight)을 유연하게 조절하여 최적의 랭킹 정책을 찾을 수 있다.

**해결하는 비즈니스 요구사항:**
- US-1: 구매자가 인기 상품 랭킹을 페이지 단위로 조회
- US-2: 구매자가 상품 상세 조회 시 해당 상품의 현재 순위 확인
- US-3: 관리자가 항목별 Weight를 실시간으로 조절

### 1.2 주요 설계 결정

**경로 설계**
- 결정: 랭킹 조회는 `/api/v1/rankings`, Weight 관리는 `/api/v1/rankings/weight`
- 배경: Rankings 도메인으로 분리하되, 현재는 상품 랭킹만 있으므로 단순한 경로 채택
- 전략적 의미: 향후 다른 종류의 랭킹 추가 시 경로 확장 용이

**시간 단위 조회 파라미터**
- 결정: `date` 파라미터로 시간 단위(yyyyMMddHH) 조회 지원
- 배경: 1시간 단위 Tumbling Window로 집계하므로 시간 단위까지 필요
- 전략적 의미: TTL 기반 자연 소실과 함께 과거 랭킹 조회 가능

**페이지네이션 방식**
- 결정: `page` (0부터 시작) + `size` + `hasNext` 방식 채택
- 배경: 요구사항에 "페이지 단위 조회" 명시, 기존 API 패턴과 일관성 유지
- 전략적 의미: 클라이언트에서 페이지 번호 기반 UI 구현 용이

**Score 비노출**
- 결정: API 응답에 Score를 노출하지 않고 rank만 반환
- 배경: Score는 내부 계산용 지표이며, 사용자에게는 순위만 의미 있음
- 전략적 의미: 내부 로직 변경 시 API 영향 최소화

**상품 상세 응답 구조 변경**
- 결정: 기존 `product` 객체를 플랫하게 펼치고 `rank` 필드 추가
- 배경: 미배포 상태이므로 하위 호환성 고려 불필요, 깔끔한 구조 우선
- 전략적 의미: 중첩 구조 제거로 클라이언트 코드 단순화

**필드명 통일**
- 결정: 랭킹 응답의 상품 필드명을 기존 ProductDto와 동일하게 (`id`, `name` 등)
- 배경: API 간 일관성 유지
- 전략적 의미: 클라이언트에서 동일한 모델로 처리 가능

## 2. API 명세

### 2.1 랭킹 조회 API

**엔드포인트**: `GET /api/v1/rankings`

**설명**: 인기 상품 랭킹을 페이지 단위로 조회한다.

**요청**:

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `date` | String | N | 현재 시간 | 조회 시간 (yyyyMMddHH 형식) |
| `page` | Int | N | 0 | 페이지 번호 (0부터 시작) |
| `size` | Int | N | 20 | 페이지 크기 |

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
        "id": 101,
        "name": "인기 상품 A",
        "price": 29000,
        "stock": 150,
        "brandId": 10,
        "brandName": "브랜드A",
        "likeCount": 1520
      },
      {
        "rank": 2,
        "id": 203,
        "name": "인기 상품 B",
        "price": 45000,
        "stock": 80,
        "brandId": 12,
        "brandName": "브랜드B",
        "likeCount": 980
      }
    ],
    "hasNext": true
  }
}
```

**비즈니스 규칙**:
- 랭킹은 Score 내림차순으로 정렬된다
- 해당 시간에 랭킹 데이터가 없으면 빈 배열을 반환한다
- TTL이 지난 과거 데이터는 자연 소실된다

**에러 케이스**:

| 상황 | HTTP 상태 | 에러 코드 | 메시지 |
|------|----------|----------|--------|
| date 형식 오류 | 400 | Bad Request | 요청 파라미터 'date' (타입: String)의 값이 잘못되었습니다. |
| page가 0 미만 | 400 | Bad Request | 요청 파라미터 'page' (타입: Int)의 값이 잘못되었습니다. |
| size가 1 미만 | 400 | Bad Request | 요청 파라미터 'size' (타입: Int)의 값이 잘못되었습니다. |

### 2.2 Weight 조회 API

**엔드포인트**: `GET /api/v1/rankings/weight`

**설명**: 현재 랭킹 Weight 설정을 조회한다.

**요청**: 없음

**응답**:

```json
{
  "meta": {
    "result": "SUCCESS",
    "errorCode": null,
    "message": null
  },
  "data": {
    "viewWeight": 0.1,
    "likeWeight": 0.3,
    "orderWeight": 0.6
  }
}
```

**비즈니스 규칙**:
- Weight 설정이 없는 경우 fallback 값이 반환된다 (viewWeight=0.1, likeWeight=0.2, orderWeight=0.6)

### 2.3 Weight 변경 API

**엔드포인트**: `PUT /api/v1/rankings/weight`

**설명**: 랭킹 Weight 설정을 변경하고 Score 재계산을 트리거한다.

**요청**:

```json
{
  "viewWeight": 0.2,
  "likeWeight": 0.3,
  "orderWeight": 0.5
}
```

| 필드 | 타입 | 필수 | 제약 | 설명 |
|------|------|------|------|------|
| `viewWeight` | Double | Y | 0.0 ~ 1.0 | 조회 가중치 |
| `likeWeight` | Double | Y | 0.0 ~ 1.0 | 좋아요 가중치 |
| `orderWeight` | Double | Y | 0.0 ~ 1.0 | 주문 가중치 |

**응답**:

```json
{
  "meta": {
    "result": "SUCCESS",
    "errorCode": null,
    "message": null
  },
  "data": {
    "viewWeight": 0.2,
    "likeWeight": 0.3,
    "orderWeight": 0.5
  }
}
```

**비즈니스 규칙**:
- Weight 값은 모두 0 이상 1 이하여야 한다
- 변경 성공 시 `RankingWeightChangedEvent`가 발행되어 비동기로 Score가 재계산된다
- Score 재계산은 수 분 내 완료된다

**에러 케이스**:

| 상황 | HTTP 상태 | 에러 코드 | 메시지 |
|------|----------|----------|--------|
| Weight 값 범위 초과 | 400 | Bad Request | Weight는 0과 1 사이여야 합니다. |
| 필수 필드 누락 | 400 | Bad Request | 필수 필드 '{필드명}'이(가) 누락되었습니다. |

## 3. API 변경 사항

### 3.1 추가되는 API

| HTTP 메서드 | 경로 | 설명 | 영향 |
|------------|------|------|------|
| GET | `/api/v1/rankings` | 인기 상품 랭킹 조회 | 신규 엔드포인트 |
| GET | `/api/v1/rankings/weight` | Weight 설정 조회 | 신규 엔드포인트 |
| PUT | `/api/v1/rankings/weight` | Weight 설정 변경 | 신규 엔드포인트 |

### 3.2 변경되는 API

- **변경 대상**: `GET /api/v1/products/{productId}`
- **변경 유형**: 응답 구조 변경 + 필드 추가
- **변경 내용**: 
  - 기존 `product` 객체를 플랫하게 펼침 (`data.product.id` → `data.id`)
  - `rank` 필드 추가 (랭킹에 없는 상품은 null)
- **변경 이유**: 
  - US-2 요구사항 충족 (상품 상세에서 순위 확인)
  - 중첩 구조 제거로 응답 구조 단순화
- **하위 호환성**: 깨짐 (미배포 상태이므로 영향 없음)
- **마이그레이션 기간**: 없음

**변경 전 응답**:

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "product": {
      "id": 101,
      "name": "상품명",
      "price": 29000,
      "stock": 150,
      "brandId": 10,
      "brandName": "브랜드A",
      "likeCount": 1520
    }
  }
}
```

**변경 후 응답**:

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id": 101,
    "name": "상품명",
    "price": 29000,
    "stock": 150,
    "brandId": 10,
    "brandName": "브랜드A",
    "likeCount": 1520,
    "rank": 1
  }
}
```

**추가 변경 사항**:
- `ProductV1Response.GetProducts`와 `ProductV1Response.GetProduct`에서 공통 DTO 분리
- 리스트 조회용 DTO와 상세 조회용 DTO를 각각 사용하도록 변경
