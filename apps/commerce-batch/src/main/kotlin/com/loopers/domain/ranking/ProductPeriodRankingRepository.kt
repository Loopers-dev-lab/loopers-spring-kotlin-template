package com.loopers.domain.ranking

import java.time.LocalDate

/**
 * ProductPeriodRankingRepository 인터페이스
 *
 * - 주간/월간 상품 인기 랭킹 저장 및 삭제
 * - 배치 작업에서 사용
 * - infrastructure/ranking에서 구현
 */
interface ProductPeriodRankingRepository {

    /**
     * 특정 날짜의 주간 랭킹을 삭제한다
     *
     * @param baseDate 삭제할 기준 날짜
     * @return 삭제된 레코드 수
     */
    fun deleteWeeklyByBaseDate(baseDate: LocalDate): Int

    /**
     * 특정 날짜의 월간 랭킹을 삭제한다
     *
     * @param baseDate 삭제할 기준 날짜
     * @return 삭제된 레코드 수
     */
    fun deleteMonthlyByBaseDate(baseDate: LocalDate): Int

    /**
     * 주간 랭킹 데이터를 일괄 저장한다
     *
     * @param rankings 저장할 주간 랭킹 목록
     */
    fun saveAllWeekly(rankings: List<MvProductRankWeekly>)

    /**
     * 월간 랭킹 데이터를 일괄 저장한다
     *
     * @param rankings 저장할 월간 랭킹 목록
     */
    fun saveAllMonthly(rankings: List<MvProductRankMonthly>)
}
