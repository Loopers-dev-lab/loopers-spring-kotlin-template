# 상품 목록 조회 필터,정렬 조건별 인덱스 성능 비교

## 시나리오 1. 등록일 정렬
### 쿼리
```sql
SELECT p.id,
       p.brand_id,
       p.created_at,
       p.deleted_at,
       p.description,
       p.name,
       p.price,
       p.updated_at
FROM product p
INNER JOIN like_count lc
  ON lc.target_id = p.id AND lc.type = 'PRODUCT'
WHERE p.deleted_at IS NULL
ORDER BY p.created_at DESC, p.id DESC
LIMIT 20;
```

### 테스트 환경
- 데이터 규모: product 1,000만 건, like_count 1,000만 건
- DB 버전: MySQL (InnoDB)
- 조건: `deleted_at IS NULL`
- LIMIT: 20
- 조인: INNER JOIN / LEFT JOIN

### 실행 계획 & 결과
| 인덱스 상태 | 실행 계획 요약 | 실행 시간(ms) | rows read | filesort | 비고 |
|-------------|---------------|--------------:|----------:|----------|------|
| 없음 | Table scan + Filter + Sort | 12518 (INNER)<br>8896 (LEFT) | 9.9M | O | 전체 스캔 후 정렬 |
| 조건 인덱스만<br>(deleted_at, id) | Index range scan + Sort | 28333 (INNER)<br>29126 (LEFT) | 9.9M | O | 정렬 미커버로 filesort 발생 |
| 정렬 인덱스만<br>(created_at, id) | Reverse index scan | 2 (INNER)<br>0.34 (LEFT) | 20 | X | LIMIT 20 즉시 종료 |
| 조건+정렬 인덱스<br>(deleted_at, created_at, id) | Reverse index scan | 1.02 (INNER)<br>0.83 (LEFT) | 20 | X | 조건+정렬 모두 커버 |

### 분석
- 인덱스 없음: 풀스캔 후 정렬 → 가장 느림 (데이터 수량 줄일까 고민 많이했습니다...)
- 조건 인덱스만: 정렬을 커버하지 못해 filesort 발생, 성능 개선 효과 거의 없음 (중간에 끄고 싶을 정도로 오래걸림)
- 정렬 인덱스만: `created_at DESC, id DESC` 역순 인덱스 스캔으로 LIMIT 20만 읽어 매우 빠름
- 조건+정렬 인덱스: `deleted_at` 조건을 강제하면서 정렬도 커버, 정렬 인덱스 단독과 유사한 성능

### 결론
- 삭제 데이터 비율이 낮다면 `(created_at, id)`만으로 충분히 빠름
- 삭제 데이터 비율이 높거나 `deleted_at IS NULL` 조건을 강제하고 싶다면 `(deleted_at, created_at, id)`이 좋아 보이기 때문에 앞으로 운영 상 삭제 데이터의 비율이 점진적으로 늘어날 것이라 판단되어 채택!


## 시나리오 2. 가격 정렬
### 쿼리
```sql
SELECT p.id,
       p.brand_id,
       p.created_at,
       p.deleted_at,
       p.description,
       p.name,
       p.price,
       p.updated_at
FROM product p
INNER JOIN like_count lc
  ON lc.target_id = p.id AND lc.type = 'PRODUCT'
WHERE p.deleted_at IS NULL
ORDER BY p.price DESC, p.id DESC
LIMIT 20;
```

### 테스트 환경
- 데이터 규모: product 1,000만 건, like_count 1,000만 건
- DB 버전: MySQL (InnoDB)
- 조건: `deleted_at IS NULL`
- LIMIT: 20
- 조인: INNER JOIN / LEFT JOIN

### 실행 계획 & 결과
| 인덱스 상태 | 실행 계획 요약 | 실행 시간(ms) | rows read | filesort | 비고 |
|-------------|---------------|--------------:|----------:|----------|------|
| 없음 | Table scan + Filter + Sort | 15438 (INNER)<br>11813 (LEFT) | 9.9M | O | 전체 스캔 후 정렬 |
| 조건 인덱스만<br>(deleted_at, id) | Index range scan + Sort | 27898 (INNER)<br>24992 (LEFT) | 9.9M | O | 정렬 미커버로 filesort 발생 |
| 정렬 인덱스만<br>(price, id) | Reverse index scan | 0.26 (INNER)<br>0.33 (LEFT) | 20 | X | LIMIT 20 즉시 종료 |
| 조건+정렬 인덱스<br>(deleted_at, price, id) | Reverse index scan | 0.87 (INNER)<br>0.79 (LEFT) | 20 | X | 조건+정렬 모두 커버 |

### 분석
- 인덱스 없음: 풀스캔 후 정렬 → 매우 느림
- 조건 인덱스만: 정렬 커버 X, filesort 발생 → 개선 효과 미미
- 정렬 인덱스만: `price DESC, id DESC` 역순 인덱스 스캔으로 LIMIT 20만 읽어 매우 빠름
- 조건+정렬 인덱스: `deleted_at` 조건을 강제하면서 정렬도 커버, 정렬 인덱스 단독과 비슷한 성능

### 결론
- 삭제 데이터 비율이 낮다면 `(price, id)` 인덱스만으로 충분히 빠름
- 삭제 데이터 비율이 높거나 `deleted_at IS NULL` 조건 강제 필요 시 `(deleted_at, price, id)` 마찬가지로 점진적 증가 할 것으로 예상되어 채택!

## 시나리오 3. 좋아요 정렬
### 쿼리
```sql
SELECT p.id,
       p.brand_id,
       p.created_at,
       p.deleted_at,
       p.description,
       p.name,
       p.price,
       p.updated_at
FROM product p
LEFT JOIN like_count lc
  ON lc.target_id = p.id AND lc.type = 'PRODUCT'
WHERE p.deleted_at IS NULL
ORDER BY lc.count DESC, lc.target_id DESC
LIMIT 20;
```

### 테스트 환경
- 데이터 규모: product 1,000만 건, like_count 1,000만 건
- DB 버전: MySQL (InnoDB)
- 조건: `deleted_at IS NULL`
- LIMIT: 20
- 조인: INNER JOIN / LEFT JOIN
- 기본 uk 인덱스: `uk_like_count_target (target_id, type)`

### 실행 계획 & 결과
| 인덱스 상태 | 실행 계획 요약 | 실행 시간(ms) | rows read | filesort | 비고 |
|-------------|---------------|--------------:|----------:|----------|------|
| 없음 (기본 uk만 존재) | Table scan(product) + index lookup(like_count) + sort | 81209 (INNER)<br>58905 (LEFT) | 9.9M | O | 전체 스캔 후 like_count 조회 |
| 조건 인덱스만<br>(product.deleted_at, id) | Index range scan(product) + join + sort | 4780 (INNER)<br>114396 (LEFT) | 9.9M | O | like_count 정렬 미커버 |
| 좋아요 정렬 인덱스만<br>(like_count.type, count, target_id) | Covering index scan(like_count) reverse + join(product) | 66576 (INNER)<br>77565 (LEFT) | 9.9M | O | product filter 미커버 |
| 조건+좋아요 정렬 인덱스<br>(product.deleted_at, id + like_count.type, count, target_id) | Covering index scan(like_count) reverse + PK lookup(product) | 1.70 (INNER)<br>85180 (LEFT) | 20 | X | 조건/정렬 모두 커버 |

### 분석
- 없음 / 조건 인덱스만: 대량 데이터에서 like_count 정렬을 커버하지 못해 성능 저하
- 좋아요 정렬 인덱스만: like_count에서 역순 리밋 빠른 조회가 가능하지만, product 필터링 시 전체 조회 발생
- 조건+좋아요 정렬 인덱스: like_count에서 리밋 빠른 조회 후 product PK lookup으로 매우 빠름 (INNER JOIN 기준)
- LEFT JOIN의 경우, 좋아요 없는 상품도 포함되기 때문에 여전히 product 전체 스캔 발생 가능 (제 비지니스에는 상품을 만들면 like_count에 등록되기 때문에 애초에 좋아요 카운트가 없는 상품은 없음)

### 결론
- 좋아요순 정렬 엔드포인트는 `like_count(type, count, target_id)` 커버링 인덱스를 필수 채택! (물론 도메인 특성 상 좋아요 발생이 잦을 것으로 예상되지만 그건 다음에 고민!)
- 삭제 필터가 중요하면 product에도 `(deleted_at, id)` 추가하여 PK lookup 최적화
- INNER JOIN 패턴에서는 두 테이블 인덱스 최적화로 1ms대 응답 가능, LEFT JOIN은 조건 설계가 필요

## 시나리오 4. 브랜드 아이디 조건 추가 (브랜드별 최신순)
### 쿼리
```sql
SELECT p.id,
       p.brand_id,
       p.created_at,
       p.deleted_at,
       p.description,
       p.name,
       p.price,
       p.updated_at
FROM product p
INNER JOIN like_count lc
  ON lc.target_id = p.id AND lc.type = 'PRODUCT'
WHERE p.deleted_at IS NULL
  AND p.brand_id = 100
ORDER BY p.created_at DESC, p.id DESC
LIMIT 20;
```

### 테스트 환경
- 데이터 규모: product 1,000만 건, like_count 1,000만 건
- DB 버전: MySQL (InnoDB)
- 조건: `deleted_at IS NULL AND brand_id = 100` (10,010건)
- LIMIT: 20
- 조인: INNER JOIN / LEFT JOIN

### 실행 계획 & 결과
| 인덱스 상태 | 실행 계획 요약 | 실행 시간(ms) | rows read | filesort | 비고 |
|-------------|---------------|--------------:|----------:|----------|------|
| 없음 | Table scan + Filter(brand,deleted) + Sort(created_at) | 3518 (INNER)<br>3014 (LEFT) | 10M | O | 전체 스캔 후 정렬 |
| 조건 인덱스만<br>(deleted_at, brand_id, id) | Index range(brand=NULL 파티션) + Sort(created_at) | 110 (INNER)<br>187 (LEFT) | ~10k | O | 정렬 미커버 |
| 정렬 인덱스만<br>(created_at, id) | Reverse index scan + brand 필터 | 100 (INNER)<br>56 (LEFT) | ~8k | X | 정렬 커버, 필터는 스캔 중 적용 |
| 조건+정렬 인덱스<br>(deleted_at, brand_id, created_at, id) | **Reverse index range** (조건+정렬 커버) | **0.30 (INNER)**<br>**0.91 (LEFT)** | 20 | X | 최적 패턴 |

### 분석
- 조건 인덱스만: 브랜드로 카디널리티는 줄었지만 정렬을 커버하지 못해 **filesort** 비용이 남아있음.
- 정렬 인덱스만: 정렬은 커버되지만 브랜드 필터가 **스캔 중 필터링**되어 상위 LIMIT 개수를 찾기까지 스캔량이 커질 수 있음.
- 조건+정렬 인덱스 `(deleted_at, brand_id, created_at, id)`: 브랜드 파티션 안에서 최신순 역방향 스캔으로 즉시 Top‑N 확보 → 가장 안정적이고 빠름.
- INNER/LEFT 모두 이 인덱스에서 매우 빠르며, LEFT가 약간 느린 것은 조인 특성상 부가 체크 비용 때문.

### 결론
- 브랜드별 최신 정렬 엔드포인트는 `idx_product_deleted_at_brand_id_created_at_id` 채택!
- 브랜드 분포가 치우치지 않고 삭제 비율이 낮다면, 운영 단순화를 위해 `(brand_id, created_at, id)`만으로도 충분할 수 있으나, **삭제 조건 일관성**을 위해 위 복합 인덱스를 사용!

---

# 최종 인덱스 선택

### product 테이블
```sql
CREATE INDEX idx_product_deleted_at_created_at_id
    ON product (deleted_at, created_at DESC, id DESC);

CREATE INDEX idx_product_deleted_at_price_id
    ON product (deleted_at, price DESC, id DESC);

CREATE INDEX idx_product_deleted_at_brand_id_created_at_id
    ON product (deleted_at, brand_id, created_at DESC, id DESC);
```

### like_count 테이블
```sql
CREATE INDEX idx_like_count_type_count_target_id
    ON like_count (type, count DESC, target_id DESC);

-- (참고) 이미 존재: uk_like_count_target(target_id, type)
```

> 이유: 각 시나리오의 ORDER BY와 WHERE를 **동시에** 커버하는 컬럼 순서를 채택!
> - 정렬 동률 방지와 인덱스 효율을 위해 `id DESC`를 보조 정렬키로 포함시켰습니다.

## 시나리오별 인덱스 적용 근거

| 시나리오      | 추천 인덱스 | INNER JOIN(ms) | LEFT JOIN(ms) | 근거 요약                                                       |
|-----------|---|---:|---:|-------------------------------------------------------------|
| 등록일 정렬 | `product(deleted_at, created_at, id)` | **1.02** | **0.83** | 정렬·조건 동시 커버 → filesort 제거, 20건만 스캔                          |
| 가격 정렬 | `product(deleted_at, price, id)` | **0.87** | **0.79** | 정렬·조건 동시 커버 → 최단 시간                                         |
| 좋아요 정렬 | `like_count(type, count, target_id)` | **1.70** |  –  | like_count 커버링 → Top‑N 후 `product` PK lookup (LEFT는 구조상 불리) |
| 브랜드+최신 | `product(deleted_at, brand_id, created_at, id)` | **0.30** | **0.91** | 브랜드 파티션 내 최신순 **역방향 레인지** → 최적                              |

- 공통 패턴: **정렬 인덱스만** 또는 **조건 인덱스만**은 filesort/불필요 스캔으로 느렸고,  
  **조건+정렬** 인덱스가 항상 가장 안정적이고 빨랐습니다.