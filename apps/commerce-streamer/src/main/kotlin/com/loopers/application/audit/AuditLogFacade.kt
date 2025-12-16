package com.loopers.application.audit

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.audit.AuditLogService
import com.loopers.support.dto.UniversalEventDto
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

                // 범용 DTO로 변환
                val dto = objectMapper.readValue(rawPayload, UniversalEventDto::class.java)
                dto.topicName = topicName
                dto.rawPayload = rawPayload

                // EventId 추출 (헤더 우선, 없으면 payload에서)
                if (dto.eventId.isNullOrBlank()) {
                    dto.eventId = EventIdExtractor.extract(record)
                }

                // 유효성 검증
                if (!dto.isValid()) {
                    log.warn(
                        "유효하지 않은 이벤트: topic={}, partition={}, offset={}",
                        topicName,
                        record.partition(),
                        record.offset(),
                    )
                    failed++
                    updateTopicStats(topicStats, topicName, 0, 0, 1)
                    continue
                }

                // 감사 로그 저장
                val saved = saveAuditLog(dto)
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
     * 감사 로그 저장
     * 중복 체크 후 저장
     */
    fun saveAuditLog(dto: UniversalEventDto): Boolean {
        val eventId = dto.eventId ?: return false

        return auditLogService.saveAuditLog(
            eventId = eventId,
            eventType = dto.eventType ?: "UNKNOWN",
            topicName = dto.topicName ?: "UNKNOWN",
            aggregateId = dto.aggregateId ?: "UNKNOWN",
            rawPayload = dto.rawPayload ?: "",
        )
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
