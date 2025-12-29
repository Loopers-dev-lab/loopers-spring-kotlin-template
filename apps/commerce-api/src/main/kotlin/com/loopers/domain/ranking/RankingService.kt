package com.loopers.domain.ranking

import com.loopers.domain.product.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
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
class RankingService(
    private val rankingRepository: RankingRepository,
    private val productRepository: ProductRepository,
    private val productRankWeeklyRepository: ProductRankWeeklyRepository,
    private val productRankMonthlyRepository: ProductRankMonthlyRepository,
) {
    private val logger = LoggerFactory.getLogger(RankingService::class.java)

    /**
     * Top-N 랭킹 조회 (페이징)
     *
     * @param window 시간 윈도우 (DAILY, HOURLY, WEEKLY, MONTHLY)
     * @param timestamp 조회할 시점
     *   - DAILY: yyyyMMdd (예: 20250906)
     *   - HOURLY: yyyyMMddHH (예: 2025090614)
     *   - WEEKLY: yyyyWww (예: 2025W01)
     *   - MONTHLY: yyyyMM (예: 202501)
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

        return when (window) {
            TimeWindow.DAILY, TimeWindow.HOURLY -> getTopNFromRedis(window, timestamp, page, size)
            TimeWindow.WEEKLY -> getTopNFromWeeklyDB(timestamp, page, size)
            TimeWindow.MONTHLY -> getTopNFromMonthlyDB(timestamp, page, size)
        }
    }

    /**
     * Redis에서 랭킹 조회 (DAILY, HOURLY)
     */
    private fun getTopNFromRedis(
        window: TimeWindow,
        timestamp: String,
        page: Int,
        size: Int,
    ): Pair<List<Ranking>, Long> {
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

                else -> throw IllegalArgumentException("Redis 기반 랭킹은 DAILY, HOURLY만 지원합니다")
            }
        } catch (e: DateTimeParseException) {
            val expectedFormat = when (window) {
                TimeWindow.DAILY -> "yyyyMMdd (예: 20250906)"
                TimeWindow.HOURLY -> "yyyyMMddHH (예: 2025090614)"
                else -> "unknown"
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
            "랭킹 조회 완료 (Redis): window=$window, timestamp=$timestamp, " +
                    "page=$page, size=$size, count=${rankings.size}, totalCount=$totalCount",
        )

        return rankings to totalCount
    }

    /**
     * DB에서 주간 랭킹 조회
     */
    private fun getTopNFromWeeklyDB(
        timestamp: String,
        page: Int,
        size: Int,
    ): Pair<List<Ranking>, Long> {
        // timestamp 형식: yyyyWww (예: 2025W01)
        val yearWeek = try {
            require(timestamp.length == 7 && timestamp[4] == 'W') {
                "주간 랭킹 형식은 yyyyWww 형식이어야 합니다 (예: 2025W01)"
            }

            // 연도와 주차 추출 및 검증
            val year = timestamp.substring(0, 4).toIntOrNull()
                ?: throw IllegalArgumentException("유효하지 않은 연도입니다: ${timestamp.substring(0, 4)}")
            val week = timestamp.substring(5, 7).toIntOrNull()
                ?: throw IllegalArgumentException("유효하지 않은 주차입니다: ${timestamp.substring(5, 7)}")

            require(year in 1970..2040) {
                "연도는 1970부터 2040 사이여야 합니다: $year"
            }
            require(week in 1..53) {
                "주차는 1부터 53 사이여야 합니다: $week"
            }
            try {
                LocalDate.parse(
                    "${year}W${week.toString().padStart(2, '0')}-1",
                    DateTimeFormatter.ISO_WEEK_DATE,
                )
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("해당 연도에 $week 주차가 존재하지 않습니다: $year", e)
            }

            timestamp
        } catch (e: Exception) {
            throw IllegalArgumentException("잘못된 주간 형식입니다: $timestamp", e)
        }

        // DB에서 조회
        val pageResult = productRankWeeklyRepository.findByYearWeekOrderByRankAsc(yearWeek, PageRequest.of(page - 1, size))
        val pagedRankings = pageResult
            .map {
                Ranking(
                    productId = it.productId,
                    score = RankingScore(it.score),
                    rank = it.rank,
                )
            }.toList()
        val totalCount = pageResult.totalElements

        logger.debug(
            "랭킹 조회 완료 (주간 DB): yearWeek=$yearWeek, " +
                    "page=$page, size=$size, count=${pagedRankings.size}, totalCount=$totalCount",
        )

        return pagedRankings to totalCount
    }

    /**
     * DB에서 월간 랭킹 조회
     */
    private fun getTopNFromMonthlyDB(
        timestamp: String,
        page: Int,
        size: Int,
    ): Pair<List<Ranking>, Long> {
        // timestamp 형식: yyyyMM (예: 202501)
        val yearMonth = try {
            require(timestamp.length == 6) {
                "월간 랭킹 형식은 yyyyMM 형식이어야 합니다 (예: 202501)"
            }

            // 연도와 월 추출 및 검증
            val year = timestamp.substring(0, 4).toIntOrNull()
                ?: throw IllegalArgumentException("유효하지 않은 연도입니다: ${timestamp.substring(0, 4)}")
            val month = timestamp.substring(4, 6).toIntOrNull()
                ?: throw IllegalArgumentException("유효하지 않은 월입니다: ${timestamp.substring(4, 6)}")

            require(year in 1970..2040) {
                "연도는 1970부터 2040 사이여야 합니다: $year"
            }
            require(month in 1..12) {
                "월은 1부터 12 사이여야 합니다: $month"
            }

            timestamp
        } catch (e: Exception) {
            throw IllegalArgumentException("잘못된 월간 형식입니다: $timestamp", e)
        }

        // DB에서 조회
        val pageResult = productRankMonthlyRepository.findByYearMonthOrderByRankAsc(yearMonth, PageRequest.of(page - 1, size))
        val pagedRankings = pageResult
            .map {
                Ranking(
                    productId = it.productId,
                    score = RankingScore(it.score),
                    rank = it.rank,
                )
            }.toList()
        val totalCount = pageResult.totalElements

        logger.debug(
            "랭킹 조회 완료 (월간 DB): yearMonth=$yearMonth, " +
                    "page=$page, size=$size, count=${pagedRankings.size}, totalCount=$totalCount",
        )

        return pagedRankings to totalCount
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

            TimeWindow.WEEKLY, TimeWindow.MONTHLY -> {
                // 주간/월간은 현재 랭킹 개념이 없으므로 null 반환
                return null
            }
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
        return products.associateBy { it.id }
    }
}
