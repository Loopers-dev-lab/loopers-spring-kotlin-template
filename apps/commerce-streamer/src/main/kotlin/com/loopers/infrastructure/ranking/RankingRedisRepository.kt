package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.connection.ReturnType
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
            // ZSET에서 특정 멤버의 점수를 조회한다. (없으면 null)
            redisTemplate.opsForZSet().score(key, member)
        } catch (e: Exception) {
            log.error("랭킹 점수 조회 실패: key={}, productId={}", key, productId, e)
            null
        }
    }

    /**
     * Redis Key 생성: ranking:all:{yyyyMMdd}
     *
     * - 일자별 랭킹 스냅샷을 구분하기 위해 dateKey를 접미사로 사용한다.
     */
    private fun buildKey(dateKey: String): String {
        return "$KEY_PREFIX:$dateKey"
    }

    /**
     * 랭킹 점수를 일괄 증가시킨다.
     *
     * - 같은 날짜 키에 대한 여러 상품 점수를 Pipeline으로 묶어 성능을 최적화한다.
     * - TTL은 최초 생성 시에만 설정한다.
     */
    override fun batchIncrementScores(dateKey: String, productScores: Map<Long, Double>) {
        if (productScores.isEmpty()) {
            return
        }

        val key = buildKey(dateKey)

        try {
            redisTemplate.executePipelined { connection ->
                productScores.forEach { (productId, score) ->
                    // ZINCRBY로 점수를 누적한다.
                    connection.zIncrBy(key.toByteArray(), score, productId.toString().toByteArray())
                }
                null
            }

            // 키 생성 직후에만 TTL을 세팅한다.
            setTTLIfNeeded(key)

            log.info("랭킹 점수 배치 증가 완료: key={}, {} 건", key, productScores.size)
        } catch (e: Exception) {
            log.error("랭킹 점수 배치 증가 실패: key={}", key, e)
            throw e
        }
    }

    /**
     * 랭킹 점수를 일괄 감소시킨다.
     *
     * - 감소와 음수 보정을 Lua 스크립트로 원자 처리한다.
     * - Pipeline으로 묶어 네트워크 왕복 비용을 줄인다.
     */
    override fun batchDecrementScores(dateKey: String, productScores: Map<Long, Double>) {
        if (productScores.isEmpty()) {
            return
        }

        val key = buildKey(dateKey)
        // Lua 실행에 사용할 키/스크립트는 바이트 배열로 변환한다.
        val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
        val script = """
            local newScore = redis.call('ZINCRBY', KEYS[1], -tonumber(ARGV[1]), ARGV[2])
            if newScore < 0 then
                redis.call('ZADD', KEYS[1], 0, ARGV[2])
                return 0
            end
            return newScore
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        try {
            // 1. Lua로 감소 + 음수 보정까지 원자 처리
            redisTemplate.executePipelined { connection ->
                productScores.forEach { (productId, score) ->
                    // KEYS[1]=key, ARGV[1]=score, ARGV[2]=productId
                    connection.eval(
                        script,
                        ReturnType.VALUE,
                        1,
                        keyBytes,
                        score.toString().toByteArray(StandardCharsets.UTF_8),
                        productId.toString().toByteArray(StandardCharsets.UTF_8),
                    )
                }
                null
            }

            // 키 생성 직후에만 TTL을 세팅한다.
            setTTLIfNeeded(key)

            log.info("랭킹 점수 배치 감소 완료: key={}, {} 건", key, productScores.size)
        } catch (e: Exception) {
            log.error("랭킹 점수 배치 감소 실패: key={}", key, e)
            throw e
        }
    }

    /**
     * 전일 랭킹 점수를 가중치와 함께 익일 키에 이월한다.
     *
     * - ZUNIONSTORE로 targetKey와 sourceKey를 합산한다.
     * - targetKey 기존 점수 + (sourceKey 점수 × weight) 형태로 반영된다.
     */
    override fun carryOverScores(sourceDateKey: String, targetDateKey: String, weight: Double) {
        val sourceKey = buildKey(sourceDateKey)
        val targetKey = buildKey(targetDateKey)

        try {
            redisTemplate.execute { connection ->
                //
                /**
                 * ZUNIONSTORE targetKey 2 targetKey sourceKey WEIGHTS 1 weight
                 *
                 * targetKey에 두 개의 ZSET(targetKey, sourceKey)을 합산해서 저장
                 * - WEIGHTS 1 weight 때문에 점수는
                 *   - targetKey 쪽은 그대로 *1
                 *   - sourceKey 쪽은 *weight
                 * - 즉 최종 점수는 targetKey의 기존 점수 + (sourceKey 점수 × weight)
                 */
                connection.execute(
                    "ZUNIONSTORE",
                    targetKey.toByteArray(StandardCharsets.UTF_8),
                    "2".toByteArray(StandardCharsets.UTF_8),
                    targetKey.toByteArray(StandardCharsets.UTF_8),
                    sourceKey.toByteArray(StandardCharsets.UTF_8),
                    "WEIGHTS".toByteArray(StandardCharsets.UTF_8),
                    "1".toByteArray(StandardCharsets.UTF_8),
                    weight.toString().toByteArray(StandardCharsets.UTF_8),
                )
                // 이월 결과 키에 TTL 부여
                connection.expire(
                    targetKey.toByteArray(StandardCharsets.UTF_8),
                    TimeUnit.DAYS.toSeconds(TTL_DAYS),
                )
                null
            }

            // TTL 누락 대비용 보정
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
                // 일간 키의 보관 기간을 강제한다.
                redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS)
                log.info("랭킹 키 TTL 설정: key={}, ttl={}일", key, TTL_DAYS)
            }
        } catch (e: Exception) {
            log.error("TTL 설정 실패: key={}", key, e)
            // TTL 설정 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }
}
