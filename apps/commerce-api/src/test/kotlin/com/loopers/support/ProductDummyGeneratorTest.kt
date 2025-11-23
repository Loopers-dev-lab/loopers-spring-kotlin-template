package com.loopers.support

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.ProductLike
import com.loopers.domain.like.ProductLikeCount
import com.loopers.domain.product.Product
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.like.ProductLikeCountJpaRepository
import com.loopers.infrastructure.like.ProductLikeJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.random.Random

/**
 * Product 더미 데이터 (10만개)
 *
 * 다양한 가격 분포 (파레토 법칙 적용)
 *
 * 70%: 1만원~5만원 (저가)
 * 20%: 5만원~20만원 (중가)
 * 10%: 20만원~100만원 (고가)
 *
 *
 * 브랜드 분포 (파레토 법칙)
 *
 * 80%: 상위 20개 브랜드에 집중
 * 20%: 나머지 80개 브랜드에 분산
 *
 * 배치 처리 (1,000개씩 100회)로 메모리 효율성 확보
 *
 * ProductLike 더미 데이터 (50만개)
 *
 * 1,000명의 사용자가 생성
 * 중복 방지 로직 (같은 사용자가 같은 상품 중복 좋아요 불가)
 * 배치 처리 (5,000개씩 100회)
 *
 */
@SpringBootTest
@ActiveProfiles("local")
@Disabled("상품 데이터 더미 생성")
class ProductDummyDateGeneratorTest {

    private val log = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var brandJpaRepository: BrandJpaRepository

    @Autowired
    private lateinit var productJpaRepository: ProductJpaRepository

    @Autowired
    private lateinit var productLikeJpaRepository: ProductLikeJpaRepository

    @Autowired
    private lateinit var productLikeCountJpaRepository: ProductLikeCountJpaRepository

    @Test
    fun `브랜드 - 상품 데이터 생성`() {
        // 1. 브랜드 생성 (100개)
        log.info("브랜드 생성 중...")
        val brands = createBrands(100)
        brandJpaRepository.saveAll(brands)
        val brandIds = brands.map { it.id }
        log.info("브랜드 {}개 생성 완료", brands.size)

        // 2. 상품 생성 (10만개) - 배치 처리
        log.info("상품 생성 중...")
        val batchSize = 1000
        var totalCreated = 0

        repeat(100) { batchIndex ->
            val products = createProducts(
                count = batchSize,
                brandIds = brandIds,
                startIndex = batchIndex * batchSize,
            )
            productJpaRepository.saveAll(products)
            totalCreated += products.size

            if ((batchIndex + 1) % 10 == 0) {
                log.info("진행률: {}/100,000", totalCreated)
            }
        }
        log.info("상품 {}개 생성 완료", totalCreated)
    }

    @Test
    fun `상품 좋아요 데이터 생성`() {
        // 1. 상품 ID 조회
        val productIds = productJpaRepository.findAll().map { it.id }
        require(productIds.isNotEmpty()) { "상품 데이터가 없습니다. generateProductDummyData()를 먼저 실행하세요." }
        log.info("상품 {}개 조회 완료", productIds.size)

        // 2. 좋아요 생성 (50만개) - 배치 처리
        log.info("좋아요 생성 중...")
        val batchSize = 5000
        var totalCreated = 0
        val createdPairs = mutableSetOf<Pair<Long, Long>>() // 중복 방지

        // 가상의 사용자 ID 풀 (1~1000번)
        val userIdPool = (1L..1000L).toList()

        repeat(100) { batchIndex ->
            val likes = mutableListOf<ProductLike>()

            while (likes.size < batchSize) {
                val userId = userIdPool.random()
                val productId = productIds.random()
                val pair = Pair(userId, productId)

                if (!createdPairs.contains(pair)) {
                    likes.add(ProductLike.create(productId, userId))
                    createdPairs.add(pair)
                }
            }

            productLikeJpaRepository.saveAll(likes)
            totalCreated += likes.size

            if ((batchIndex + 1) % 10 == 0) {
                log.info("진행률: {}/500,000", totalCreated)
            }
        }
        log.info("좋아요 {}개 생성 완료", totalCreated)
    }

    @Test
    fun `상품 좋아요 카운트 데이터 생성`() {
        // 1. 전체 상품 ID 조회
        val productIds = productJpaRepository.findAll().map { it.id }
        require(productIds.isNotEmpty()) { "상품 데이터가 없습니다." }
        log.info("상품 {}개 조회 완료", productIds.size)

        // 2. ProductLike 데이터 기반으로 집계
        log.info("좋아요 집계 중...")
        val likeCountMap = productLikeJpaRepository.findAll()
            .groupBy { it.productId }
            .mapValues { it.value.size.toLong() }

        log.info("집계 완료: {}개 상품에 좋아요 존재", likeCountMap.size)

        // 3. ProductLikeCount 생성 (배치 처리)
        log.info("ProductLikeCount 생성 중...")
        val batchSize = 1000
        var totalCreated = 0

        productIds.chunked(batchSize).forEachIndexed { batchIndex, productIdBatch ->
            val productLikeCounts = productIdBatch.map { productId ->
                ProductLikeCount.create(
                    productId = productId,
                    likeCount = likeCountMap[productId] ?: 0L
                )
            }

            productLikeCountJpaRepository.saveAll(productLikeCounts)
            totalCreated += productLikeCounts.size

            if ((batchIndex + 1) % 10 == 0) {
                log.info("진행률: {}/{}", totalCreated, productIds.size)
            }
        }

        log.info("ProductLikeCount {}개 생성 완료", totalCreated)

        // 4. 검증
        val totalLikes = likeCountMap.values.sum()
        val savedTotalLikes = productLikeCountJpaRepository.findAll().sumOf { it.likeCount }
        log.info("검증 - 원본 좋아요 수: {}, 저장된 좋아요 수: {}", totalLikes, savedTotalLikes)
        require(totalLikes == savedTotalLikes) { "좋아요 수 불일치!" }
    }

    private fun createBrands(count: Int): List<Brand> {
        return (1..count).map { index ->
            Brand.create(name = "브랜드_$index")
        }
    }

    private fun createProducts(count: Int, brandIds: List<Long>, startIndex: Int): List<Product> {
        val categories = listOf("의류", "신발", "가방", "액세서리", "전자기기", "도서", "식품", "화장품")
        val adjectives = listOf("프리미엄", "베스트", "신상", "인기", "한정판", "특가", "추천", "고급")

        return (1..count).map { index ->
            val globalIndex = startIndex + index
            val category = categories.random()
            val adjective = adjectives.random()

            // 가격 분포: 파레토 법칙 적용 (대부분 저가, 일부 고가)
            val price = when (Random.nextInt(100)) {
                in 0..69 -> Random.nextLong(10_000, 50_000) // 70% - 저가
                in 70..89 -> Random.nextLong(50_000, 200_000) // 20% - 중가
                else -> Random.nextLong(200_000, 1_000_000) // 10% - 고가
            }

            // 브랜드 분포: 상위 20% 브랜드에 80% 상품 집중
            val brandId = if (Random.nextInt(100) < 80) {
                // 상위 20개 브랜드 중 선택
                brandIds.take(20).random()
            } else {
                // 나머지 브랜드 중 선택
                brandIds.drop(20).random()
            }

            Product.create(
                name = "${adjective}_${category}_상품_$globalIndex",
                price = price,
                brandId = brandId,
            )
        }
    }
}
