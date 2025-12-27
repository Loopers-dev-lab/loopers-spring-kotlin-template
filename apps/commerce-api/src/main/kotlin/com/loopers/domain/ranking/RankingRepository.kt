package com.loopers.domain.ranking

/**
 * 일간 상품 랭킹 조회용 Repository
 *
 * Redis ZSET Key 패턴: ranking:all:{yyyyMMdd}
 */
interface RankingRepository {
    fun getScores(dateKey: String, start: Long, end: Long): List<RankingScore>

    fun getScore(dateKey: String, productId: Long): Double?

    fun getTotalCount(dateKey: String): Long

    fun getRank(dateKey: String, productId: Long): Long?
}
