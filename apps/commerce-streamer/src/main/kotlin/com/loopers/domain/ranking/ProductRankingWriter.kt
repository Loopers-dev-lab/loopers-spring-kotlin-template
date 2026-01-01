package com.loopers.domain.ranking

import java.time.ZonedDateTime

/**
 * ProductRankingWriter - Redis ZSET 쓰기를 위한 Port 인터페이스
 *
 * 인프라스트럭처 계층에서 Redis ZSET 연산으로 구현됨
 * - ZINCRBY: 기존 점수에 추가
 * - ZADD: 전체 교체
 * - Pipeline: 배치 연산 최적화
 */
interface ProductRankingWriter {
    /**
     * 전체 점수 교체 (ZADD)
     *
     * 가중치 변경 시 전체 점수 재계산에 사용
     *
     * @param period 랭킹 기간 (HOURLY, DAILY)
     * @param dateTime 버킷 기준 시간
     * @param scores 상품ID -> 점수 맵
     */
    fun replaceAll(period: RankingPeriod, dateTime: ZonedDateTime, scores: Map<Long, Score>)
}
