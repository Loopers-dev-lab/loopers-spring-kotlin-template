package com.loopers.domain.ranking

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 랭킹 ZSET 키 전략을 담당하는 Value Object
 *
 * 시간의 양자화(Time Quantization)를 통해 랭킹 데이터를 시간 단위로 분리 관리
 */
data class RankingKey(
    val scope: RankingScope,
    val window: TimeWindow,
    val timestamp: LocalDateTime,
) {
    /**
     * Redis ZSET 키 생성
     *
     * 예: ranking:all:daily:20250906
     *     ranking:all:hourly:2025090614
     */
    fun toRedisKey(): String {
        return when (window) {
            TimeWindow.DAILY -> "ranking:${scope.value}:daily:${timestamp.format(DAILY_FORMAT)}"
            TimeWindow.HOURLY -> "ranking:${scope.value}:hourly:${timestamp.format(HOURLY_FORMAT)}"
        }
    }

    companion object {
        private val DAILY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val HOURLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH")

        /**
         * 일간 랭킹 키 생성
         */
        fun daily(scope: RankingScope, date: LocalDate): RankingKey {
            return RankingKey(
                scope = scope,
                window = TimeWindow.DAILY,
                timestamp = date.atStartOfDay(),
            )
        }

        /**
         * 시간별 랭킹 키 생성
         */
        fun hourly(scope: RankingScope, dateTime: LocalDateTime): RankingKey {
            return RankingKey(
                scope = scope,
                window = TimeWindow.HOURLY,
                timestamp = dateTime.withMinute(0).withSecond(0).withNano(0),
            )
        }

        /**
         * 현재 일간 랭킹 키
         */
        fun currentDaily(scope: RankingScope): RankingKey {
            return daily(scope, LocalDate.now())
        }

        /**
         * 현재 시간별 랭킹 키
         */
        fun currentHourly(scope: RankingScope): RankingKey {
            return hourly(scope, LocalDateTime.now())
        }
    }
}

/**
 * 랭킹 범위
 */
enum class RankingScope(val value: String) {
    /**
     * 전체 상품 랭킹
     */
    ALL("all"),
}

/**
 * 시간 윈도우
 */
enum class TimeWindow(val ttlDays: Int) {
    /**
     * 일간 집계 (TTL: 2일)
     */
    DAILY(ttlDays = 2),

    /**
     * 시간별 집계 (TTL: 1일)
     */
    HOURLY(ttlDays = 1),
}
