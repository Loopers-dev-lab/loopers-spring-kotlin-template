# 상품 조회 API 성능 최적화 리포트

## 1. 개요

### 1.1 배경

- **문제**: 상품 목록/단일 조회 API가 피크 타임에 5초 이상 소요
- **영향**: 사용자 불만 접수, UX 저하
- **데이터 규모**: 상품 10만 건, 브랜드 5,000개, 피크 트래픽 ~70 req/s

### 1.2 목표 (SLO)

프로덕션 환경의 네트워크 레이턴시를 감안하여 공격적인 목표 설정:

| 지표          | 개선 전     | 목표          | 최종 결과         | 달성 |
|-------------|----------|-------------|---------------|----|
| p50 latency | ~1,000ms | < 50ms      | **8.89ms**    | ✅  |
| p95 latency | 4,057ms  | < 100ms     | **37.76ms**   | ✅  |
| p99 latency | 5,000ms+ | < 300ms     | **117.47ms**  | ✅  |
| Throughput  | 52 req/s | > 100 req/s | **230 req/s** | ✅  |
| Error rate  | -        | < 0.1%      | **0%**        | ✅  |

---

## 2. 테스트 시나리오

### 2.1 테스트 대상

- **Primary**: `GET /api/v1/products` (상품 목록 조회)
- **Secondary**: 상품 단일 조회, 유저 조회, 쿠폰, 포인트, 좋아요, 주문 API

### 2.2 테스트 데이터

| 항목  | 규모            |
|-----|---------------|
| 상품  | 100,000건      |
| 브랜드 | 5,000개        |
| 쿠폰  | 100개 (시드 데이터) |

### 2.3 부하 프로파일

#### Mixed Workload 테스트 (Background Load 포함)

실제 프로덕션 트래픽 패턴 시뮬레이션:

| API           | 비중   | 목표 TPS   |
|---------------|------|----------|
| 상품 목록 조회 (타겟) | 40%  | ~100     |
| 상품 단일 조회      | 25%  | ~62      |
| 유저 조회         | 10%  | ~25      |
| 발급 쿠폰 목록      | 8%   | ~20      |
| 포인트 잔액        | 7%   | ~17      |
| 좋아요 토글        | 5%   | ~12      |
| 주문 생성         | 3%   | ~7       |
| 쿠폰 발급         | 2%   | ~5       |
| **전체**        | 100% | **~250** |

#### 페이지 분포 (현실적 감소 패턴)

| 페이지 | 비중  |
|-----|-----|
| 0   | 80% |
| 1   | 15% |
| 2   | 1%  |
| 3   | 1%  |
| 4   | 1%  |
| 5   | 1%  |

### 2.4 테스트 환경

- **테스트 도구**: k6
- **환경**: 로컬 Docker (PostgreSQL, Redis) + IDE 서버 실행
- **스크립트 위치**: `.projects/week5/`

---

## 3. 분석

### 3.1 시스템 흐름

```
Client Request
    ↓
ProductV1Controller.getProducts()
    ↓
ProductFacade.findProducts()  ←── 캐싱 적용 지점
    ↓
┌─────────────────────────────────────────────────┐
│  1. productRepository.findAllBy() [QueryDSL]    │
│  2. productStatisticRepository.findAllByIds()   │
│  3. brandRepository.findAllByIds()              │
└─────────────────────────────────────────────────┘
    ↓
Response
```

### 3.2 병목 식별

| 순위 | 병목                                   | Before | After (인덱스) | 개선율       |
|----|--------------------------------------|--------|-------------|-----------|
| 1  | LIKES_DESC 정렬 (Full Scan + filesort) | 333ms  | 0.98ms      | **99.7%** |
| 2  | PRICE_ASC 정렬 (인덱스 없음)                | 52ms   | 0.43ms      | **99.2%** |
| 3  | brandId 필터 (Full Scan)               | 41.9ms | 1.65ms      | **96.1%** |
| 4  | LATEST 정렬                            | 0.45ms | 0.45ms      | (원래 OK)   |

### 3.3 근본 원인

**ORDER BY 인덱스 활용 불가 문제**:

```sql
-- ❌ 문제: 두 테이블 컬럼이 섞여 인덱스 활용 불가
ORDER BY ps.like_count DESC, p.id DESC

-- ✅ 해결: 단일 테이블 컬럼으로 수정
ORDER BY ps.like_count DESC, ps.product_id DESC
```

---

## 4. 개선 내용

### 4.1 Phase 1: 인덱스 최적화

#### 개선 1: LIKES_DESC 정렬 인덱스 추가

```sql
CREATE INDEX ix_product_statistics_like_count_product_id
    ON product_statistics (like_count DESC, product_id DESC);
```

```kotlin
// QueryDSL ORDER BY 수정
.orderBy(productStatistics.likeCount.desc(), productStatistics.productId.desc())
```

#### 개선 2: PRICE_ASC 정렬 인덱스 추가

```sql
CREATE INDEX idx_products_price ON products (price ASC, id DESC);
```

#### 개선 3: brandId 필터 인덱스 추가

```sql
CREATE INDEX idx_products_brand_id ON products (brand_id);
```

**복합 인덱스 vs 단독 인덱스 결정**:

- 브랜드당 최대 상품 수: 39건 (거의 균등 분포)
- 단독 인덱스 사용 시 filesort: ~0.26ms
- 복합 인덱스 추가 이득: ~0.2ms
- **결론**: 브랜드당 상품 수가 적어 단독 인덱스로 충분. 복합 인덱스는 오버엔지니어링.

### 4.2 Phase 2: Redis 캐싱

#### 캐시 구조

| 캐시    | 키 패턴                                            | 값                    | TTL |
|-------|-------------------------------------------------|----------------------|-----|
| 상품 목록 | `product-list:v1:{sort}:{filter}:{page}:{size}` | `List<Long>`         | 60초 |
| 상품 상세 | `product-detail:{productId}`                    | `ProductView` (JSON) | 60초 |

#### 캐싱 범위

- page: 0, 1, 2만 (3페이지 이내, 트래픽 90% 커버)
- 그 외는 DB 직접 조회

#### 갱신 전략

| 이벤트           | 처리        | 이유                |
|---------------|-----------|-------------------|
| 재고 차감         | 즉시 갱신     | 품절 여부는 구매 가능성에 직결 |
| 좋아요 변경        | TTL 만료 대기 | 1분 지연 허용, 빈번한 이벤트 |
| 상품 정보 변경 (향후) | 즉시 갱신     | 가격 등 중요 정보 즉시 반영  |

### 4.3 추가된 인덱스 목록

| 인덱스                                           | 테이블                | 컬럼                                 | 용도            |
|-----------------------------------------------|--------------------|------------------------------------|---------------|
| `ix_product_statistics_like_count_product_id` | product_statistics | (like_count DESC, product_id DESC) | LIKES_DESC 정렬 |
| `idx_products_price`                          | products           | (price ASC, id DESC)               | PRICE_ASC 정렬  |
| `idx_products_brand_id`                       | products           | (brand_id)                         | brandId 필터    |

### 4.4 검토했으나 적용하지 않은 대안

| 대안                          | 미적용 사유                               |
|-----------------------------|--------------------------------------|
| brandId + like_count 복합 인덱스 | 브랜드당 상품 수 평균 20건으로 filesort 비용 무시 가능 |
| Redis Sorted Set            | 전체 상품 랭킹에는 오버엔지니어링, 기획전 등 소규모에 적합    |
| 읽기 전용 레플리카                  | 현재 부하 수준에서 불필요                       |

---

## 5. 결과

### 5.1 단계별 개선 추이 (Mixed Workload 테스트)

| 단계          | p50        | p95         | p99          | Throughput    |
|-------------|------------|-------------|--------------|---------------|
| 베이스라인       | ~1,000ms   | 4,057ms     | 5,000ms+     | 52 req/s      |
| 인덱스 최적화     | 13.43ms    | 113.14ms    | 252.71ms     | 225 req/s     |
| **+ 캐시 적용** | **8.89ms** | **37.76ms** | **117.47ms** | **230 req/s** |

### 5.2 최종 Before/After 비교

| 지표          | Before   | After         | 개선율       | 목표          | 달성 |
|-------------|----------|---------------|-----------|-------------|----|
| p50 latency | ~1,000ms | **8.89ms**    | **99.1%** | < 50ms      | ✅  |
| p95 latency | 4,057ms  | **37.76ms**   | **99.1%** | < 100ms     | ✅  |
| p99 latency | 5,000ms+ | **117.47ms**  | **97.7%** | < 300ms     | ✅  |
| avg latency | 1,255ms  | **14.58ms**   | **98.8%** | -           | ✅  |
| Throughput  | 52 req/s | **230 req/s** | **342%**  | > 100 req/s | ✅  |
| Error rate  | -        | **0%**        | -         | < 0.1%      | ✅  |
| SLO 미달율     | 78.6%    | **0.0%**      | **100%**  | < 1%        | ✅  |

### 5.3 캐시 적용 효과

| 지표  | 인덱스만     | + 캐시     | 추가 개선율  |
|-----|----------|----------|---------|
| p50 | 13.43ms  | 8.89ms   | **34%** |
| p95 | 113.14ms | 37.76ms  | **67%** |
| p99 | 252.71ms | 117.47ms | **54%** |

### 5.4 핵심 성과

- **응답 시간**: p95 기준 4,057ms → 37.76ms (**99.1% 개선**)
- **처리량**: 52 req/s → 230 req/s (**342% 증가**)
- **SLO 달성률**: 21.4% → 100% (**완전 달성**)

---

## 6. 향후 계획

### 6.1 추가 최적화 기회

- **상품 단일 조회 API**: 3개 쿼리 → 1개 JOIN 쿼리로 통합 가능
- **캐시 워밍업**: 배포 시 인기 페이지 프리로딩

### 6.2 모니터링 계획

- APM 도입 검토 (Datadog, New Relic, Pinpoint 등)
- Redis 캐시 히트율 모니터링
- Slow Query 로깅 활성화

---

## 7. 결론

상품 목록 조회 API의 성능을 **p95 기준 4,057ms에서 37.76ms로 99.1% 개선**했습니다.

**Phase 1 (인덱스 최적화)**: 3개의 인덱스 추가와 QueryDSL ORDER BY 수정으로 p95 113ms 달성

**Phase 2 (Redis 캐싱)**: Application Layer 캐싱으로 p95 37.76ms 달성, 공격적 SLO (p95 < 100ms) 충족

Mixed Workload 테스트를 통해 실제 프로덕션 환경과 유사한 조건에서도 모든 SLO를 만족하는 것을 확인했습니다.