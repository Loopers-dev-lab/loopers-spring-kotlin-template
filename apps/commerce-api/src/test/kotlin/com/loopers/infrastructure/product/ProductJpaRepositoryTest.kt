package com.loopers.infrastructure.product

import com.loopers.IntegrationTest
import com.loopers.domain.brand.Brand
import com.loopers.domain.like.ProductLike
import com.loopers.domain.product.Product
import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserCommand
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.like.ProductLikeJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.fixtures.withId
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest

class ProductJpaRepositoryTest @Autowired constructor(
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val productLikeJpaRepository: ProductLikeJpaRepository,
) : IntegrationTest() {

    private lateinit var brand1: Brand
    private lateinit var brand2: Brand
    private lateinit var user1: User
    private lateinit var user2: User

    @BeforeEach
    fun setUp() {
        brand1 = brandJpaRepository.save(Brand(name = "브랜드A")).withId(1L)
        brand2 = brandJpaRepository.save(Brand(name = "브랜드B")).withId(2L)

        user1 = userJpaRepository.save(
            User.singUp(
                UserCommand.SignUp(
                    userId = "user1",
                    email = "user1@test.com",
                    birthDate = "1990-01-01",
                    gender = Gender.MALE,
                ),
            ),
        ).withId(1L)
        user2 = userJpaRepository.save(
            User.singUp(
                UserCommand.SignUp(
                    userId = "user2",
                    email = "user2@test.com",
                    birthDate = "1995-05-05",
                    gender = Gender.FEMALE,
                ),
            ),
        ).withId(2L)
    }

    @Nested
    @DisplayName("브랜드별 상품 조회")
    inner class FindByBrandId {

        @Test
        fun `특정 브랜드의 상품만 조회한다`() {
            // given
            val product1 = productJpaRepository.save(
                Product(name = "상품1", price = 10000, brandId = brand1.id),
            )
            val product2 = productJpaRepository.save(
                Product(name = "상품2", price = 20000, brandId = brand1.id),
            )
            productJpaRepository.save(
                Product(name = "상품3", price = 30000, brandId = brand2.id),
            )

            val pageable = PageRequest.of(0, 10)

            // when
            val result = productJpaRepository.findAllByBrandId(brand1.id, pageable)

            // then
            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(2)
                softly.assertThat(result.content.map { it.id })
                    .containsExactlyInAnyOrder(product1.id, product2.id)
            }
        }

        @Test
        fun `페이징이 정상적으로 동작한다`() {
            // given
            repeat(25) { index ->
                productJpaRepository.save(
                    Product(
                        name = "상품$index",
                        price = 10000L + index,
                        brandId = brand1.id,
                    ),
                )
            }

            val pageable = PageRequest.of(0, 10)

            // when
            val result = productJpaRepository.findAllByBrandId(brand1.id, pageable)

            // then
            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(10)
                softly.assertThat(result.totalElements).isEqualTo(25)
                softly.assertThat(result.totalPages).isEqualTo(3)
            }
        }
    }

    @Nested
    @DisplayName("브랜드별 좋아요 순 정렬 조회")
    inner class FindAllByBrandIdOrderByLikesDesc {

        @Test
        fun `특정 브랜드 상품을 좋아요 많은 순으로 조회한다`() {
            // given
            val product1 = productJpaRepository.save(
                Product(name = "상품1", price = 10000, brandId = brand1.id),
            )
            val product2 = productJpaRepository.save(
                Product(name = "상품2", price = 20000, brandId = brand1.id),
            )
            val product3 = productJpaRepository.save(
                Product(name = "상품3", price = 30000, brandId = brand1.id),
            )

            // product2에 좋아요 2개
            productLikeJpaRepository.save(ProductLike(productId = product2.id, userId = user1.id))
            productLikeJpaRepository.save(ProductLike(productId = product2.id, userId = user2.id))

            // product1에 좋아요 1개
            productLikeJpaRepository.save(ProductLike(productId = product1.id, userId = user1.id))

            // product3에 좋아요 없음

            val pageable = PageRequest.of(0, 10)

            // when
            val result = productJpaRepository.findAllByBrandIdOrderByLikesDesc(brand1.id, pageable)

            // then
            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(3)
                softly.assertThat(result.content[0].id).isEqualTo(product2.id)
                softly.assertThat(result.content[1].id).isEqualTo(product1.id)
                softly.assertThat(result.content[2].id).isEqualTo(product3.id)
            }
        }

        @Test
        fun `다른 브랜드 상품은 조회되지 않는다`() {
            // given
            val product1 = productJpaRepository.save(
                Product(name = "브랜드A 상품", price = 10000, brandId = brand1.id),
            )
            val product2 = productJpaRepository.save(
                Product(name = "브랜드B 상품", price = 20000, brandId = brand2.id),
            )

            // brand2 상품에 더 많은 좋아요
            productLikeJpaRepository.save(ProductLike(productId = product2.id, userId = user1.id))
            productLikeJpaRepository.save(ProductLike(productId = product2.id, userId = user2.id))

            val pageable = PageRequest.of(0, 10)

            // when
            val result = productJpaRepository.findAllByBrandIdOrderByLikesDesc(brand1.id, pageable)

            // then
            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(1)
                softly.assertThat(result.content[0].id).isEqualTo(product1.id)
            }
        }
    }

    @Nested
    @DisplayName("전체 상품 좋아요 순 정렬 조회")
    inner class FindAllOrderByLikesDesc {

        @Test
        fun `모든 브랜드의 상품을 좋아요 많은 순으로 조회한다`() {
            // given
            val product1 = productJpaRepository.save(
                Product(name = "상품1", price = 10000, brandId = brand1.id),
            )
            val product2 = productJpaRepository.save(
                Product(name = "상품2", price = 20000, brandId = brand2.id),
            )
            val product3 = productJpaRepository.save(
                Product(name = "상품3", price = 30000, brandId = brand1.id),
            )

            // product2에 좋아요 2개
            productLikeJpaRepository.save(ProductLike(productId = product2.id, userId = user1.id))
            productLikeJpaRepository.save(ProductLike(productId = product2.id, userId = user2.id))

            // product3에 좋아요 1개
            productLikeJpaRepository.save(ProductLike(productId = product3.id, userId = user1.id))

            // product1에 좋아요 없음

            val pageable = PageRequest.of(0, 10)

            // when
            val result = productJpaRepository.findAllOrderByLikesDesc(pageable)

            // then
            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(3)
                softly.assertThat(result.content[0].id).isEqualTo(product2.id)
                softly.assertThat(result.content[1].id).isEqualTo(product3.id)
                softly.assertThat(result.content[2].id).isEqualTo(product1.id)
            }
        }

        @Test
        fun `좋아요가 없는 상품들도 포함된다`() {
            // given
            val product1 = productJpaRepository.save(
                Product(name = "상품1", price = 10000, brandId = brand1.id),
            )
            val product2 = productJpaRepository.save(
                Product(name = "상품2", price = 20000, brandId = brand1.id),
            )
            val product3 = productJpaRepository.save(
                Product(name = "상품3", price = 30000, brandId = brand1.id),
            )

            // 좋아요 없음

            val pageable = PageRequest.of(0, 10)

            // when
            val result = productJpaRepository.findAllOrderByLikesDesc(pageable)

            // then
            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(3)
                softly.assertThat(result.content.map { it.id })
                    .containsExactlyInAnyOrder(product1.id, product2.id, product3.id)
            }
        }

        @Test
        fun `페이징이 정상적으로 동작한다`() {
            // given
            repeat(25) { index ->
                val product = productJpaRepository.save(
                    Product(
                        name = "상품$index",
                        price = 10000L + index,
                        brandId = brand1.id,
                    ),
                )

                // 첫 번째 상품에만 user1, 두 번째 상품에만 user2 좋아요
                when (index % 2) {
                    0 -> productLikeJpaRepository.save(
                        ProductLike(productId = product.id, userId = user1.id),
                    )

                    1 -> productLikeJpaRepository.save(
                        ProductLike(productId = product.id, userId = user2.id),
                    )
                }
            }

            val pageable = PageRequest.of(1, 10)

            // when
            val result = productJpaRepository.findAllOrderByLikesDesc(pageable)

            // then
            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(10)
                softly.assertThat(result.totalElements).isEqualTo(25)
                softly.assertThat(result.number).isEqualTo(1)
                softly.assertThat(result.totalPages).isEqualTo(3)
            }
        }
    }
}
