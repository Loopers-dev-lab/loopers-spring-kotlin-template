package com.loopers.domain.ranking

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 랭킹 ZSET 키 생성 유틸리티
 *
 * 키 전략:
 * - 일간 랭킹: ranking:all:{yyyyMMdd}
 * - 예시: ranking:all:20251222
 *
 * 왜 이렇게 설계했나?
 * 1. 날짜별 분리로 시간의 양자화 구현
 * 2. 패턴 일관성으로 확장 가능 (주간/월간 랭킹 추가 시)
 * 3. TTL 관리 용이 (날짜별로 독립적인 만료)
 */
object RankingKeyGenerator { // object 선언하여 싱글톤 유틸리티로 사용
    private const val RANKING_PREFIX = "ranking:all:"
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * 일간 랭킹 키 생성
     *
     * @param date 대상 날짜
     * @return ranking:all:{yyyyMMdd} 형식의 키
     */
    fun generateDailyKey(date: LocalDate): String {
        return RANKING_PREFIX + date.format(DATE_FORMATTER)
    }

    /**
     * 오늘 날짜의 랭킹 키 생성
     */
    fun generateTodayKey(): String {
        return generateDailyKey(LocalDate.now())
    }

    /**
     * 문자열 날짜로부터 키 생성
     *
     * @param dateString yyyyMMdd 형식의 날짜 문자열
     * @return ranking:all:{yyyyMMdd} 형식의 키
     * @throws DateTimeParseException 날짜 형식이 잘못된 경우
     */
    fun generateKeyFromString(dateString: String): String {
        val date = LocalDate.parse(dateString, DATE_FORMATTER)
        return generateDailyKey(date)
    }

    /**
     * 키로부터 날짜 추출
     *
     * @param key ranking:all:{yyyyMMdd} 형식의 키
     * @return 추출된 날짜
     */
    fun extractDateFromKey(key: String): LocalDate {
        val dateString = key.removePrefix(RANKING_PREFIX)
        return LocalDate.parse(dateString, DATE_FORMATTER)
    }
}
