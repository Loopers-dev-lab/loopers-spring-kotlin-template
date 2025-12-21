package com.loopers.config

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CoroutineConfig {

    private val logger = LoggerFactory.getLogger(javaClass)
    private lateinit var scope: CoroutineScope

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
        scope = CoroutineScope(
            eventDispatcher + SupervisorJob() + eventExceptionHandler
        )
        return scope
    }

    @PreDestroy
    fun cleanup() {
        logger.info("이벤트 코루틴 스코프 종료 중...")
        if (::scope.isInitialized) {
            scope.cancel()
            runBlocking {
                logger.info("진행 중인 이벤트 처리 완료 대기 중...")
            }
        }
        logger.info("이벤트 코루틴 스코프 종료 완료")
    }
}
