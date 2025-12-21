package com.loopers.infrastructure.event

import com.loopers.domain.event.EventOutbox
import com.loopers.domain.like.event.ProductLikedEvent
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.payment.event.PaymentCompletedEvent
import com.loopers.domain.product.event.ProductViewedEvent
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * OutboxEventListener 통합 테스트
 *
 * 테스트 범위:
 * - BEFORE_COMMIT 페이즈에서 EventOutbox 저장 검증
 * - 멱등성 검증 (중복 이벤트 무시)
 * - aggregateType 매핑 검증
 * - JSON 직렬화 검증
 */
@SpringBootTest
@TestPropertySource(properties = ["spring.task.scheduling.enabled=false"])
class OutboxEventListenerTest @Autowired constructor(
    private val eventPublisher: ApplicationEventPublisher,
    private val eventOutboxRepository: EventOutboxJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp
) {

    @Autowired
    private lateinit var entityManager: jakarta.persistence.EntityManager

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("ProductLikedEvent 발행 시 EventOutbox에 저장된다")
    @Test
    fun saveProductLikedEventToOutbox() {
        // given
        val event = ProductLikedEvent(
            eventId = "test-event-id-1",
            aggregateId = 100L,
            likeId = 1L,
            productId = 100L,
            memberId = "testUser1",
            occurredAt = Instant.now()
        )

        // when
        transactionTemplate.execute {
            eventPublisher.publishEvent(event)
            entityManager.flush()
        }

        // then
        val outboxList = eventOutboxRepository.findAll()
        assertThat(outboxList).hasSize(1)

        val outbox = outboxList.first()
        assertThat(outbox.eventId).isEqualTo("test-event-id-1")
        assertThat(outbox.eventType).isEqualTo("PRODUCT_LIKED")
        assertThat(outbox.aggregateType).isEqualTo("product")
        assertThat(outbox.aggregateId).isEqualTo(100L)
        assertThat(outbox.processed).isFalse()
        assertThat(outbox.retryCount).isEqualTo(0)
        assertThat(outbox.payload).contains("\"productId\":100")
        assertThat(outbox.payload).contains("\"memberId\":\"testUser1\"")
    }

    @DisplayName("ProductViewedEvent 발행 시 EventOutbox에 저장된다")
    @Test
    fun saveProductViewedEventToOutbox() {
        // given
        val event = ProductViewedEvent(
            eventId = "test-event-id-2",
            aggregateId = 200L,
            productId = 200L,
            memberId = "testUser2",
            occurredAt = Instant.now()
        )

        // when
        transactionTemplate.execute {
            eventPublisher.publishEvent(event)
            entityManager.flush()
        }

        // then
        val outboxList = eventOutboxRepository.findAll()
        assertThat(outboxList).hasSize(1)

        val outbox = outboxList.first()
        assertThat(outbox.eventId).isEqualTo("test-event-id-2")
        assertThat(outbox.eventType).isEqualTo("PRODUCT_VIEWED")
        assertThat(outbox.aggregateType).isEqualTo("product")
        assertThat(outbox.aggregateId).isEqualTo(200L)
    }

    @DisplayName("OrderCreatedEvent 발행 시 aggregateType이 'order'로 저장된다")
    @Test
    fun saveOrderCreatedEventWithCorrectAggregateType() {
        // given
        val event = OrderCreatedEvent(
            eventId = "test-event-id-3",
            aggregateId = 1000L,
            orderId = 1000L,
            memberId = "testUser3",
            orderAmount = 50000L,
            couponId = null,
            orderItems = emptyList(),
            occurredAt = Instant.now()
        )

        // when
        transactionTemplate.execute {
            eventPublisher.publishEvent(event)
            entityManager.flush()
        }

        // then
        val outboxList = eventOutboxRepository.findAll()
        assertThat(outboxList).hasSize(1)

        val outbox = outboxList.first()
        assertThat(outbox.eventType).isEqualTo("ORDER_CREATED")
        assertThat(outbox.aggregateType).isEqualTo("order")
        assertThat(outbox.aggregateId).isEqualTo(1000L)
    }

    @DisplayName("PaymentCompletedEvent 발행 시 aggregateType이 'order'로 저장된다")
    @Test
    fun savePaymentCompletedEventWithCorrectAggregateType() {
        // given
        val event = PaymentCompletedEvent(
            eventId = "test-event-id-4",
            aggregateId = 2000L,
            paymentId = 1L,
            orderId = 2000L,
            memberId = "testUser4",
            amount = 50000L,
            occurredAt = Instant.now()
        )

        // when
        transactionTemplate.execute {
            eventPublisher.publishEvent(event)
            entityManager.flush()
        }

        // then
        val outboxList = eventOutboxRepository.findAll()
        assertThat(outboxList).hasSize(1)

        val outbox = outboxList.first()
        assertThat(outbox.eventType).isEqualTo("PAYMENT_COMPLETED")
        assertThat(outbox.aggregateType).isEqualTo("order")
    }

    @DisplayName("동일한 eventId로 이벤트 재발행 시 중복 저장되지 않는다 (멱등성)")
    @Test
    fun ignoresDuplicateEventId() {
        // given
        val eventId = "duplicate-event-id"
        val event1 = ProductLikedEvent(
            eventId = eventId,
            aggregateId = 100L,
            likeId = 1L,
            productId = 100L,
            memberId = "testUser1",
            occurredAt = Instant.now()
        )

        // when: 첫 번째 발행
        transactionTemplate.execute {
            eventPublisher.publishEvent(event1)
            entityManager.flush()
        }

        // then: 1개 저장됨
        assertThat(eventOutboxRepository.findAll()).hasSize(1)

        // when: 동일한 eventId로 두 번째 발행
        val event2 = ProductLikedEvent(
            eventId = eventId,
            aggregateId = 100L,
            likeId = 2L,
            productId = 100L,
            memberId = "testUser1",
            occurredAt = Instant.now()
        )
        transactionTemplate.execute {
            eventPublisher.publishEvent(event2)
            entityManager.flush()
        }

        // then: 여전히 1개만 저장됨 (중복 무시)
        val outboxList = eventOutboxRepository.findAll()
        assertThat(outboxList).hasSize(1)
    }

    @DisplayName("여러 이벤트를 발행하면 모두 EventOutbox에 저장된다")
    @Test
    fun saveMultipleEventsToOutbox() {
        // given
        val event1 = ProductLikedEvent(
            eventId = "event-1",
            aggregateId = 100L,
            likeId = 1L,
            productId = 100L,
            memberId = "user1",
            occurredAt = Instant.now()
        )
        val event2 = ProductViewedEvent(
            eventId = "event-2",
            aggregateId = 200L,
            productId = 200L,
            memberId = "user2",
            occurredAt = Instant.now()
        )
        val event3 = OrderCreatedEvent(
            eventId = "event-3",
            aggregateId = 1000L,
            orderId = 1000L,
            memberId = "user3",
            orderAmount = 30000L,
            couponId = null,
            orderItems = emptyList(),
            occurredAt = Instant.now()
        )

        // when
        transactionTemplate.execute {
            eventPublisher.publishEvent(event1)
            eventPublisher.publishEvent(event2)
            eventPublisher.publishEvent(event3)
            entityManager.flush()
        }

        // then
        val outboxList = eventOutboxRepository.findAll()
        assertThat(outboxList).hasSize(3)

        val eventTypes = outboxList.map { it.eventType }
        assertThat(eventTypes).containsExactlyInAnyOrder(
            "PRODUCT_LIKED",
            "PRODUCT_VIEWED",
            "ORDER_CREATED"
        )
    }

    @DisplayName("EventOutbox 초기 상태는 processed=false, retryCount=0 이다")
    @Test
    fun outboxInitialStateIsUnprocessed() {
        // given
        val event = ProductLikedEvent(
            eventId = "test-event-initial",
            aggregateId = 100L,
            likeId = 1L,
            productId = 100L,
            memberId = "testUser",
            occurredAt = Instant.now()
        )

        // when
        transactionTemplate.execute {
            eventPublisher.publishEvent(event)
            entityManager.flush()
        }

        // then
        val outbox = eventOutboxRepository.findAll().first()
        assertThat(outbox.processed).isFalse()
        assertThat(outbox.processedAt).isNull()
        assertThat(outbox.retryCount).isEqualTo(0)
        assertThat(outbox.lastError).isNull()
        assertThat(outbox.kafkaPartition).isNull()
        assertThat(outbox.kafkaOffset).isNull()
    }

    @DisplayName("payload에 이벤트의 모든 필드가 JSON으로 직렬화된다")
    @Test
    fun payloadContainsAllEventFields() {
        // given
        val now = Instant.now()
        val event = ProductLikedEvent(
            eventId = "test-event-json",
            aggregateId = 999L,
            likeId = 1L,
            productId = 999L,
            memberId = "jsonTestUser",
            occurredAt = now
        )

        // when
        transactionTemplate.execute {
            eventPublisher.publishEvent(event)
            entityManager.flush()
        }

        // then
        val outbox = eventOutboxRepository.findAll().first()
        val payload = outbox.payload

        assertThat(payload).contains("\"eventId\":\"test-event-json\"")
        assertThat(payload).contains("\"eventType\":\"PRODUCT_LIKED\"")
        assertThat(payload).contains("\"aggregateId\":999")
        assertThat(payload).contains("\"productId\":999")
        assertThat(payload).contains("\"memberId\":\"jsonTestUser\"")
        assertThat(payload).contains("\"occurredAt\"")
    }
}
