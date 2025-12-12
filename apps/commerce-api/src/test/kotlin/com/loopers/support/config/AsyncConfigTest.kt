package com.loopers.support.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@DisplayName("AsyncConfig 단위 테스트")
@SpringBootTest
@ActiveProfiles("test")
class AsyncConfigTest {

    @Autowired
    private lateinit var taskExecutor: TaskExecutor

    @DisplayName("빈 생성")
    @Nested
    inner class BeanCreation {

        @DisplayName("taskExecutor 빈이 생성된다")
        @Test
        fun `taskExecutor bean is created`() {
            // then
            assertThat(taskExecutor).isNotNull
            assertThat(taskExecutor).isInstanceOf(ThreadPoolTaskExecutor::class.java)
        }
    }

    @DisplayName("쓰레드 설정")
    @Nested
    inner class ThreadConfiguration {

        @DisplayName("쓰레드 이름 접두사가 event-async- 이다")
        @Test
        fun `thread name prefix is event-async-`() {
            // given
            val threadNameRef = AtomicReference<String>()
            val latch = CountDownLatch(1)

            // when
            taskExecutor.execute {
                threadNameRef.set(Thread.currentThread().name)
                latch.countDown()
            }
            latch.await(5, TimeUnit.SECONDS)

            // then
            assertThat(threadNameRef.get()).startsWith("event-async-")
        }
    }
}
