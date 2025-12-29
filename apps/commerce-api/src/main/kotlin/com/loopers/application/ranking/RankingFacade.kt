package com.loopers.application.ranking

import com.loopers.application.product.ProductInfo
import com.loopers.domain.product.ProductService
import com.loopers.domain.ranking.RankingService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.collections.mapIndexedNotNull

/**
 * 랭킹 조회 Facade (Application Layer)
 *
 * 역할:
 * 1. Redis에서 랭킹 데이터 조회
 * 2. 상품 정보 Aggregation (ID → 전체 정보)
 * 3. 페이지네이션 처리
 *
 * 왜 상품 정보를 함께 반환하는가?
 * - 프론트엔드가 productId만 받으면 N번의 추가 API 호출 필요
 * - Aggregation으로 1번의 요청으로 모든 정보 제공
 * - 사용자 경험 개선 (빠른 로딩)
 */
@Component
class RankingFacade(
    private val rankingService: RankingService,
    private val productService: ProductService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * 랭킹 페이지 조회 (상품 정보 포함)
     *
     * @param dateStr 날짜 문자열 (yyyyMMdd, null이면 오늘)
     * @param pageable 페이지 정보 (page, size)
     * @return 상품 정보가 포함된 랭킹 페이지
     */
    fun getRankings(dateStr: String?, pageable: Pageable): Page<RankingInfo> {
        val date = dateStr?.let { LocalDate.parse(it, dateFormatter) } ?: LocalDate.now()

        // 페이지네이션 계산 (0-based)
        val start = pageable.offset  // page * size
        val end = start + pageable.pageSize - 1

        logger.debug("랭킹 조회: date=$date, start=$start, end=$end")

        // 1. Redis에서 랭킹 데이터 조회
        val rankings = rankingService.getProductsInRange(date, start, end)

        if (rankings.isEmpty()) {
            logger.info("랭킹 데이터 없음: date=$date")
            return Page.empty(pageable)
        }

        // 2. 상품 ID 추출
        val productIds = rankings.map { it.first }

        // 3. 상품 정보 조회 (ProductService 직접 사용)
        val products = productService.getProductsByIds(productIds)
            .map { ProductInfo.from(it) }
            .associateBy { it.id }

        // 4. 랭킹 정보와 상품 정보 결합
        val rankingInfos = rankings.mapIndexedNotNull { index, (productId, score) ->
            val product = products[productId]
            if (product == null) {
                logger.warn("상품 정보 없음: productId=$productId (삭제된 상품일 수 있음)")
                null
            } else {
                RankingInfo(
                    product = product,
                    rank = start + index + 1,  // 0-based → 1-based
                    score = score
                )
            }
        }

        // 5. 전체 개수 조회 (ZCARD)
        val totalElements = rankingService.getRankingSize(date)

        return PageImpl(rankingInfos, pageable, totalElements)
    }

    /**
     * 특정 상품의 랭킹 정보 조회
     *
     * @param productId 상품 ID
     * @param dateStr 날짜 문자열 (yyyyMMdd, null이면 오늘)
     * @return 랭킹 정보 (순위권에 없으면 null)
     */
    fun getProductRank(productId: Long, dateStr: String?): RankingInfo? {
        val date = dateStr?.let { LocalDate.parse(it, dateFormatter) } ?: LocalDate.now()

        // 1. Redis에서 순위 조회 (0-based)
        val rank = rankingService.getProductRank(date, productId) ?: return null
        val score = rankingService.getProductScore(date, productId) ?: return null

        // 2. 상품 정보 조회
        val product = productService.getProduct(productId)
        val productInfo = ProductInfo.from(product)

        return RankingInfo(
            product = productInfo,
            rank = rank + 1,  // 0-based → 1-based
            score = score
        )
    }
}

/**
 * 랭킹 정보 (상품 정보 + 순위 + 점수)
 *
 * @property product 상품 정보
 * @property rank 순위 (1부터 시작)
 * @property score 랭킹 점수
 */
data class RankingInfo(
    val product: ProductInfo,
    val rank: Long,
    val score: Double
)
