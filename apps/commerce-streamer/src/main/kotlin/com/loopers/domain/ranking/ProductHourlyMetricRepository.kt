package com.loopers.domain.ranking

/**
 * ProductHourlyMetric Repository 인터페이스
 *
 * - 시간별 상품 집계 데이터 저장
 * - infrastructure/ranking에서 구현
 */
interface ProductHourlyMetricRepository {
    /**
     * 배치로 집계 데이터를 누적 저장
     *
     * - INSERT 시 새 레코드 생성
     * - ON CONFLICT 시 기존 값에 누적 (view_count, like_count, order_count, order_amount)
     */
    fun batchAccumulateCounts(rows: List<ProductHourlyMetricRow>)
}
