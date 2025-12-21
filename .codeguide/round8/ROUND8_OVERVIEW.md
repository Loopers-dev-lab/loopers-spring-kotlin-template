# Round 8: Kafka 이벤트 파이프라인 구현 완전 정리

> **목적**: Round 8 구현 과제의 전체 구조와 핵심 개념을 한눈에 이해할 수 있도록 정리

---

## 📌 두 기술 문서 비교

### TECHNICAL_WRITING_ROUND8-1.md: "Kafka PartitionKey를 aggregateId로 설정한 이유"
**장점:**
- 실제 문제 상황과 해결 과정이 구체적
- 테스트 시나리오가 명확 (좋아요 추가/취소)
- 코드 예시가 적절
- 배치 처리까지 다룸

**단점:**
- 구조가 약간 산만할 수 있음

### TECHNICAL_WRITING_ROUND8-2.md: "왜 이벤트 핸들링 테이블과 로그 테이블을 분리했을까?"
**장점:**
- 4가지 문제점을 체계적으로 정리
- 비교표로 이해하기 쉬움
- 마이크로서비스 아키텍처 관점이 잘 드러남
- 구조가 명확하고 논리적

**단점:**
- 실제 테스트 경험이 덜 드러남

**결론**: **2번이 더 나음** - 구조가 체계적이고, 설계 결정의 이유가 명확하게 드러남

---

## 🎯 Round 8 과제 목표

### 핵심 목표
1. **Kafka 기반 이벤트 파이프라인 구축**
   - `commerce-api` (Producer) → Kafka → `commerce-streamer` (Consumer)

2. **At Least Once Producer 보장**
   - Transactional Outbox Pattern 구현
   - 이벤트 유실 방지

3. **At Most Once Consumer (멱등 처리)**
   - 중복 메시지 처리 방지
   - `event_handled` 테이블로 멱등성 보장

4. **이벤트 순서 보장**
   - PartitionKey = aggregateId 설정
   - 같은 집계 단위의 이벤트는 순서대로 처리

---

## 🏗️ 전체 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    commerce-api (Producer)                    │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  [비즈니스 로직]                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ LikeService  │  │ OrderService │  │ProductService│      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                 │                  │               │
│         └─────────────────┼──────────────────┘               │
│                           │                                   │
│                  ApplicationEventPublisher                     │
│                           │                                   │
│                           ▼                                   │
│  ┌─────────────────────────────────────────┐                  │
│  │  OutboxEventListener (BEFORE_COMMIT)  │                  │
│  │  - DomainEvent → EventOutbox 저장      │                  │
│  └──────────────────┬─────────────────────┘                  │
│                     │                                         │
│                     ▼                                         │
│  ┌─────────────────────────────────────────┐                  │
│  │      EventOutbox 테이블 (DB)            │                  │
│  │  - eventId, aggregateId, payload       │                  │
│  │  - processed=false (미발행)            │                  │
│  └──────────────────┬─────────────────────┘                  │
│                     │                                         │
│                     ▼                                         │
│  ┌─────────────────────────────────────────┐                  │
│  │  OutboxEventPublisher (스케줄러 1초)   │                  │
│  │  - EventOutbox 조회 (processed=false)  │                  │
│  │  - Kafka 발행                          │                  │
│  │  - processed=true 업데이트             │                  │
│  └──────────────────┬─────────────────────┘                  │
└─────────────────────┼─────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                      Kafka Broker                           │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐      ┌──────────────────┐            │
│  │ catalog-events  │      │  order-events    │            │
│  │ (key=productId)  │      │  (key=orderId)   │            │
│  └──────────────────┘      └──────────────────┘            │
│                                                               │
└─────────────────────┬─────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│              commerce-streamer (Consumer)                    │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────┐   ┌──────────────────────┐       │
│  │ MetricsKafka         │   │ CacheInvalidation    │       │
│  │ Consumer             │   │ KafkaConsumer        │       │
│  │ (metrics-group)      │   │ (cache-group)        │       │
│  └──────────┬───────────┘   └──────────┬───────────┘       │
│             │                          │                     │
│             ▼                          ▼                     │
│  ┌──────────────────────┐   ┌──────────────────────┐       │
│  │ MetricsEventFacade   │   │ CacheInvalidation    │       │
│  │ - event_handled      │   │ Facade               │       │
│  │ - 이벤트 라우팅       │   │ - event_handled      │       │
│  └──────────┬───────────┘   └──────────┬───────────┘       │
│             │                          │                     │
│             ▼                          ▼                     │
│  ┌──────────────────────┐   ┌──────────────────────┐       │
│  │ ProductMetrics       │   │ ProductCacheService  │       │
│  │ Service              │   │ - Redis 캐시 무효화   │       │
│  │ - 집계 업데이트       │   │ - 상품 캐시          │       │
│  │ - 순서 보장          │   │ - 목록 캐시          │       │
│  └──────────┬───────────┘   └──────────────────────┘       │
│             │                                                │
│             ▼                                                │
│  ┌──────────────────┐      ┌──────────────────┐            │
│  │ ProductMetrics   │      │  EventHandled    │            │
│  │ (DB)             │      │  (DB)            │            │
│  └──────────────────┘      └──────────────────┘            │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔄 전체 이벤트 흐름 (상세)

### 1단계: 사용자 액션 → 이벤트 발행

**예시: 사용자가 상품에 좋아요 클릭**

```kotlin
// LikeService.addLike()
fun addLike(memberId: String, productId: Long) {
    // 1. 비즈니스 로직 실행
    val like = Like(memberId, productId)
    likeRepository.save(like)
    
    // 2. 도메인 이벤트 발행
    eventPublisher.publishEvent(
        ProductLikedEvent(
            eventId = UUID.randomUUID(),
            eventType = "PRODUCT_LIKED",
            aggregateId = productId,  // 상품 ID
            occurredAt = Instant.now(),
            productId = productId,
            memberId = memberId
        )
    )
}
```

### 2단계: OutboxEventListener → EventOutbox 저장

```kotlin
// OutboxEventListener.kt
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
fun handleDomainEvent(event: DomainEvent) {
    // 같은 트랜잭션 내에서 EventOutbox 저장
    val outbox = EventOutbox(
        eventId = event.eventId,
        eventType = event.eventType,
        aggregateId = event.aggregateId,
        aggregateType = getAggregateType(event),  // "product" or "order"
        payload = objectMapper.writeValueAsString(event),
        processed = false  // 아직 Kafka 발행 안 됨
    )
    eventOutboxRepository.save(outbox)
}
```

**핵심**: `BEFORE_COMMIT`으로 설정하여, 비즈니스 로직과 EventOutbox 저장이 **같은 트랜잭션**에서 처리됨

### 3단계: OutboxEventPublisher → Kafka 발행

```kotlin
// OutboxEventPublisher.kt
@Scheduled(fixedDelay = 1000)  // 1초마다 실행
fun publishPendingEvents() {
    // 1. 미처리 이벤트 조회
    val pendingEvents = eventOutboxRepository
        .findTop100ByProcessedFalseOrderByCreatedAtAsc()
    
    // 2. Kafka 발행
    pendingEvents.forEach { outbox ->
        val topic = when (outbox.aggregateType.lowercase()) {
            "product" -> "catalog-events"
            "order" -> "order-events"
            else -> "general-events"
        }
        
        // 핵심: partitionKey = aggregateId
        val partitionKey = outbox.aggregateId.toString()
        kafkaTemplate.send(topic, partitionKey, outbox.payload)
        
        // 3. 발행 완료 표시
        outbox.processed = true
        outbox.processedAt = Instant.now()
        eventOutboxRepository.save(outbox)
    }
}
```

**핵심 포인트:**
- **PartitionKey = aggregateId**: 같은 상품의 이벤트는 같은 파티션으로 감 → 순서 보장
- **스케줄러 방식**: 트랜잭션 커밋 후 별도 스레드에서 Kafka 발행 → 트랜잭션 안전성 보장

### 4단계: Kafka Consumer → 이벤트 처리

```kotlin
// MetricsKafkaConsumer.kt
@KafkaListener(
    topics = ["catalog-events", "order-events"],
    groupId = "metrics-consumer-group",
    containerFactory = "manualAckKafkaListenerContainerFactory"
)
fun consume(
    @Payload message: String,
    @Header(KafkaHeaders.RECEIVED_KEY) key: String,
    acknowledgment: Acknowledgment
) {
    // 1. JSON 파싱
    val event = parseEvent(message)
    
    // 2. Facade에 위임 (멱등성 체크 + 처리)
    metricsEventFacade.handleEvent(event)
    
    // 3. Manual ACK (처리 성공 후에만)
    acknowledgment.acknowledge()
}
```

### 5단계: MetricsEventFacade → 멱등성 체크 + 라우팅

```kotlin
// MetricsEventFacade.kt
fun handleEvent(event: DomainEvent) {
    // 1. 멱등성 체크
    if (eventHandledRepository.existsByEventId(event.eventId)) {
        logger.warn("이미 처리된 이벤트 무시: eventId=${event.eventId}")
        return
    }
    
    // 2. 이벤트 타입별 라우팅
    when (event) {
        is ProductLikedEvent -> 
            productMetricsService.incrementLikes(event.productId, event.occurredAt)
        is ProductUnlikedEvent -> 
            productMetricsService.decrementLikes(event.productId, event.occurredAt)
        is ProductViewedEvent -> 
            productMetricsService.incrementViews(event.productId, event.occurredAt)
        is OrderCreatedEvent -> 
            productMetricsService.incrementSales(event.productId, event.orderItems, event.occurredAt)
        // ...
    }
    
    // 3. 처리 완료 기록
    eventHandledRepository.save(
        EventHandled(eventId = event.eventId, handledAt = Instant.now())
    )
}
```

### 6단계: ProductMetricsService → 집계 데이터 업데이트

```kotlin
// ProductMetricsService.kt
fun incrementLikes(productId: Long, eventOccurredAt: Instant) {
    val metrics = productMetricsRepository.findByProductId(productId)
        ?: ProductMetrics(productId = productId)
    
    // 이벤트 순서 역전 체크
    if (metrics.updatedAt.isAfter(eventOccurredAt)) {
        logger.warn("이벤트 순서 역전 무시: productId=$productId")
        return
    }
    
    metrics.likesCount++
    metrics.updatedAt = eventOccurredAt
    productMetricsRepository.save(metrics)
}
```

---

## 🔑 핵심 개념 정리

### 1. Transactional Outbox Pattern

**문제**: DB 트랜잭션과 Kafka 발행을 동시에 보장하기 어려움

**해결**:
1. 비즈니스 로직 실행 + EventOutbox 저장 (같은 트랜잭션)
2. 트랜잭션 커밋 후 스케줄러가 Kafka 발행
3. 발행 성공 시 `processed=true` 업데이트

**장점**:
- DB 트랜잭션 안전성 보장
- 이벤트 유실 방지 (At Least Once)

### 2. At Least Once Producer

**의미**: 이벤트가 **최소 1회 이상** 발행됨을 보장

**구현 방법**:
- `acks=all`: 모든 브로커가 메시지 수신 확인
- `idempotence=true`: 중복 발행 방지
- EventOutbox: 발행 실패 시 재시도

**결과**: 이벤트 유실 없음 (중복 가능)

### 3. At Most Once Consumer (멱등성)

**의미**: 같은 이벤트가 **최대 1회만** 처리됨을 보장

**구현 방법**:
- `event_handled` 테이블: `eventId` PK로 중복 체크
- `updatedAt` 비교: 이벤트 순서 역전 방지
- Manual ACK: 처리 성공 후에만 ACK

**결과**: 중복 처리 없음

### 4. Exactly Once Semantics

**공식**: 
```
At Least Once (Producer) + At Most Once (Consumer) = Exactly Once
```

**의미**: 
- Producer: 이벤트 유실 없음 (중복 가능)
- Consumer: 중복 처리 없음
- **결과**: 각 이벤트가 정확히 1회만 처리됨

### 5. 이벤트 순서 보장

**문제**: Kafka는 파티션 내에서만 순서 보장

**해결**:
1. **PartitionKey = aggregateId**: 같은 상품의 이벤트는 같은 파티션으로 감
2. **updatedAt 체크**: Consumer에서 순서 역전 방지

**예시**:
```
상품 1번 좋아요 추가 → 취소 → 다시 추가
- 모든 이벤트가 같은 파티션으로 감 (key=1)
- 파티션 내에서 순서 보장
- 최종 결과: likesCount = 1
```

### 6. EventOutbox vs EventHandled 분리 이유

| 구분 | EventOutbox | EventHandled |
|------|-------------|--------------|
| **소유 서비스** | commerce-api | commerce-streamer |
| **목적** | 발행 보장 (At Least Once) | 멱등성 보장 (At Most Once) |
| **생명주기** | 발행 후 7일 삭제 | 영구 보관 |
| **인덱스** | processed, created_at | eventId (PK) |
| **조회 패턴** | 스케줄러가 배치 조회 | 메시지마다 개별 조회 |

**분리 이유**:
1. 생명주기가 다름 (7일 vs 영구)
2. 서비스가 분리됨 (마이크로서비스)
3. 인덱스 전략이 다름
4. 동시 접근 이슈 방지

---

## 📊 주요 테이블 구조

### EventOutbox (commerce-api DB)

```kotlin
@Entity
@Table(name = "event_outbox")
class EventOutbox {
    @Id
    val id: Long
    
    val eventId: UUID  // DomainEvent의 eventId
    val eventType: String  // "PRODUCT_LIKED", "ORDER_CREATED" 등
    val aggregateId: Long  // 상품 ID 또는 주문 ID
    val aggregateType: String  // "product" 또는 "order"
    val payload: String  // JSON 문자열 (DomainEvent 직렬화)
    
    var processed: Boolean = false  // Kafka 발행 여부
    var processedAt: Instant? = null
    var retryCount: Int = 0
    var lastError: String? = null
    
    var kafkaPartition: Int? = null
    var kafkaOffset: Long? = null
    
    val createdAt: Instant
    val updatedAt: Instant
}
```

**인덱스**: `(processed, created_at)` - 스케줄러 조회용

### EventHandled (commerce-streamer DB)

```kotlin
@Entity
@Table(name = "event_handled")
class EventHandled {
    @Id
    val eventId: UUID  // PK (DomainEvent의 eventId)
    
    val handledAt: Instant
}
```

**인덱스**: `eventId` (PK, unique) - 멱등성 체크용

### ProductMetrics (commerce-streamer DB)

```kotlin
@Entity
@Table(name = "product_metrics")
class ProductMetrics {
    @Id
    val productId: Long  // PK
    
    var likesCount: Long = 0
    var viewCount: Long = 0
    var salesCount: Long = 0
    
    @Version
    var version: Long = 0  // 낙관적 락
    
    var updatedAt: Instant = Instant.now()  // 이벤트 순서 보장용
}
```

---

## 🎯 주요 이벤트 타입

### catalog-events 토픽

| 이벤트 타입 | aggregateId | 설명 | Consumer 처리 |
|------------|-------------|------|--------------|
| `PRODUCT_LIKED` | productId | 상품 좋아요 추가 | Metrics: likesCount++ |
| `PRODUCT_UNLIKED` | productId | 상품 좋아요 취소 | Metrics: likesCount-- |
| `PRODUCT_VIEWED` | productId | 상품 상세 페이지 조회 | Metrics: viewCount++ |
| `STOCK_DECREASED` | productId | 재고 차감 | Metrics: 집계 없음<br>Cache: 상품 캐시 무효화 |

### order-events 토픽

| 이벤트 타입 | aggregateId | 설명 |
|------------|-------------|------|
| `ORDER_CREATED` | orderId | 주문 생성 |
| `COUPON_USED` | orderId | 쿠폰 사용 |
| `PAYMENT_COMPLETED` | orderId | 결제 완료 |
| `PAYMENT_FAILED` | orderId | 결제 실패 |

---

## 🔧 주요 설정

### Kafka Producer 설정 (commerce-api)

```yaml
spring:
  kafka:
    producer:
      bootstrap-servers: localhost:9092
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all  # 모든 브로커 확인
      idempotence: true  # 중복 발행 방지
      retries: 3
```

### Kafka Consumer 설정 (commerce-streamer)

```yaml
spring:
  kafka:
    consumer:
      bootstrap-servers: localhost:9092
      group-id: metrics-consumer-group
      enable-auto-commit: false  # Manual ACK
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

---

## 🚨 에러 처리

### Producer 에러 처리

1. **Kafka 발행 실패**
   - 재시도 (최대 3회)
   - 실패 시 `retryCount` 증가, `lastError` 저장
   - 최대 재시도 초과 시 → Dead Letter Queue 이동

2. **Dead Letter Queue**
   ```kotlin
   // DeadLetterQueueService.kt
   fun moveToDeadLetterQueue(outbox: EventOutbox, error: Exception) {
       val dlq = DeadLetterQueue(
           eventId = outbox.eventId,
           eventType = outbox.eventType,
           payload = outbox.payload,
           errorMessage = error.message,
           retryCount = outbox.retryCount
       )
       deadLetterQueueRepository.save(dlq)
   }
   ```

### Consumer 에러 처리

1. **이벤트 파싱 실패**
   - ACK 하지 않음 → 재처리됨
   - 로그 기록

2. **비즈니스 로직 실패**
   - ACK 하지 않음 → 재처리됨
   - 로그 기록

3. **멱등성 체크 실패**
   - 이미 처리된 이벤트 → 무시하고 ACK

---

## 📈 성능 최적화

### Producer 최적화

1. **배치 발행**: 스케줄러가 100개씩 조회하여 발행
2. **인덱스**: `(processed, created_at)` 인덱스로 빠른 조회
3. **Cleanup**: 7일 이상 된 `processed=true` 이벤트 자동 삭제

### Consumer 최적화

1. **배치 처리**: 여러 이벤트를 한 번에 처리
2. **이벤트 정렬**: `occurredAt` 기준으로 정렬하여 순서 보장
3. **낙관적 락**: `@Version`으로 동시성 제어

---

## ✅ 구현 체크리스트

### Producer (commerce-api)
- [x] DomainEvent 인터페이스 정의
- [x] EventOutbox 엔티티 생성
- [x] OutboxEventListener 구현 (BEFORE_COMMIT)
- [x] OutboxEventPublisher 구현 (스케줄러)
- [x] Kafka Producer 설정
- [x] PartitionKey = aggregateId 설정
- [x] Dead Letter Queue 구현
- [x] EventOutbox Cleanup 스케줄러

### Consumer (commerce-streamer)
- [x] EventHandled 엔티티 생성
- [x] ProductMetrics 엔티티 생성
- [x] MetricsKafkaConsumer 구현
- [x] MetricsEventFacade 구현 (멱등성 체크)
- [x] ProductMetricsService 구현 (집계 로직)
- [x] Kafka Consumer 설정 (Manual ACK)
- [x] 이벤트 순서 보장 (updatedAt 체크)
- [x] **CacheInvalidationKafkaConsumer 구현** (별도 consumer group)
- [x] **CacheInvalidationFacade 구현** (캐시 무효화 로직)
- [x] **ProductCacheService 구현** (Redis 캐시 무효화)
- [x] **재고 소진 시 상품 캐시 자동 갱신**

### 배치 처리 (Nice-to-Have)
- [x] BatchMetricsKafkaConsumer 구현
- [x] BatchMetricsEventFacade 구현
- [x] productId별 이벤트 그룹핑 및 일괄 처리

### 테스트
- [x] 단위 테스트 (각 서비스)
- [x] 통합 테스트 (이벤트 파이프라인)
- [x] 멱등성 테스트 (중복 메시지)
- [x] 순서 보장 테스트
- [x] 캐시 무효화 테스트

---

## 🔄 캐시 무효화 전략

### 아키텍처

```
StockDecreasedEvent
       │
       ▼
CacheInvalidationKafkaConsumer (별도 consumer group)
       │
       ▼
CacheInvalidationFacade
       │
       ├─ event_handled 체크 (멱등성)
       │
       ▼
ProductCacheService
       │
       ├─ invalidateProductCache(productId)
       │     → Redis DEL "product:{productId}"
       │
       └─ (재고 소진 시) invalidateProductListCache()
             → Redis DEL "products:*"
```

### Consumer Group 분리 전략

| Consumer Group | 처리 대상 | 목적 | 독립성 |
|---------------|----------|------|--------|
| `metrics-consumer-group` | 모든 이벤트 | 집계 데이터 업데이트 | 집계 실패가 캐시에 영향 없음 |
| `cache-invalidation-consumer-group` | StockDecreasedEvent만 | 캐시 무효화 | 캐시 실패가 집계에 영향 없음 |
| `metrics-consumer-group-batch` | 모든 이벤트 | 배치 집계 | 배치 실패가 단건 처리에 영향 없음 |

### 캐시 일관성 보장

1. **재고 감소 시**: 상품 캐시만 무효화
   - 상품 상세 조회 시 최신 재고 반영

2. **재고 소진 시** (`remainingStock=0`): 상품 + 목록 캐시 모두 무효화
   - 품절 상품이 목록에서 제외됨
   - 검색 결과에서도 제외됨

3. **멱등성**: `event_handled` 테이블로 중복 처리 방지

## 🎓 핵심 학습 포인트

1. **Transactional Outbox Pattern**: DB 트랜잭션과 메시지 발행의 원자성 보장
2. **At Least Once + At Most Once = Exactly Once**: 이벤트 처리의 정확성 보장
3. **PartitionKey 전략**: 이벤트 순서 보장을 위한 키 설계
4. **멱등성 처리**: 중복 메시지 처리 방지
5. **마이크로서비스 분리**: EventOutbox와 EventHandled 분리의 이유
6. **Consumer Group 분리**: 관심사 분리로 장애 격리 및 확장성 확보
7. **캐시 일관성**: 이벤트 기반 캐시 무효화로 데이터 정합성 보장

---

## 📚 참고 문서

- `.codeguide/round8/KAFKA_EVENT_PIPELINE_GUIDE.md`: 상세 구현 가이드
- `.codeguide/round8/IMPLEMENTATION_STATUS.md`: 구현 상태 체크리스트
- `.codeguide/round8/TECHNICAL_WRITING_ROUND8-1.md`: PartitionKey 설계 이유
- `.codeguide/round8/TECHNICAL_WRITING_ROUND8-2.md`: 테이블 분리 이유

---

**마지막 업데이트**: 2024-12-20

## 🆕 최근 업데이트 (2024-12-20)

### 캐시 무효화 기능 추가
- **CacheInvalidationKafkaConsumer**: StockDecreasedEvent 전용 Consumer 구현
- **ProductCacheService**: Redis 캐시 무효화 서비스
- **CacheInvalidationFacade**: 캐시 무효화 이벤트 처리 Facade
- **Consumer Group 분리**: `cache-invalidation-consumer-group` 추가로 장애 격리
- **테스트 완료**: CacheInvalidationFacadeTest 단위 테스트 작성

### 주요 개선 사항
- 재고 소진 시 상품 캐시와 목록 캐시 자동 무효화
- commerce-api와 commerce-streamer 간 캐시 일관성 보장
- 멱등성 처리로 중복 캐시 무효화 방지

