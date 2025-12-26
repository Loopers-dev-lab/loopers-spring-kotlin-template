package com.loopers.infrastructure.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.coupon.event.CouponUsedEvent
import com.loopers.domain.event.DomainEvent
import com.loopers.domain.event.EventOutbox
import com.loopers.domain.like.event.ProductLikedEvent
import com.loopers.domain.like.event.ProductUnlikedEvent
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.payment.event.PaymentCompletedEvent
import com.loopers.domain.payment.event.PaymentFailedEvent
import com.loopers.domain.product.event.ProductBrowsedEvent
import com.loopers.domain.product.event.ProductViewedEvent
import com.loopers.domain.product.event.StockDecreasedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Transactional Outbox Pattern 구현
 * - BEFORE_COMMIT: 비즈니스 로직과 같은 트랜잭션에서 저장
 * - fallbackExecution: 트랜잭션 없는 컨텍스트에서도 실행
 * - 이벤트 유실 방지
 */
@Component
class OutboxEventListener(
    private val eventOutboxRepository: EventOutboxJpaRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRED)
    fun handleDomainEvent(event: DomainEvent) {
        // 멱등성 체크
        if (eventOutboxRepository.existsByEventId(event.eventId)) {
            logger.warn("중복 이벤트 무시: eventId=${event.eventId}, type=${event.eventType}")
            return
        }

        // Outbox에 저장 (같은 트랜잭션)
        val outbox = EventOutbox(
            eventId = event.eventId,
            eventType = event.eventType,
            aggregateType = getAggregateType(event),
            aggregateId = event.aggregateId,
            payload = objectMapper.writeValueAsString(event),
            occurredAt = event.occurredAt
        )

        eventOutboxRepository.save(outbox)
        logger.debug("Outbox 저장 완료: eventId=${event.eventId}, type=${event.eventType}")
    }

    private fun getAggregateType(event: DomainEvent): String {
        return when (event) {
            is ProductLikedEvent,
            is ProductUnlikedEvent,
            is ProductViewedEvent,
            is ProductBrowsedEvent,
            is StockDecreasedEvent -> "product"
            is OrderCreatedEvent,
            is PaymentCompletedEvent,
            is PaymentFailedEvent,
            is CouponUsedEvent -> "order"

            else -> "UNKNOWN"
        }
    }
}
