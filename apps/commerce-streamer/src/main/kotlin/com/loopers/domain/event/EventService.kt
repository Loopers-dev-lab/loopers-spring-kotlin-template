package com.loopers.domain.event

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

/**
 * 이벤트 처리 관련 도메인 서비스
 *
 * 멱등성 체크, 순서 보장, 이벤트 처리 기록 등의 로직을 담당
 */
@Service
class EventService(
    private val eventHandledRepository: EventHandledRepository,
    private val eventProcessingTimestampRepository: EventProcessingTimestampRepository,
) {
    private val log = LoggerFactory.getLogger(EventService::class.java)

    /**
     * 이벤트 멱등성 체크 (eventId + aggregateId)
     *
     * @return true: 이미 처리된 이벤트, false: 처리되지 않은 이벤트
     */
    fun isAlreadyHandled(eventId: String, aggregateId: String): Boolean {
        return eventHandledRepository.existsByEventIdAndAggregateId(eventId, aggregateId)
    }

    /**
     * 이벤트 순서 체크 (과거 이벤트 여부)
     *
     * @return true: 처리해야 할 이벤트, false: 과거 이벤트 (무시해야 함)
     */
    fun shouldProcess(
        consumerGroup: String,
        aggregateId: String,
        eventTimestamp: ZonedDateTime,
    ): Boolean {
        val lastProcessed = eventProcessingTimestampRepository.findByConsumerGroupAndAggregateId(
            consumerGroup,
            aggregateId,
        )
        return lastProcessed == null || lastProcessed.shouldProcess(eventTimestamp)
    }

    /**
     * 이벤트 처리 완료 기록 (멱등성 용 - eventId + aggregateId)
     */
    fun markAsHandled(
        eventId: String,
        aggregateId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
    ) {
        eventHandledRepository.save(EventHandled.create(eventId, aggregateId, eventType, eventTimestamp))
    }

    /**
     * 이벤트 처리 타임스탬프 업데이트 (순서 보장 용)
     */
    fun updateProcessingTimestamp(
        consumerGroup: String,
        aggregateId: String,
        eventTimestamp: ZonedDateTime,
    ) {
        val existing = eventProcessingTimestampRepository.findByConsumerGroupAndAggregateId(
            consumerGroup,
            aggregateId,
        )

        if (existing == null) {
            eventProcessingTimestampRepository.save(
                EventProcessingTimestamp.create(consumerGroup, aggregateId, eventTimestamp),
            )
        } else {
            existing.updateLastProcessedAt(eventTimestamp)
            eventProcessingTimestampRepository.save(existing)
        }
    }

    /**
     * 이벤트 처리 가능 여부를 확인하고, 과거 이벤트인 경우 처리 완료로 마킹
     *
     * @return EventProcessingResult - 처리 결과
     */
    fun checkAndPrepareForProcessing(
        eventId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
        consumerGroup: String,
        aggregateId: String,
    ): EventProcessingResult {
        // 1. 멱등성 체크 (eventId + aggregateId 조합)
        if (isAlreadyHandled(eventId, aggregateId)) {
            log.debug("이미 처리된 이벤트입니다: eventId={}, aggregateId={}", eventId, aggregateId)
            return EventProcessingResult.ALREADY_HANDLED
        }

        // 2. 순서 체크
        if (!shouldProcess(consumerGroup, aggregateId, eventTimestamp)) {
            log.warn(
                "오래된 이벤트 무시: aggregateId={}, eventTimestamp={}",
                aggregateId,
                eventTimestamp,
            )
            // 무시한 이벤트도 처리 완료로 마킹 (재처리 방지)
            markAsHandled(eventId, aggregateId, eventType, eventTimestamp)
            return EventProcessingResult.OUTDATED
        }

        return EventProcessingResult.SHOULD_PROCESS
    }

    /**
     * 이벤트 처리 완료 후 기록
     */
    fun recordProcessingComplete(
        eventId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
        consumerGroup: String,
        aggregateId: String,
    ) {
        // 마지막 처리 시간 업데이트
        updateProcessingTimestamp(consumerGroup, aggregateId, eventTimestamp)

        // 이벤트 처리 기록 (eventId + aggregateId 조합)
        markAsHandled(eventId, aggregateId, eventType, eventTimestamp)
    }

    enum class EventProcessingResult {
        SHOULD_PROCESS,
        ALREADY_HANDLED,
        OUTDATED,
    }
}
