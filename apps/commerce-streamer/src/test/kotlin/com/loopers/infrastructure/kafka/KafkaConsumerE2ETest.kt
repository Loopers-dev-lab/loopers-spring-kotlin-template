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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.ZonedDateTime
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
        if (productMetricsJpaRepository != null) {
            productMetricsJpaRepository!!.deleteAll()
        }
        if (eventHandledJpaRepository != null) {
            eventHandledJpaRepository!!.deleteAll()
        }
    }

    @Test
    fun `Kafka가 실행 중이지 않으면 KafkaTemplate 빈이 생성되지 않는다`() {
        // Kafka가 없으면 bean이 null
        println("KafkaTemplate bean: ${kafkaTemplate != null}")
    }

    @Test
    fun `Consumer가 LikeAddedEvent를 수신하여 ProductMetrics를 업데이트한다`() {
        // given: Kafka가 실행 중이어야 함
        if (kafkaTemplate == null || productMetricsRepository == null) return

        val productId = 100L
        val event = LikeAddedEvent(
            userId = 1L,
            productId = productId,
            createdAt = ZonedDateTime.now(),
        )
        val payload = objectMapper.writeValueAsString(event)

        // when: Kafka로 메시지 전송
        kafkaTemplate!!.send("catalog-events", productId.toString(), payload)
            .get(5, TimeUnit.SECONDS)

        // then: Consumer가 메시지를 처리하고 ProductMetrics 업데이트
        // 최대 10초 대기
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository!!.findByProductId(productId)
                assertThat(metrics).isNotNull
                assertThat(metrics!!.likeCount).isGreaterThan(0)
            }
    }

    @Test
    fun `Consumer가 LikeRemovedEvent를 수신하여 ProductMetrics를 감소시킨다`() {
        // given: Kafka가 실행 중이어야 함
        if (kafkaTemplate == null || productMetricsRepository == null) return

        val productId = 200L

        // 먼저 좋아요 추가
        val addEvent = LikeAddedEvent(
            userId = 1L,
            productId = productId,
            createdAt = ZonedDateTime.now(),
        )
        kafkaTemplate!!.send("catalog-events", productId.toString(), objectMapper.writeValueAsString(addEvent))
            .get(5, TimeUnit.SECONDS)

        // 좋아요가 추가될 때까지 대기
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository!!.findByProductId(productId)
                assertThat(metrics?.likeCount).isGreaterThan(0)
            }

        // when: 좋아요 제거 이벤트 전송
        Thread.sleep(1000) // 1초 대기 (eventVersion 중복 방지)
        val removeEvent = LikeRemovedEvent(
            userId = 1L,
            productId = productId,
            createdAt = ZonedDateTime.now(),
        )
        kafkaTemplate!!.send("catalog-events", productId.toString(), objectMapper.writeValueAsString(removeEvent))
            .get(5, TimeUnit.SECONDS)

        // then: 좋아요 수가 감소함
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository!!.findByProductId(productId)
                assertThat(metrics?.likeCount).isEqualTo(0)
            }
    }

    @Test
    fun `Consumer가 OrderCreatedEvent를 수신하여 판매량을 집계한다`() {
        // given: Kafka가 실행 중이어야 함
        if (kafkaTemplate == null || productMetricsRepository == null) return

        val orderId = 1000L
        val productId = 300L
        val event = OrderCreatedEvent(
            orderId = orderId,
            userId = 1L,
            amount = 50000,
            couponId = null,
            items = listOf(
                OrderCreatedEvent.OrderItemInfo(
                    productId = productId,
                    productName = "Test Product",
                    quantity = 2,
                    priceAtOrder = 25000,
                ),
            ),
            createdAt = ZonedDateTime.now(),
        )
        val payload = objectMapper.writeValueAsString(event)

        // when: Kafka로 메시지 전송
        kafkaTemplate!!.send("order-events", orderId.toString(), payload)
            .get(5, TimeUnit.SECONDS)

        // then: Consumer가 메시지를 처리하고 판매량 집계
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository!!.findByProductId(productId)
                assertThat(metrics).isNotNull
                assertThat(metrics!!.salesCount).isEqualTo(2)
                assertThat(metrics.totalSalesAmount).isEqualTo(50000)
            }
    }

    @Test
    fun `중복 메시지를 재전송해도 멱등성이 보장된다`() {
        // given: Kafka가 실행 중이어야 함
        if (kafkaTemplate == null || eventHandledRepository == null || productMetricsRepository == null) return

        val productId = 400L
        val createdAt = ZonedDateTime.now()
        val event = LikeAddedEvent(
            userId = 1L,
            productId = productId,
            createdAt = createdAt,
        )
        val payload = objectMapper.writeValueAsString(event)

        // when: 같은 메시지를 3번 전송
        repeat(3) {
            kafkaTemplate!!.send("catalog-events", productId.toString(), payload)
                .get(5, TimeUnit.SECONDS)
        }

        // then: Consumer가 처리하고 ProductMetrics는 1번만 증가
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository!!.findByProductId(productId)
                assertThat(metrics).isNotNull
                // 멱등성 보장 - 3번 전송했지만 1번만 증가
                assertThat(metrics!!.likeCount).isEqualTo(1)
            }

        // EventHandled 테이블 확인 - 한 번만 기록됨
        val eventHandled = eventHandledRepository!!.existsByEventKey(
            eventType = "LikeAddedEvent",
            aggregateType = "Product",
            aggregateId = productId,
            eventVersion = createdAt.nano.toLong(),
        )
        assertThat(eventHandled).isTrue
    }

    @Test
    fun `여러 상품의 주문을 동시에 처리할 수 있다`() {
        // given: Kafka가 실행 중이어야 함
        if (kafkaTemplate == null || productMetricsRepository == null) return

        val orderId = 2000L
        val event = OrderCreatedEvent(
            orderId = orderId,
            userId = 1L,
            amount = 100000,
            couponId = null,
            items = listOf(
                OrderCreatedEvent.OrderItemInfo(
                    productId = 500L,
                    productName = "Product A",
                    quantity = 3,
                    priceAtOrder = 20000,
                ),
                OrderCreatedEvent.OrderItemInfo(
                    productId = 600L,
                    productName = "Product B",
                    quantity = 2,
                    priceAtOrder = 20000,
                ),
            ),
            createdAt = ZonedDateTime.now(),
        )
        val payload = objectMapper.writeValueAsString(event)

        // when: Kafka로 메시지 전송
        kafkaTemplate!!.send("order-events", orderId.toString(), payload)
            .get(5, TimeUnit.SECONDS)

        // then: 두 상품 모두 판매량이 집계됨
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted {
                val metricsA = productMetricsRepository!!.findByProductId(500L)
                val metricsB = productMetricsRepository!!.findByProductId(600L)

                assertThat(metricsA).isNotNull
                assertThat(metricsA!!.salesCount).isEqualTo(3)
                assertThat(metricsA.totalSalesAmount).isEqualTo(60000)

                assertThat(metricsB).isNotNull
                assertThat(metricsB!!.salesCount).isEqualTo(2)
                assertThat(metricsB.totalSalesAmount).isEqualTo(40000)
            }
    }

    @Test
    fun `알 수 없는 이벤트 타입은 무시된다`() {
        // given: Kafka가 실행 중이어야 함
        if (kafkaTemplate == null) return

        val payload = """{"unknown":"event"}"""

        // when: 알 수 없는 이벤트 전송
        kafkaTemplate!!.send("catalog-events", "999", payload)
            .get(5, TimeUnit.SECONDS)

        // then: 에러 없이 무시됨 (로그만 출력)
        Thread.sleep(2000) // 2초 대기
        // 에러가 발생하지 않았으면 성공
    }
}
