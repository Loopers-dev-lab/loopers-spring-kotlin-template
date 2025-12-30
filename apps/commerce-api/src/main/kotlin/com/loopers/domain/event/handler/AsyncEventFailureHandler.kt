package com.loopers.domain.event.handler

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.stereotype.Component
import java.lang.reflect.Method

/**
 * 비동기 이벤트 처리 중 발생한 예외를 처리하는 핸들러
 *
 * @Async 메서드에서 발생한 예외가 @Retryable의 모든 재시도를 소진한 후에도
 * 처리되지 않으면 이 핸들러가 호출됨
 *
 * 역할:
 * 1. 실패한 이벤트 정보를 별도 저장소에 저장
 * 2. 운영팀에 알림 발송
 * 3. 로그 기록
 */
@Component
class AsyncEventFailureHandler(
    private val failedEventStore: FailedEventStore,
    private val failedEventNotifier: FailedEventNotifier,
) : AsyncUncaughtExceptionHandler {

    companion object {
        private val logger = LoggerFactory.getLogger(AsyncEventFailureHandler::class.java)
    }

    override fun handleUncaughtException(ex: Throwable, method: Method, vararg params: Any?) {
        logger.error(
            "비동기 이벤트 처리 실패: method=${method.name}, " +
                    "class=${method.declaringClass.simpleName}, " +
                    "params=${params.contentToString()}",
            ex,
        )

        try {
            // 1. 실패한 이벤트를 별도 테이블에 저장
            failedEventStore.store(method, params.filterNotNull().toTypedArray(), ex)
        } catch (e: Exception) {
            logger.error("실패한 이벤트 저장 중 오류 발생", e)
        }

        try {
            // 2. 운영팀에 알림 발송
            failedEventNotifier.notifyFailure(method, params.filterNotNull().toTypedArray(), ex)
        } catch (e: Exception) {
            logger.error("실패 알림 발송 중 오류 발생", e)
        }
    }
}
