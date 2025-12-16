package com.loopers.domain.event

import com.loopers.IntegrationTest
import com.loopers.domain.event.EventService.EventProcessingResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.ZonedDateTime

@DisplayName("EventService 테스트")
class EventServiceTest : IntegrationTest() {

    @Autowired
    private lateinit var eventService: EventService

    @Autowired
    private lateinit var eventHandledRepository: EventHandledRepository

    @Autowired
    private lateinit var eventProcessingTimestampRepository: EventProcessingTimestampRepository

    companion object {
        private const val CONSUMER_GROUP = "test-consumer-group"
        private const val EVENT_TYPE = "TestEvent"
    }

    @Nested
    @DisplayName("isAlreadyHandled 메서드")
    inner class IsAlreadyHandledTest {

        @Test
        @DisplayName("처리되지 않은 이벤트는 false를 반환한다")
        fun `returns false for unhandled event`() {
            // given
            val eventId = "new-event-id"

            // when
            val result = eventService.isAlreadyHandled(eventId)

            // then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("이미 처리된 이벤트는 true를 반환한다")
        fun `returns true for already handled event`() {
            // given
            val eventId = "handled-event-id"
            eventService.markAsHandled(eventId, EVENT_TYPE, ZonedDateTime.now())

            // when
            val result = eventService.isAlreadyHandled(eventId)

            // then
            assertThat(result).isTrue()
        }
    }

    @Nested
    @DisplayName("shouldProcess 메서드")
    inner class ShouldProcessTest {

        @Test
        @DisplayName("첫 번째 이벤트는 처리해야 한다")
        fun `returns true for first event`() {
            // given
            val aggregateId = "product-1"
            val eventTimestamp = ZonedDateTime.now()

            // when
            val result = eventService.shouldProcess(CONSUMER_GROUP, aggregateId, eventTimestamp)

            // then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("최신 이벤트는 처리해야 한다")
        fun `returns true for newer event`() {
            // given
            val aggregateId = "product-2"
            val oldTimestamp = ZonedDateTime.now()
            val newTimestamp = oldTimestamp.plusMinutes(10)

            eventService.updateProcessingTimestamp(CONSUMER_GROUP, aggregateId, oldTimestamp)

            // when
            val result = eventService.shouldProcess(CONSUMER_GROUP, aggregateId, newTimestamp)

            // then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("과거 이벤트는 무시해야 한다")
        fun `returns false for older event`() {
            // given
            val aggregateId = "product-3"
            val oldTimestamp = ZonedDateTime.now()
            val newTimestamp = oldTimestamp.plusMinutes(10)

            eventService.updateProcessingTimestamp(CONSUMER_GROUP, aggregateId, newTimestamp)

            // when
            val result = eventService.shouldProcess(CONSUMER_GROUP, aggregateId, oldTimestamp)

            // then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("동일한 시간의 이벤트는 무시해야 한다")
        fun `returns false for same timestamp event`() {
            // given
            val aggregateId = "product-4"
            val timestamp = ZonedDateTime.now()

            eventService.updateProcessingTimestamp(CONSUMER_GROUP, aggregateId, timestamp)

            // when
            val result = eventService.shouldProcess(CONSUMER_GROUP, aggregateId, timestamp)

            // then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("서로 다른 애그리게이트는 독립적으로 관리된다")
        fun `different aggregates are managed independently`() {
            // given
            val aggregateId1 = "product-5"
            val aggregateId2 = "product-6"
            val timestamp = ZonedDateTime.now()

            eventService.updateProcessingTimestamp(CONSUMER_GROUP, aggregateId1, timestamp.plusMinutes(10))

            // when - aggregateId2는 아직 처리 기록이 없음
            val result = eventService.shouldProcess(CONSUMER_GROUP, aggregateId2, timestamp)

            // then
            assertThat(result).isTrue()
        }
    }

    @Nested
    @DisplayName("markAsHandled 메서드")
    inner class MarkAsHandledTest {

        @Test
        @DisplayName("이벤트를 처리 완료로 마킹한다")
        fun `marks event as handled`() {
            // given
            val eventId = "event-to-mark"
            val eventTimestamp = ZonedDateTime.now()

            // when
            eventService.markAsHandled(eventId, EVENT_TYPE, eventTimestamp)

            // then
            assertThat(eventHandledRepository.existsById(eventId)).isTrue()
        }
    }

    @Nested
    @DisplayName("updateProcessingTimestamp 메서드")
    inner class UpdateProcessingTimestampTest {

        @Test
        @DisplayName("새로운 타임스탬프를 생성한다")
        fun `creates new timestamp`() {
            // given
            val aggregateId = "product-10"
            val timestamp = ZonedDateTime.now()

            // when
            eventService.updateProcessingTimestamp(CONSUMER_GROUP, aggregateId, timestamp)

            // then
            val saved = eventProcessingTimestampRepository.findByConsumerGroupAndAggregateId(CONSUMER_GROUP, aggregateId)
            assertThat(saved).isNotNull
            assertThat(saved!!.lastProcessedAt).isEqualTo(timestamp)
        }

        @Test
        @DisplayName("기존 타임스탬프를 업데이트한다")
        fun `updates existing timestamp`() {
            // given
            val aggregateId = "product-11"
            val oldTimestamp = ZonedDateTime.now()
            val newTimestamp = oldTimestamp.plusMinutes(10)

            eventService.updateProcessingTimestamp(CONSUMER_GROUP, aggregateId, oldTimestamp)

            // when
            eventService.updateProcessingTimestamp(CONSUMER_GROUP, aggregateId, newTimestamp)

            // then
            val saved = eventProcessingTimestampRepository.findByConsumerGroupAndAggregateId(CONSUMER_GROUP, aggregateId)
            assertThat(saved).isNotNull
            assertThat(saved!!.lastProcessedAt).isEqualTo(newTimestamp)
        }
    }

    @Nested
    @DisplayName("checkAndPrepareForProcessing 메서드")
    inner class CheckAndPrepareForProcessingTest {

        @Test
        @DisplayName("새로운 이벤트는 SHOULD_PROCESS를 반환한다")
        fun `returns SHOULD_PROCESS for new event`() {
            // given
            val eventId = "new-event-1"
            val aggregateId = "product-20"
            val timestamp = ZonedDateTime.now()

            // when
            val result = eventService.checkAndPrepareForProcessing(
                eventId = eventId,
                eventType = EVENT_TYPE,
                eventTimestamp = timestamp,
                consumerGroup = CONSUMER_GROUP,
                aggregateId = aggregateId,
            )

            // then
            assertThat(result).isEqualTo(EventProcessingResult.SHOULD_PROCESS)
        }

        @Test
        @DisplayName("이미 처리된 이벤트는 ALREADY_HANDLED를 반환한다")
        fun `returns ALREADY_HANDLED for duplicate event`() {
            // given
            val eventId = "duplicate-event-1"
            val aggregateId = "product-21"
            val timestamp = ZonedDateTime.now()

            eventService.markAsHandled(eventId, EVENT_TYPE, timestamp)

            // when
            val result = eventService.checkAndPrepareForProcessing(
                eventId = eventId,
                eventType = EVENT_TYPE,
                eventTimestamp = timestamp,
                consumerGroup = CONSUMER_GROUP,
                aggregateId = aggregateId,
            )

            // then
            assertThat(result).isEqualTo(EventProcessingResult.ALREADY_HANDLED)
        }

        @Test
        @DisplayName("과거 이벤트는 OUTDATED를 반환하고 처리 완료로 마킹된다")
        fun `returns OUTDATED for old event and marks as handled`() {
            // given
            val eventId = "old-event-1"
            val aggregateId = "product-22"
            val oldTimestamp = ZonedDateTime.now()
            val newTimestamp = oldTimestamp.plusMinutes(10)

            // 먼저 최신 이벤트의 타임스탬프를 기록
            eventService.updateProcessingTimestamp(CONSUMER_GROUP, aggregateId, newTimestamp)

            // when - 과거 이벤트 처리 시도
            val result = eventService.checkAndPrepareForProcessing(
                eventId = eventId,
                eventType = EVENT_TYPE,
                eventTimestamp = oldTimestamp,
                consumerGroup = CONSUMER_GROUP,
                aggregateId = aggregateId,
            )

            // then
            assertThat(result).isEqualTo(EventProcessingResult.OUTDATED)
            // 과거 이벤트도 처리 완료로 마킹되어야 함 (재처리 방지)
            assertThat(eventHandledRepository.existsById(eventId)).isTrue()
        }
    }

    @Nested
    @DisplayName("recordProcessingComplete 메서드")
    inner class RecordProcessingCompleteTest {

        @Test
        @DisplayName("이벤트 처리를 완료 기록한다")
        fun `records processing complete`() {
            // given
            val eventId = "complete-event-1"
            val aggregateId = "product-30"
            val timestamp = ZonedDateTime.now()

            // when
            eventService.recordProcessingComplete(
                eventId = eventId,
                eventType = EVENT_TYPE,
                eventTimestamp = timestamp,
                consumerGroup = CONSUMER_GROUP,
                aggregateId = aggregateId,
            )

            // then
            assertThat(eventHandledRepository.existsById(eventId)).isTrue()

            val processingTimestamp = eventProcessingTimestampRepository.findByConsumerGroupAndAggregateId(
                CONSUMER_GROUP,
                aggregateId,
            )
            assertThat(processingTimestamp).isNotNull
            assertThat(processingTimestamp!!.lastProcessedAt).isEqualTo(timestamp)
        }
    }
}
