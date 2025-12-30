package com.loopers.infrastructure.notification

/**
 * 배치 실패 알림 인터페이스
 *
 * 배치 Job 실행 실패 시 운영팀에 알림을 발송하는 역할을 담당합니다.
 * 구현체로 Slack, 이메일, SMS 등 다양한 알림 채널을 지원할 수 있습니다.
 */
interface BatchAlarmNotifier {
    /**
     * 배치 실패 알림 발송
     *
     * @param jobName 실패한 배치 Job 이름
     * @param message 알림 메시지
     * @param error 발생한 예외
     */
    fun notifyBatchFailure(jobName: String, message: String, error: Exception)
}
