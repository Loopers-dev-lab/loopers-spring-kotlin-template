package com.loopers.infrastructure.product

import com.loopers.IntegrationTest
import com.loopers.application.dto.PageResult
import com.loopers.application.product.ProductResult
import com.loopers.domain.product.ProductSort
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import java.time.ZonedDateTime

@DisplayName("ProductCacheImpl 통합 테스트")
class ProductCacheImplTest : IntegrationTest() {

    @Autowired
    private lateinit var productCache: ProductCacheImpl

    @Autowired
    private lateinit var productListRedisTemplate: RedisTemplate<String, *>

    @Autowired
    private lateinit var productDetailRedisTemplate: RedisTemplate<String, *>

    @Autowired
    private lateinit var productLikedListRedisTemplate: RedisTemplate<String, *>

    @BeforeEach
    fun setUp() {
        productListRedisTemplate.connectionFactory?.connection?.commands()?.flushAll()
        productDetailRedisTemplate.connectionFactory?.connection?.commands()?.flushAll()
        productLikedListRedisTemplate.connectionFactory?.connection?.commands()?.flushAll()
    }

    @Nested
    @DisplayName("상품 상세 캐시")
    inner class ProductDetailCache {

        @Test
        fun `상품 상세 정보를 캐시에 저장하고 조회할 수 있다`() {
            // given
            val productId = 1L
            val userId = "user123"
            val detailInfo = ProductResult.DetailInfo(
                id = productId,
                name = "테스트 상품",
                price = 10000L,
                brandName = "테스트 브랜드",
                likeCount = 5L,
                likedByMe = true,
                rank = null,
                score = null,
            )

            // when
            productCache.setProductDetail(productId, userId, detailInfo)
            val cached = productCache.getProductDetail(productId, userId)

            // then
            assertSoftly { softly ->
                softly.assertThat(cached).isNotNull
                softly.assertThat(cached?.id).isEqualTo(productId)
                softly.assertThat(cached?.name).isEqualTo("테스트 상품")
                softly.assertThat(cached?.likedByMe).isTrue()
            }
        }

        @Test
        fun `userId가 null인 경우에도 캐시에 저장하고 조회할 수 있다`() {
            // given
            val productId = 1L
            val detailInfo = ProductResult.DetailInfo(
                id = productId,
                name = "테스트 상품",
                price = 10000L,
                brandName = "테스트 브랜드",
                likeCount = 5L,
                likedByMe = false,
                rank = null,
                score = null,
            )

            // when
            productCache.setProductDetail(productId, null, detailInfo)
            val cached = productCache.getProductDetail(productId, null)

            // then
            assertSoftly { softly ->
                softly.assertThat(cached).isNotNull
                softly.assertThat(cached?.likedByMe).isFalse()
            }
        }

        @Test
        fun `같은 상품이라도 다른 userId는 별도 캐시로 관리된다`() {
            // given
            val productId = 1L
            val user1 = "user1"
            val user2 = "user2"

            val detailInfo1 = ProductResult.DetailInfo(
                id = productId,
                name = "테스트 상품",
                price = 10000L,
                brandName = "테스트 브랜드",
                likeCount = 5L,
                likedByMe = true,
                rank = null,
                score = null,
            )

            val detailInfo2 = detailInfo1.copy(likedByMe = false)

            // when
            productCache.setProductDetail(productId, user1, detailInfo1)
            productCache.setProductDetail(productId, user2, detailInfo2)

            val cached1 = productCache.getProductDetail(productId, user1)
            val cached2 = productCache.getProductDetail(productId, user2)

            // then
            assertSoftly { softly ->
                softly.assertThat(cached1?.likedByMe).isTrue()
                softly.assertThat(cached2?.likedByMe).isFalse()
            }
        }

        @Test
        fun `특정 상품의 모든 캐시를 삭제할 수 있다`() {
            // given
            val productId = 1L
            val detailInfo = ProductResult.DetailInfo(
                id = productId,
                name = "테스트 상품",
                price = 10000L,
                brandName = "테스트 브랜드",
                likeCount = 5L,
                likedByMe = true,
                rank = null,
                score = null,
            )

            productCache.setProductDetail(productId, "user1", detailInfo)
            productCache.setProductDetail(productId, "user2", detailInfo)
            productCache.setProductDetail(productId, null, detailInfo)

            // when
            productCache.evictProductDetail(productId)

            // then
            assertSoftly { softly ->
                softly.assertThat(productCache.getProductDetail(productId, "user1")).isNull()
                softly.assertThat(productCache.getProductDetail(productId, "user2")).isNull()
                softly.assertThat(productCache.getProductDetail(productId, null)).isNull()
            }
        }
    }

    @Nested
    @DisplayName("상품 목록 캐시")
    inner class ProductListCache {

        @Test
        fun `상품 목록을 캐시에 저장하고 조회할 수 있다`() {
            // given
            val brandId = 1L
            val sort = ProductSort.LATEST
            val pageable = PageRequest.of(0, 20)

            val listInfo = listOf(
                ProductResult.ListInfo(
                    id = 1L,
                    name = "상품1",
                    price = 10000L,
                    brandName = "브랜드A",
                    likeCount = 5L,
                ),
            )
            val page = PageImpl(listInfo, pageable, 1L)

            // when
            productCache.setProductList(brandId, sort, pageable, PageResult.from(page))
            val cached = productCache.getProductList(brandId, sort, pageable)

            // then
            assertSoftly { softly ->
                softly.assertThat(cached).isNotNull
                softly.assertThat(cached?.content).hasSize(1)
                softly.assertThat(cached?.content?.get(0)?.name).isEqualTo("상품1")
            }
        }

        @ParameterizedTest
        @CsvSource(
            "0, true",
            "1, true",
            "4, true",
            "5, false",
            "10, false",
        )
        fun `5페이지까지만 캐시에 저장된다`(pageNumber: Int, shouldBeCached: Boolean) {
            // given
            val brandId = 1L
            val sort = ProductSort.LATEST
            val pageable = PageRequest.of(pageNumber, 20)

            val listInfo = listOf(
                ProductResult.ListInfo(
                    id = 1L,
                    name = "상품1",
                    price = 10000L,
                    brandName = "브랜드A",
                    likeCount = 5L,
                ),
            )
            val page = PageImpl(listInfo, pageable, 1L)

            // when
            productCache.setProductList(brandId, sort, pageable, PageResult.from(page))
            val cached = productCache.getProductList(brandId, sort, pageable)

            // then
            if (shouldBeCached) {
                assertSoftly { softly ->
                    softly.assertThat(cached).isNotNull
                    softly.assertThat(cached?.content).hasSize(1)
                }
            } else {
                assertSoftly { softly ->
                    softly.assertThat(cached).isNull()
                }
            }
        }

        @Test
        fun `brandId가 null인 경우에도 캐시에 저장하고 조회할 수 있다`() {
            // given
            val sort = ProductSort.LATEST
            val pageable = PageRequest.of(0, 20)

            val listInfo = listOf(
                ProductResult.ListInfo(
                    id = 1L,
                    name = "상품1",
                    price = 10000L,
                    brandName = "브랜드A",
                    likeCount = 5L,
                ),
            )
            val page = PageImpl(listInfo, pageable, 1L)

            // when
            productCache.setProductList(null, sort, pageable, PageResult.from(page))
            val cached = productCache.getProductList(null, sort, pageable)

            // then
            assertSoftly { softly ->
                softly.assertThat(cached).isNotNull
                softly.assertThat(cached?.content).hasSize(1)
            }
        }

        @Test
        fun `정렬 조건이 다르면 별도 캐시로 관리된다`() {
            // given
            val brandId = 1L
            val pageable = PageRequest.of(0, 20)

            val listInfo1 = listOf(
                ProductResult.ListInfo(
                    id = 1L,
                    name = "최신상품",
                    price = 10000L,
                    brandName = "브랜드A",
                    likeCount = 5L,
                ),
            )
            val listInfo2 = listOf(
                ProductResult.ListInfo(
                    id = 2L,
                    name = "인기상품",
                    price = 20000L,
                    brandName = "브랜드B",
                    likeCount = 10L,
                ),
            )

            val page1 = PageImpl(listInfo1, pageable, 1L)
            val page2 = PageImpl(listInfo2, pageable, 1L)

            // when
            productCache.setProductList(brandId, ProductSort.LATEST, pageable, PageResult.from(page1))
            productCache.setProductList(brandId, ProductSort.LIKE_DESC, pageable, PageResult.from(page2))

            val cached1 = productCache.getProductList(brandId, ProductSort.LATEST, pageable)
            val cached2 = productCache.getProductList(brandId, ProductSort.LIKE_DESC, pageable)

            // then
            assertSoftly { softly ->
                softly.assertThat(cached1?.content?.get(0)?.name).isEqualTo("최신상품")
                softly.assertThat(cached2?.content?.get(0)?.name).isEqualTo("인기상품")
            }
        }

        @Test
        fun `모든 상품 목록 캐시를 삭제할 수 있다`() {
            // given
            val pageable = PageRequest.of(0, 20)
            val listInfo = listOf(
                ProductResult.ListInfo(
                    id = 1L,
                    name = "상품1",
                    price = 10000L,
                    brandName = "브랜드A",
                    likeCount = 5L,
                ),
            )
            val page = PageImpl(listInfo, pageable, 1L)

            productCache.setProductList(1L, ProductSort.LATEST, pageable, PageResult.from(page))
            productCache.setProductList(2L, ProductSort.PRICE_ASC, pageable, PageResult.from(page))
            productCache.setProductList(null, ProductSort.LIKE_DESC, pageable, PageResult.from(page))

            // when
            productCache.evictProductList()

            // then
            assertSoftly { softly ->
                softly.assertThat(productCache.getProductList(1L, ProductSort.LATEST, pageable)).isNull()
                softly.assertThat(productCache.getProductList(2L, ProductSort.PRICE_ASC, pageable)).isNull()
                softly.assertThat(productCache.getProductList(null, ProductSort.LIKE_DESC, pageable)).isNull()
            }
        }
    }

    @Nested
    @DisplayName("좋아요한 상품 목록 캐시")
    inner class LikedProductListCache {

        @Test
        fun `좋아요한 상품 목록을 캐시에 저장하고 조회할 수 있다`() {
            // given
            val userId = "user123"
            val pageable = PageRequest.of(0, 20)

            val likedInfo = listOf(
                ProductResult.LikedInfo(
                    id = 1L,
                    name = "좋아요 상품1",
                    price = 10000L,
                    brandName = "브랜드A",
                    likedCreatedAt = ZonedDateTime.now(),
                ),
            )
            val page = PageImpl(likedInfo, pageable, 1L)

            // when
            productCache.setLikedProductList(userId, pageable, PageResult.from(page))
            val cached = productCache.getLikedProductList(userId, pageable)

            // then
            assertSoftly { softly ->
                softly.assertThat(cached).isNotNull
                softly.assertThat(cached?.content).hasSize(1)
                softly.assertThat(cached?.content?.get(0)?.name).isEqualTo("좋아요 상품1")
            }
        }

        @ParameterizedTest
        @CsvSource(
            "0, true",
            "1, true",
            "4, true",
            "5, false",
            "10, false",
        )
        fun `5페이지까지만 캐시에 저장된다`(pageNumber: Int, shouldBeCached: Boolean) {
            // given
            val userId = "user123"
            val pageable = PageRequest.of(pageNumber, 20)

            val likedInfo = listOf(
                ProductResult.LikedInfo(
                    id = 1L,
                    name = "좋아요 상품1",
                    price = 10000L,
                    brandName = "브랜드A",
                    likedCreatedAt = ZonedDateTime.now(),
                ),
            )
            val page = PageImpl(likedInfo, pageable, 1L)

            // when
            productCache.setLikedProductList(userId, pageable, PageResult.from(page))
            val cached = productCache.getLikedProductList(userId, pageable)

            // then
            if (shouldBeCached) {
                assertSoftly { softly ->
                    softly.assertThat(cached).isNotNull
                    softly.assertThat(cached?.content).hasSize(1)
                }
            } else {
                assertSoftly { softly ->
                    softly.assertThat(cached).isNull()
                }
            }
        }

        @Test
        fun `다른 사용자의 좋아요 목록은 별도로 관리된다`() {
            // given
            val user1 = "user1"
            val user2 = "user2"
            val pageable = PageRequest.of(0, 20)

            val likedInfo1 = listOf(
                ProductResult.LikedInfo(
                    id = 1L,
                    name = "사용자1 좋아요",
                    price = 10000L,
                    brandName = "브랜드A",
                    likedCreatedAt = ZonedDateTime.now(),
                ),
            )
            val likedInfo2 = listOf(
                ProductResult.LikedInfo(
                    id = 2L,
                    name = "사용자2 좋아요",
                    price = 20000L,
                    brandName = "브랜드B",
                    likedCreatedAt = ZonedDateTime.now(),
                ),
            )

            val page1 = PageImpl(likedInfo1, pageable, 1L)
            val page2 = PageImpl(likedInfo2, pageable, 1L)

            // when
            productCache.setLikedProductList(user1, pageable, PageResult.from(page1))
            productCache.setLikedProductList(user2, pageable, PageResult.from(page2))

            val cached1 = productCache.getLikedProductList(user1, pageable)
            val cached2 = productCache.getLikedProductList(user2, pageable)

            // then
            assertSoftly { softly ->
                softly.assertThat(cached1?.content?.get(0)?.name).isEqualTo("사용자1 좋아요")
                softly.assertThat(cached2?.content?.get(0)?.name).isEqualTo("사용자2 좋아요")
            }
        }

        @Test
        fun `특정 사용자의 좋아요 목록 캐시를 삭제할 수 있다`() {
            // given
            val userId = "user123"
            val pageable1 = PageRequest.of(0, 20)
            val pageable2 = PageRequest.of(1, 20)

            val likedInfo = listOf(
                ProductResult.LikedInfo(
                    id = 1L,
                    name = "좋아요 상품1",
                    price = 10000L,
                    brandName = "브랜드A",
                    likedCreatedAt = ZonedDateTime.now(),
                ),
            )
            val page = PageImpl(likedInfo, pageable1, 1L)

            productCache.setLikedProductList(userId, pageable1, PageResult.from(page))
            productCache.setLikedProductList(userId, pageable2, PageResult.from(page))

            // when
            productCache.evictLikedProductList(userId)

            // then
            assertSoftly { softly ->
                softly.assertThat(productCache.getLikedProductList(userId, pageable1)).isNull()
                softly.assertThat(productCache.getLikedProductList(userId, pageable2)).isNull()
            }
        }
    }
}
