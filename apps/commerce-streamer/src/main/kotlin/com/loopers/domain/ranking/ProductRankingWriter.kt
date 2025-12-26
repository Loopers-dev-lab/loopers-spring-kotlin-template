package com.loopers.domain.ranking

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
     * 점수 일괄 증분 업데이트 (Pipeline + ZINCRBY)
     *
     * ZINCRBY는 기존 점수에 더하므로 여러 번 호출해도 누적됨
     * Pipeline을 사용하여 네트워크 왕복을 최소화
     *
     * @param bucketKey Redis 키 (예: "ranking:hourly:2025011514")
     * @param deltas 상품ID -> 점수 증분 맵
     */
    fun incrementScores(bucketKey: String, deltas: Map<Long, Score>)

    /**
     * 전체 점수 교체 (ZADD)
     *
     * 가중치 변경 시 전체 점수 재계산에 사용
     *
     * @param bucketKey Redis 키
     * @param scores 상품ID -> 점수 맵
     */
    fun replaceAll(bucketKey: String, scores: Map<Long, Score>)

    /**
     * 새 버킷 생성 (버킷 전환 시)
     *
     *
     * @param bucketKey Redis 키
     * @param scores 상품ID -> 점수 맵 (이전 버킷의 감쇠된 점수)
     */
    fun createBucket(bucketKey: String, scores: Map<Long, Score>)
}
