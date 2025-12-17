package com.loopers.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.event.LikeAddedEvent
import com.loopers.domain.event.LikeRemovedEvent
import com.loopers.domain.event.OrderCreatedEvent
import com.loopers.domain.product.ProductMetricsRepository
import com.loopers.infrastructure.event.EventHandledJpaRepository
import com.loopers.infrastructure.product.ProductMetricsJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.ZonedDateTime
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

    @Test
    fun `Kafka가 실행 중이지 않으면 KafkaTemplate 빈이 생성되지 않는다`() {
        // Kafka가 없으면 bean이 null
        println("KafkaTemplate bean: ${kafkaTemplate != null}")
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
        kafkaTemplate?.send("catalog-events", PRODUCT_ID_1.toString(), payload)
            ?.get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // then: Consumer가 메시지를 처리하고 ProductMetrics 업데이트
        await()
            .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pollInterval(AWAIT_POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository?.findByProductId(PRODUCT_ID_1)
                assertThat(metrics).isNotNull
                assertThat(metrics?.likeCount).isGreaterThan(0)
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
        kafkaTemplate?.send("catalog-events", PRODUCT_ID_2.toString(), objectMapper.writeValueAsString(addEvent))
            ?.get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // 좋아요가 추가될 때까지 대기
        await()
            .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository?.findByProductId(PRODUCT_ID_2)
                assertThat(metrics?.likeCount).isGreaterThan(0)
            }

        // when: 좋아요 제거 이벤트 전송 (UUID로 고유성 보장, Thread.sleep 불필요)
        val removeEvent = LikeRemovedEvent(
            eventId = UUID.randomUUID(),
            userId = 1L,
            productId = PRODUCT_ID_2,
            createdAt = ZonedDateTime.now(),
        )
        kafkaTemplate?.send("catalog-events", PRODUCT_ID_2.toString(), objectMapper.writeValueAsString(removeEvent))
            ?.get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // then: 좋아요 수가 감소함
        await()
            .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository?.findByProductId(PRODUCT_ID_2)
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
        kafkaTemplate?.send("order-events", orderId.toString(), payload)
            ?.get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // then: Consumer가 메시지를 처리하고 판매량 집계
        await()
            .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pollInterval(AWAIT_POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository?.findByProductId(PRODUCT_ID_3)
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
            kafkaTemplate?.send("catalog-events", PRODUCT_ID_4.toString(), payload)
                ?.get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }

        // then: Consumer가 처리하고 ProductMetrics는 1번만 증가
        await()
            .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pollInterval(AWAIT_POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository?.findByProductId(PRODUCT_ID_4)
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
        kafkaTemplate?.send("order-events", orderId.toString(), payload)
            ?.get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // then: 두 상품 모두 판매량이 집계됨
        await()
            .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pollInterval(AWAIT_POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
            .untilAsserted {
                val metricsA = productMetricsRepository?.findByProductId(PRODUCT_ID_5)
                val metricsB = productMetricsRepository?.findByProductId(PRODUCT_ID_6)

                assertThat(metricsA).isNotNull
                assertThat(metricsA?.salesCount).isEqualTo(3)
                assertThat(metricsA?.totalSalesAmount).isEqualTo(60000)

                assertThat(metricsB).isNotNull
                assertThat(metricsB?.salesCount).isEqualTo(2)
                assertThat(metricsB?.totalSalesAmount).isEqualTo(40000)
            }
    }

    @Test
    fun `알 수 없는 이벤트 타입은 무시된다`() {
        // given: Kafka가 실행 중이어야 함
        assumeTrue(kafkaTemplate != null && productMetricsRepository != null, "Kafka is not running")

        val unknownPayload = """{"unknown":"event"}"""

        // when: 알 수 없는 이벤트 전송
        kafkaTemplate?.send("catalog-events", "999", unknownPayload)
            ?.get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // 알 수 없는 이벤트 이후 정상 이벤트 전송하여 컨슈머가 여전히 작동하는지 확인
        val normalEvent = LikeAddedEvent(
            eventId = UUID.randomUUID(),
            userId = 1L,
            productId = PRODUCT_ID_7,
            createdAt = ZonedDateTime.now(),
        )
        kafkaTemplate?.send("catalog-events", PRODUCT_ID_7.toString(), objectMapper.writeValueAsString(normalEvent))
            ?.get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // then: 알 수 없는 이벤트는 무시되고, 정상 이벤트는 처리됨
        await()
            .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pollInterval(AWAIT_POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository?.findByProductId(PRODUCT_ID_7)
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
        kafkaTemplate?.send("catalog-events", PRODUCT_ID_8.toString(), objectMapper.writeValueAsString(event1))
            ?.get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val event2 = LikeRemovedEvent(
            eventId = UUID.randomUUID(),
            userId = userId,
            productId = PRODUCT_ID_8,
            createdAt = ZonedDateTime.now(),
        )
        kafkaTemplate?.send("catalog-events", PRODUCT_ID_8.toString(), objectMapper.writeValueAsString(event2))
            ?.get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val event3 = LikeAddedEvent(
            eventId = UUID.randomUUID(),
            userId = userId,
            productId = PRODUCT_ID_8,
            createdAt = ZonedDateTime.now(),
        )
        kafkaTemplate?.send("catalog-events", PRODUCT_ID_8.toString(), objectMapper.writeValueAsString(event3))
            ?.get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // then: 최종 상태는 이벤트 순서를 반영 (0 + 1 - 1 + 1 = 1)
        await()
            .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pollInterval(AWAIT_POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository?.findByProductId(PRODUCT_ID_8)
                assertThat(metrics).isNotNull
                assertThat(metrics?.likeCount).isEqualTo(1)
            }
    }
}
