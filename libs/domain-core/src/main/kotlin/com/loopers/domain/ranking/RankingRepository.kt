package com.loopers.domain.ranking

/**
 * 랭킹 저장소 인터페이스
 */
interface RankingRepository {
    /**
     * 상품의 랭킹 점수 증가 (ZINCRBY)
     *
     * @param key 랭킹 키
     * @param productId 상품 ID
     * @param score 증가할 점수
     * @return 갱신된 총 점수
     */
    fun incrementScore(key: RankingKey, productId: Long, score: RankingScore): Double

    /**
     * 상품의 랭킹 점수 배치 증가
     *
     * @param key 랭킹 키
     * @param scoreMap 상품 ID -> 증가할 점수 맵
     */
    fun incrementScoreBatch(key: RankingKey, scoreMap: Map<Long, RankingScore>)

    /**
     * Top-N 랭킹 조회 (ZREVRANGE)
     *
     * @param key 랭킹 키
     * @param start 시작 인덱스 (0부터)
     * @param end 종료 인덱스 (inclusive)
     * @return 랭킹 목록
     */
    fun getTopN(key: RankingKey, start: Int, end: Int): List<Ranking>

    /**
     * 특정 상품의 순위 조회 (ZREVRANK)
     *
     * @param key 랭킹 키
     * @param productId 상품 ID
     * @return 순위 (1부터 시작, 없으면 null)
     */
    fun getRank(key: RankingKey, productId: Long): Int?

    /**
     * 특정 상품의 점수 조회 (ZSCORE)
     *
     * @param key 랭킹 키
     * @param productId 상품 ID
     * @return 점수 (없으면 null)
     */
    fun getScore(key: RankingKey, productId: Long): RankingScore?

    /**
     * 랭킹 항목 수 조회 (ZCARD)
     *
     * @param key 랭킹 키
     * @return 항목 수
     */
    fun getCount(key: RankingKey): Long

    /**
     * TTL 설정
     * TTL 기간은 RankingKey의 타입(daily/hourly)에 따라 구현체에서 결정됩니다.
     * - Daily 랭킹: 2일
     * - Hourly 랭킹: 1일
     *
     * @param key 랭킹 키
     */
    fun setExpire(key: RankingKey)

    /**
     * 이전 랭킹 데이터를 가중치를 곱해서 새 랭킹으로 복사 (ZUNIONSTORE)
     * 콜드 스타트 방지용
     *
     * @param sourceKey 원본 랭킹 키
     * @param targetKey 대상 랭킹 키
     * @param weight 가중치 (0.1 = 10%)
     */
    fun copyWithWeight(sourceKey: RankingKey, targetKey: RankingKey, weight: Double)
}
