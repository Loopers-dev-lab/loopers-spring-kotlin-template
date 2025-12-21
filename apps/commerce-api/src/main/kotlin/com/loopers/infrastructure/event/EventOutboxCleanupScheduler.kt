package com.loopers.infrastructure.event

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * EventOutbox 정리 스케줄러
 * - processed=true인 오래된 이벤트 자동 삭제
 * - 테이블 비대화 방지
 */
@Component
class EventOutboxCleanupScheduler(
    private val eventOutboxRepository: EventOutboxJpaRepository
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(javaClass)

    companion object {
        const val RETENTION_DAYS = 7L // 7일 보관
    }

    /**
     * 매일 새벽 2시에 실행
     * - 7일 이상 된 processed=true 이벤트 삭제
     */
//    @Scheduled(cron = "0 0 2 * * *")
}
