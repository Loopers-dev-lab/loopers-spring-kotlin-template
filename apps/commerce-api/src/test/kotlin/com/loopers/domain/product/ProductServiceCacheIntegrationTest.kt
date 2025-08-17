package com.loopers.domain.product

import com.loopers.domain.product.cache.ProductCacheKey
import com.loopers.domain.product.dto.criteria.ProductCriteria
import com.loopers.support.cache.RedisCacheStore
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductServiceCacheIntegrationTest @Autowired constructor(
    private val productService: ProductService,
    private val redis: RedisCacheStore,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @Test
    fun `목록 조회시 전체 개수가 캐시에 적재된다`() {
        // given
        val criteria = ProductCriteria.FindAll(
            brandIds = emptyList(),
            sort = ProductCriteria.FindAll.ProductSortCondition.LATEST,
            page = 0,
            size = 20,
        )

        // when
        val page = productService.findAllCached(criteria)

        // then
        assertThat(page).isNotNull
        assertThat(redis.get(ProductCacheKey.countKey(criteria))).isNotEmpty
    }

    @ParameterizedTest
    @MethodSource("sortCases")
    fun `정렬별 - 목록 조회 시 전체 개수가 캐시에 잘 적재된다`(sort: ProductCriteria.FindAll.ProductSortCondition) {
        // given
        val criteria = ProductCriteria.FindAll(
            brandIds = emptyList(),
            sort = sort,
            page = 0,
            size = 20,
        )

        // when
        val countKey = ProductCacheKey.countKey(criteria)
        val page = productService.findAllCached(criteria)

        // then
        val cachedCount = redis.get(countKey)?.toLong()
        assertThat(cachedCount).isNotNull()
        assertThat(cachedCount).isEqualTo(page.totalElements)

        val page2 = productService.findAllCached(criteria)
        assertThat(page2.totalElements).isEqualTo(page.totalElements)
    }

    @Test
    fun `brandId 조건이 없을 때 의도한 캐시키가 나와야 한다`() {
        // given
        val criteria = ProductCriteria.FindAll(
            sort = ProductCriteria.FindAll.ProductSortCondition.LATEST,
            page = 1,
            size = 30,
        )

        // when
        val expectedKey = ProductCacheKey.countKey(criteria)

        // then
        assertThat(expectedKey).contains("brandId:all")
    }

    @Test
    fun `brandId 조건이 하나일 때 의도한 캐시키가 나와야 한다`() {
        // given
        val criteria = ProductCriteria.FindAll(
            brandIds = listOf(10L),
            sort = ProductCriteria.FindAll.ProductSortCondition.LATEST,
            page = 1,
            size = 30,
        )

        // when
        val expectedKey = ProductCacheKey.countKey(criteria)

        // then
        assertThat(expectedKey).contains("brandId:10")
    }

    @Test
    fun `brandId 조건이 여러개일 때 의도한 캐시키가 나와야 한다`() {
        // given
        val criteria = ProductCriteria.FindAll(
            brandIds = listOf(10L, 20L),
            sort = ProductCriteria.FindAll.ProductSortCondition.LATEST,
            page = 1,
            size = 30,
        )

        // when
        val expectedKey = ProductCacheKey.countKey(criteria)

        // then
        assertThat(expectedKey).contains("brandId:10,20")
    }

    @Test
    fun `첫 조회시 count가 캐시되고, 다음 조회는 캐시값을 사용한다`() {
        // given
        val criteria = ProductCriteria.FindAll(
            brandIds = listOf(10L, 20L),
            sort = ProductCriteria.FindAll.ProductSortCondition.LATEST,
            page = 1,
            size = 30,
        )
        val countKey = ProductCacheKey.countKey(criteria)

        // when
        val first = productService.findAllCached(criteria)
        val cachedAfterFirst = redis.get(countKey)?.toLong()

        // then
        assertThat(cachedAfterFirst).isNotNull()
        assertThat(cachedAfterFirst).isEqualTo(first.totalElements)

        redis.set(countKey, "999999")
        val second = productService.findAllCached(criteria)
        assertThat(second.totalElements).isEqualTo(999999)
    }

    @Test
    fun `count 캐시를 삭제하면 다음 조회에서 DB count로 복구되어야 한다`() {
        val criteria = ProductCriteria.FindAll(
            brandIds = listOf(10L, 20L),
            sort = ProductCriteria.FindAll.ProductSortCondition.LATEST,
            page = 1,
            size = 30,
        )
        val countKey = ProductCacheKey.countKey(criteria)

        val first = productService.findAllCached(criteria)
        val expectedDbCount = first.totalElements
        assertThat(redis.get(countKey)).isNotNull()

        redis.delete(countKey)

        val second = productService.findAllCached(criteria)
        assertThat(second.totalElements).isEqualTo(expectedDbCount)
    }

    @Test
    fun `페이지, 정렬이 달라도 동일 조건의 count 키는 동일해야 한다`() {
        val criteria = ProductCriteria.FindAll(
            brandIds = listOf(10L, 20L),
            sort = ProductCriteria.FindAll.ProductSortCondition.LATEST,
            page = 1,
            size = 30,
        )
        val criteria2 = ProductCriteria.FindAll(
            brandIds = listOf(10L, 20L),
            sort = ProductCriteria.FindAll.ProductSortCondition.PRICE_DESC,
            page = 3,
            size = 30,
        )

        val key = ProductCacheKey.countKey(criteria)
        val key2 = ProductCacheKey.countKey(criteria2)

        assertThat(key).isEqualTo(key2)

        val product = productService.findAllCached(criteria)
        val product2 = productService.findAllCached(criteria2)

        assertThat(product.totalElements).isEqualTo(product2.totalElements)
        assertThat(redis.get(key)).isNotNull()
        assertThat(redis.get(key2)).isNotNull()
    }

    @Test
    fun `brandId가 다중일 때 순서가 달라도 count 키는 동일해야 한다`() {
        val criteria = ProductCriteria.FindAll(
            brandIds = listOf(1, 2),
            sort = ProductCriteria.FindAll.ProductSortCondition.LATEST,
            page = 1,
            size = 30,
        )
        val criteria2 = ProductCriteria.FindAll(
            brandIds = listOf(2, 1),
            sort = ProductCriteria.FindAll.ProductSortCondition.LATEST,
            page = 1,
            size = 30,
        )

        val key = ProductCacheKey.countKey(criteria)
        val key2 = ProductCacheKey.countKey(criteria2)

        assertThat(key).isEqualTo(key2)

        val product = productService.findAllCached(criteria)
        val product2 = productService.findAllCached(criteria2)
        assertThat(product.totalElements).isEqualTo(product2.totalElements)
    }

    companion object {
        @JvmStatic
        fun sortCases() = listOf(
            ProductCriteria.FindAll.ProductSortCondition.LATEST,
            ProductCriteria.FindAll.ProductSortCondition.CREATED_AT_ASC,
            ProductCriteria.FindAll.ProductSortCondition.CREATED_AT_DESC,
            ProductCriteria.FindAll.ProductSortCondition.PRICE_ASC,
            ProductCriteria.FindAll.ProductSortCondition.PRICE_DESC,
            ProductCriteria.FindAll.ProductSortCondition.LIKES_ASC,
            ProductCriteria.FindAll.ProductSortCondition.LIKES_DESC,
        )
    }
}
