package com.loopers.batch.job.ranking

/**
 * RankingPeriodType - 배치 Job에서 사용하는 랭킹 기간 유형
 *
 * - HOURLY: 0일 윈도우 (Instant 기반), ranking:products:hourly:{yyyyMMddHH}:staging 키 사용
 * - DAILY: 1일 윈도우, ranking:products:daily:{yyyyMMdd}:staging 키 사용
 * - WEEKLY: 7일 윈도우, ranking:products:weekly:{yyyyMMdd}:staging 키 사용
 * - MONTHLY: 30일 윈도우, ranking:products:monthly:{yyyyMMdd}:staging 키 사용
 */
enum class RankingPeriodType(
    val key: String,
    val windowDays: Long,
    val redisPrefix: String,
) {
    HOURLY("hourly", 0L, "ranking:products:hourly"),
    DAILY("daily", 1L, "ranking:products:daily"),
    WEEKLY("weekly", 7L, "ranking:products:weekly"),
    MONTHLY("monthly", 30L, "ranking:products:monthly"),
    ;

    companion object {
        fun fromString(value: String): RankingPeriodType {
            return entries.find { it.key.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown ranking period type: $value")
        }
    }
}
