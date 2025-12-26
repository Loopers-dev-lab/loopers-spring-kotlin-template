package com.loopers.domain.ranking

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 랭킹 도메인 서비스
 *
 * 역할:
 * 1. Redis ZSET 연산 캡슐화
 * 2. TTL 자동 관리 (키 생성 시 2일 자동 설정)
 * 3. 비즈니스 로직과 인프라 분리
 *
 * Redis ZSET 명령어 매핑:
 * - incrementScore() → ZINCRBY
 * - getTopProducts() / getProductsInRange() → ZREVRANGE ... WITHSCORES
 * - getProductRank() → ZREVRANK
 * - getProductScore() → ZSCORE
 * - getRankingSize() → ZCARD
 */
@Component
class RankingService(
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val TTL_DAYS = 2L  // 2일 TTL
        private const val TTL_SECONDS = TTL_DAYS * 24 * 60 * 60
    }

    /**
     * 특정 상품의 랭킹 점수 증가
     *
     * Redis 명령어: ZINCRBY ranking:all:{yyyyMMdd} {productId} {score}
     *
     * @param date 대상 날짜
     * @param productId 상품 ID
     * @param score 증가시킬 점수 (음수 가능 - 좋아요 취소 시)
     */
    fun incrementScore(date: LocalDate, productId: Long, score: Double) {
        val key = RankingKeyGenerator.generateDailyKey(date)
        val member = "product:$productId"

        val zSetOps = redisTemplate.opsForZSet()

        // ZINCRBY: 점수 증가 (member가 없으면 자동 생성)
        val newScore = zSetOps.incrementScore(key, member, score)

        // TTL 설정 (키가 처음 생성될 때만)
        ensureTtl(key)
        logger.debug("랭킹 점수 증가: key=$key, productId=$productId, score=$score, newScore=$newScore")
    }

    /**
     * 특정 상품의 점수 조회
     *
     * Redis 명령어: ZSCORE ranking:all:20251222 "product:101"
     *
     * @param date 대상 날짜
     * @param productId 상품 ID
     * @return 점수 (없으면 null)
     */
    fun getProductScore(date: LocalDate, productId: Long): Double? {
        val key = RankingKeyGenerator.generateDailyKey(date)
        val member = "product:$productId"
        val zSetOps = redisTemplate.opsForZSet()

        // ZSCORE: 점수 조회
        return zSetOps.score(key, member)
    }

    /**
     * 상위 N개 상품을 점수와 함께 조회
     *
     * Redis 명령어: ZREVRANGE ranking:all:{yyyyMMdd} {start} {end} WITHSCORES
     *
     * @param date 대상 날짜
     * @param start 시작 인덱스 (0부터 시작)
     * @param end 종료 인덱스
     * @return 상품 ID와 점수 쌍의 리스트 (점수 내림차순)
     */
    fun getTopProducts(date: LocalDate, start: Long, end: Long): List<Pair<Long, Double>> {
        val key = RankingKeyGenerator.generateDailyKey(date)
        val zSetOps = redisTemplate.opsForZSet()

        // ZREVRANGE WITHSCORES: 점수 내림차순으로 범위 조회
        val rangeWithScores = zSetOps.reverseRangeWithScores(key, start, end)
            ?: return emptyList()

        return rangeWithScores.map { typedTuple ->
            val member = typedTuple.value ?: ""
            val productId = member.removePrefix("product:").toLongOrNull() ?: 0L
            val score = typedTuple.score ?: 0.0
            productId to score
        }
    }

    /**
     * 범위 내 상품을 점수와 함께 조회 (getTopProducts의 alias)
     *
     * Redis 명령어: ZREVRANGE ranking:all:{yyyyMMdd} {start} {end} WITHSCORES
     *
     * @param date 대상 날짜
     * @param start 시작 인덱스 (0부터 시작)
     * @param end 종료 인덱스
     * @return 상품 ID와 점수 쌍의 리스트 (점수 내림차순)
     */
    fun getProductsInRange(date: LocalDate, start: Long, end: Long): List<Pair<Long, Double>> {
        return getTopProducts(date, start, end)
    }

    /**
     * 특정 상품의 랭킹 순위 조회
     *
     * Redis 명령어: ZREVRANK ranking:all:{yyyyMMdd} "product:{productId}"
     *
     * @param date 대상 날짜
     * @param productId 상품 ID
     * @return 순위 (0부터 시작, 순위권에 없으면 null)
     */
    fun getProductRank(date: LocalDate, productId: Long): Long? {
        val key = RankingKeyGenerator.generateDailyKey(date)
        val member = "product:$productId"
        val zSetOps = redisTemplate.opsForZSet()

        // ZREVRANK: 점수 내림차순 순위 조회 (0-based)
        return zSetOps.reverseRank(key, member)
    }

    /**
     * 랭킹에 포함된 상품 수 조회
     * Redis 명령어: ZCARD ranking:all:{yyyyMMdd}
     *
     * @param date 대상 날짜
     * @return 상품 갯수
     */
    fun getRankingSize(date: LocalDate): Long {
        val key = RankingKeyGenerator.generateDailyKey(date)
        val zSetOps = redisTemplate.opsForZSet()

        // ZCARD: 상품 개수 조회
        return zSetOps.size(key) ?: 0L
    }


    /**
     * TTL 설정 (키가 처음 생성될 때만)
     *
     * 왜 이렇게 구현했는가?
     * - 매번 TTL을 설정하면 불필요한 Redis 명령 실행
     * - GETEXPIRE로 TTL 확인 후 필요할 때만 설정
     *
     * 주의사항:
     * - TTL = -1: 키는 존재하지만 만료 시간 없음
     * - TTL = -2: 키는 존재하지 않음
     */
    private fun ensureTtl(key: String) {
        val ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS)

        // TTL이 설정되지 않았을 때만 설정
        if (ttl == -1L) {
            redisTemplate.expire(key, TTL_SECONDS, TimeUnit.SECONDS)
            logger.info("TTL 설정: key=$key, TTL=${TTL_DAYS}일")
        }
    }
}
