package com.loopers.application.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.OutboxEvent
import com.loopers.domain.ranking.RankingService
import com.loopers.domain.ranking.dto.LikeScoreEvent
import com.loopers.domain.ranking.dto.OrderScoreEvent
import com.loopers.domain.ranking.dto.OrderScoreItem
import com.loopers.domain.ranking.dto.ViewScoreEvent
import com.loopers.support.util.EventIdExtractor
import com.loopers.support.util.readEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

/**
 * 랭킹 Facade
 *
 * Kafka Consumer로부터 배치 이벤트를 받아 처리를 조율합니다.
 * - 이벤트 역직렬화
 * - 날짜 키 계산
 * - RankingService 호출
 */
@Service
class RankingFacade(
    private val rankingService: RankingService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(RankingFacade::class.java)
    private val dateKeyFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * 조회수 이벤트 배치 처리
     */
    fun handleViewEvents(records: List<ConsumerRecord<Any, Any>>) {
        log.info("랭킹 조회수 이벤트 배치 처리 시작: {} 건", records.size)

        val dateKey = currentDateKey()
        val events = records.map { record ->
            val event = readEvent<OutboxEvent.ViewCountIncreased>(record, objectMapper)
            val eventId = EventIdExtractor.extract(record)

            ViewScoreEvent(
                productId = event.productId,
                dateKey = dateKey,
                eventId = eventId,
                eventType = OutboxEvent.ViewCountIncreased.EVENT_TYPE,
                eventTimestamp = event.timestamp,
            )
        }

        rankingService.incrementViewScoreBatch(events)
    }

    /**
     * 좋아요 이벤트 배치 처리
     */
    fun handleLikeEvents(records: List<ConsumerRecord<Any, Any>>) {
        log.info("랭킹 좋아요 이벤트 배치 처리 시작: {} 건", records.size)

        val dateKey = currentDateKey()
        val likedEvents = mutableListOf<LikeScoreEvent>()
        val unlikedEvents = mutableListOf<LikeScoreEvent>()

        records.forEach { record ->
            val event = readEvent<OutboxEvent.LikeCountChanged>(record, objectMapper)
            val eventId = EventIdExtractor.extract(record)
            val likeEvent = LikeScoreEvent(
                productId = event.productId,
                dateKey = dateKey,
                eventId = eventId,
                eventType = OutboxEvent.LikeCountChanged.EVENT_TYPE,
                eventTimestamp = event.timestamp,
            )

            when (event.action) {
                OutboxEvent.LikeCountChanged.LikeAction.LIKED -> {
                    likedEvents.add(likeEvent)
                }

                OutboxEvent.LikeCountChanged.LikeAction.UNLIKED -> {
                    unlikedEvents.add(likeEvent)
                }
            }
        }

        if (likedEvents.isNotEmpty()) {
            rankingService.incrementLikeScoreBatch(likedEvents)
        }
        if (unlikedEvents.isNotEmpty()) {
            rankingService.decrementLikeScoreBatch(unlikedEvents)
        }
    }

    /**
     * 주문 완료 이벤트 배치 처리
     */
    fun handleOrderCompletedEvents(records: List<ConsumerRecord<Any, Any>>) {
        log.info("랭킹 주문 완료 이벤트 배치 처리 시작: {} 건", records.size)

        val dateKey = currentDateKey()
        val events = records.map { record ->
            val event = readEvent<OutboxEvent.OrderCompleted>(record, objectMapper)
            val eventId = EventIdExtractor.extract(record)

            OrderScoreEvent(
                dateKey = dateKey,
                eventId = eventId,
                eventType = OutboxEvent.OrderCompleted.EVENT_TYPE,
                eventTimestamp = event.timestamp,
                items = event.items.map {
                    OrderScoreItem(
                        productId = it.productId,
                        quantity = it.quantity,
                        price = it.price,
                    )
                },
            )
        }

        rankingService.incrementOrderScoreBatch(events)
    }

    /**
     * 주문 취소 이벤트 배치 처리
     */
    fun handleOrderCanceledEvents(records: List<ConsumerRecord<Any, Any>>) {
        log.info("랭킹 주문 취소 이벤트 배치 처리 시작: {} 건", records.size)

        val currentDateKey = currentDateKey()
        val events = records.mapNotNull { record ->
            val event = readEvent<OutboxEvent.OrderCanceled>(record, objectMapper)
            val eventId = EventIdExtractor.extract(record)
            val eventDateKey = dateKeyFrom(event.orderCreatedAt)

            if (eventDateKey != currentDateKey) {
                log.info(
                    "당일 주문이 아닌 취소 이벤트는 랭킹 점수에 반영하지 않습니다: orderDateKey={}, currentDateKey={}",
                    eventDateKey,
                    currentDateKey,
                )
                return@mapNotNull null
            }

            OrderScoreEvent(
                dateKey = eventDateKey,
                eventId = eventId,
                eventType = OutboxEvent.OrderCanceled.EVENT_TYPE,
                eventTimestamp = event.timestamp,
                items = event.items.map {
                    OrderScoreItem(
                        productId = it.productId,
                        quantity = it.quantity,
                        price = it.price,
                    )
                },
            )
        }

        rankingService.decrementOrderScoreBatch(events)
    }

    /**
     * 날짜 키 계산 (yyyyMMdd 형식)
     */
    private fun currentDateKey(): String {
        return dateKeyFrom(java.time.ZonedDateTime.now())
    }

    private fun dateKeyFrom(timestamp: java.time.ZonedDateTime): String {
        return timestamp.format(dateKeyFormatter)
    }
}
