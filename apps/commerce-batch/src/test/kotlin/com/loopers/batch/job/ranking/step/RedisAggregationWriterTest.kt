package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.RankingPeriodType
import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ExecutionContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

@SpringBootTest
@Import(RedisTestContainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklyRankingJobConfig.JOB_NAME}"])
@DisplayName("RedisAggregationWriter")
class RedisAggregationWriterTest @Autowired constructor(
    private val redisTemplate: RedisTemplate<String, String>,
) {

    @BeforeEach
    fun setUp() {
        // 테스트 전 Redis 초기화
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    @Nested
    @DisplayName("WEEKLY 기간에서 write 메서드는")
    inner class WeeklyWriteTest {

        @DisplayName("ZINCRBY로 점수를 누적한다")
        @Test
        fun shouldAccumulateScoresWithZincrby() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val writer = createWriter(baseDate, RankingPeriodType.WEEKLY)
            val stagingKey = "ranking:products:weekly:20250108:staging"

            val chunk1 = Chunk(
                listOf(
                    ScoreEntry(100L, BigDecimal("100.50")),
                    ScoreEntry(200L, BigDecimal("200.75")),
                ),
            )

            // 같은 상품 100 - 누적되어야 함
            val chunk2 = Chunk(
                listOf(
                    ScoreEntry(100L, BigDecimal("50.25")),
                    ScoreEntry(300L, BigDecimal("300.00")),
                ),
            )

            // act
            writer.write(chunk1)
            writer.write(chunk2)

            // assert
            val zSetOps = redisTemplate.opsForZSet()

            // 상품 100: 100.50 + 50.25 = 150.75
            val score100 = zSetOps.score(stagingKey, "100")
            assertThat(score100).isEqualTo(150.75)

            // 상품 200: 200.75
            val score200 = zSetOps.score(stagingKey, "200")
            assertThat(score200).isEqualTo(200.75)

            // 상품 300: 300.00
            val score300 = zSetOps.score(stagingKey, "300")
            assertThat(score300).isEqualTo(300.0)
        }

        @DisplayName("스테이징 키에 TTL 24시간을 설정한다")
        @Test
        fun shouldSetTtlTo24Hours() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val writer = createWriter(baseDate, RankingPeriodType.WEEKLY)
            val stagingKey = "ranking:products:weekly:20250108:staging"

            val chunk = Chunk(
                listOf(ScoreEntry(100L, BigDecimal("100.00"))),
            )

            // act
            writer.write(chunk)

            // assert
            val ttl = redisTemplate.getExpire(stagingKey, TimeUnit.HOURS)
            assertThat(ttl).isBetween(23L, 24L) // 약간의 시간 경과를 고려
        }

        @DisplayName("올바른 WEEKLY 스테이징 키 포맷을 사용한다")
        @Test
        fun shouldUseCorrectWeeklyStagingKeyFormat() {
            // arrange
            val baseDate = LocalDate.of(2025, 12, 25)
            val writer = createWriter(baseDate, RankingPeriodType.WEEKLY)
            val expectedKey = "ranking:products:weekly:20251225:staging"

            val chunk = Chunk(
                listOf(ScoreEntry(100L, BigDecimal("100.00"))),
            )

            // act
            writer.write(chunk)

            // assert
            val exists = redisTemplate.hasKey(expectedKey)
            assertThat(exists).isTrue()
        }

        @DisplayName("빈 chunk는 아무 작업도 하지 않는다")
        @Test
        fun shouldDoNothing_whenChunkIsEmpty() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val writer = createWriter(baseDate, RankingPeriodType.WEEKLY)
            val stagingKey = "ranking:products:weekly:20250108:staging"

            val emptyChunk = Chunk<ScoreEntry>(emptyList())

            // act
            writer.write(emptyChunk)

            // assert
            val exists = redisTemplate.hasKey(stagingKey)
            assertThat(exists).isFalse()
        }
    }

    @Nested
    @DisplayName("MONTHLY 기간에서 write 메서드는")
    inner class MonthlyWriteTest {

        @DisplayName("올바른 MONTHLY 스테이징 키 포맷을 사용한다")
        @Test
        fun shouldUseCorrectMonthlyStagingKeyFormat() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 1)
            val writer = createWriter(baseDate, RankingPeriodType.MONTHLY)
            val expectedKey = "ranking:products:monthly:20250201:staging"

            val chunk = Chunk(
                listOf(ScoreEntry(100L, BigDecimal("100.00"))),
            )

            // act
            writer.write(chunk)

            // assert
            val exists = redisTemplate.hasKey(expectedKey)
            assertThat(exists).isTrue()
        }

        @DisplayName("ZINCRBY로 점수를 누적한다")
        @Test
        fun shouldAccumulateScoresWithZincrby() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 1)
            val writer = createWriter(baseDate, RankingPeriodType.MONTHLY)
            val stagingKey = "ranking:products:monthly:20250201:staging"

            val chunk1 = Chunk(
                listOf(
                    ScoreEntry(100L, BigDecimal("500.00")),
                    ScoreEntry(200L, BigDecimal("300.00")),
                ),
            )

            val chunk2 = Chunk(
                listOf(
                    ScoreEntry(100L, BigDecimal("250.00")),
                ),
            )

            // act
            writer.write(chunk1)
            writer.write(chunk2)

            // assert
            val zSetOps = redisTemplate.opsForZSet()

            // 상품 100: 500.00 + 250.00 = 750.00
            val score100 = zSetOps.score(stagingKey, "100")
            assertThat(score100).isEqualTo(750.0)

            // 상품 200: 300.00
            val score200 = zSetOps.score(stagingKey, "200")
            assertThat(score200).isEqualTo(300.0)
        }
    }

    @Nested
    @DisplayName("HOURLY 기간에서 write 메서드는 (Instant 기반)")
    inner class HourlyWriteTest {

        @DisplayName("올바른 HOURLY 스테이징 키 포맷을 사용한다 (yyyyMMddHH)")
        @Test
        fun shouldUseCorrectHourlyStagingKeyFormat() {
            // arrange
            // 2025-01-08 14:30:00 KST
            val baseDateTime = ZonedDateTime.of(2025, 1, 8, 14, 30, 0, 0, ZoneId.of("Asia/Seoul")).toInstant()
            val writer = createWriterWithInstant(baseDateTime, RankingPeriodType.HOURLY)
            val expectedKey = "ranking:products:hourly:2025010814:staging"

            val chunk = Chunk(
                listOf(ScoreEntry(100L, BigDecimal("100.00"))),
            )

            // act
            writer.write(chunk)

            // assert
            val exists = redisTemplate.hasKey(expectedKey)
            assertThat(exists).isTrue()
        }

        @DisplayName("ZINCRBY로 점수를 누적한다")
        @Test
        fun shouldAccumulateScoresWithZincrby() {
            // arrange
            val baseDateTime = ZonedDateTime.of(2025, 1, 8, 14, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant()
            val writer = createWriterWithInstant(baseDateTime, RankingPeriodType.HOURLY)
            val stagingKey = "ranking:products:hourly:2025010814:staging"

            val chunk1 = Chunk(
                listOf(
                    ScoreEntry(100L, BigDecimal("100.50")),
                    ScoreEntry(200L, BigDecimal("200.75")),
                ),
            )

            // 같은 상품 100 - 누적되어야 함
            val chunk2 = Chunk(
                listOf(
                    ScoreEntry(100L, BigDecimal("50.25")),
                    ScoreEntry(300L, BigDecimal("300.00")),
                ),
            )

            // act
            writer.write(chunk1)
            writer.write(chunk2)

            // assert
            val zSetOps = redisTemplate.opsForZSet()

            // 상품 100: 100.50 + 50.25 = 150.75
            val score100 = zSetOps.score(stagingKey, "100")
            assertThat(score100).isEqualTo(150.75)

            // 상품 200: 200.75
            val score200 = zSetOps.score(stagingKey, "200")
            assertThat(score200).isEqualTo(200.75)

            // 상품 300: 300.00
            val score300 = zSetOps.score(stagingKey, "300")
            assertThat(score300).isEqualTo(300.0)
        }

        @DisplayName("스테이징 키에 TTL 24시간을 설정한다")
        @Test
        fun shouldSetTtlTo24Hours() {
            // arrange
            val baseDateTime = ZonedDateTime.of(2025, 1, 8, 14, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant()
            val writer = createWriterWithInstant(baseDateTime, RankingPeriodType.HOURLY)
            val stagingKey = "ranking:products:hourly:2025010814:staging"

            val chunk = Chunk(
                listOf(ScoreEntry(100L, BigDecimal("100.00"))),
            )

            // act
            writer.write(chunk)

            // assert
            val ttl = redisTemplate.getExpire(stagingKey, TimeUnit.HOURS)
            assertThat(ttl).isBetween(23L, 24L) // 약간의 시간 경과를 고려
        }

        @DisplayName("자정 시간을 올바르게 처리한다")
        @Test
        fun shouldHandleMidnightCorrectly() {
            // arrange
            // 2025-01-09 00:15:00 KST
            val baseDateTime = ZonedDateTime.of(2025, 1, 9, 0, 15, 0, 0, ZoneId.of("Asia/Seoul")).toInstant()
            val writer = createWriterWithInstant(baseDateTime, RankingPeriodType.HOURLY)
            val expectedKey = "ranking:products:hourly:2025010900:staging"

            val chunk = Chunk(
                listOf(ScoreEntry(100L, BigDecimal("100.00"))),
            )

            // act
            writer.write(chunk)

            // assert
            val exists = redisTemplate.hasKey(expectedKey)
            assertThat(exists).isTrue()
        }

        @DisplayName("23시 시간을 올바르게 처리한다")
        @Test
        fun shouldHandleLastHourOfDayCorrectly() {
            // arrange
            // 2025-01-08 23:45:00 KST
            val baseDateTime = ZonedDateTime.of(2025, 1, 8, 23, 45, 0, 0, ZoneId.of("Asia/Seoul")).toInstant()
            val writer = createWriterWithInstant(baseDateTime, RankingPeriodType.HOURLY)
            val expectedKey = "ranking:products:hourly:2025010823:staging"

            val chunk = Chunk(
                listOf(ScoreEntry(100L, BigDecimal("100.00"))),
            )

            // act
            writer.write(chunk)

            // assert
            val exists = redisTemplate.hasKey(expectedKey)
            assertThat(exists).isTrue()
        }

        @DisplayName("빈 chunk는 아무 작업도 하지 않는다")
        @Test
        fun shouldDoNothing_whenChunkIsEmpty() {
            // arrange
            val baseDateTime = ZonedDateTime.of(2025, 1, 8, 14, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant()
            val writer = createWriterWithInstant(baseDateTime, RankingPeriodType.HOURLY)
            val stagingKey = "ranking:products:hourly:2025010814:staging"

            val emptyChunk = Chunk<ScoreEntry>(emptyList())

            // act
            writer.write(emptyChunk)

            // assert
            val exists = redisTemplate.hasKey(stagingKey)
            assertThat(exists).isFalse()
        }
    }

    @Nested
    @DisplayName("ItemStream 메서드")
    inner class ItemStreamTest {

        @DisplayName("open()은 ExecutionContext에 저장된 writeCount를 복원한다")
        @Test
        fun shouldRestoreWriteCountFromExecutionContext() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val writer = createWriter(baseDate, RankingPeriodType.WEEKLY)
            val executionContext = ExecutionContext()
            executionContext.putInt("redisAggregationWriter.write.count", 100)

            // act
            writer.open(executionContext)

            // assert - write()를 호출하고 update()로 확인
            val chunk = Chunk(
                listOf(ScoreEntry(100L, BigDecimal("10.00"))),
            )
            writer.write(chunk)
            writer.update(executionContext)

            // 100 (복원) + 1 (새로 작성) = 101
            assertThat(executionContext.getInt("redisAggregationWriter.write.count")).isEqualTo(101)
        }

        @DisplayName("open()은 ExecutionContext에 키가 없으면 writeCount를 0으로 초기화한다")
        @Test
        fun shouldInitializeWriteCountToZero_whenKeyNotPresent() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val writer = createWriter(baseDate, RankingPeriodType.WEEKLY)
            val executionContext = ExecutionContext()

            // act
            writer.open(executionContext)

            // assert - write() 호출 후 update()로 확인
            val chunk = Chunk(
                listOf(
                    ScoreEntry(100L, BigDecimal("10.00")),
                    ScoreEntry(200L, BigDecimal("20.00")),
                ),
            )
            writer.write(chunk)
            writer.update(executionContext)

            // 0 (초기값) + 2 (새로 작성) = 2
            assertThat(executionContext.getInt("redisAggregationWriter.write.count")).isEqualTo(2)
        }

        @DisplayName("update()는 writeCount를 ExecutionContext에 저장한다")
        @Test
        fun shouldSaveWriteCountToExecutionContext() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val writer = createWriter(baseDate, RankingPeriodType.WEEKLY)
            val executionContext = ExecutionContext()
            writer.open(executionContext)

            val chunk1 = Chunk(
                listOf(
                    ScoreEntry(100L, BigDecimal("10.00")),
                    ScoreEntry(200L, BigDecimal("20.00")),
                ),
            )
            val chunk2 = Chunk(
                listOf(
                    ScoreEntry(300L, BigDecimal("30.00")),
                ),
            )

            // act
            writer.write(chunk1)
            writer.update(executionContext)
            val countAfterFirstChunk = executionContext.getInt("redisAggregationWriter.write.count")

            writer.write(chunk2)
            writer.update(executionContext)
            val countAfterSecondChunk = executionContext.getInt("redisAggregationWriter.write.count")

            // assert
            assertThat(countAfterFirstChunk).isEqualTo(2)
            assertThat(countAfterSecondChunk).isEqualTo(3)
        }

        @DisplayName("write()는 chunk 크기만큼 writeCount를 증가시킨다")
        @Test
        fun shouldIncrementWriteCountByChunkSize() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val writer = createWriter(baseDate, RankingPeriodType.WEEKLY)
            val executionContext = ExecutionContext()
            writer.open(executionContext)

            val chunk = Chunk(
                listOf(
                    ScoreEntry(100L, BigDecimal("10.00")),
                    ScoreEntry(200L, BigDecimal("20.00")),
                    ScoreEntry(300L, BigDecimal("30.00")),
                    ScoreEntry(400L, BigDecimal("40.00")),
                    ScoreEntry(500L, BigDecimal("50.00")),
                ),
            )

            // act
            writer.write(chunk)
            writer.update(executionContext)

            // assert
            assertThat(executionContext.getInt("redisAggregationWriter.write.count")).isEqualTo(5)
        }

        @DisplayName("close()는 정상적으로 호출된다 (no-op)")
        @Test
        fun shouldExecuteCloseWithoutError() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val writer = createWriter(baseDate, RankingPeriodType.WEEKLY)

            // act & assert - close()가 예외 없이 호출되는지 확인
            writer.close()
        }
    }

    private fun createWriter(baseDate: LocalDate, periodType: RankingPeriodType): RedisAggregationWriter {
        return RedisAggregationWriter(redisTemplate, baseDate, periodType)
    }

    private fun createWriterWithInstant(baseDateTime: Instant, periodType: RankingPeriodType): RedisAggregationWriter {
        return RedisAggregationWriter(redisTemplate, baseDateTime, periodType)
    }
}
