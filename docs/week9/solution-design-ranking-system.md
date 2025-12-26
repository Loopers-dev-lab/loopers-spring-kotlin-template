# 솔루션 설계 문서: 랭킹 시스템

## 1. 설계 컨텍스트

### 1.1 핵심 해결 과제

- 사용자 행동(조회, 좋아요, 주문)을 종합하여 인기 상품 랭킹 제공
- 구매자가 인기 상품을 쉽게 탐색할 수 있도록 실시간 랭킹 조회 지원
- 관리자가 항목별 Weight를 실시간으로 조절하여 최적의 랭킹 정책 탐색 가능
- 인기 상품 노출을 통한 구매 전환율 향상

### 1.2 현재 아키텍처 영향

- Kotlin + Spring Boot 멀티모듈 모놀리식 프로젝트 (commerce-api, commerce-streamer)
- Application Layer의 Service(단일 도메인) + Facade(도메인 간 오케스트레이션) 구조
- Transactional Outbox 패턴으로 도메인 이벤트를 Kafka로 발행하는 파이프라인 구축됨
- 랭킹에 필요한 이벤트(ProductViewedEventV1, LikeCreatedEventV1, OrderPaidEventV1)가 이미 발행 중
- Redis 모듈 존재

### 1.3 기술 스택 개요

- 개발 언어: Kotlin (Spring Boot 프레임워크)
- 데이터베이스: PostgreSQL (RDB)
- 캐시: Redis
- 메시지 큐: Kafka
- ORM: Spring Data JPA

## 2. 솔루션 대안 분석

### 대안 1: commerce-streamer에서 집계 + Redis ZSET 직접 관리

- **설명**: commerce-streamer가 Kafka에서 이벤트를 소비하여 Redis ZSET에 직접 Score를 업데이트한다. commerce-api의 Rankings 도메인은 Redis를 조회만 한다.
- **문제 해결 방식**: 이벤트 발생 시 Redis ZSET의 해당 상품 Score를 Weight 기반으로 증감하고, Weight 변경 시 전체 Score를 재계산한다.
- **장점**:
    - 실시간성이 높음 (이벤트 발생 즉시 Score 반영)
    - 조회 성능 최적화 (Redis ZSET O(log N))
    - 기존 Outbox → Kafka 파이프라인 활용
- **단점**:
    - Score 재계산 시 원본 데이터 필요 (Redis에는 최종 Score만 존재)
    - Weight 변경 시 전체 재계산을 위한 별도 저장소 필요
    - 이벤트 유실 시 복구 어려움
- **아키텍처 영향**: commerce-streamer에 Kafka Consumer 추가, commerce-api에 Rankings 도메인 추가

### 대안 2: commerce-api에서 집계 + RDB 기반 Score 관리

- **설명**: commerce-api 내에서 이벤트를 수신하여 RDB에 행동 카운트를 저장하고, 조회 시점에 Score를 계산하거나 주기적으로 Redis에 캐싱한다.
- **문제 해결 방식**: Rankings 도메인이 Spring Application Event로 이벤트 수신하고, 상품별 행동 카운트를 RDB에 저장한다. 주기적으로 Score를 계산하여 Redis에 캐싱한다.
- **장점**:
    - Weight 변경 시 재계산 용이 (원본 카운트 보존)
    - 데이터 정합성 관리 용이 (RDB ACID)
    - Kafka 의존성 없이 구현 가능
- **단점**:
    - 실시간성 떨어짐 (캐시 갱신 주기에 의존)
    - Application Event는 프로세스 내부에서만 동작 (인스턴스 간 동기화 이슈)
    - 조회 시점 계산은 성능 문제 발생 가능
- **아키텍처 영향**: commerce-api에 Rankings 도메인 추가, 행동 카운트 테이블 및 캐싱 스케줄러 추가

### 대안 3: commerce-streamer에서 집계 + RDB 원본 + Redis 캐시 (하이브리드)

- **설명**: commerce-streamer가 Kafka 이벤트를 소비하여 RDB에 행동 카운트를 저장하고, 주기적으로 Score를 계산하여 Redis에 캐싱한다. Pre-aggregated 패턴을 적용한다.
- **문제 해결 방식**: commerce-streamer가 이벤트 소비 후 RDB에 카운트 저장하고, 스케줄러가 주기적으로 Score 계산하여 Redis ZSET을 갱신한다. Weight 변경 시 즉시 전체 재계산을 트리거한다.
- **장점**:
    - Weight 변경 시 재계산 용이 (원본 카운트 보존)
    - 이벤트 유실 시에도 RDB에서 복구 가능
    - Kafka 기반으로 인스턴스 간 동기화 문제 없음
    - 조회 성능 최적화 (Redis ZSET)
- **단점**:
    - 실시간성은 스케줄러 주기에 의존 (수 분 지연 허용하므로 OK)
    - 저장소 이중화 (RDB + Redis)
    - 구현 복잡도 상승
- **아키텍처 영향**: commerce-streamer에 Kafka Consumer 및 집계 로직 추가, commerce-api에 Rankings 도메인 추가

## 3. 선택된 솔루션

### 3.1 결정 요약

**대안 3: commerce-streamer에서 집계 + RDB 원본 + Redis 캐시 (하이브리드)**를 선택한다.

**선택 이유**:

1. 팀의 "ROI 중심" 가치에 부합
    - 기존 Outbox → Kafka 파이프라인을 그대로 활용하여 새로운 인프라 도입 없이 구현 가능
    - Redis 모듈도 이미 존재
2. 팀의 "실용주의" 가치에 부합
    - Weight 변경 시 전체 재계산이라는 요구사항을 RDB 원본 카운트로 쉽게 해결
    - 이벤트 유실 시에도 복구 가능한 안정성 확보
3. 요구사항 충족
    - 랭킹 조회 50ms 이하: Redis ZSET으로 해결
    - 수 분 내 반영 허용: 스케줄러 주기로 충분
    - Weight 변경 후 재계산: 원본 카운트 기반으로 즉시 가능
4. 기존 아키텍처 패턴과 일관성
    - commerce-streamer가 이미 Kafka Consumer 역할을 하도록 설계됨
    - 도메인 간 이벤트 기반 통신 패턴 유지

### 3.2 솔루션 구조

#### 핵심 아키텍처 컴포넌트

**1. Rankings 도메인 (commerce-api)**

- 인기 상품 랭킹 조회 (페이지네이션)
- 특정 상품의 현재 순위 조회
- Weight 설정 조회 및 변경
- Weight 변경 시 전체 Score 재계산 트리거

**2. 랭킹 집계 시스템 (commerce-streamer)**

- Kafka에서 ProductViewed, LikeCreated, LikeCanceled, OrderPaid 이벤트 소비
- RankingAggregationBuffer(메모리)에서 집계 후 주기적 flush
- 1시간 단위 Tumbling Window 집계
- 버킷 전환 시 이전 Score × 0.1을 base로 적용 (cold start 방지)
- 증분 업데이트는 ZINCRBY, Weight 변경 시만 전체 ZADD

**3. 데이터 저장소**

- RDB: 시간 버킷별 상품 행동 카운트 (bucket_time, product_id, view_count, like_count, order_count), Weight 설정 (view_weight, like_weight, order_weight)
- Redis: ZSET으로 상품 랭킹 저장 (key: ranking:products:{bucket}, member: productId, score: 계산된 인기도)

#### 핵심 설계 결정

**1. Tumbling Window + Decay Factor**

- 1시간 단위 버킷으로 집계
- 정시가 되면 새 버킷 시작
- 새 버킷 시작 시: 이전 Score × 0.1을 base로 깔고 시작 (cold start 방지)
- 이후 들어오는 이벤트는 현재 버킷에 × 1.0으로 반영

**2. RankingAggregationBuffer**

- 이벤트 수신 시 메모리 버퍼에 적재
- 주기적으로 RDB flush + Redis ZINCRBY
- 이벤트당 DB I/O 제거, 증분 업데이트로 성능 최적화

**3. Weight 관리**

- RDB에 Weight 저장 (영속성)
- Score 계산 시마다 RDB에서 조회 (항상 최신 값 보장)
- 별도 캐싱이나 이벤트 동기화 불필요

#### 데이터 흐름

**1. 이벤트 수집 및 집계 흐름**

```mermaid
sequenceDiagram
    participant K as Kafka
    participant CS as 랭킹 집계 시스템<br/>(commerce-streamer)
    participant BUF as RankingAggregationBuffer<br/>(메모리)
    participant DB as RDB
    participant R as Redis

    K->>CS: 이벤트 수신 (ProductViewed/LikeCreated/OrderPaid)
    activate CS
    CS->>BUF: 상품별 카운트 증가
    deactivate CS

    Note over CS,R: 주기적 flush (예: 10초)

    CS->>DB: 현재 버킷에 카운트 저장
    activate CS
    activate DB
    DB-->>CS: 저장 완료
    deactivate DB
    
    CS->>DB: Weight 설정 조회
    activate DB
    DB-->>CS: Weight 반환
    deactivate DB
    
    CS->>CS: 증분 Score 계산<br/>(Δview×W1 + Δlike×W2 + Δorder×W3)
    
    CS->>R: ZINCRBY (증분 Score 추가)
    activate R
    R-->>CS: 갱신 완료
    deactivate R
    
    CS->>BUF: 버퍼 초기화
    deactivate CS
```

**2. 버킷 전환 흐름 (정시 10분 전 실행)**

```mermaid
sequenceDiagram
    participant CS as 랭킹 집계 시스템<br/>(commerce-streamer)
    participant DB as RDB
    participant R as Redis

    Note over CS: 정시 10분 전 (예: 14:50)

    CS->>R: 현재 ZSET 전체 조회 (ZRANGE)
    activate CS
    activate R
    R-->>CS: 상품별 Score 목록
    deactivate R
    
    CS->>CS: 이전 Score × 0.1 계산
    
    CS->>R: 다음 버킷 ZSET 생성 (ZADD with decayed scores)
    activate R
    R-->>CS: 생성 완료
    deactivate R
    
    CS->>DB: 다음 시간 버킷 생성
    activate DB
    DB-->>CS: 생성 완료
    deactivate DB
    deactivate CS

    Note over CS,R: 정시 도달 시 (15:00)<br/>새 버킷으로 자연스럽게 전환
```

**3. 랭킹 조회 흐름 (US-1)**

```mermaid
sequenceDiagram
    participant C as Client
    participant RS as Rankings 도메인<br/>(commerce-api)
    participant R as Redis
    participant PS as Product 도메인<br/>(commerce-api)

    C->>RS: 랭킹 조회 요청 (page, size)
    activate RS
    
    RS->>R: ZREVRANGE (상위 N개 조회)
    activate R
    R-->>RS: productId 목록 + score
    deactivate R
    
    RS->>PS: 상품 정보 조회 (productIds)
    activate PS
    PS-->>RS: 상품 정보 목록
    deactivate PS
    
    RS-->>C: 랭킹 목록 응답 (상품 정보 + 순위)
    deactivate RS
```

**4. 상품 순위 확인 흐름 (US-2)**

```mermaid
sequenceDiagram
    participant C as Client
    participant PS as Product 도메인<br/>(commerce-api)
    participant RS as Rankings 도메인<br/>(commerce-api)
    participant R as Redis

    C->>PS: 상품 상세 조회 (productId)
    activate PS
    
    PS->>RS: 상품 순위 조회 (productId)
    activate RS
    RS->>R: ZREVRANK (순위 조회)
    activate R
    R-->>RS: 순위 반환 (또는 null)
    deactivate R
    RS-->>PS: 순위 반환
    deactivate RS
    
    PS-->>C: 상품 상세 + 순위 응답
    deactivate PS
```

**5. Weight 변경 및 재계산 흐름 (US-3)**

```mermaid
sequenceDiagram
    participant C as Admin Client
    participant RS as Rankings 도메인<br/>(commerce-api)
    participant DB as RDB
    participant R as Redis

    C->>RS: Weight 변경 요청
    activate RS
    
    alt 유효성 검증 실패
        RS-->>C: 에러 응답 (음수 등)
    else 유효성 검증 성공
        RS->>DB: Weight 설정 업데이트
        activate DB
        DB-->>RS: 업데이트 완료
        deactivate DB
        
        RS->>DB: 현재 버킷 카운트 조회
        activate DB
        DB-->>RS: 상품별 카운트 목록
        deactivate DB
        
        RS->>R: 이전 Score 조회 (ZRANGE)
        activate R
        R-->>RS: 상품별 이전 Score
        deactivate R
        
        RS->>RS: Score 재계산<br/>(카운트 × 새 Weight) + (이전 Score × 0.1)
        
        RS->>R: ZADD (전체 Score 교체)
        activate R
        R-->>RS: 갱신 완료
        deactivate R
        
        RS-->>C: 변경 완료 응답
    end
    deactivate RS
```
