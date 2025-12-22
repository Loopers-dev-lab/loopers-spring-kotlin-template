package com.loopers.domain.ranking

import com.loopers.domain.event.EventProcessingResult.SHOULD_PROCESS
import com.loopers.domain.event.EventService
import com.loopers.domain.ranking.dto.LikeScoreEvent
import com.loopers.domain.ranking.dto.OrderScoreEvent
import com.loopers.domain.ranking.dto.ViewScoreEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 랭킹 도메인 서비스
 *
 * Redis ZSET에 이벤트별 랭킹 점수를 반영하고, 멱등성 및 순서 보장을 처리합니다.
 */
@Service
@Transactional
class RankingService(
    private val rankingRepository: RankingRepository,
    private val eventService: EventService,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(RankingService::class.java)

    companion object {
        private const val CARRY_OVER_WEIGHT = 0.1
        private val DATE_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    /**
     * 조회수 점수 배치 증가
     *
     * 같은 배치의 여러 조회 이벤트를 Redis Pipeline으로 한 번에 업데이트
     */
    fun incrementViewScoreBatch(events: List<ViewScoreEvent>) {
        if (events.isEmpty()) {
            return
        }

        val scoresByDate = mutableMapOf<String, MutableMap<Long, Double>>()
        val processedEventIds = mutableSetOf<String>()

        events.forEach { event ->
            val aggregateId = "${event.productId}:${event.dateKey}"

            // 배치 내 중복 체크 (같은 배치에서 동일 eventId+aggregateId 조합은 1번만 처리)
            val eventKey = "${event.eventId}:$aggregateId"
            if (processedEventIds.contains(eventKey)) {
                log.debug("배치 내 중복 랭킹 조회수 이벤트: eventId={}, aggregateId={}", event.eventId, aggregateId)
                return@forEach
            }

            val shouldProcess = transactionTemplate.execute {
                // DB 멱등성 체크
                if (eventService.isAlreadyHandled(event.eventId, aggregateId)) {
                    log.debug("이미 처리된 랭킹 조회수 이벤트: eventId={}, aggregateId={}", event.eventId, aggregateId)
                    return@execute false
                }

                // 즉시 처리 완료 기록 (배치 내 중복 방지)
                eventService.markAsHandled(event.eventId, aggregateId, event.eventType, event.eventTimestamp)
                true
            } ?: false

            if (!shouldProcess) {
                return@forEach
            }

            val score = RankingScoreCalculator.calculateViewScore()
            val productScores = scoresByDate.getOrPut(event.dateKey) { mutableMapOf() }
            productScores[event.productId] = (productScores[event.productId] ?: 0.0) + score

            processedEventIds.add(eventKey)
        }

        // Redis 작업 수행
        scoresByDate.forEach { (dateKey, productScores) ->
            if (productScores.isNotEmpty()) {
                rankingRepository.batchIncrementScores(dateKey, productScores)
                log.info("랭킹 조회수 점수 배치 증가: dateKey={}, {} 건", dateKey, productScores.size)
            }
        }
    }

    /**
     * 좋아요 점수 배치 증가
     */
    fun incrementLikeScoreBatch(events: List<LikeScoreEvent>, consumerGroup: String) {
        processLikeScoreBatch(events, consumerGroup, isIncrement = true)
    }

    /**
     * 좋아요 점수 배치 감소
     */
    fun decrementLikeScoreBatch(events: List<LikeScoreEvent>, consumerGroup: String) {
        processLikeScoreBatch(events, consumerGroup, isIncrement = false)
    }

    /**
     * 좋아요 점수 배치 처리 공통 로직
     */
    private fun processLikeScoreBatch(
        events: List<LikeScoreEvent>,
        consumerGroup: String,
        isIncrement: Boolean,
    ) {
        if (events.isEmpty()) {
            return
        }

        val scoresByDate = mutableMapOf<String, MutableMap<Long, Double>>()
        val processedEventKeys = mutableSetOf<String>()

        events.forEach { event ->
            val aggregateId = "${event.productId}:${event.dateKey}"

            // 배치 내 중복 체크
            val eventKey = "${event.eventId}:$aggregateId"
            if (processedEventKeys.contains(eventKey)) {
                log.debug("배치 내 중복 랭킹 좋아요 이벤트: eventId={}, aggregateId={}", event.eventId, aggregateId)
                return@forEach
            }

            val result = eventService.checkAndPrepareForProcessing(
                eventId = event.eventId,
                eventType = event.eventType,
                eventTimestamp = event.eventTimestamp,
                consumerGroup = consumerGroup,
                aggregateId = aggregateId,
            )

            if (result != SHOULD_PROCESS) {
                return@forEach
            }

            val score = RankingScoreCalculator.calculateLikeScore()
            val productScores = scoresByDate.getOrPut(event.dateKey) { mutableMapOf() }
            productScores[event.productId] = (productScores[event.productId] ?: 0.0) + score

            processedEventKeys.add(eventKey)
            // 즉시 처리 완료 기록 (배치 내 중복 방지)
            eventService.recordProcessingComplete(
                event.eventId,
                event.eventType,
                event.eventTimestamp,
                consumerGroup,
                aggregateId,
            )
        }

        // Redis 작업 수행
        val actionName = if (isIncrement) "증가" else "감소"
        scoresByDate.forEach { (dateKey, productScores) ->
            if (productScores.isNotEmpty()) {
                if (isIncrement) {
                    rankingRepository.batchIncrementScores(dateKey, productScores)
                } else {
                    rankingRepository.batchDecrementScores(dateKey, productScores)
                }
                log.info("랭킹 좋아요 점수 배치 {}: dateKey={}, {} 건", actionName, dateKey, productScores.size)
            }
        }
    }

    /**
     * 주문 완료 점수 배치 증가
     */
    fun incrementOrderScoreBatch(events: List<OrderScoreEvent>, consumerGroup: String) {
        processOrderScoreBatch(events, consumerGroup, isIncrement = true)
    }

    /**
     * 주문 취소 점수 배치 감소
     */
    fun decrementOrderScoreBatch(events: List<OrderScoreEvent>, consumerGroup: String) {
        processOrderScoreBatch(events, consumerGroup, isIncrement = false)
    }

    /**
     * 주문 점수 배치 처리 공통 로직
     */
    private fun processOrderScoreBatch(
        events: List<OrderScoreEvent>,
        consumerGroup: String,
        isIncrement: Boolean,
    ) {
        if (events.isEmpty()) {
            return
        }

        val scoresByDate = mutableMapOf<String, MutableMap<Long, Double>>()
        val processedEventKeys = mutableSetOf<String>()

        events.forEach { event ->
            val productScores = scoresByDate.getOrPut(event.dateKey) { mutableMapOf() }

            event.items.forEach { item ->
                val aggregateId = "${item.productId}:${event.dateKey}"

                // 배치 내 중복 체크
                val eventKey = "${event.eventId}:$aggregateId"
                if (processedEventKeys.contains(eventKey)) {
                    log.debug("배치 내 중복 랭킹 주문 이벤트: eventId={}, aggregateId={}", event.eventId, aggregateId)
                    return@forEach
                }

                val result = eventService.checkAndPrepareForProcessing(
                    eventId = event.eventId,
                    eventType = event.eventType,
                    eventTimestamp = event.eventTimestamp,
                    consumerGroup = consumerGroup,
                    aggregateId = aggregateId,
                )

                if (result == SHOULD_PROCESS) {
                    val score = RankingScoreCalculator.calculateOrderScore(item.price, item.quantity)
                    productScores[item.productId] = (productScores[item.productId] ?: 0.0) + score

                    processedEventKeys.add(eventKey)

                    // 즉시 처리 완료 기록 (배치 내 중복 방지)
                    eventService.recordProcessingComplete(
                        event.eventId,
                        event.eventType,
                        event.eventTimestamp,
                        consumerGroup,
                        aggregateId,
                    )
                }
            }
        }

        // Redis 작업 수행
        val actionName = if (isIncrement) "증가" else "감소"
        val eventTypeName = if (isIncrement) "주문 완료" else "주문 취소"
        scoresByDate.forEach { (dateKey, productScores) ->
            if (productScores.isNotEmpty()) {
                if (isIncrement) {
                    rankingRepository.batchIncrementScores(dateKey, productScores)
                } else {
                    rankingRepository.batchDecrementScores(dateKey, productScores)
                }
                log.info("랭킹 {} 점수 배치 {}: dateKey={}, {} 건", eventTypeName, actionName, dateKey, productScores.size)
            }
        }
    }

    /**
     * 당일 랭킹 점수의 일부를 익일 키에 이월하여 사전 생성합니다.
     */
    fun carryOverDailyScores() {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val sourceDateKey = today.format(DATE_KEY_FORMATTER)
        val targetDateKey = tomorrow.format(DATE_KEY_FORMATTER)

        rankingRepository.carryOverScores(sourceDateKey, targetDateKey, CARRY_OVER_WEIGHT)
        log.info("랭킹 점수 이월 수행: sourceDateKey={}, targetDateKey={}", sourceDateKey, targetDateKey)
    }
}
