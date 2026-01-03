package com.loopers.domain.event.handler

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.reflect.Method

/**
 * 실패한 이벤트를 로그로만 기록하는 기본 알림 구현체
 *
 * TODO: 실제 구현 시 다음을 고려:
 * - Slack Webhook으로 알림 발송
 * - 이메일 알림
 * - PagerDuty, Datadog 등 모니터링 시스템 연동
 * - 알림 레벨 설정 (CRITICAL, WARNING 등)
 * - 알림 빈도 제한 (rate limiting)
 */
@Component
class LoggingFailedEventNotifier : FailedEventNotifier {

    companion object {
        private val logger = LoggerFactory.getLogger(LoggingFailedEventNotifier::class.java)
    }

    override fun notifyFailure(method: Method, params: Array<Any>, exception: Throwable) {
        logger.error(
            "실패한 이벤트 알림 (현재는 로그만 기록): " +
                    "method=${method.name}, " +
                    "class=${method.declaringClass.simpleName}, " +
                    "params=${params.contentToString()}, " +
                    "error=${exception.message}",
        )
        // TODO: 실제 알림 발송 로직 구현
        // Example:
        // slackNotifier.send(
        //     channel = "#alerts",
        //     message = """
        //         :warning: 비동기 이벤트 처리 실패
        //         - 메서드: ${method.name}
        //         - 에러: ${exception.message}
        //         - 파라미터: ${params.contentToString()}
        //     """.trimIndent()
        // )
    }
}
