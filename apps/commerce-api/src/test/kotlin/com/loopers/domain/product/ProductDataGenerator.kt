package com.loopers.domain.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.random.Random

@SpringBootTest
@ActiveProfiles("local")
@Disabled("데이터 생성용 테스트 - 필요시에만 실행")
class ProductDataGenerator @Autowired constructor(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val stockRepository: StockRepository,
    private val userRepository: UserRepository,
    private val likeRepository: LikeRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Test
    @Transactional
    fun `10만개 상품 데이터 생성`() {
        logger.info("=== 데이터 생성 시작 ===")

        // 1. 브랜드 생성 (100개)
        val brands = createBrands(100)
        logger.info("브랜드 ${brands.size}개 생성 완료")

        // 2. 유저 생성 (1000명)
        val users = createUsers(1000)
        logger.info("유저 ${users.size}명 생성 완료")

        // 3. 상품 생성 (10만개)
        val products = createProducts(100_000, brands)
        logger.info("상품 ${products.size}개 생성 완료")

        // 4. 재고 생성
        createStocks(products)
        logger.info("재고 ${products.size}개 생성 완료")

        // 5. 좋아요 데이터 생성 (랜덤)
        createLikes(users, products)
        logger.info("좋아요 데이터 생성 완료")

        logger.info("=== 데이터 생성 완료 ===")
    }

    private fun createBrands(count: Int): List<Brand> {
        val brands = mutableListOf<Brand>()
        for (i in 1..count) {
            val brand = Brand(
                name = "브랜드_$i",
                description = "브랜드 $i 설명",
            )
            brands.add(brandRepository.save(brand))
        }
        return brands
    }

    private fun createUsers(count: Int): List<User> {
        val users = mutableListOf<User>()
        for (i in 1..count) {
            val user = User(
                name = "사용자_$i",
                email = "user$i@example.com",
                gender = if (i % 2 == 0) Gender.MALE else Gender.FEMALE,
                birthDate = java.time.LocalDate.of(1990, 1, 1).plusDays(i.toLong()),
            )
            users.add(userRepository.save(user))
        }
        return users
    }

    private fun createProducts(count: Int, brands: List<Brand>): List<Product> {
        val products = mutableListOf<Product>()
        val batchSize = 1000

        for (i in 1..count) {
            val brand = brands.random()
            val price = Price(
                amount = BigDecimal.valueOf(Random.nextLong(1000, 1000000)),
                currency = Currency.KRW,
            )
            val product = Product(
                name = "상품_$i",
                price = price,
                brand = brand,
            )
            products.add(product)

            // 배치 저장
            if (i % batchSize == 0) {
                productRepository.saveAll(products.takeLast(batchSize))
                logger.info("상품 $i 개 저장 완료...")
            }
        }

        // 남은 데이터 저장
        val remaining = count % batchSize
        if (remaining > 0) {
            productRepository.saveAll(products.takeLast(remaining))
        }

        return products
    }

    private fun createStocks(products: List<Product>) {
        val stocks = mutableListOf<Stock>()
        val batchSize = 1000

        products.forEachIndexed { index, product ->
            val stock = Stock(
                productId = product.id,
                quantity = Random.nextInt(0, 1000),
            )
            stocks.add(stock)

            if ((index + 1) % batchSize == 0) {
                stockRepository.saveAll(stocks.takeLast(batchSize))
                logger.info("재고 ${index + 1} 개 저장 완료...")
            }
        }

        // 남은 데이터 저장
        val remaining = products.size % batchSize
        if (remaining > 0) {
            stockRepository.saveAll(stocks.takeLast(remaining))
        }
    }

    private fun createLikes(users: List<User>, products: List<Product>) {
        val likes = mutableListOf<Like>()
        val batchSize = 1000
        var count = 0

        // 상품의 20%에 대해 랜덤하게 좋아요 생성
        val productsToLike = products.shuffled().take((products.size * 0.2).toInt())

        productsToLike.forEach { product ->
            // 각 상품마다 0~50명의 유저가 좋아요
            val likeCount = Random.nextInt(0, 51)
            val usersWhoLike = users.shuffled().take(likeCount)

            usersWhoLike.forEach { user ->
                val like = Like(
                    userId = user.id,
                    productId = product.id,
                )
                likes.add(like)
                count++

                if (count % batchSize == 0) {
                    likeRepository.saveAll(likes.takeLast(batchSize))
                    // Product의 likeCount도 업데이트
                    logger.info("좋아요 $count 개 저장 완료...")
                }
            }
        }

        // 남은 데이터 저장
        val remaining = count % batchSize
        if (remaining > 0) {
            likeRepository.saveAll(likes.takeLast(remaining))
        }

        logger.info("총 좋아요 $count 개 생성 완료")

        // 각 상품의 likeCount 업데이트
        updateProductLikeCounts(products)
    }

    private fun updateProductLikeCounts(products: List<Product>) {
        logger.info("상품별 좋아요 수 업데이트 시작...")
        val batchSize = 1000

        products.forEachIndexed { index, product ->
            val likeCount = likeRepository.countByProductId(product.id)
            product.setLikeCount(likeCount)

            if ((index + 1) % batchSize == 0) {
                logger.info("상품 좋아요 수 ${index + 1} 개 업데이트 완료...")
            }
        }

        logger.info("상품별 좋아요 수 업데이트 완료")
    }
}
