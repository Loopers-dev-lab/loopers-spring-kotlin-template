package com.loopers.domain.ranking

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 랭킹 ZSET 키 전략을 담당하는 Value Object
 *
 * 시간의 양자화(Time Quantization)를 통해 랭킹 데이터를 시간 단위로 분리 관리
 */
data class RankingKey(val scope: RankingScope, val window: TimeWindow, val timestamp: LocalDateTime) {
    /**
     * Redis ZSET 키 생성
     *
     * 예: ranking:all:daily:20250906
     *     ranking:all:hourly:2025090614
     *
     * 주의: WEEKLY, MONTHLY는 DB에 저장되므로 Redis 키를 사용하지 않음
     */
    fun toRedisKey(): String = when (window) {
        TimeWindow.DAILY -> "ranking:${scope.value}:daily:${timestamp.format(DAILY_FORMAT)}"

        TimeWindow.HOURLY -> "ranking:${scope.value}:hourly:${timestamp.format(HOURLY_FORMAT)}"

        TimeWindow.WEEKLY, TimeWindow.MONTHLY -> throw UnsupportedOperationException(
            "WEEKLY와 MONTHLY 랭킹은 Redis가 아닌 DB에 저장됩니다",
        )
    }

    companion object {
        private val DAILY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val HOURLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH")

        /**
         * LocalDate를 yyyyMMdd 포맷 문자열로 변환
         */
        fun dateToString(date: LocalDate): String = date.format(DAILY_FORMAT)

        /**
         * LocalDateTime을 yyyyMMddHH 포맷 문자열로 변환
         */
        fun dateTimeToString(dateTime: LocalDateTime): String = dateTime.format(HOURLY_FORMAT)

        /**
         * yyyyMMdd 포맷 문자열을 LocalDate로 파싱
         */
        fun parseDate(dateString: String): LocalDate = LocalDate.parse(dateString, DAILY_FORMAT)

        /**
         * yyyyMMddHH 포맷 문자열을 LocalDateTime으로 파싱
         */
        fun parseDateTime(dateTimeString: String): LocalDateTime = LocalDateTime.parse(dateTimeString, HOURLY_FORMAT)

        /**
         * 일간 랭킹 키 생성
         */
        fun daily(scope: RankingScope, date: LocalDate): RankingKey = RankingKey(
            scope = scope,
            window = TimeWindow.DAILY,
            timestamp = date.atStartOfDay(),
        )

        /**
         * 시간별 랭킹 키 생성
         */
        fun hourly(scope: RankingScope, dateTime: LocalDateTime): RankingKey = RankingKey(
            scope = scope,
            window = TimeWindow.HOURLY,
            timestamp = dateTime.withMinute(0).withSecond(0).withNano(0),
        )

        /**
         * 현재 일간 랭킹 키
         */
        fun currentDaily(
            scope: RankingScope,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): RankingKey = daily(scope, LocalDate.now(zoneId))

        /**
         * 현재 시간별 랭킹 키
         */
        fun currentHourly(
            scope: RankingScope,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): RankingKey = hourly(scope, LocalDateTime.now(zoneId))
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

    /**
     * 주간 집계 (DB 저장, TTL 없음)
     */
    WEEKLY(ttlDays = -1),

    /**
     * 월간 집계 (DB 저장, TTL 없음)
     */
    MONTHLY(ttlDays = -1),
}
