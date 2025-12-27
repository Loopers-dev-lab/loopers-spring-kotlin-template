package com.loopers.domain.ranking

/**
 * 랭킹 Repository 인터페이스
 *
 * Redis ZSET을 활용한 일간 상품 랭킹 관리
 */
interface RankingRepository {

    /**
     * 특정 상품의 현재 점수 조회
     *
     * @param dateKey 날짜 키 (yyyyMMdd 형식)
     * @param productId 상품 ID
     * @return 현재 점수, 존재하지 않으면 null
     */
    fun getScore(dateKey: String, productId: Long): Double?

    /**
     * 여러 상품의 점수를 배치로 증가 (Redis Pipeline 사용)
     *
     * @param dateKey 날짜 키 (yyyyMMdd 형식)
     * @param productScores 상품ID와 증가할 점수의 맵
     */
    fun batchIncrementScores(dateKey: String, productScores: Map<Long, Double>)

    /**
     * 여러 상품의 점수를 배치로 감소 (Redis Pipeline 사용, 음수 방지 포함)
     *
     * @param dateKey 날짜 키 (yyyyMMdd 형식)
     * @param productScores 상품ID와 감소할 점수의 맵 (양수 값)
     */
    fun batchDecrementScores(dateKey: String, productScores: Map<Long, Double>)

    /**
     * 당일 랭킹 점수 일부를 익일 키에 이월 (ZUNIONSTORE 사용)
     *
     * @param sourceDateKey 당일 날짜 키 (yyyyMMdd 형식)
     * @param targetDateKey 익일 날짜 키 (yyyyMMdd 형식)
     * @param weight 당일 점수 반영 비율
     */
    fun carryOverScores(sourceDateKey: String, targetDateKey: String, weight: Double)
}
