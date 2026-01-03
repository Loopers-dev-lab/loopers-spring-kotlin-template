package com.loopers.domain.event.handler

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.reflect.Method

/**
 * 실패한 이벤트를 로그로만 기록하는 기본 구현체
 *
 * TODO: 실제 구현 시 다음을 고려:
 * - DB 테이블에 저장 (failed_events 테이블)
 * - 이벤트 타입, 페이로드, 에러 메시지, 스택트레이스 저장
 * - 재처리 상태 관리 (PENDING, RETRYING, COMPLETED, FAILED)
 * - 재처리 API 제공
 */
@Component
class LoggingFailedEventStore : FailedEventStore {

    companion object {
        private val logger = LoggerFactory.getLogger(LoggingFailedEventStore::class.java)
    }

    override fun store(method: Method, params: Array<Any>, exception: Throwable) {
        logger.warn(
            "실패한 이벤트 저장 (현재는 로그만 기록): " +
                    "method=${method.name}, " +
                    "class=${method.declaringClass.simpleName}, " +
                    "params=${params.contentToString()}, " +
                    "error=${exception.message}",
        )
        // TODO: 실제 DB 저장 로직 구현
        // Example:
        // val failedEvent = FailedEvent(
        //     methodName = method.name,
        //     className = method.declaringClass.name,
        //     payload = objectMapper.writeValueAsString(params),
        //     errorMessage = exception.message,
        //     stackTrace = exception.stackTraceToString(),
        //     status = FailedEventStatus.PENDING
        // )
        // failedEventRepository.save(failedEvent)
    }
}
