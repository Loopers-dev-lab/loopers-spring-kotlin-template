package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductSort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

@DisplayName("ProductRepositoryImpl 단위 테스트")
class ProductRepositoryImplTest {

    private val productJpaRepository: ProductJpaRepository = mockk()
    private val stockJpaRepository: StockJpaRepository = mockk()
    private val productRepository = ProductRepositoryImpl(productJpaRepository, stockJpaRepository)

    private val pageable: Pageable = PageRequest.of(0, 20)
    private val mockProducts: Page<Product> = PageImpl(emptyList())

    @Nested
    @DisplayName("brandId가 null일 때")
    inner class WhenBrandIdIsNull {

        @Test
        fun `좋아요 순 정렬 시 전체 상품을 좋아요 내림차순으로 조회한다`() {
            // given
            every {
                productJpaRepository.findAllOrderByLikesDesc(pageable)
            } returns mockProducts

            // when
            val result = productRepository.findAll(null, ProductSort.LIKE_DESC, pageable)

            // then
            assertThat(result).isEqualTo(mockProducts)
            verify(exactly = 1) {
                productJpaRepository.findAllOrderByLikesDesc(pageable)
            }
        }

        @Test
        fun `최신순 정렬 시 전체 상품을 생성일 내림차순으로 조회한다`() {
            // given
            val sortedPageable = PageRequest.of(0, 20, Sort.by("createdAt").descending())
            every {
                productJpaRepository.findAll(sortedPageable)
            } returns mockProducts

            // when
            val result = productRepository.findAll(null, ProductSort.LATEST, pageable)

            // then
            assertThat(result).isEqualTo(mockProducts)
            verify(exactly = 1) {
                productJpaRepository.findAll(sortedPageable)
            }
        }

        @Test
        fun `가격 오름차순 정렬 시 전체 상품을 가격 오름차순으로 조회한다`() {
            // given
            val sortedPageable = PageRequest.of(0, 20, Sort.by("price").ascending())
            every {
                productJpaRepository.findAll(sortedPageable)
            } returns mockProducts

            // when
            val result = productRepository.findAll(null, ProductSort.PRICE_ASC, pageable)

            // then
            assertThat(result).isEqualTo(mockProducts)
            verify(exactly = 1) {
                productJpaRepository.findAll(sortedPageable)
            }
        }
    }

    @Nested
    @DisplayName("brandId가 존재할 때")
    inner class WhenBrandIdExists {

        private val brandId = 1L

        @Test
        fun `좋아요 순 정렬 시 해당 브랜드 상품을 좋아요 내림차순으로 조회한다`() {
            // given
            every {
                productJpaRepository.findAllByBrandIdOrderByLikesDesc(brandId, pageable)
            } returns mockProducts

            // when
            val result = productRepository.findAll(brandId, ProductSort.LIKE_DESC, pageable)

            // then
            assertThat(result).isEqualTo(mockProducts)
            verify(exactly = 1) {
                productJpaRepository.findAllByBrandIdOrderByLikesDesc(brandId, pageable)
            }
        }

        @Test
        fun `최신순 정렬 시 해당 브랜드 상품을 생성일 내림차순으로 조회한다`() {
            // given
            val sortedPageable = PageRequest.of(0, 20, Sort.by("createdAt").descending())
            every {
                productJpaRepository.findAllByBrandId(brandId, sortedPageable)
            } returns mockProducts

            // when
            val result = productRepository.findAll(brandId, ProductSort.LATEST, pageable)

            // then
            assertThat(result).isEqualTo(mockProducts)
            verify(exactly = 1) {
                productJpaRepository.findAllByBrandId(brandId, sortedPageable)
            }
        }

        @Test
        fun `가격 오름차순 정렬 시 해당 브랜드 상품을 가격 오름차순으로 조회한다`() {
            // given
            val sortedPageable = PageRequest.of(0, 20, Sort.by("price").ascending())
            every {
                productJpaRepository.findAllByBrandId(brandId, sortedPageable)
            } returns mockProducts

            // when
            val result = productRepository.findAll(brandId, ProductSort.PRICE_ASC, pageable)

            // then
            assertThat(result).isEqualTo(mockProducts)
            verify(exactly = 1) {
                productJpaRepository.findAllByBrandId(brandId, sortedPageable)
            }
        }
    }

    @Nested
    @DisplayName("정렬 조건별 메서드 호출 검증")
    inner class VerifyMethodCallsBySort {

        @ParameterizedTest
        @EnumSource(ProductSort::class)
        fun `모든 정렬 조건에서 적절한 메서드가 호출된다`(sort: ProductSort) {
            // given
            val brandId = 1L
            val sortedPageable = when (sort) {
                ProductSort.LATEST -> PageRequest.of(0, 20, Sort.by("createdAt").descending())
                ProductSort.PRICE_ASC -> PageRequest.of(0, 20, Sort.by("price").ascending())
                ProductSort.LIKE_DESC -> pageable
            }

            every {
                productJpaRepository.findAllByBrandIdOrderByLikesDesc(any(), any())
            } returns mockProducts
            every {
                productJpaRepository.findAllByBrandId(any(), any())
            } returns mockProducts

            // when
            productRepository.findAll(brandId, sort, pageable)

            // then
            when (sort) {
                ProductSort.LIKE_DESC -> {
                    verify(exactly = 1) {
                        productJpaRepository.findAllByBrandIdOrderByLikesDesc(brandId, pageable)
                    }
                }

                ProductSort.LATEST, ProductSort.PRICE_ASC -> {
                    verify(exactly = 1) {
                        productJpaRepository.findAllByBrandId(brandId, sortedPageable)
                    }
                }
            }
        }
    }
}
