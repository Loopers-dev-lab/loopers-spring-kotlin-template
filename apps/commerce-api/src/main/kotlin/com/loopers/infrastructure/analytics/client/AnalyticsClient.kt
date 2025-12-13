package com.loopers.infrastructure.analytics.client

import com.loopers.domain.event.UserActionEvent

/**
 * 분석 시스템 연동 클라이언트 인터페이스
 * 사용자 행동 데이터를 외부 분석 시스템으로 전송
 */
interface AnalyticsClient {
    /**
     * 사용자 행동 이벤트를 분석 시스템으로 전송
     */
    fun sendUserAction(event: UserActionEvent)
}
