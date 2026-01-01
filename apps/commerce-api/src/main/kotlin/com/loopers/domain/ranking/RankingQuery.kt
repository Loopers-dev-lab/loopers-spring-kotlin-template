package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * RankingQuery - Encapsulates ranking query conditions
 *
 * - Contains all conditions needed for querying
 * - Encapsulates bucketKey generation logic
 * - Encapsulates fallback key generation logic
 * - Encapsulates offset/limit calculations
 */
data class RankingQuery(
    val period: RankingPeriod,
    val bucketKey: String,
    val fallbackKey: String?,
    val offset: Long,
    val limit: Long,
) {
    init {
        if (offset < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "offset은 0 이상이어야 합니다.")
        }
        if (limit <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "limit은 1 이상이어야 합니다.")
        }
        if (limit > MAX_LIMIT) {
            throw CoreException(ErrorType.BAD_REQUEST, "limit은 최대 ${MAX_LIMIT}까지 가능합니다.")
        }
    }

    /**
     * Returns limit + 1 for hasNext determination
     */
    fun limitForHasNext(): Long = limit + 1

    companion object {
        private const val MAX_LIMIT = 100L
        private const val DEFAULT_PAGE = 0
        private const val DEFAULT_SIZE = 20

        fun of(
            period: RankingPeriod,
            date: String?,
            page: Int?,
            size: Int?,
            rankingKeyGenerator: RankingKeyGenerator,
        ): RankingQuery {
            val resolvedPage = page ?: DEFAULT_PAGE
            val resolvedSize = size ?: DEFAULT_SIZE

            val bucketKey = resolveBucketKey(period, date, rankingKeyGenerator)
            val fallbackKey = resolveFallbackKey(period, date, bucketKey, rankingKeyGenerator)

            return RankingQuery(
                period = period,
                bucketKey = bucketKey,
                fallbackKey = fallbackKey,
                offset = (resolvedPage * resolvedSize).toLong(),
                limit = resolvedSize.toLong(),
            )
        }

        private fun resolveBucketKey(
            period: RankingPeriod,
            date: String?,
            rankingKeyGenerator: RankingKeyGenerator,
        ): String {
            return if (date != null) {
                rankingKeyGenerator.bucketKey(period, date)
            } else {
                rankingKeyGenerator.currentBucketKey(period)
            }
        }

        private fun resolveFallbackKey(
            period: RankingPeriod,
            date: String?,
            bucketKey: String,
            rankingKeyGenerator: RankingKeyGenerator,
        ): String? {
            // No fallback when a specific date is specified
            if (date != null) return null

            return rankingKeyGenerator.previousBucketKey(bucketKey)
        }
    }
}
