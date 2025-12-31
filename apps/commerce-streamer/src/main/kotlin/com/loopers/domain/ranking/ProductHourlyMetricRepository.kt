package com.loopers.domain.ranking

import java.time.Instant

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

    /**
     * 특정 시간 버킷의 모든 집계 데이터를 조회
     *
     * @param statHour 조회할 시간 버킷 (시간 단위로 truncate된 Instant)
     * @return 해당 시간 버킷의 모든 ProductHourlyMetric 목록
     */
    fun findAllByStatHour(statHour: Instant): List<ProductHourlyMetric>
}
