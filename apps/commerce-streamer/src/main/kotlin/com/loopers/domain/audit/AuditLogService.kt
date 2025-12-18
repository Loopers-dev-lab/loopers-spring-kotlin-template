package com.loopers.domain.audit

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 감사 로그 도메인 서비스
 *
 * 감사 로그 저장 및 중복 체크 로직을 담당
 */
@Service
@Transactional
class AuditLogService(
    private val auditLogRepository: AuditLogRepository,
) {
    private val log = LoggerFactory.getLogger(AuditLogService::class.java)

    /**
     * 감사 로그 저장
     *
     * @return true: 저장 성공, false: 중복으로 인한 스킵
     */
    fun saveAuditLog(
        eventId: String,
        eventType: String,
        topicName: String,
        aggregateId: String,
        rawPayload: String,
    ): Boolean {
        // 중복 체크
        if (auditLogRepository.existsByEventId(eventId)) {
            log.debug("이미 기록된 감사 로그: eventId={}", eventId)
            return false
        }

        // 감사 로그 저장
        return try {
            val auditLog = AuditLog.create(
                eventId = eventId,
                eventType = eventType,
                topicName = topicName,
                aggregateId = aggregateId,
                rawPayload = rawPayload,
            )

            auditLogRepository.save(auditLog)
            log.info("감사 로그 저장 완료: eventId={}, eventType={}, topic={}", eventId, eventType, topicName)
            return true
        } catch (e: DataIntegrityViolationException) {
            log.debug("중복 eventId로 인한 저장 실패: eventId={}, {}", eventId, e)
            false
        }
    }
}
