package com.loopers.infrastructure.outbox

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [OutboxRelayScheduler::class])
@ActiveProfiles("test")
class OutboxRelaySchedulerTest {
    @MockkBean
    private lateinit var outboxRelayService: OutboxRelayService

    @Autowired
    private lateinit var outboxRelayScheduler: OutboxRelayScheduler

    @Nested
    @DisplayName("relayNewMessages")
    inner class RelayNewMessagesTest {
        @BeforeEach
        fun setUp() {
            every { outboxRelayService.relayNewMessages() } returns RelayResult(
                successCount = 5,
                failedCount = 1,
                lastProcessedId = 100L,
            )
        }

        @Test
        @DisplayName("OutboxRelayService.relayNewMessages를 호출한다")
        fun `calls OutboxRelayService relayNewMessages`() {
            // when
            outboxRelayScheduler.relayNewMessages()

            // then
            verify(exactly = 1) { outboxRelayService.relayNewMessages() }
        }
    }

    @Nested
    @DisplayName("retryFailedMessages")
    inner class RetryFailedMessagesTest {
        @BeforeEach
        fun setUp() {
            every { outboxRelayService.retryFailedMessages() } returns RetryResult(
                successCount = 3,
                failedCount = 2,
            )
        }

        @Test
        @DisplayName("OutboxRelayService.retryFailedMessages를 호출한다")
        fun `calls OutboxRelayService retryFailedMessages`() {
            // when
            outboxRelayScheduler.retryFailedMessages()

            // then
            verify(exactly = 1) { outboxRelayService.retryFailedMessages() }
        }
    }
}
