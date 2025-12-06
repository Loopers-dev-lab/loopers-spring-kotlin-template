package com.loopers.infrastructure.analytics.client

import com.loopers.domain.event.UserActionEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 분석 시스템 클라이언트 구현체
 * 외부 시스템이 완성되기 전까지 로깅으로 대체
 * TODO: 실제 분석 시스템에 전송
 */
@Component
class AnalyticsClientImpl : AnalyticsClient {
    private val logger = LoggerFactory.getLogger(AnalyticsClientImpl::class.java)

    override fun sendUserAction(event: UserActionEvent) {
        logger.debug("분석 시스템 전송 (Mock): ${event.actionType} - userId=${event.userId}")
    }
}
