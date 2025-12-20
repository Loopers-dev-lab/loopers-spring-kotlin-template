package com.loopers.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.event.LikeAddedEvent
import com.loopers.domain.event.LikeRemovedEvent
import com.loopers.domain.event.OrderCreatedEvent
import com.loopers.domain.event.ProductViewEvent
import com.loopers.domain.event.StockDepletedEvent
import com.loopers.domain.product.ProductMetricsRepository
import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScore
import com.loopers.domain.ranking.RankingScoreCalculator
import com.loopers.domain.ranking.RankingScope
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

/**
 * Kafka 이벤트 Consumer
 * catalog-events, order-events 토픽에서 메시지를 소비하여 집계 처리
 *
 * 배치 리스너를 사용하여 여러 메시지를 한 번에 처리하고,
 * Redis ZSET에 랭킹 점수를 실시간으로 반영
 */
@Component
@ConditionalOnBean(KafkaTemplate::class)
class KafkaEventConsumer(
    private val objectMapper: ObjectMapper,
    private val eventHandledRepository: EventHandledRepository,
    private val productMetricsRepository: ProductMetricsRepository,
    private val rankingRepository: RankingRepository,
    private val rankingWeightProperties: com.loopers.config.RankingWeightProperties,
) {
    private val logger = LoggerFactory.getLogger(KafkaEventConsumer::class.java)

    /**
     * catalog-events 토픽 리스너
     * LikeAddedEvent, LikeRemovedEvent 등을 처리
     */
    @KafkaListener(
        topics = ["\${kafka.topics.catalog-events}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    @Transactional
    fun consumeCatalogEvents(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_KEY) key: String,
        @Header("eventType", required = false) eventType: String?,
        acknowledgment: Acknowledgment,
    ) {
        try {
            logger.debug("catalog-events 메시지 수신: key=$key, eventType=$eventType")

            // 이벤트 타입에 따라 분기 처리
            when (eventType) {
                "LikeAddedEvent" -> handleLikeAdded(message, acknowledgment)
                "LikeRemovedEvent" -> handleLikeRemoved(message, acknowledgment)
                "ProductViewEvent" -> handleProductView(message, acknowledgment)
                "StockDepletedEvent" -> handleStockDepleted(message, acknowledgment)
                else -> {
                    logger.warn("알 수 없는 이벤트 타입: $eventType")
                    acknowledgeAfterCommit(acknowledgment)
                }
            }
        } catch (e: Exception) {
            logger.error("catalog-events 메시지 처리 실패: key=$key", e)
            // DefaultErrorHandler가 재시도 후 DLQ로 라우팅 처리
            throw e
        }
    }

    /**
     * order-events 토픽 리스너
     * OrderCreatedEvent 등을 처리
     */
    @KafkaListener(
        topics = ["\${kafka.topics.order-events}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    @Transactional
    fun consumeOrderEvents(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_KEY) key: String,
        @Header("eventType", required = false) eventType: String?,
        acknowledgment: Acknowledgment,
    ) {
        try {
            logger.debug("order-events 메시지 수신: key=$key, eventType=$eventType")

            when (eventType) {
                "OrderCreatedEvent" -> handleOrderCreated(message, acknowledgment)
                else -> {
                    logger.warn("알 수 없는 이벤트 타입: $eventType")
                    acknowledgeAfterCommit(acknowledgment)
                }
            }
        } catch (e: Exception) {
            logger.error("order-events 메시지 처리 실패: key=$key", e)
            // DefaultErrorHandler가 재시도 후 DLQ로 라우팅 처리
            throw e
        }
    }

    /**
     * LikeAddedEvent 처리
     */
    private fun handleLikeAdded(message: String, acknowledgment: Acknowledgment) {
        val event: LikeAddedEvent = objectMapper.readValue(message)

        // 멱등성 체크
        if (isAlreadyHandled(event.eventId)) {
            logger.debug("이미 처리된 이벤트: LikeAddedEvent, eventId=${event.eventId}, productId=${event.productId}")
            acknowledgeAfterCommit(acknowledgment)
            return
        }

        // 집계 처리 (비관적 락 사용)
        val metrics = productMetricsRepository.findOrCreateByProductIdWithLock(event.productId)
        metrics.incrementLikeCount()
        productMetricsRepository.save(metrics)

        // 랭킹 점수 업데이트
        updateRankingScore(event.productId, RankingScoreCalculator.fromLike(rankingWeightProperties.like))

        // 처리 완료 기록
        eventHandledRepository.save(
            EventHandled.create(
                eventId = event.eventId,
                eventType = "LikeAddedEvent",
                aggregateType = "Product",
                aggregateId = event.productId,
            ),
        )

        acknowledgeAfterCommit(acknowledgment)
        logger.info(
            "LikeAddedEvent 처리 완료: eventId=${event.eventId}, productId=${event.productId}, likeCount=${metrics.likeCount}",
        )
    }

    /**
     * LikeRemovedEvent 처리
     */
    private fun handleLikeRemoved(message: String, acknowledgment: Acknowledgment) {
        val event: LikeRemovedEvent = objectMapper.readValue(message)

        if (isAlreadyHandled(event.eventId)) {
            logger.debug("이미 처리된 이벤트: LikeRemovedEvent, eventId=${event.eventId}, productId=${event.productId}")
            acknowledgeAfterCommit(acknowledgment)
            return
        }

        // 집계 처리 (비관적 락 사용)
        val metrics = productMetricsRepository.findOrCreateByProductIdWithLock(event.productId)
        metrics.decrementLikeCount()
        productMetricsRepository.save(metrics)

        eventHandledRepository.save(
            EventHandled.create(
                eventId = event.eventId,
                eventType = "LikeRemovedEvent",
                aggregateType = "Product",
                aggregateId = event.productId,
            ),
        )

        acknowledgeAfterCommit(acknowledgment)
        logger.info(
            "LikeRemovedEvent 처리 완료: eventId=${event.eventId}, productId=${event.productId}, likeCount=${metrics.likeCount}",
        )
    }

    /**
     * OrderCreatedEvent 처리
     */
    private fun handleOrderCreated(message: String, acknowledgment: Acknowledgment) {
        val event: OrderCreatedEvent = objectMapper.readValue(message)

        if (isAlreadyHandled(event.eventId)) {
            logger.debug("이미 처리된 이벤트: OrderCreatedEvent, eventId=${event.eventId}, orderId=${event.orderId}")
            acknowledgeAfterCommit(acknowledgment)
            return
        }

        // 주문 상품별 판매량 집계 (비관적 락 사용)
        event.items.forEach { item ->
            val metrics = productMetricsRepository.findOrCreateByProductIdWithLock(item.productId)
            val totalAmount = item.priceAtOrder * item.quantity
            metrics.incrementSales(
                quantity = item.quantity.toLong(),
                amount = totalAmount,
            )
            productMetricsRepository.save(metrics)

            // 랭킹 점수 업데이트
            val orderScore = RankingScoreCalculator.fromOrder(item.priceAtOrder, item.quantity, rankingWeightProperties.order)
            updateRankingScore(item.productId, orderScore)

            logger.debug(
                "상품 판매량 집계 완료: productId=${item.productId}, " +
                    "quantity=${item.quantity}, amount=$totalAmount, " +
                    "totalSalesCount=${metrics.salesCount}, totalSalesAmount=${metrics.totalSalesAmount}",
            )
        }

        eventHandledRepository.save(
            EventHandled.create(
                eventId = event.eventId,
                eventType = "OrderCreatedEvent",
                aggregateType = "Order",
                aggregateId = event.orderId,
            ),
        )

        acknowledgeAfterCommit(acknowledgment)
        logger.info(
            "OrderCreatedEvent 처리 완료: eventId=${event.eventId}, orderId=${event.orderId}, " +
                "items=${event.items.size}개 상품 판매량 집계",
        )
    }

    /**
     * StockDepletedEvent 처리
     *
     * 재고 소진 시 상품 캐시 갱신은 commerce-api의 StockEventHandler에서 처리됨
     * (이벤트 기반 아키텍처: 재고 차감 로직과 캐시 관리 완전 분리)
     *
     * commerce-streamer는 다음을 담당:
     * - 재고 소진 이벤트 기록 (멱등성 보장)
     * - 추후 확장: 재고 소진 알림 발송, 자동 발주 등
     */
    private fun handleStockDepleted(message: String, acknowledgment: Acknowledgment) {
        val event: StockDepletedEvent = objectMapper.readValue(message)

        if (isAlreadyHandled(event.eventId)) {
            logger.debug("이미 처리된 이벤트: StockDepletedEvent, eventId=${event.eventId}, productId=${event.productId}")
            acknowledgeAfterCommit(acknowledgment)
            return
        }

        // 재고 소진 이벤트 로깅
        logger.warn(
            "재고 소진 이벤트 수신: productId=${event.productId}, " +
                "previousQuantity=${event.previousQuantity}",
        )

        // 처리 완료 기록
        eventHandledRepository.save(
            EventHandled.create(
                eventId = event.eventId,
                eventType = "StockDepletedEvent",
                aggregateType = "Product",
                aggregateId = event.productId,
            ),
        )

        acknowledgeAfterCommit(acknowledgment)
        logger.info(
            "StockDepletedEvent 처리 완료: eventId=${event.eventId}, productId=${event.productId}",
        )
    }

    /**
     * ProductViewEvent 처리
     */
    private fun handleProductView(message: String, acknowledgment: Acknowledgment) {
        val event: ProductViewEvent = objectMapper.readValue(message)

        if (isAlreadyHandled(event.eventId)) {
            logger.debug("이미 처리된 이벤트: ProductViewEvent, eventId=${event.eventId}, productId=${event.productId}")
            acknowledgeAfterCommit(acknowledgment)
            return
        }

        // 집계 처리 (비관적 락 사용)
        val metrics = productMetricsRepository.findOrCreateByProductIdWithLock(event.productId)
        metrics.incrementViewCount()
        productMetricsRepository.save(metrics)

        // 랭킹 점수 업데이트
        updateRankingScore(event.productId, RankingScoreCalculator.fromView(rankingWeightProperties.view))

        // 처리 완료 기록
        eventHandledRepository.save(
            EventHandled.create(
                eventId = event.eventId,
                eventType = "ProductViewEvent",
                aggregateType = "Product",
                aggregateId = event.productId,
            ),
        )

        acknowledgeAfterCommit(acknowledgment)
        logger.info(
            "ProductViewEvent 처리 완료: eventId=${event.eventId}, productId=${event.productId}, viewCount=${metrics.viewCount}",
        )
    }

    /**
     * 이미 처리된 이벤트인지 확인 (멱등성 보장)
     */
    private fun isAlreadyHandled(eventId: UUID): Boolean {
        return eventHandledRepository.existsByEventId(eventId)
    }

    /**
     * 랭킹 점수 업데이트
     * 일간 랭킹과 시간별 랭킹에 점수를 각각 반영
     */
    private fun updateRankingScore(productId: Long, score: RankingScore) {
        // 일간 랭킹 업데이트
        val dailyKey = RankingKey.currentDaily(RankingScope.ALL)
        rankingRepository.incrementScore(dailyKey, productId, score)
        rankingRepository.setExpire(dailyKey) // TTL 갱신

        // 시간별 랭킹 업데이트
        val hourlyKey = RankingKey.currentHourly(RankingScope.ALL)
        rankingRepository.incrementScore(hourlyKey, productId, score)
        rankingRepository.setExpire(hourlyKey) // TTL 갱신

        logger.debug(
            "랭킹 점수 업데이트 완료: productId=$productId, score=${score.value}, " +
                "dailyKey=${dailyKey.toRedisKey()}, hourlyKey=${hourlyKey.toRedisKey()}",
        )
    }

    /**
     * DB 트랜잭션 커밋 후 Kafka offset을 acknowledge
     * 이를 통해 DB 커밋 실패 시 메시지 손실을 방지
     */
    private fun acknowledgeAfterCommit(acknowledgment: Acknowledgment) {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    acknowledgment.acknowledge()
                }
            },
        )
    }
}
