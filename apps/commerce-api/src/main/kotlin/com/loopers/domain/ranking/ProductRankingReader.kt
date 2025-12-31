package com.loopers.domain.ranking

/**
 * 상품 랭킹 조회를 위한 포트 인터페이스
 * Redis ZSET에서 랭킹 정보를 읽어오는 역할
 */
interface ProductRankingReader {

    /**
     * 상위 N개 랭킹을 페이지네이션하여 조회
     *
     * @param bucketKey Redis 키 (예: "ranking:products:2025011514")
     * @param offset 시작 위치 (0-based)
     * @param limit 조회할 개수
     * @return ProductRanking 리스트 (rank는 1-based)
     */
    fun getTopRankings(bucketKey: String, offset: Long, limit: Long): List<ProductRanking>

    /**
     * 특정 상품의 순위 조회
     *
     * @param bucketKey Redis 키 (예: "ranking:products:2025011514")
     * @param productId 상품 ID
     * @return 순위 (1-based), 랭킹에 없으면 null
     */
    fun getRankByProductId(bucketKey: String, productId: Long): Int?
}
