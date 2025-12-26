package com.loopers.domain.ranking

/**
 * ProductRankingReader - Redis ZSET 읽기를 위한 Port 인터페이스
 *
 * commerce-streamer에서 버킷 전환 시 필요한 읽기 연산만 제공
 * - getAllScores: 이전 버킷의 전체 점수 조회 (감쇠 계산용)
 * - exists: 버킷 존재 여부 확인
 */
interface ProductRankingReader {

    /**
     * 버킷 내 전체 점수 조회 (버킷 전환용)
     *
     * @param bucketKey Redis 키 (예: "ranking:hourly:2025011514")
     * @return productId → Score 맵
     */
    fun getAllScores(bucketKey: String): Map<Long, Score>

    /**
     * 버킷 존재 여부 확인
     *
     * @param bucketKey Redis 키
     * @return 존재하면 true, 아니면 false
     */
    fun exists(bucketKey: String): Boolean
}
