package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * RedisAggregationWriter - Redis ZINCRBY를 통한 점수 누적
 *
 * - 상품별 점수를 Redis Sorted Set에 ZINCRBY로 누적
 * - 스테이징 키 사용: ranking:products:weekly:{yyyyMMdd}:staging
 * - TTL 24시간 설정 (배치 완료 후 삭제, 실패 시 자동 만료)
 */
@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = WeeklyRankingJobConfig.JOB_NAME)
@Component
class RedisAggregationWriter(
    private val redisTemplate: RedisTemplate<String, String>,
    @Value("#{jobParameters['baseDate']}") private val baseDateParam: LocalDate?,
) : ItemWriter<ScoreEntry> {

    companion object {
        private const val WEEKLY_PREFIX = "ranking:products:weekly"
        private const val STAGING_SUFFIX = ":staging"
        private const val TTL_HOURS = 24L
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    private val stagingKey: String by lazy {
        val baseDate = baseDateParam ?: LocalDate.now()
        "$WEEKLY_PREFIX:${DATE_FORMATTER.format(baseDate)}$STAGING_SUFFIX"
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
    }
}
