package com.loopers.domain.event.handler

import java.lang.reflect.Method

/**
 * 비동기 이벤트 처리 실패 시 알림을 발송하는 인터페이스
 *
 * 역할:
 * - 재시도 후에도 실패한 이벤트에 대해 개발팀/운영팀에 알림
 * - Slack, 이메일, SMS 등 다양한 채널로 알림 가능
 */
interface FailedEventNotifier {
    /**
     * 실패한 이벤트에 대한 알림 발송
     *
     * @param method 실패한 메서드
     * @param params 메서드 파라미터 (이벤트 객체 포함)
     * @param exception 발생한 예외
     */
    fun notifyFailure(method: Method, params: Array<Any>, exception: Throwable)
}
