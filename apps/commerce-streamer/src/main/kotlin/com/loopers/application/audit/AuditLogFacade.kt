package com.loopers.application.audit

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.audit.AuditLogService
import com.loopers.support.util.EventIdExtractor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AuditLogFacade(
    private val auditLogService: AuditLogService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(AuditLogFacade::class.java)

    /**
     * 감사 로그 배치 처리
     */
    fun processAuditBatch(records: List<ConsumerRecord<Any, Any>>) {
        var processed = 0
        var skipped = 0
        var failed = 0

        // 토픽별 통계
        val topicStats = mutableMapOf<String, Triple<Int, Int, Int>>()

        for (record in records) {
            val topicName = record.topic()

            try {
                val rawPayload = when (val value = record.value()) {
                    is String -> value
                    is ByteArray -> String(value, Charsets.UTF_8)
                    else -> objectMapper.writeValueAsString(value)
                }

                // EventId 추출
                val eventId = EventIdExtractor.extract(record)
                if (eventId.isBlank()) {
                    log.warn(
                        "eventId를 추출할 수 없음: topic={}, partition={}, offset={}",
                        topicName,
                        record.partition(),
                        record.offset(),
                    )
                    failed++
                    updateTopicStats(topicStats, topicName, 0, 0, 1)
                    continue
                }

                // 토픽에서 eventType 추출
                val eventType = extractEventType(topicName)

                // 원본 JSON에서 aggregateId 추출
                val aggregateId = extractAggregateId(rawPayload, topicName)
                if (aggregateId == null) {
                    log.warn(
                        "aggregateId를 추출할 수 없음: topic={}, partition={}, offset={}",
                        topicName,
                        record.partition(),
                        record.offset(),
                    )
                    failed++
                    updateTopicStats(topicStats, topicName, 0, 0, 1)
                    continue
                }

                // 감사 로그 저장
                val saved = auditLogService.saveAuditLog(
                    eventId = eventId,
                    eventType = eventType,
                    topicName = topicName,
                    aggregateId = aggregateId,
                    rawPayload = rawPayload,
                )

                if (saved) {
                    processed++
                    updateTopicStats(topicStats, topicName, 1, 0, 0)
                } else {
                    skipped++
                    updateTopicStats(topicStats, topicName, 0, 1, 0)
                }
            } catch (e: Exception) {
                log.error(
                    "감사 로그 처리 실패: topic={}, partition={}, offset={}",
                    topicName,
                    record.partition(),
                    record.offset(),
                    e,
                )
                failed++
                updateTopicStats(topicStats, topicName, 0, 0, 1)
            }
        }

        // 전체 통계 로그
        log.info(
            "감사 로그 배치 처리 완료: total={}, processed={}, skipped={}, failed={}",
            records.size,
            processed,
            skipped,
            failed,
        )

        // 토픽별 통계 로그
        topicStats.forEach { (topic, stats) ->
            log.info(
                "  - {}: processed={}, skipped={}, failed={}",
                topic,
                stats.first,
                stats.second,
                stats.third,
            )
        }
    }

    /**
     * 토픽 이름에서 eventType 추출
     */
    private fun extractEventType(topicName: String): String {
        return when (topicName) {
            "product-like-events" -> "LIKE_COUNT_CHANGED"
            "product-view-events" -> "VIEW_COUNT_INCREASED"
            "order-completed-events" -> "ORDER_COMPLETED"
            "order-canceled-events" -> "ORDER_CANCELED"
            "product-sold-out-events" -> "PRODUCT_SOLD_OUT"
            else -> "UNKNOWN"
        }
    }

    /**
     * 원본 JSON에서 aggregateId 추출
     * 각 이벤트 타입별로 주요 ID 필드 추출
     */
    private fun extractAggregateId(rawPayload: String, topicName: String): String? {
        return try {
            val jsonNode = objectMapper.readTree(rawPayload)
            when (topicName) {
                "product-like-events",
                "product-view-events",
                "product-sold-out-events",
                    -> {
                    jsonNode.get("productId")?.asText()
                }

                "order-completed-events",
                "order-canceled-events",
                    -> {
                    jsonNode.get("orderId")?.asText()
                }

                else -> null
            }
        } catch (e: Exception) {
            log.error("aggregateId 추출 실패: topic={}", topicName, e)
            null
        }
    }

    private fun updateTopicStats(
        topicStats: MutableMap<String, Triple<Int, Int, Int>>,
        topicName: String,
        processedDelta: Int,
        skippedDelta: Int,
        failedDelta: Int,
    ) {
        val current = topicStats.getOrDefault(topicName, Triple(0, 0, 0))
        topicStats[topicName] = Triple(
            current.first + processedDelta,
            current.second + skippedDelta,
            current.third + failedDelta,
        )
    }
}
