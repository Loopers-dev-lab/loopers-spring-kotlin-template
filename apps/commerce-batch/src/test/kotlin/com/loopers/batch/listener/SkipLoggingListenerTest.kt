package com.loopers.batch.listener

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.loopers.batch.job.ranking.step.ScoreEntry
import com.loopers.domain.ranking.ProductDailyMetric
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("SkipLoggingListener")
class SkipLoggingListenerTest {

    private lateinit var listener: SkipLoggingListener
    private lateinit var logAppender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun setUp() {
        listener = SkipLoggingListener()

        // Setup log capture
        val logger = LoggerFactory.getLogger(SkipLoggingListener::class.java) as Logger
        logAppender = ListAppender()
        logAppender.start()
        logger.addAppender(logAppender)
    }

    @Nested
    @DisplayName("onSkipInRead 메서드는")
    inner class OnSkipInReadTest {

        @DisplayName("[SKIP-READ] 형식으로 읽기 스킵 에러를 로깅한다")
        @Test
        fun shouldLogSkipReadError() {
            // arrange
            val exception = RuntimeException("Read failed due to parsing error")

            // act
            listener.onSkipInRead(exception)

            // assert
            val logEvent = logAppender.list.first()
            assertThat(logEvent.formattedMessage).contains("[SKIP-READ]")
            assertThat(logEvent.formattedMessage).contains("Failed to read item")
            assertThat(logEvent.throwableProxy?.message).isEqualTo("Read failed due to parsing error")
        }
    }

    @Nested
    @DisplayName("onSkipInProcess 메서드는")
    inner class OnSkipInProcessTest {

        @DisplayName("[SKIP-PROCESS] 형식으로 item, error를 로깅한다")
        @Test
        fun shouldLogSkipProcessError() {
            // arrange
            val metric = ProductDailyMetric(
                statDate = LocalDate.of(2025, 1, 15),
                productId = 12345L,
                viewCount = 100L,
                likeCount = 10L,
                orderAmount = BigDecimal("1000.00"),
            )
            val exception = IllegalStateException("Score calculation overflow")

            // act
            listener.onSkipInProcess(metric, exception)

            // assert
            val logEvent = logAppender.list.first()
            assertThat(logEvent.formattedMessage).contains("[SKIP-PROCESS]")
            assertThat(logEvent.formattedMessage).contains("item=")
            assertThat(logEvent.formattedMessage).contains("error=Score calculation overflow")
        }
    }

    @Nested
    @DisplayName("onSkipInWrite 메서드는")
    inner class OnSkipInWriteTest {

        @DisplayName("[SKIP-WRITE] 형식으로 item, error를 로깅한다")
        @Test
        fun shouldLogSkipWriteError() {
            // arrange
            val scoreEntry = ScoreEntry(
                productId = 67890L,
                score = BigDecimal("1234.56"),
            )
            val exception = RuntimeException("Redis connection timeout")

            // act
            listener.onSkipInWrite(scoreEntry, exception)

            // assert
            val logEvent = logAppender.list.first()
            assertThat(logEvent.formattedMessage).contains("[SKIP-WRITE]")
            assertThat(logEvent.formattedMessage).contains("item=")
            assertThat(logEvent.formattedMessage).contains("error=Redis connection timeout")
        }
    }
}
