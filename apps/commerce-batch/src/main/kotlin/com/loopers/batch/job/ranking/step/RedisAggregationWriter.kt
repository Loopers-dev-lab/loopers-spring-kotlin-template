package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.RankingPeriodType
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.ItemStream
import org.springframework.batch.item.ItemWriter
import org.springframework.data.redis.core.RedisTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * RedisAggregationWriter - Redis ZINCRBY를 통한 점수 누적
 *
 * - 상품별 점수를 Redis Sorted Set에 ZINCRBY로 누적
 * - 스테이징 키 사용:
 *   - LocalDate 기반: {prefix}:{yyyyMMdd}:staging (Weekly/Monthly)
 *   - Instant 기반: {prefix}:{yyyyMMddHH}:staging (Hourly)
 * - TTL 24시간 설정 (배치 완료 후 삭제, 실패 시 자동 만료)
 */
class RedisAggregationWriter private constructor(
    private val redisTemplate: RedisTemplate<String, String>,
    private val periodType: RankingPeriodType,
    private val keyFormatter: () -> String,
) : ItemWriter<ScoreEntry>, ItemStream {

    /**
     * LocalDate 기반 생성자 (Weekly/Monthly 랭킹용)
     * 키 포맷: {prefix}:{yyyyMMdd}:staging
     */
    constructor(
        redisTemplate: RedisTemplate<String, String>,
        baseDate: LocalDate,
        periodType: RankingPeriodType,
    ) : this(
        redisTemplate = redisTemplate,
        periodType = periodType,
        keyFormatter = { "${periodType.redisPrefix}:${DATE_FORMATTER.format(baseDate)}$STAGING_SUFFIX" },
    )

    /**
     * Instant 기반 생성자 (Hourly 랭킹용)
     * 키 포맷: {prefix}:{yyyyMMddHH}:staging
     */
    constructor(
        redisTemplate: RedisTemplate<String, String>,
        baseDateTime: Instant,
        periodType: RankingPeriodType,
    ) : this(
        redisTemplate = redisTemplate,
        periodType = periodType,
        keyFormatter = {
            val zonedDateTime = baseDateTime.atZone(ZONE_ID)
            "${periodType.redisPrefix}:${DATETIME_HOUR_FORMATTER.format(zonedDateTime)}$STAGING_SUFFIX"
        },
    )

    companion object {
        private const val STAGING_SUFFIX = ":staging"
        private const val TTL_HOURS = 24L
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val DATETIME_HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH")
        private val ZONE_ID = ZoneId.of("Asia/Seoul")
        private const val WRITE_COUNT_KEY = "redisAggregationWriter.write.count"
    }

    private var writeCount: Int = 0

    private val stagingKey: String by lazy {
        keyFormatter()
    }

    override fun open(executionContext: ExecutionContext) {
        if (executionContext.containsKey(WRITE_COUNT_KEY)) {
            writeCount = executionContext.getInt(WRITE_COUNT_KEY)
        }
    }

    override fun update(executionContext: ExecutionContext) {
        executionContext.putInt(WRITE_COUNT_KEY, writeCount)
    }

    override fun close() {
        // no-op
    }

    override fun write(chunk: Chunk<out ScoreEntry>) {
        val zSetOps = redisTemplate.opsForZSet()

        chunk.items.forEach { entry ->
            // ZINCRBY로 점수 누적 (같은 productId에 대해 여러 날의 점수가 합산됨)
            zSetOps.incrementScore(
                stagingKey,
                entry.productId.toString(),
                entry.score.toDouble(),
            )
        }

        // 첫 chunk에서 TTL 설정 (이미 설정된 경우 무시됨)
        if (redisTemplate.getExpire(stagingKey) == -1L) {
            redisTemplate.expire(stagingKey, TTL_HOURS, TimeUnit.HOURS)
        }

        writeCount += chunk.items.size
    }
}
