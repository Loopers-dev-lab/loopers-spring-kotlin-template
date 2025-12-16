package com.loopers.domain.audit

import com.loopers.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("AuditLogService 테스트")
class AuditLogServiceTest : IntegrationTest() {

    @Autowired
    private lateinit var auditLogService: AuditLogService

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    companion object {
        private const val EVENT_TYPE = "LikeCountChanged"
        private const val TOPIC_NAME = "product-like-events"
    }

    @Nested
    @DisplayName("saveAuditLog 메서드")
    inner class SaveAuditLogTest {

        @Test
        @DisplayName("새로운 감사 로그를 저장하면 true를 반환한다")
        fun `returns true when saving new audit log`() {
            // given
            val eventId = "event-001"
            val aggregateId = "product-1"
            val rawPayload = """{"productId":1,"action":"LIKED"}"""

            // when
            val result = auditLogService.saveAuditLog(
                eventId = eventId,
                eventType = EVENT_TYPE,
                topicName = TOPIC_NAME,
                aggregateId = aggregateId,
                rawPayload = rawPayload,
            )

            // then
            assertThat(result).isTrue()
            assertThat(auditLogRepository.existsByEventId(eventId)).isTrue()
        }

        @Test
        @DisplayName("중복된 이벤트 ID로 저장하면 false를 반환한다")
        fun `returns false when saving duplicate event id`() {
            // given
            val eventId = "event-002"
            val aggregateId = "product-2"
            val rawPayload = """{"productId":2,"action":"LIKED"}"""

            // 첫 번째 저장
            auditLogService.saveAuditLog(
                eventId = eventId,
                eventType = EVENT_TYPE,
                topicName = TOPIC_NAME,
                aggregateId = aggregateId,
                rawPayload = rawPayload,
            )

            // when - 동일한 eventId로 다시 저장 시도
            val result = auditLogService.saveAuditLog(
                eventId = eventId,
                eventType = EVENT_TYPE,
                topicName = TOPIC_NAME,
                aggregateId = aggregateId,
                rawPayload = rawPayload,
            )

            // then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("동일한 이벤트 ID로 여러 번 저장해도 한 번만 저장된다")
        fun `saves only once for duplicate event ids`() {
            // given
            val eventId = "event-003"
            val aggregateId = "product-3"
            val rawPayload = """{"productId":3,"action":"LIKED"}"""

            // when - 3번 저장 시도
            val result1 = auditLogService.saveAuditLog(eventId, EVENT_TYPE, TOPIC_NAME, aggregateId, rawPayload)
            val result2 = auditLogService.saveAuditLog(eventId, EVENT_TYPE, TOPIC_NAME, aggregateId, rawPayload)
            val result3 = auditLogService.saveAuditLog(eventId, EVENT_TYPE, TOPIC_NAME, aggregateId, rawPayload)

            // then
            assertThat(result1).isTrue()
            assertThat(result2).isFalse()
            assertThat(result3).isFalse()
        }

        @Test
        @DisplayName("서로 다른 이벤트 ID는 각각 저장된다")
        fun `saves each different event id`() {
            // given
            val eventId1 = "event-004"
            val eventId2 = "event-005"
            val eventId3 = "event-006"
            val aggregateId = "product-4"
            val rawPayload = """{"productId":4,"action":"LIKED"}"""

            // when
            val result1 = auditLogService.saveAuditLog(eventId1, EVENT_TYPE, TOPIC_NAME, aggregateId, rawPayload)
            val result2 = auditLogService.saveAuditLog(eventId2, EVENT_TYPE, TOPIC_NAME, aggregateId, rawPayload)
            val result3 = auditLogService.saveAuditLog(eventId3, EVENT_TYPE, TOPIC_NAME, aggregateId, rawPayload)

            // then
            assertThat(result1).isTrue()
            assertThat(result2).isTrue()
            assertThat(result3).isTrue()
            assertThat(auditLogRepository.existsByEventId(eventId1)).isTrue()
            assertThat(auditLogRepository.existsByEventId(eventId2)).isTrue()
            assertThat(auditLogRepository.existsByEventId(eventId3)).isTrue()
        }
    }
}
