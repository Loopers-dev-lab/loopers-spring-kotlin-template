package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Redis ZSET을 활용한 랭킹 Repository 구현체
 *
 * ZSET Key 패턴: ranking:all:{yyyyMMdd}
 * TTL: 2일 (48시간)
 */
@Repository
class RankingRedisRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingRepository {

    private val log = LoggerFactory.getLogger(RankingRedisRepository::class.java)

    companion object {
        private const val KEY_PREFIX = "ranking:all"
        private const val TTL_DAYS = 2L
    }

    override fun getScore(dateKey: String, productId: Long): Double? {
        val key = buildKey(dateKey)
        val member = productId.toString()

        return try {
            redisTemplate.opsForZSet().score(key, member)
        } catch (e: Exception) {
            log.error("랭킹 점수 조회 실패: key={}, productId={}", key, productId, e)
            null
        }
    }

    /**
     * Redis Key 생성: ranking:all:{yyyyMMdd}
     */
    private fun buildKey(dateKey: String): String {
        return "$KEY_PREFIX:$dateKey"
    }

    override fun batchIncrementScores(dateKey: String, productScores: Map<Long, Double>) {
        if (productScores.isEmpty()) {
            return
        }

        val key = buildKey(dateKey)

        try {
            redisTemplate.executePipelined { connection ->
                productScores.forEach { (productId, score) ->
                    connection.zIncrBy(key.toByteArray(), score, productId.toString().toByteArray())
                }
                null
            }

            setTTLIfNeeded(key)

            log.info("랭킹 점수 배치 증가 완료: key={}, {} 건", key, productScores.size)
        } catch (e: Exception) {
            log.error("랭킹 점수 배치 증가 실패: key={}", key, e)
            throw e
        }
    }

    override fun batchDecrementScores(dateKey: String, productScores: Map<Long, Double>) {
        if (productScores.isEmpty()) {
            return
        }

        val key = buildKey(dateKey)

        try {
            // 1. Pipeline으로 감소 처리
            val results = redisTemplate.executePipelined { connection ->
                productScores.forEach { (productId, score) ->
                    connection.zIncrBy(key.toByteArray(), -score, productId.toString().toByteArray())
                }
                null
            }

            // 2. 음수 방지: 각 결과 확인
            productScores.keys.forEachIndexed { index, productId ->
                val newScore = results[index] as? Double
                if (newScore != null && newScore < 0.0) {
                    redisTemplate.opsForZSet().add(key, productId.toString(), 0.0)
                    log.warn("랭킹 점수가 음수가 되어 0으로 설정: key={}, productId={}, score={}", key, productId, newScore)
                }
            }

            setTTLIfNeeded(key)

            log.info("랭킹 점수 배치 감소 완료: key={}, {} 건", key, productScores.size)
        } catch (e: Exception) {
            log.error("랭킹 점수 배치 감소 실패: key={}", key, e)
            throw e
        }
    }

    override fun carryOverScores(sourceDateKey: String, targetDateKey: String, weight: Double) {
        val sourceKey = buildKey(sourceDateKey)
        val targetKey = buildKey(targetDateKey)

        try {
            redisTemplate.execute { connection ->
                connection.execute(
                    "ZUNIONSTORE",
                    targetKey.toByteArray(StandardCharsets.UTF_8),
                    "1".toByteArray(StandardCharsets.UTF_8),
                    sourceKey.toByteArray(StandardCharsets.UTF_8),
                    "WEIGHTS".toByteArray(StandardCharsets.UTF_8),
                    weight.toString().toByteArray(StandardCharsets.UTF_8),
                )
                connection.expire(
                    targetKey.toByteArray(StandardCharsets.UTF_8),
                    TimeUnit.DAYS.toSeconds(TTL_DAYS),
                )
                null
            }

            setTTLIfNeeded(targetKey)

            log.info(
                "랭킹 점수 이월 완료: sourceKey={}, targetKey={}, weight={}",
                sourceKey,
                targetKey,
                weight,
            )
        } catch (e: Exception) {
            log.error("랭킹 점수 이월 실패: sourceKey={}, targetKey={}", sourceKey, targetKey, e)
            throw e
        }
    }

    /**
     * TTL 설정 (키 생성 시 한 번만)
     *
     * TTL이 설정되지 않은 경우 (-1) 2일(48시간)로 설정
     */
    private fun setTTLIfNeeded(key: String) {
        try {
            val ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS)
            // TTL이 설정되지 않았으면 (-1) 설정
            if (ttl == -1L) {
                redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS)
                log.info("랭킹 키 TTL 설정: key={}, ttl={}일", key, TTL_DAYS)
            }
        } catch (e: Exception) {
            log.error("TTL 설정 실패: key={}", key, e)
            // TTL 설정 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }
}
