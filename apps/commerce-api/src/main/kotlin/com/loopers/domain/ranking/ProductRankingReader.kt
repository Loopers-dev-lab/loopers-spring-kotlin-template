package com.loopers.domain.ranking

/**
 * 상품 랭킹 조회를 위한 포트 인터페이스
 * Redis ZSET에서 랭킹 정보를 읽어오는 역할
 */
interface ProductRankingReader {

    /**
     * 랭킹을 RankingQuery 조건으로 조회
     *
     * @param query 조회 조건 (period, dateTime, offset, limit 포함)
     * @return ProductRanking 리스트
     */
    fun findTopRankings(query: RankingQuery): List<ProductRanking>

    /**
     * 특정 상품의 순위 조회
     *
     * @param query 조회 조건 (period, dateTime으로 버킷 결정)
     * @param productId 상품 ID
     * @return 순위 (1-based), 랭킹에 없으면 null
     */
    fun findRankByProductId(query: RankingQuery, productId: Long): Int?

    /**
     * 버킷 존재 여부 확인
     *
     * @param query 조회 조건 (period, dateTime으로 버킷 결정)
     * @return 버킷 존재 여부
     */
    fun exists(query: RankingQuery): Boolean
}
