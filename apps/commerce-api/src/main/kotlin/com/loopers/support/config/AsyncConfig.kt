package com.loopers.support.config

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurerSupport
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.lang.reflect.Method
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurerSupport() {

    @Bean(name = ["threadPoolTaskExecutor"])
    override fun getAsyncExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 10
            queueCapacity = 500
            setThreadNamePrefix("Executor-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(10)
            initialize()
        }
    }

    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
        return AsyncExceptionHandler()
    }

    class AsyncExceptionHandler : AsyncUncaughtExceptionHandler {

        private val log = LoggerFactory.getLogger(AsyncExceptionHandler::class.java)

        override fun handleUncaughtException(ex: Throwable, method: Method, vararg params: Any?) {
            log.error("[ASYNC-ERROR] method: ${method.name} exception: ${ex.message}", ex)
        }
    }
}
