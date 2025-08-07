package com.loopers.application.like

import com.loopers.application.product.ProductFacade
import com.loopers.domain.brand.entity.Brand
import com.loopers.domain.like.LikeCountRepository
import com.loopers.domain.like.dto.command.LikeCommand
import com.loopers.domain.like.dto.command.LikeCommand.AddLike
import com.loopers.domain.like.dto.criteria.LikeCriteria
import com.loopers.domain.like.vo.LikeTarget.Type.PRODUCT
import com.loopers.domain.product.dto.command.ProductCommand
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
class LikeFacadeIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val brandRepository: BrandJpaRepository,
    private val likeFacade: LikeFacade,
    private val likeCountRepository: LikeCountRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("조회 테스트")
    @Nested
    inner class Find {
        @Test
        fun `페이징 조회`() {
            // given
            val brand1 = brandRepository.save(Brand.create("BrandName1", "BrandDescription1"))
            val brand2 = brandRepository.save(Brand.create("BrandName2", "BrandDescription2"))

            val productDetail1 = productFacade.registerProduct(ProductCommand.RegisterProduct(brand1.id, "name1", "description1", BigDecimal(1)))
            val productDetail2 = productFacade.registerProduct(ProductCommand.RegisterProduct(brand1.id, "name2", "description2", BigDecimal(3)))
            val productDetail3 = productFacade.registerProduct(ProductCommand.RegisterProduct(brand2.id, "name3", "description3", BigDecimal(2)))

            likeFacade.addLike(AddLike(1, productDetail1.id, PRODUCT))
            likeFacade.addLike(AddLike(1, productDetail2.id, PRODUCT))

            // when
            val likeDetails = likeFacade.getLikesForProduct(LikeCriteria.FindAll(userId = 1, type = PRODUCT))

            // then
            assertThat(true)
        }
    }

    @DisplayName("동시성 테스트")
    @Nested
    inner class Concurrency {

        @Test
        fun `여러 유저가 동시에 같은 상품에 좋아요 요청을 보내도 좋아요 수는 정확히 반영되어야 한다`() {
            // given
            val userCount = 20
            val brand = brandRepository.save(Brand.create("브랜드", "설명"))
            val product = productFacade.registerProduct(
                ProductCommand.RegisterProduct(brand.id, "상품", "설명", BigDecimal(1000)),
            )

            val latch = CountDownLatch(userCount)
            val executor = Executors.newFixedThreadPool(userCount)

            repeat(userCount) { i ->
                executor.submit {
                    try {
                        likeFacade.addLike(AddLike(userId = (i + 1).toLong(), product.id, PRODUCT))
                    } catch (e: Exception) {
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()

            // when
            val likeCount = likeCountRepository.findCountByTargetIdAndType(product.id, PRODUCT)

            // then
            assertThat(likeCount?.count?.value?.toInt()).isEqualTo(userCount)
        }

        @Test
        fun `여러 유저가 동시에 같은 상품에 좋아요 취소를 요청해도 최종 좋아요 수는 0이어야 한다`() {
            // given
            val userCount = 20
            val brand = brandRepository.save(Brand.create("브랜드", "설명"))
            val productDetail = productFacade.registerProduct(
                ProductCommand.RegisterProduct(brand.id, "상품", "설명", BigDecimal(1000)),
            )

            repeat(userCount) { i ->
                likeFacade.addLike(AddLike(userId = (i + 1).toLong(), productDetail.id, PRODUCT))
            }

            val createLikeCount = likeCountRepository.findCountByTargetIdAndType(productDetail.id, PRODUCT)
            assertThat(createLikeCount?.count?.value?.toInt()).isEqualTo(userCount)

            val latch = CountDownLatch(userCount)
            val executor = Executors.newFixedThreadPool(userCount)

            repeat(userCount) { i ->
                executor.submit {
                    try {
                        likeFacade.removeLike(LikeCommand.RemoveLike(userId = (i + 1).toLong(), productDetail.id, PRODUCT))
                    } catch (e: Exception) {
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()

            // when
            val likeCount = likeCountRepository.findCountByTargetIdAndType(productDetail.id, PRODUCT)

            // then
            assertThat(likeCount?.count?.value).isEqualTo(0)
        }
    }
}
