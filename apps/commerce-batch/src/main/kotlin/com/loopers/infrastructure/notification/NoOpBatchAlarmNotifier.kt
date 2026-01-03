package com.loopers.infrastructure.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 배치 실패 알림 기본 구현체 (No Operation)
 *
 * 실제 알림을 발송하지 않고 로그만 남깁니다.
 * 운영 환경 배포 시 SlackBatchAlarmNotifier, EmailBatchAlarmNotifier 등으로 교체하여 사용합니다.
 *
 * 교체 방법:
 * 1. SlackBatchAlarmNotifier 등의 구현체를 작성
 * 2. @Primary 또는 @Qualifier를 사용하여 주입할 구현체 선택
 *
 * @see BatchAlarmNotifier
 */
@Component
class NoOpBatchAlarmNotifier : BatchAlarmNotifier {
    companion object {
        private val logger = LoggerFactory.getLogger(NoOpBatchAlarmNotifier::class.java)
    }

    override fun notifyBatchFailure(jobName: String, message: String, error: Throwable) {
        logger.warn(
            "배치 실패 알림 (NoOp): jobName={}, message={}, error={}",
            jobName,
            message,
            error.message,
            error,
        )
        // TODO: 실제 알림 발송 구현
        // 예시:
        // - Slack Webhook API 호출
        // - 이메일 발송 (JavaMailSender)
        // - SMS 발송
        // - 사내 알림 시스템 연동
    }
}
