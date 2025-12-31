package com.loopers.domain.metrics

import com.loopers.domain.event.EventProcessingResult.SHOULD_PROCESS
import com.loopers.domain.event.EventService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

/**
 * 상품 메트릭스 도메인 서비스
 *
 * 좋아요, 조회수, 판매 수량 등 상품 메트릭스 업데이트 로직을 담당
 */
@Service
@Transactional
class ProductMetricsService(
    private val productMetricsRepository: ProductMetricsRepository,
    private val eventService: EventService,
) {
    private val log = LoggerFactory.getLogger(ProductMetricsService::class.java)

    /**
     * 좋아요 수 증가
     */
    fun increaseLikeCount(
        productId: Long,
        eventId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
        consumerGroup: String,
    ) {
        // 1. 날짜 추출
        val metricDate = eventTimestamp.toLocalDate()

        // 2. aggregateId에 날짜 포함
        val aggregateId = "${productId}_$metricDate"

        // 이벤트 처리 가능 여부 체크
        val result = eventService.checkAndPrepareForProcessing(
            eventId = eventId,
            eventType = eventType,
            eventTimestamp = eventTimestamp,
            consumerGroup = consumerGroup,
            aggregateId = aggregateId,
        )

        if (result != SHOULD_PROCESS) {
            return
        }

        // 메트릭스 업데이트 (날짜별 조회)
        val metrics = productMetricsRepository.findByProductIdAndMetricDateWithLock(productId, metricDate)
            ?: ProductMetrics.create(productId, metricDate)

        metrics.increaseLikeCount()
        productMetricsRepository.save(metrics)

        // 이벤트 처리 완료 기록
        eventService.recordProcessingComplete(
            eventId = eventId,
            eventType = eventType,
            eventTimestamp = eventTimestamp,
            consumerGroup = consumerGroup,
            aggregateId = aggregateId,
        )

        log.info("좋아요 수 증가: productId={}, metricDate={}, likeCount={}", productId, metricDate, metrics.likeCount)
    }

    /**
     * 좋아요 수 감소
     */
    fun decreaseLikeCount(
        productId: Long,
        eventId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
        consumerGroup: String,
    ) {
        // 1. 날짜 추출
        val metricDate = eventTimestamp.toLocalDate()

        // 2. aggregateId에 날짜 포함
        val aggregateId = "${productId}_$metricDate"

        val result = eventService.checkAndPrepareForProcessing(
            eventId = eventId,
            eventType = eventType,
            eventTimestamp = eventTimestamp,
            consumerGroup = consumerGroup,
            aggregateId = aggregateId,
        )

        if (result != SHOULD_PROCESS) {
            return
        }

        val metrics = productMetricsRepository.findByProductIdAndMetricDateWithLock(productId, metricDate)
            ?: ProductMetrics.create(productId, metricDate)

        metrics.decreaseLikeCount()
        productMetricsRepository.save(metrics)

        eventService.recordProcessingComplete(
            eventId = eventId,
            eventType = eventType,
            eventTimestamp = eventTimestamp,
            consumerGroup = consumerGroup,
            aggregateId = aggregateId,
        )

        log.info("좋아요 수 감소: productId={}, metricDate={}, likeCount={}", productId, metricDate, metrics.likeCount)
    }

    /**
     * 조회 수 증가
     *
     * 조회수는 단순 증가만 있으므로 순서 보장이 필요 없고, 멱등성만 체크한다.
     */
    fun increaseViewCount(
        productId: Long,
        eventId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
    ) {
        // 1. 날짜 추출
        val metricDate = eventTimestamp.toLocalDate()

        // 2. aggregateId에 날짜 포함
        val aggregateId = "${productId}_$metricDate"

        // 멱등성 체크만 수행 (순서 보장 불필요) - eventId + aggregateId 조합
        if (eventService.isAlreadyHandled(eventId, aggregateId)) {
            log.debug("이미 처리된 조회수 이벤트입니다: eventId={}, productId={}, metricDate={}", eventId, productId, metricDate)
            return
        }

        val metrics = productMetricsRepository.findByProductIdAndMetricDateWithLock(productId, metricDate)
            ?: ProductMetrics.create(productId, metricDate)

        metrics.increaseViewCount()
        productMetricsRepository.save(metrics)

        // 이벤트 처리 완료 기록 (멱등성 용) - eventId + aggregateId 조합
        eventService.markAsHandled(eventId, aggregateId, eventType, eventTimestamp)

        log.debug("조회 수 증가: productId={}, metricDate={}, viewCount={}", productId, metricDate, metrics.viewCount)
    }

    /**
     * 판매 수량 증가 (주문 완료)
     */
    fun increaseSoldCount(
        productId: Long,
        quantity: Int,
        eventId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
        consumerGroup: String,
    ) {
        // 1. 날짜 추출
        val metricDate = eventTimestamp.toLocalDate()

        // 2. aggregateId에 날짜 포함
        val aggregateId = "${productId}_$metricDate"

        val result = eventService.checkAndPrepareForProcessing(
            eventId = eventId,
            eventType = eventType,
            eventTimestamp = eventTimestamp,
            consumerGroup = consumerGroup,
            aggregateId = aggregateId,
        )

        if (result != SHOULD_PROCESS) {
            return
        }

        val metrics = productMetricsRepository.findByProductIdAndMetricDateWithLock(productId, metricDate)
            ?: ProductMetrics.create(productId, metricDate)

        metrics.increaseSoldCount(quantity)
        productMetricsRepository.save(metrics)

        eventService.recordProcessingComplete(
            eventId = eventId,
            eventType = eventType,
            eventTimestamp = eventTimestamp,
            consumerGroup = consumerGroup,
            aggregateId = aggregateId,
        )

        log.info(
            "판매 수량 증가: productId={}, metricDate={}, quantity={}, soldCount={}",
            productId,
            metricDate,
            quantity,
            metrics.soldCount,
        )
    }

    /**
     * 판매 수량 감소 (주문 취소)
     */
    fun decreaseSoldCount(
        productId: Long,
        quantity: Int,
        eventId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
        consumerGroup: String,
    ) {
        // 1. 날짜 추출
        val metricDate = eventTimestamp.toLocalDate()

        // 2. aggregateId에 날짜 포함
        val aggregateId = "${productId}_$metricDate"

        val result = eventService.checkAndPrepareForProcessing(
            eventId = eventId,
            eventType = eventType,
            eventTimestamp = eventTimestamp,
            consumerGroup = consumerGroup,
            aggregateId = aggregateId,
        )

        if (result != SHOULD_PROCESS) {
            return
        }

        val metrics = productMetricsRepository.findByProductIdAndMetricDateWithLock(productId, metricDate)
            ?: ProductMetrics.create(productId, metricDate)

        metrics.decreaseSoldCount(quantity)
        productMetricsRepository.save(metrics)

        eventService.recordProcessingComplete(
            eventId = eventId,
            eventType = eventType,
            eventTimestamp = eventTimestamp,
            consumerGroup = consumerGroup,
            aggregateId = aggregateId,
        )

        log.info(
            "판매 수량 감소: productId={}, metricDate={}, quantity={}, soldCount={}",
            productId,
            metricDate,
            quantity,
            metrics.soldCount,
        )
    }
}
