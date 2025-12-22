package com.loopers.interfaces.api.v1.ranking

import com.loopers.ApiTest
import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.dto.PageResponse
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod

@DisplayName("Ranking V1 API 테스트")
class RankingV1ApiTest(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) : ApiTest() {

    @AfterEach
    fun tearDownRedis() {
        redisCleanUp.truncateAll()
    }

    @Test
    fun testGetRankingsWhenPageIsZeroThenReturnsFirstPage() {
        // given
        val brand = brandJpaRepository.save(Brand.create("loopers"))
        val product1 = productJpaRepository.save(Product.create("item-1", 1000L, brand.id))
        val product2 = productJpaRepository.save(Product.create("item-2", 900L, brand.id))

        val dateKey = "20250102"
        val key = "ranking:all:$dateKey"
        redisTemplate.opsForZSet().add(key, product1.id.toString().padStart(15, '0'), 10.0)
        redisTemplate.opsForZSet().add(key, product2.id.toString().padStart(15, '0'), 9.0)

        val responseType = object :
            ParameterizedTypeReference<ApiResponse<PageResponse<RankingV1Dto.RankingListResponse>>>() {}

        // when
        val response = testRestTemplate.exchange(
            "/api/v1/rankings?date=$dateKey&size=1&page=0",
            HttpMethod.GET,
            null,
            responseType,
        )

        // then
        assertThat(response.statusCode.is2xxSuccessful).isTrue
        assertThat(response.body?.data?.pageInfo?.page).isEqualTo(0)
        assertThat(response.body?.data?.content?.items?.firstOrNull()?.productId).isEqualTo(product1.id)
    }
}
