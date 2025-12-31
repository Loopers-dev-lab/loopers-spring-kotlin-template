package com.loopers.domain.ranking

import java.time.LocalDate

/**
 * ProductDailyMetric Repository 인터페이스
 *
 * - 일별 상품 집계 데이터 저장
 * - infrastructure/ranking에서 구현
 */
interface ProductDailyMetricRepository {
    /**
     * 일별 집계 데이터를 일괄 upsert
     *
     * - INSERT 시 새 레코드 생성
     * - ON CONFLICT 시 기존 값을 덮어씀
     */
    fun upsertFromHourly(dailyMetrics: List<ProductDailyMetric>)

    /**
     * 특정 날짜의 모든 집계 데이터를 조회
     *
     * @param statDate 조회할 날짜
     * @return 해당 날짜의 모든 ProductDailyMetric 목록
     */
    fun findAllByStatDate(statDate: LocalDate): List<ProductDailyMetric>
}
