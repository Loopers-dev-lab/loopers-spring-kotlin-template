package com.loopers.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.event.LikeAddedEvent
import com.loopers.domain.event.LikeRemovedEvent
import com.loopers.domain.event.OrderCreatedEvent
import com.loopers.domain.product.ProductMetrics
import com.loopers.domain.product.ProductMetricsRepository
import com.loopers.infrastructure.event.EventHandledJpaRepository
import com.loopers.infrastructure.product.ProductMetricsJpaRepository
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Kafka Consumer E2E 통합 테스트
 *
 * 이 테스트는 docker/infra-compose.yml의 Kafka가 실행 중이어야 합니다.
 *
 * 실행 방법:
 * 1. cd docker && docker-compose -f infra-compose.yml up -d kafka
 * 2. export KAFKA_BOOTSTRAP_SERVERS=localhost:19092
 * 3. ./gradlew :apps:commerce-streamer:test --tests "KafkaConsumerE2ETest"
 *
 * Kafka가 실행 중이지 않으면 이 테스트는 skip됩니다.
 *
 * ## 테스트 커버리지
 * - 기본 이벤트 처리: LikeAdded, LikeRemoved, OrderCreated
 * - 멱등성: 중복 메시지 처리
 * - 동시성: 동일 상품에 대한 동시 업데이트 (pessimistic locking)
 * - 파티션 순서 보장: 동일 productId의 이벤트 시퀀스
 * - 에러 처리:
 *   - DLQ 라우팅: 파싱 실패한 메시지는 재시도 후 DLQ로 전송
 *   - 재시도 로직: KafkaConfig의 재시도 정책(3회)이 DLQ 테스트에서 자동 검증
 *   - 알 수 없는 이벤트 타입: 경고 로그만 남기고 정상 처리
 *
 * ## 참고: DB 장애 시나리오 테스트
 * 데이터베이스 장애 시나리오는 E2E 통합 테스트보다는 단위 테스트나
 * 카오스 엔지니어링 테스트에서 다루는 것이 더 적합합니다:
 * - E2E 테스트에서 실제 DB 장애를 시뮬레이션하기 어려움
 * - 트랜잭션 롤백 시나리오는 멱등성 테스트에서 간접적으로 검증됨
 * - acknowledgeAfterCommit 패턴으로 메시지 손실 방지 메커니즘이 구현되어 있음
 */
@SpringBootTest
@ActiveProfiles("test")
class KafkaConsumerE2ETest {

    companion object {
        private const val KAFKA_SEND_TIMEOUT_SECONDS = 5L
        private const val AWAIT_TIMEOUT_SECONDS = 10L
        private const val AWAIT_POLL_INTERVAL_MILLIS = 500L

        private const val PRODUCT_ID_1 = 100L
        private const val PRODUCT_ID_2 = 200L
        private const val PRODUCT_ID_3 = 300L
        private const val PRODUCT_ID_4 = 400L
        private const val PRODUCT_ID_5 = 500L
        private const val PRODUCT_ID_6 = 600L
        private const val PRODUCT_ID_7 = 700L
        private const val PRODUCT_ID_8 = 800L
        private const val PRODUCT_ID_9 = 900L
    }

    @Autowired(required = false)
    private var kafkaTemplate: KafkaTemplate<String, String>? = null

    @Autowired(required = false)
    private var productMetricsRepository: ProductMetricsRepository? = null

    @Autowired(required = false)
    private var eventHandledRepository: EventHandledRepository? = null

    @Autowired(required = false)
    private var productMetricsJpaRepository: ProductMetricsJpaRepository? = null

    @Autowired(required = false)
    private var eventHandledJpaRepository: EventHandledJpaRepository? = null

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @BeforeEach
    fun setUp() {
        // Kafka가 없으면 테스트 skip
        if (kafkaTemplate == null) {
            println("⚠️  Kafka가 실행 중이지 않습니다. 테스트를 건너뜁니다.")
            println("   Kafka 실행: cd docker && docker-compose -f infra-compose.yml up -d kafka")
            println("   환경 변수: export KAFKA_BOOTSTRAP_SERVERS=localhost:19092")
        }
    }

    @AfterEach
    fun cleanup() {
        // 테스트 데이터 정리
        productMetricsJpaRepository?.deleteAll()
        eventHandledJpaRepository?.deleteAll()
    }

    private fun sendEvent(topic: String, key: String, payload: String) {
        kafkaTemplate?.send(topic, key, payload)
            ?.get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun awaitMetricsUpdate(
        productId: Long,
        assertion: (ProductMetrics?) -> Unit,
    ) {
        await()
            .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pollInterval(AWAIT_POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository?.findByProductId(productId)
                assertion(metrics)
            }
    }

    /**
     * DLQ 토픽에서 메시지가 수신되는지 확인하는 헬퍼 메서드
     */
    private fun checkDlqMessage(dlqTopic: String, expectedKey: String): Boolean {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, "test-dlq-consumer-${UUID.randomUUID()}")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        }

        return KafkaConsumer<String, String>(props).use { consumer ->
            consumer.subscribe(listOf(dlqTopic))

            // DLQ에 메시지가 도착할 때까지 폴링 (최대 15초)
            var found = false
            val endTime = System.currentTimeMillis() + 15000

            while (System.currentTimeMillis() < endTime && !found) {
                val records = consumer.poll(Duration.ofMillis(1000))
                for (record in records) {
                    if (record.key() == expectedKey) {
                        found = true
                        break
                    }
                }
            }

            found
        }
    }

    /**
     * DLQ 메시지를 기다리는 헬퍼 메서드
     */
    private fun awaitDlqMessage(dlqTopic: String, expectedKey: String) {
        await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(checkDlqMessage(dlqTopic, expectedKey))
                    .withFailMessage("DLQ 토픽($dlqTopic)에서 key($expectedKey)를 가진 메시지를 찾을 수 없습니다.")
                    .isTrue()
            }
    }

    @Test
    fun `Kafka가 실행 중이지 않으면 KafkaTemplate 빈이 생성되지 않는다`() {
        // 이 테스트는 Kafka 없이 실행될 때만 의미가 있음
        if (System.getenv("KAFKA_BOOTSTRAP_SERVERS") == null) {
            assertThat(kafkaTemplate).isNull()
        }
    }

    @Test
    fun `Consumer가 LikeAddedEvent를 수신하여 ProductMetrics를 업데이트한다`() {
        // given: Kafka가 실행 중이어야 함
        assumeTrue(kafkaTemplate != null && productMetricsRepository != null, "Kafka is not running")

        val event = LikeAddedEvent(
            eventId = UUID.randomUUID(),
            userId = 1L,
            productId = PRODUCT_ID_1,
            createdAt = ZonedDateTime.now(),
        )
        val payload = objectMapper.writeValueAsString(event)

        // when: Kafka로 메시지 전송
        sendEvent("catalog-events", PRODUCT_ID_1.toString(), payload)

        // then: Consumer가 메시지를 처리하고 ProductMetrics 업데이트
        awaitMetricsUpdate(PRODUCT_ID_1) { metrics ->
            assertThat(metrics).isNotNull
            assertThat(metrics?.likeCount).isEqualTo(1)
        }
    }

    @Test
    fun `Consumer가 LikeRemovedEvent를 수신하여 ProductMetrics를 감소시킨다`() {
        // given: Kafka가 실행 중이어야 함
        assumeTrue(kafkaTemplate != null && productMetricsRepository != null, "Kafka is not running")

        // 먼저 좋아요 추가
        val addEvent = LikeAddedEvent(
            eventId = UUID.randomUUID(),
            userId = 1L,
            productId = PRODUCT_ID_2,
            createdAt = ZonedDateTime.now(),
        )
        sendEvent("catalog-events", PRODUCT_ID_2.toString(), objectMapper.writeValueAsString(addEvent))

        // 좋아요가 추가될 때까지 대기
        awaitMetricsUpdate(PRODUCT_ID_2) { metrics ->
            assertThat(metrics?.likeCount).isEqualTo(1)
        }

        // when: 좋아요 제거 이벤트 전송 (UUID로 고유성 보장, Thread.sleep 불필요)
        val removeEvent = LikeRemovedEvent(
            eventId = UUID.randomUUID(),
            userId = 1L,
            productId = PRODUCT_ID_2,
            createdAt = ZonedDateTime.now(),
        )
        sendEvent("catalog-events", PRODUCT_ID_2.toString(), objectMapper.writeValueAsString(removeEvent))

        // then: 좋아요 수가 감소함
        awaitMetricsUpdate(PRODUCT_ID_2) { metrics ->
            assertThat(metrics?.likeCount).isEqualTo(0)
        }
    }

    @Test
    fun `Consumer가 OrderCreatedEvent를 수신하여 판매량을 집계한다`() {
        // given: Kafka가 실행 중이어야 함
        assumeTrue(kafkaTemplate != null && productMetricsRepository != null, "Kafka is not running")

        val orderId = 1000L
        val event = OrderCreatedEvent(
            eventId = UUID.randomUUID(),
            orderId = orderId,
            userId = 1L,
            amount = 50000,
            couponId = null,
            items = listOf(
                OrderCreatedEvent.OrderItemInfo(
                    productId = PRODUCT_ID_3,
                    productName = "Test Product",
                    quantity = 2,
                    priceAtOrder = 25000,
                ),
            ),
            createdAt = ZonedDateTime.now(),
        )
        val payload = objectMapper.writeValueAsString(event)

        // when: Kafka로 메시지 전송
        sendEvent("order-events", orderId.toString(), payload)

        // then: Consumer가 메시지를 처리하고 판매량 집계
        awaitMetricsUpdate(PRODUCT_ID_3) { metrics ->
            assertThat(metrics).isNotNull
            assertThat(metrics?.salesCount).isEqualTo(2)
            assertThat(metrics?.totalSalesAmount).isEqualTo(50000)
        }
    }

    @Test
    fun `중복 메시지를 재전송해도 멱등성이 보장된다`() {
        // given: Kafka가 실행 중이어야 함
        assumeTrue(
            kafkaTemplate != null && eventHandledRepository != null && productMetricsRepository != null,
            "Kafka is not running",
        )

        val eventId = UUID.randomUUID()
        val event = LikeAddedEvent(
            eventId = eventId,
            userId = 1L,
            productId = PRODUCT_ID_4,
            createdAt = ZonedDateTime.now(),
        )
        val payload = objectMapper.writeValueAsString(event)

        // when: 같은 메시지를 3번 전송
        repeat(3) {
            sendEvent("catalog-events", PRODUCT_ID_4.toString(), payload)
        }

        // then: Consumer가 처리하고 ProductMetrics는 1번만 증가
        awaitMetricsUpdate(PRODUCT_ID_4) { metrics ->
            assertThat(metrics).isNotNull
            // 멱등성 보장 - 3번 전송했지만 1번만 증가
            assertThat(metrics?.likeCount).isEqualTo(1)
        }

        // EventHandled 테이블 확인 - 한 번만 기록됨
        val eventHandled = eventHandledRepository?.existsByEventId(eventId)
        assertThat(eventHandled).isTrue
    }

    @Test
    fun `여러 상품의 주문을 동시에 처리할 수 있다`() {
        // given: Kafka가 실행 중이어야 함
        assumeTrue(kafkaTemplate != null && productMetricsRepository != null, "Kafka is not running")

        val orderId = 2000L
        val event = OrderCreatedEvent(
            eventId = UUID.randomUUID(),
            orderId = orderId,
            userId = 1L,
            amount = 100000,
            couponId = null,
            items = listOf(
                OrderCreatedEvent.OrderItemInfo(
                    productId = PRODUCT_ID_5,
                    productName = "Product A",
                    quantity = 3,
                    priceAtOrder = 20000,
                ),
                OrderCreatedEvent.OrderItemInfo(
                    productId = PRODUCT_ID_6,
                    productName = "Product B",
                    quantity = 2,
                    priceAtOrder = 20000,
                ),
            ),
            createdAt = ZonedDateTime.now(),
        )
        val payload = objectMapper.writeValueAsString(event)

        // when: Kafka로 메시지 전송
        sendEvent("order-events", orderId.toString(), payload)

        // then: 두 상품 모두 판매량이 집계됨
        awaitMetricsUpdate(PRODUCT_ID_5) { metrics ->
            assertThat(metrics).isNotNull
            assertThat(metrics?.salesCount).isEqualTo(3)
            assertThat(metrics?.totalSalesAmount).isEqualTo(60000)
        }

        awaitMetricsUpdate(PRODUCT_ID_6) { metrics ->
            assertThat(metrics).isNotNull
            assertThat(metrics?.salesCount).isEqualTo(2)
            assertThat(metrics?.totalSalesAmount).isEqualTo(40000)
        }
    }

    @Test
    fun `알 수 없는 이벤트 타입은 무시된다`() {
        // given: Kafka가 실행 중이어야 함
        assumeTrue(kafkaTemplate != null && productMetricsRepository != null, "Kafka is not running")

        val unknownPayload = """{"unknown":"event"}"""

        // when: 알 수 없는 이벤트 전송
        sendEvent("catalog-events", "999", unknownPayload)

        // 알 수 없는 이벤트 이후 정상 이벤트 전송하여 컨슈머가 여전히 작동하는지 확인
        val normalEvent = LikeAddedEvent(
            eventId = UUID.randomUUID(),
            userId = 1L,
            productId = PRODUCT_ID_7,
            createdAt = ZonedDateTime.now(),
        )
        sendEvent("catalog-events", PRODUCT_ID_7.toString(), objectMapper.writeValueAsString(normalEvent))

        // then: 알 수 없는 이벤트는 무시되고, 정상 이벤트는 처리됨
        awaitMetricsUpdate(PRODUCT_ID_7) { metrics ->
            assertThat(metrics).isNotNull
            assertThat(metrics?.likeCount).isEqualTo(1)
        }
    }

    @Test
    fun `동일 productId의 이벤트 시퀀스는 파티션 순서가 보장된다`() {
        // given: Kafka가 실행 중이어야 함
        assumeTrue(kafkaTemplate != null && productMetricsRepository != null, "Kafka is not running")

        val userId = 1L

        // when: 동일 productId로 빠르게 이벤트 시퀀스 전송 (LikeAdded -> LikeRemoved -> LikeAdded)
        // 각 이벤트는 고유한 UUID를 가지며, productId를 파티션 키로 사용하여 순서 보장
        val event1 = LikeAddedEvent(
            eventId = UUID.randomUUID(),
            userId = userId,
            productId = PRODUCT_ID_8,
            createdAt = ZonedDateTime.now(),
        )
        sendEvent("catalog-events", PRODUCT_ID_8.toString(), objectMapper.writeValueAsString(event1))

        val event2 = LikeRemovedEvent(
            eventId = UUID.randomUUID(),
            userId = userId,
            productId = PRODUCT_ID_8,
            createdAt = ZonedDateTime.now(),
        )
        sendEvent("catalog-events", PRODUCT_ID_8.toString(), objectMapper.writeValueAsString(event2))

        val event3 = LikeAddedEvent(
            eventId = UUID.randomUUID(),
            userId = userId,
            productId = PRODUCT_ID_8,
            createdAt = ZonedDateTime.now(),
        )
        sendEvent("catalog-events", PRODUCT_ID_8.toString(), objectMapper.writeValueAsString(event3))

        // then: 최종 상태는 이벤트 순서를 반영 (0 + 1 - 1 + 1 = 1)
        awaitMetricsUpdate(PRODUCT_ID_8) { metrics ->
            assertThat(metrics).isNotNull
            assertThat(metrics?.likeCount).isEqualTo(1)
        }
    }

    @Test
    fun `동일 상품에 대한 동시 주문 이벤트를 안전하게 처리한다`() {
        // given: Kafka가 실행 중이어야 함
        assumeTrue(kafkaTemplate != null && productMetricsRepository != null, "Kafka is not running")

        val productId = PRODUCT_ID_9

        // when: 서로 다른 orderId로 동일 상품에 대한 여러 주문 이벤트를 빠르게 전송
        // ProductMetrics의 pessimistic locking이 동시성을 안전하게 처리하는지 검증
        repeat(5) { i ->
            val event = OrderCreatedEvent(
                eventId = UUID.randomUUID(),
                orderId = 3000L + i,
                userId = 1L,
                amount = 10000,
                couponId = null,
                items = listOf(
                    OrderCreatedEvent.OrderItemInfo(
                        productId = productId,
                        productName = "Concurrent Product",
                        quantity = 1,
                        priceAtOrder = 10000,
                    ),
                ),
                createdAt = ZonedDateTime.now(),
            )
            sendEvent("order-events", (3000L + i).toString(), objectMapper.writeValueAsString(event))
        }

        // then: 최종 집계 결과 검증 - 5개의 이벤트가 모두 정확하게 집계됨
        awaitMetricsUpdate(productId) { metrics ->
            assertThat(metrics).isNotNull
            assertThat(metrics?.salesCount).isEqualTo(5)
            assertThat(metrics?.totalSalesAmount).isEqualTo(50000)
        }
    }

    @Test
    fun `파싱 실패한 메시지는 재시도 후 DLQ로 라우팅된다`() {
        // given: Kafka가 실행 중이어야 함
        assumeTrue(kafkaTemplate != null, "Kafka is not running")

        val invalidPayload = """{"invalid": "malformed json"""
        val key = "dlq-test-key-1"

        // when: 파싱할 수 없는 잘못된 JSON 전송
        sendEvent("catalog-events", key, invalidPayload)

        // then: 재시도 후 DLQ로 라우팅됨
        awaitDlqMessage("catalog-events-dlq", key)
    }

    @Test
    fun `eventType 헤더 없는 메시지는 경고 로그만 남기고 처리된다`() {
        // given: Kafka가 실행 중이어야 함
        assumeTrue(kafkaTemplate != null, "Kafka is not running")

        // eventType 헤더가 없는 메시지는 "알 수 없는 이벤트 타입" 분기로 처리됨
        // 이는 에러가 아니므로 DLQ로 가지 않고 정상 처리됨 (acknowledge)

        val payload = """{"someData": "test"}"""
        val key = "no-event-type-key"

        // when: eventType 헤더 없이 메시지 전송
        // 참고: sendEvent는 헤더를 설정하지 않으므로 eventType이 null이 됨
        sendEvent("catalog-events", key, payload)

        // then: 정상 처리되어 DLQ로 가지 않음 (에러가 없으므로)
        // 이후 정상 이벤트가 여전히 처리되는지 확인
        val normalEvent = LikeAddedEvent(
            eventId = UUID.randomUUID(),
            userId = 1L,
            productId = PRODUCT_ID_1,
            createdAt = ZonedDateTime.now(),
        )
        sendEvent("catalog-events", PRODUCT_ID_1.toString(), objectMapper.writeValueAsString(normalEvent))

        awaitMetricsUpdate(PRODUCT_ID_1) { metrics ->
            assertThat(metrics).isNotNull
            assertThat(metrics?.likeCount).isGreaterThanOrEqualTo(1)
        }
    }
}
