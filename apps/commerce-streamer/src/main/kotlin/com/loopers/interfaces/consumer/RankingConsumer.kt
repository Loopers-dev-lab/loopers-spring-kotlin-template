package com.loopers.interfaces.consumer

import com.loopers.application.ranking.RankingFacade
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.event.OutboxEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * 랭킹 Kafka Consumer
 *
 * 4개 이벤트 타입(조회/좋아요/주문완료/주문취소)을 배치로 컨슘하여 랭킹 점수에 반영합니다.
 *
 * 흐름
 *   RankingConsumer (4개 리스너)
 *       ↓
 *   RankingFacade (이벤트 역직렬화 & 날짜 키 계산)
 *       ↓
 *   RankingService (배치 집계 & 멱등성)
 *       ↓
 *   RankingRedisRepository (Pipeline 배치 업데이트)
 *       ↓
 *   Redis ZSET (ranking:all:{yyyyMMdd})
 */
@Component
class RankingConsumer(
    private val rankingFacade: RankingFacade,
) {
    private val log = LoggerFactory.getLogger(RankingConsumer::class.java)

    /**
     * 조회수 이벤트 배치 처리 (ViewCountIncreased)
     */
    @KafkaListener(
        topics = [OutboxEvent.ViewCountIncreased.TOPIC],
        groupId = "ranking-view-consumer",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consumeViewEvents(
        records: List<ConsumerRecord<Any, Any>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("랭킹 조회수 이벤트 배치 수신: {} 건", records.size)
        try {
            rankingFacade.handleViewEvents(records)
            acknowledgment.acknowledge()
            log.info("랭킹 조회수 이벤트 배치 처리 완료: {} 건", records.size)
        } catch (e: Exception) {
            log.error("랭킹 조회수 이벤트 배치 처리 실패", e)
            throw e
        }
    }

    /**
     * 좋아요 이벤트 배치 처리 (LikeCountChanged)
     */
    @KafkaListener(
        topics = [OutboxEvent.LikeCountChanged.TOPIC],
        groupId = "ranking-like-consumer",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consumeLikeEvents(
        records: List<ConsumerRecord<Any, Any>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("랭킹 좋아요 이벤트 배치 수신: {} 건", records.size)
        try {
            rankingFacade.handleLikeEvents(records)
            acknowledgment.acknowledge()
            log.info("랭킹 좋아요 이벤트 배치 처리 완료: {} 건", records.size)
        } catch (e: Exception) {
            log.error("랭킹 좋아요 이벤트 배치 처리 실패", e)
            throw e
        }
    }

    /**
     * 주문 완료 이벤트 배치 처리 (OrderCompleted)
     */
    @KafkaListener(
        topics = [OutboxEvent.OrderCompleted.TOPIC],
        groupId = "ranking-order-completed-consumer",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consumeOrderCompletedEvents(
        records: List<ConsumerRecord<Any, Any>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("랭킹 주문 완료 이벤트 배치 수신: {} 건", records.size)
        try {
            rankingFacade.handleOrderCompletedEvents(records)
            acknowledgment.acknowledge()
            log.info("랭킹 주문 완료 이벤트 배치 처리 완료: {} 건", records.size)
        } catch (e: Exception) {
            log.error("랭킹 주문 완료 이벤트 배치 처리 실패", e)
            throw e
        }
    }

    /**
     * 주문 취소 이벤트 배치 처리 (OrderCanceled)
     */
    @KafkaListener(
        topics = [OutboxEvent.OrderCanceled.TOPIC],
        groupId = "ranking-order-canceled-consumer",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consumeOrderCanceledEvents(
        records: List<ConsumerRecord<Any, Any>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("랭킹 주문 취소 이벤트 배치 수신: {} 건", records.size)
        try {
            rankingFacade.handleOrderCanceledEvents(records)
            acknowledgment.acknowledge()
            log.info("랭킹 주문 취소 이벤트 배치 처리 완료: {} 건", records.size)
        } catch (e: Exception) {
            log.error("랭킹 주문 취소 이벤트 배치 처리 실패", e)
            throw e
        }
    }
}
