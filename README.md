# E-Commerce Platform (Spring + Kotlin)

이 프로젝트는 10주간의 학습을 하며 구축한 이커머스 플랫폼입니다.  
TDD, 동시성 제어, 성능 최적화, 이벤트 기반 아키텍처, 분산 시스템 설계까지  
백엔드 개발의 개념들을 학습하며 단계적으로 적용했습니다.

## 프로젝트 개요

### 비즈니스 도메인

- 상품 조회 및 브랜드별 필터링
- 좋아요 기능 (찜하기)
- 포인트 관리 및 충전
- 주문/결제 (쿠폰 할인 지원)
- 인기 상품 랭킹 시스템

### 기술 스택

- **언어**: Kotlin 2.3.0
- **프레임워크**: Spring Boot 4.0.1, Spring Data JPA
- **클라우드**: Spring Cloud 2025.1.0
- **데이터베이스**: MySQL 8.0, Hibernate DDL (local/test), Flyway (배치 랭킹 테이블)
- **캐싱**: Redis 7.0
- **메시징**: Kafka 3.x
- **배치**: Spring Batch
- **직렬화**: Jackson 3.0.3
- **API 문서**: SpringDoc OpenAPI 3.0.0
- **결제 연동**: 외부 PG사 연동 (Resilience4j)
- **모니터링**: Prometheus + Grafana
- **테스트**: JUnit 5, MockK 5.0.1, Mockito 5.21.0

---

## 주차별 PR 링크

- [01주차 (2025.10.26-2025.11.01) : **테스트 가능한 구조**](https://github.com/ghojeong/loopers-round-1/pull/1)
- [02주차 (2025.11.02-2025.11.08) : **이커머스 설계**](https://github.com/Loopers-dev-lab/loopers-spring-kotlin-template/pull/19)
- [03주차 (2025.11.09-2025.11.15) : **도메인 모델링**](https://github.com/Loopers-dev-lab/loopers-spring-kotlin-template/pull/25)
- [04주차 (2025.11.16-2025.11.22) : **트랜잭션과 락**](https://github.com/Loopers-dev-lab/loopers-spring-kotlin-template/pull/31)
- [05주차 (2025.11.23-2025.11.29) : **조회 성능 최적화**](https://github.com/Loopers-dev-lab/loopers-spring-kotlin-template/pull/41)
- [06주차 (2025.11.30-2025.12.06) : **외부 시스템 연동**](https://github.com/Loopers-dev-lab/loopers-spring-kotlin-template/pull/48)
- [07주차 (2025.12.07-2025.12.13) : **이벤트 기반 아키텍처**](https://github.com/Loopers-dev-lab/loopers-spring-kotlin-template/pull/54)
- [08주차 (2025.12.14-2025.12.20) : **카프카 분산 메시징**](https://github.com/Loopers-dev-lab/loopers-spring-kotlin-template/pull/64)
- [09주차 (2025.12.21-2025.12.27) : **상품 인기 랭킹**](https://github.com/Loopers-dev-lab/loopers-spring-kotlin-template/pull/70)
- [10주차 (2025.12.28-2026.01.03) : **주간/월간 인기 랭킹 배치**](https://github.com/Loopers-dev-lab/loopers-spring-kotlin-template/pull/76)

---

## 10주간의 개발 히스토리

### Week 1: 테스트 가능한 구조

**주제:** TDD

**학습 내용:**

- **단위 테스트의 가치**: E2E 테스트보다 단위 테스트가 개발 사이클을 빠르게 만듦
- **도메인으로 검증 분리**: Controller의 `@Valid` 대신 User 도메인에 검증 로직 이동
- **Mock vs Fake vs Spy**: 각각의 차이와 사용 시점 이해
- **TDD의 본질**: "테스트 먼저"가 아닌 "테스트 가능한 구조"

### Week 2: 이커머스 설계

**주제:** 요구사항을 설계 문서로 옮기고, 도메인을 해석하는 힘을 기른다.

**학습 내용:**

- **요구사항 정의서**
- **시퀀스 다이어그램**
- **클래스 다이어그램**
- **ERD (Entity Relationship Diagram)**

### Week 3: 도메인 모델링

**주제:** 응집성, 데이터를 가진 쪽이 로직도 가져야 한다.

**학습 내용:**

- **Value Object**: Price, Money로 비즈니스 규칙 캡슐화
- **Entity 책임**: Stock이 스스로 재고 감소 검증
- **상태 전이 제어**: Order가 스스로 취소 가능 여부 판단
- **Tell, Don't Ask**: `stock.decrease(5)` vs `stock.quantity -= 5`

### Week 4: 트랜잭션과 락

**주제:** 트랜잭션과 락은 정답이 없다. '이 도메인에서 무엇이 가장 중요한가?'를 먼저 고민해야 한다.

**학습 내용:**

- **@Transactional의 한계**: 동시성 제어는 별개
- **Gap Lock 데드락**: UniqueConstraint + 비관적 락의 위험
- **REQUIRES_NEW 함정**: 외부 트랜잭션과 원자성 깨짐
- **재시도 전략**: PessimisticLockException 발생 시 재시도

### Week 5: 조회 성능 최적화

**주제:** 비정규화와 캐싱을 통해 조회 성능을 향상시킬 수 있다.

**학습 내용:**

- **정규화의 함정**: 10만건 데이터에서 1초씩 걸리는 쿼리
- **비정규화 결정**: likeCount 비정규화 컬럼 추가로 JOIN 제거
- **복합 인덱스 설계**: `(brand_id, like_count)` 순서의 중요성
- **Redis 캐싱**: 조회 성능 향상
- **Atomic 연산**: Redis INCR/DECR로 동시성 안전

### Week 6: 외부 시스템 연동

**주제:** 외부 시스템은 언제든 죽을 수 있다. 우리 시스템은 그래도 살아있어야 한다.

**학습 내용:**

- **Resilience4j**: Timeout, Retry, Circuit Breaker 패턴
- **Fallback 메커니즘**: 외부 장애 시 안전한 대체 동작
- **결제 상태 동기화**: 스케줄러로 주기적 상태 확인

### Week 7: 이벤트 기반 아키텍처

**주제:** 트랜잭션을 분리하면 결합도가 낮아지고, 각 도메인이 독립적으로 확장 가능해진다.

**학습 내용:**

- **ApplicationEvent**: Spring 내장 이벤트 시스템
- **@TransactionalEventListener**: AFTER_COMMIT으로 트랜잭션 분리
- **@Async**: 비동기 처리로 응답 속도 개선

### Week 8: 카프카 분산 메시징

**주제:** 분산 환경에서는 'At-Least-Once' 를 보장하고, 멱등성으로 'Exactly-Once' 효과를 낸다.

**학습 내용:**

- **Kafka**: 분산 메시징 시스템
- **Transactional Outbox Pattern**: 이벤트 유실 방지
- **Idempotent Consumer Pattern**: 중복 처리 방지
- **Manual Acknowledgment**: 처리 성공 후에만 Commit

### Week 9: 상품 인기 랭킹

**주제:** 좋은 인기 랭킹은 단순히 '많이 팔린 순'이 아니라, 시간과 맥락을 고려한 설계에서 나온다.

**학습 내용:**

- **Time Quantization**: 시간 단위로 윈도우 분할
- **Score Carry-Over**: 이전 윈도우 점수 10% 승계 (콜드 스타트 방지)
- **Log Normalization**: 극단값 완화로 공정한 점수

### Week 10: 주간/월간 인기 랭킹 배치

**주제:** 실시간 집계는 비용이 크다. 배치로 미리 계산하면 조회는 단순 SELECT가 된다.

**학습 내용:**

- **Spring Batch**: Chunk-Oriented Processing
- **Materialized View**: 집계 결과를 별도 테이블에 저장
- **Delete-then-Insert**: 멱등성 보장
- **Flyway**: 랭킹 테이블 스키마 마이그레이션 (배치 앱 전용)

---

## 주요 구현 기능

### 1. 상품 관리 (Product)

- **상품 조회**: 브랜드별 필터링, 가격/좋아요 순 정렬
- **재고 관리**: 비관적 락을 통한 동시성 제어
- **좋아요 기능**: UniqueConstraint + 멱등성 보장

**기술적 특징:**

- 복합 인덱스 (`brand_id, like_count`, `brand_id, price`)
- Redis 캐싱으로 조회 성능 향상
- 비정규화 (likeCount 컬럼)를 통한 JOIN 제거

### 2. 주문 시스템 (Order)

- **주문 생성**: 재고 차감, 포인트 결제, 쿠폰 할인 적용
- **주문 취소**: 재고 복구, 포인트 환불
- **동시성 제어**: 비관적 락 + 재시도 전략

**트랜잭션 관리:**

```txt
// 핵심 주문 생성 로직 (트랜잭션 내)
1. 쿠폰 사용 (FOR UPDATE 락)
2. 할인 금액 계산
3. 포인트 차감 (FOR UPDATE 락)
4. 재고 차감 (FOR UPDATE 락)
5. 주문 생성
```

### 3. 포인트 시스템 (Point)

- **충전/차감**: 동시성 안전 보장 (비관적 락)
- **잔액 조회**: 실시간 잔액 확인

**동시성 제어:**

- `PESSIMISTIC_WRITE` 락으로 Lost Update 방지
- 재시도 전략 (최대 3회, 지수 백오프)
- 포인트 음수 방지 보장

### 4. 쿠폰 시스템 (Coupon)

- **정액/정률 할인**: 다양한 할인 정책 지원
- **1회 사용 제한**: 중복 사용 방지
- **동시성 제어**: 비관적 락으로 쿠폰 중복 사용 차단

### 5. 결제 연동 (Payment)

- **외부 PG 연동**: Timeout, Retry, Circuit Breaker 패턴 적용
- **장애 대응**: Fallback 메커니즘으로 안정성 확보
- **결제 상태 관리**: PENDING → PROCESSING → COMPLETED/FAILED/TIMEOUT
- **실패 추적**: failureReason으로 실패 원인 기록
- **상태 동기화**: 스케줄러로 주기적 상태 확인

### 6. 랭킹 시스템 (Ranking)

- **실시간 랭킹**: Redis ZSET으로 O(log N) 성능
- **시간 기반 분할**: 시간/일간 윈도우 (Time Quantization)
- **콜드 스타트 방지**: 이전 윈도우 점수 10% 승계
- **공정한 스코어링**: 로그 정규화로 극단값 완화

### 7. 주간/월간 랭킹 집계 (Batch)

- **Spring Batch**: Chunk-Oriented Processing으로 대용량 데이터 처리
- **Materialized View**: 집계된 랭킹 데이터를 별도 테이블에 저장
- **Delete-then-Insert**: 멱등성 보장

**배치 처리 흐름:**

```txt
ItemReader (일간 랭킹 조회)
    ↓
ItemProcessor (주간/월간 집계)
    ↓
ItemWriter (Materialized View 저장)
```

### 8. 이벤트 기반 아키텍처

- **Spring ApplicationEvent**: 트랜잭션 분리 및 도메인 결합도 감소
- **Kafka**: 분산 환경에서의 이벤트 처리
- **Transactional Outbox**: 이벤트 유실 방지
- **Idempotent Consumer**: 중복 처리 방지

---

## 아키텍처 설계

### 계층 구조 (Layered Architecture)

```txt
┌─────────────────────────────────────┐
│   Presentation Layer (Controller)   │
├─────────────────────────────────────┤
│   Application Layer (Facade/Service)│
├─────────────────────────────────────┤
│   Domain Layer (Entity, VO, Logic)  │
├─────────────────────────────────────┤
│   Infrastructure Layer (Repository) │
└─────────────────────────────────────┘
```

### Bounded Context 분리

- **User BC**: 사용자 관리, 포인트
- **Brand BC**: 브랜드 정보
- **Product BC**: 상품, 재고
- **Like BC**: 좋아요
- **Order BC**: 주문, 주문 항목
- **Coupon BC**: 쿠폰, 사용자 쿠폰
- **Payment BC**: 결제 정보
- **Ranking BC**: 랭킹 시스템

### 도메인 모델링

- **Value Object**: Price, Money (불변, 자체 검증)
- **Entity**: Product, Order, Stock (ID 식별, 상태 관리)
- **Aggregate Root**: Order와 OrderItem의 일관성 보장
- **특수 엔티티**: Point, Stock 는 부속 데이터 (Shared Primary Key 패턴)

**Point/Stock이 BaseEntity를 상속하지 않는 이유:**

1. **Shared Primary Key 패턴**: FK를 PK로 직접 사용 (User 1:1 Point, Product 1:1 Stock)
2. **부속 데이터 특성**: 부모 엔티티의 생명주기에 종속되어 독립적인 ID 불필요
3. **Soft Delete 불필요**: 부모 엔티티 삭제 시 CASCADE로 함께 삭제
4. **성능 최적화**: PK 인덱스 하나로 충분, 별도 UNIQUE 인덱스 불필요

**Value Object 예시:**

```kotlin
@Embeddable
data class Price(
    val amount: BigDecimal,
    val currency: Currency = Currency.KRW
) {
    init {
        require(amount >= BigDecimal.ZERO) { "금액은 0 이상이어야 합니다." }
    }

    operator fun plus(other: Price): Price {
        require(currency == other.currency) { "통화가 다릅니다." }
        return Price(amount + other.amount, currency)
    }
}
```

### 동시성 제어 전략

| 도메인 | 전략 | 이유 |
|:--------|:------:|------:|
| 재고 (Stock) | 비관적 락 | 음수 재고 절대 방지 |
| 포인트 (Point) | 비관적 락 | 음수 잔액 절대 방지 |
| 쿠폰 (UserCoupon) | 비관적 락 | 중복 사용 절대 방지 |
| 좋아요 (Like) | UniqueConstraint | 중복만 막으면 됨, Gap Lock 회피 |
| 좋아요 카운트 | Redis Atomic | 동시성 안전 + 성능 |

---

## 프로젝트 구조

### Multi-Module Architecture

```txt
Root
├── apps (Spring Boot Applications)
│   ├── commerce-api (메인 API 서버)
│   ├── commerce-streamer (Kafka Consumer, 실시간 이벤트 처리)
│   ├── commerce-batch (Spring Batch 배치 처리) ← 독립 모듈
│   └── pg-simulator (PG 결제 시뮬레이터)
├── modules (재사용 가능한 설정 및 공유 엔티티)
│   ├── jpa (JPA 설정, Auditing, 공유 엔티티)
│   │   ├── BaseEntity (대부분 엔티티의 기본 클래스, Point/Stock 제외)
│   │   ├── ProductRankWeekly/Monthly (주간/월간 랭킹 집계)
│   │   └── Repository 인터페이스 (ProductRankWeekly/MonthlyRepository)
│   ├── redis (Redis 설정, Cache, 랭킹 시스템)
│   │   ├── 랭킹 도메인 (Ranking, RankingKey, RankingScore, RankingScope)
│   │   ├── RankingRepository 인터페이스
│   │   └── RankingRedisRepository 구현체
│   └── kafka (Kafka 설정, 공유 이벤트)
│       ├── 이벤트 도메인 (LikeEvent, StockEvent)
│       └── KafkaTopicConfig (토픽 설정)
└── supports (부가 기능)
    ├── jackson (JSON 직렬화)
    ├── logging (Structured Logging)
    └── monitoring (Prometheus, Grafana)
```

### 주요 패키지 구조

```txt
com.loopers.commerce
├── api
│   ├── controller         # REST API 엔드포인트
│   └── dto                # Request/Response DTO
├── application
│   ├── facade             # 여러 도메인 조합
│   └── service            # 비즈니스 로직
├── domain
│   ├── entity             # JPA 엔티티
│   ├── vo                 # Value Object
│   └── repository         # 인터페이스
├── infrastructure
│   ├── jpa                # JPA 구현체
│   ├── redis              # Redis 구현체
│   └── kafka              # Kafka Producer
└── event                  # 도메인 이벤트
```

---

## Getting Started

### 1. 사전 요구사항

- Java 25
- Docker & Docker Compose
- Gradle 9.2.1

### 2. 프로젝트 초기 설정

```bash
# pre-commit 훅 설치 (ktlint 자동 점검)
make init
```

### 3. 인프라 실행

```bash
# MySQL, Redis, Kafka 실행
docker-compose -f ./docker/infra-compose.yml up -d

# 실행 확인
docker-compose -f ./docker/infra-compose.yml ps
```

**포트 정보:**

- MySQL: `localhost:3306`
- Redis: `localhost:6379`
- Kafka: `localhost:19092` (외부 접속용)
- Zookeeper: `localhost:2181`

**데이터베이스 스키마 관리:**

이 프로젝트는 환경별로 다른 스키마 관리 전략을 사용합니다:

- **Local/Test 환경**: Hibernate auto-DDL (`ddl-auto: create`)
  - JPA 엔티티로부터 자동으로 테이블 생성
  - 개발 편의성을 위해 매번 스키마를 새로 생성
- **Production 환경**: 수동 스키마 관리 (`ddl-auto: none`)
  - 프로덕션에서는 DDL 자동 생성 비활성화
  - 스키마는 별도로 관리 필요
- **Batch 랭킹 테이블**: Flyway 마이그레이션
  - `apps/commerce-batch/src/main/resources/db/migration/` 참고
  - V2: ProductRankDaily, ProductRankWeekly, ProductRankMonthly
  - V3: ProductMetrics

### 4. 애플리케이션 실행

```bash
# API 서버 실행
./gradlew :apps:commerce-api:bootRun

# Kafka Consumer 실행 (별도 터미널)
KAFKA_BOOTSTRAP_SERVERS=localhost:19092 ./gradlew :apps:commerce-streamer:bootRun

# Batch 서버 실행 (독립 실행 가능)
./gradlew :apps:commerce-batch:bootRun

# 주간 랭킹 배치 수동 실행
# targetDate 형식: YYYY-MM-DD (예: 2025-12-29)
./gradlew :apps:commerce-batch:bootRun --args='--spring.batch.job.name=weeklyRankingAggregationJob targetDate=2025-12-29'

# 월간 랭킹 배치 수동 실행
# targetYearMonth 형식: YYYYMM (예: 202412)
./gradlew :apps:commerce-batch:bootRun --args='--spring.batch.job.name=monthlyRankingAggregationJob targetYearMonth=202412'
```

애플리케이션 실행 확인:

- API 서버: <http://localhost:8080>
- Actuator Health: <http://localhost:8080/actuator/health>
- Batch 서버: <http://localhost:8082> (포트 변경 필요 시)

### 5. 모니터링 (선택사항)

```bash
# Prometheus & Grafana 실행
docker-compose -f ./docker/monitoring-compose.yml up -d

# Grafana 접속
# http://localhost:3000 (admin/admin)
```

---

## 테스트 실행

### 전체 테스트 실행

```bash
# 모든 모듈의 테스트 실행
./gradlew test

# 특정 모듈만 실행
./gradlew :apps:commerce-api:test
```

### 테스트 실행법

#### 빌드 시 테스트 실행

```bash
# docker compose -f ./docker/infra-compose.yml up 실행 필수
./gradlew clean build
```

#### 단위 및 통합 테스트

```bash
# API 테스트 (infra-compose.yml 실행 필수)
./gradlew :apps:commerce-api:test
```

#### Kafka 연동 테스트

```bash
# Kafka Consumer 통합 테스트 (infra-compose.yml 실행 필수)
KAFKA_BOOTSTRAP_SERVERS=localhost:19092 ./gradlew :apps:commerce-streamer:test
```

#### Batch 테스트

```bash
# Batch 모듈 테스트 (infra-compose.yml 실행 필수)
./gradlew :apps:commerce-batch:test
```
