package com.loopers.config

import com.loopers.domain.event.handler.AsyncEventFailureHandler
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * 비동기 처리 설정
 *
 * - AsyncUncaughtExceptionHandler 등록: @Async 메서드 실행 중 예외 처리
 * - Thread Pool 설정: 비동기 작업을 위한 스레드 풀 구성
 */
@Configuration
class AsyncConfig(private val asyncEventFailureHandler: AsyncEventFailureHandler) : AsyncConfigurer {

    /**
     * 비동기 작업을 위한 Thread Pool Executor 설정
     */
    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5
        executor.maxPoolSize = 10
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("async-event-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(30)
        executor.initialize()
        return executor
    }

    /**
     * @Async 메서드에서 예외 발생 시 처리할 핸들러 등록
     * @Retryable의 모든 재시도가 실패한 후 호출됨
     */
    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler = asyncEventFailureHandler
}
