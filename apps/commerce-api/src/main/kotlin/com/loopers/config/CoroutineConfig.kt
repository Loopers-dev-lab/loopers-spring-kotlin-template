package com.loopers.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CoroutineConfig {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 이벤트 처리용 코루틴 디스패처
     * Dispatchers.IO: I/O 작업에 최적화 (DB 쿼리, HTTP 호출 등)
     */
    @Bean("eventDispatcher")
    fun eventDispatcher(): CoroutineDispatcher {
        return Dispatchers.IO.limitedParallelism(50)
    }

    @Bean("eventExceptionHandler")
    fun eventExceptionHandler(): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            logger.error("코루틴 실행 중 예외 발생", throwable)
        }
    }

    @Bean("eventCoroutineScope")
    fun eventCoroutineScope(
        eventDispatcher: CoroutineDispatcher,
        eventExceptionHandler: CoroutineExceptionHandler,
    ) : CoroutineScope {
        return CoroutineScope(
            eventDispatcher + SupervisorJob() + eventExceptionHandler
        )
    }
}
