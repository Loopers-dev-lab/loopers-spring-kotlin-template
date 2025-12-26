package com.loopers.application.facade

import com.loopers.domain.event.DomainEvent
import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.like.ProductLikedEvent
import com.loopers.domain.event.like.ProductUnlikedEvent
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.product.event.ProductViewedEvent
import com.loopers.domain.ranking.RankingScoreCalculator
import com.loopers.domain.ranking.RankingService  // modules/redis
import com.loopers.infrastructure.event.EventHandledRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 랭킹 이벤트 처리 Facade (Application Layer)
 *
 * 역할:
 * 1. 멱등성 보장 (event_handled 테이블 활용)
 * 2. 날짜별/상품별 점수 그룹화
 * 3. Redis ZSET 업데이트 오케스트레이션
 *
 * 왜 멱등성이 필요한가?
 * - Kafka Consumer 재시작 시 이벤트 재처리 가능
 * - At-Least-Once 전달 보장으로 중복 이벤트 발생 가능
 * - 동일 이벤트로 점수가 중복 증가하면 랭킹 왜곡
 */
@Service
class RankingEventFacade(
    private val rankingService: RankingService,
    private val rankingScoreCalculator: RankingScoreCalculator,
    private val eventHandledRepository: EventHandledRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 배치 이벤트 처리 (멱등성 보장 + 그룹화 최적화)
     *
     * 처리 흐름:
     * 1. 멱등성 체크 (event_handled 테이블)
     * 2. 날짜별 그룹화
     * 3. 상품별 점수 합산
     * 4. Redis ZSET 배치 업데이트
     * 5. 처리 완료 기록
     *
     * @param events 이벤트 리스트
     */
    @Transactional
    fun handleBatchEvents(events: List<DomainEvent>) {
        if (events.isEmpty()) {
            return
        }

        logger.info("랭킹 배치 이벤트 처리 시작: ${events.size}개")

        // 1. 멱등성 일괄 체크
        val eventIds = events.map { it.eventId }
        val handledEventIds = eventHandledRepository.findAllById(eventIds)
            .map { it.eventId }
            .toSet()

        // 2. 처리할 이벤트만 필터링
        val eventsToProcess = events.filter { it.eventId !in handledEventIds }

        if (eventsToProcess.isEmpty()) {
            logger.warn("모든 이벤트가 이미 처리됨 (${events.size}개)")
            return
        }

        // 3. 날짜별로 그룹화
        val eventsByDate = eventsToProcess.groupBy { event ->
            LocalDate.ofInstant(event.occurredAt, ZoneId.systemDefault())
        }

        // 4. 날짜별로 상품 점수 합산 및 Redis 업데이트
        eventsByDate.forEach { (date, dateEvents) ->
            processEventsForDate(date, dateEvents)
        }

        // 5. 처리 완료 기록 (배치 insert)
        markAllAsHandled(eventsToProcess)

        logger.info(
            "랭킹 배치 처리 완료: ${eventsToProcess.size}개 처리 " +
            "(중복 제외: ${events.size - eventsToProcess.size}개)"
        )
    }

    /**
     * 특정 날짜의 이벤트들을 처리
     *
     * 왜 상품별로 그룹화하는가?
     * - 동일 상품에 대한 여러 이벤트를 한 번의 Redis 연산으로 처리
     * - 예: 상품 A 조회 10회 → incrementScore(A, 1.0) 10번이 아닌 1번
     *
     * @param date 대상 날짜
     * @param events 해당 날짜의 이벤트 리스트
     */
    private fun processEventsForDate(date: LocalDate, events: List<DomainEvent>) {
        // 상품별로 점수 합산
        val scoresByProduct = mutableMapOf<Long, Double>()

        events.forEach { event ->
            when (event) {
                is ProductViewedEvent -> {
                    val score = rankingScoreCalculator.calculateScore(event)
                    scoresByProduct[event.productId] =
                        scoresByProduct.getOrDefault(event.productId, 0.0) + score
                }
                is ProductLikedEvent -> {
                    val score = rankingScoreCalculator.calculateScore(event)
                    scoresByProduct[event.productId] =
                        scoresByProduct.getOrDefault(event.productId, 0.0) + score
                }
                is ProductUnlikedEvent -> {
                    val score = rankingScoreCalculator.calculateScore(event)
                    scoresByProduct[event.productId] =
                        scoresByProduct.getOrDefault(event.productId, 0.0) + score  // 이미 음수
                }
                is OrderCreatedEvent -> {
                    // 주문은 여러 상품을 포함할 수 있음
                    event.orderItems.forEach { item ->
                        val score = rankingScoreCalculator.calculateOrderItemScore(item)
                        scoresByProduct[item.productId] =
                            scoresByProduct.getOrDefault(item.productId, 0.0) + score
                    }
                }
                else -> {
                    logger.debug("랭킹 처리 대상 아님: eventType=${event.eventType}")
                }
            }
        }

        // Redis ZSET에 배치 업데이트
        scoresByProduct.forEach { (productId, totalScore) ->
            rankingService.incrementScore(date, productId, totalScore)
        }

        logger.debug("날짜별 랭킹 처리 완료: date=$date, 상품수=${scoresByProduct.size}")
    }

    /**
     * 이벤트 처리 완료 기록 (멱등성 보장)
     *
     * @param events 처리 완료된 이벤트 리스트
     */
    private fun markAllAsHandled(events: List<DomainEvent>) {
        val handledEvents = events.map { event ->
            EventHandled(
                eventId = event.eventId,
                eventType = event.eventType,
                occurredAt = event.occurredAt,
                handledAt = Instant.now()
            )
        }
        eventHandledRepository.saveAll(handledEvents)
    }
}
