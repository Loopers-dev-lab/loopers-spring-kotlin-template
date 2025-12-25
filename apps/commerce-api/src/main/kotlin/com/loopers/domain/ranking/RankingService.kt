package com.loopers.domain.ranking

import com.loopers.domain.product.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 랭킹 서비스
 */
@Service
@Transactional(readOnly = true)
class RankingService(private val rankingRepository: RankingRepository, private val productRepository: ProductRepository) {
    private val logger = LoggerFactory.getLogger(RankingService::class.java)

    /**
     * Top-N 랭킹 조회 (페이징)
     *
     * @param window 시간 윈도우 (DAILY, HOURLY)
     * @param timestamp 조회할 시점 (DAILY: yyyyMMdd, HOURLY: yyyyMMddHH)
     * @param page 페이지 번호 (1부터 시작)
     * @param size 페이지 크기
     * @return 랭킹 목록과 전체 개수
     */
    fun getTopN(
        window: TimeWindow,
        timestamp: String,
        page: Int,
        size: Int,
    ): Pair<List<Ranking>, Long> {
        require(page >= 1) { "페이지 번호는 1 이상이어야 합니다: page=$page" }
        require(size > 0) { "페이지 크기는 0보다 커야 합니다: size=$size" }

        val key = try {
            when (window) {
                TimeWindow.DAILY -> {
                    val date = LocalDate.parse(timestamp, DateTimeFormatter.ofPattern("yyyyMMdd"))
                    RankingKey.daily(RankingScope.ALL, date)
                }

                TimeWindow.HOURLY -> {
                    val dateTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyyMMddHH"))
                    RankingKey.hourly(RankingScope.ALL, dateTime)
                }
            }
        } catch (e: DateTimeParseException) {
            val expectedFormat = when (window) {
                TimeWindow.DAILY -> "yyyyMMdd (예: 20250906)"
                TimeWindow.HOURLY -> "yyyyMMddHH (예: 2025090614)"
            }
            throw IllegalArgumentException(
                "잘못된 날짜/시간 형식입니다. 예상 형식: $expectedFormat, 입력값: $timestamp",
                e,
            )
        }

        // ZSET 인덱스는 0부터 시작
        val start = (page - 1) * size
        val end = start + size - 1

        val rankings = rankingRepository.getTopN(key, start, end)
        val totalCount = rankingRepository.getCount(key)

        logger.debug(
            "랭킹 조회 완료: window=$window, timestamp=$timestamp, " +
                "page=$page, size=$size, count=${rankings.size}, totalCount=$totalCount",
        )

        return rankings to totalCount
    }

    /**
     * 특정 상품의 현재 랭킹 조회
     *
     * @param productId 상품 ID
     * @param window 시간 윈도우
     * @return 랭킹 정보 (순위가 없으면 null)
     */
    fun getProductRanking(productId: Long, window: TimeWindow): Ranking? {
        val key = when (window) {
            TimeWindow.DAILY -> RankingKey.currentDaily(RankingScope.ALL)
            TimeWindow.HOURLY -> RankingKey.currentHourly(RankingScope.ALL)
        }

        val rank = rankingRepository.getRank(key, productId) ?: return null
        val score = rankingRepository.getScore(key, productId) ?: return null

        return Ranking(
            productId = productId,
            score = score,
            rank = rank,
        )
    }

    /**
     * 상품 ID 목록으로 상품 엔티티 조회 (순서 유지)
     *
     * @param productIds 상품 ID 목록
     * @return 상품 엔티티 맵 (productId -> Product)
     */
    fun findProductsByIds(productIds: List<Long>): Map<Long, com.loopers.domain.product.Product> {
        val products = productRepository.findAllById(productIds)
        return products.associateBy { it.id!! }
    }
}
