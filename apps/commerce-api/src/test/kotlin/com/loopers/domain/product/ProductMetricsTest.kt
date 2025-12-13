package com.loopers.domain.product

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * ProductMetrics 도메인 로직 단위 테스트
 */
class ProductMetricsTest {

    @Test
    fun `ProductMetrics를 생성할 수 있다`() {
        // when
        val metrics = ProductMetrics.create(productId = 100L)

        // then
        assertThat(metrics.productId).isEqualTo(100L)
        assertThat(metrics.likeCount).isEqualTo(0)
        assertThat(metrics.viewCount).isEqualTo(0)
        assertThat(metrics.salesCount).isEqualTo(0)
        assertThat(metrics.totalSalesAmount).isEqualTo(0)
    }

    @Test
    fun `좋아요 수를 증가시킬 수 있다`() {
        // given
        val metrics = ProductMetrics.create(productId = 100L)

        // when
        metrics.incrementLikeCount()
        metrics.incrementLikeCount()
        metrics.incrementLikeCount()

        // then
        assertThat(metrics.likeCount).isEqualTo(3)
    }

    @Test
    fun `좋아요 수를 감소시킬 수 있다`() {
        // given
        val metrics = ProductMetrics.create(productId = 100L)
        metrics.incrementLikeCount()
        metrics.incrementLikeCount()
        metrics.incrementLikeCount()

        // when
        metrics.decrementLikeCount()

        // then
        assertThat(metrics.likeCount).isEqualTo(2)
    }

    @Test
    fun `좋아요 수가 0일 때 감소시키면 0을 유지한다`() {
        // given
        val metrics = ProductMetrics.create(productId = 100L)

        // when
        metrics.decrementLikeCount()

        // then
        assertThat(metrics.likeCount).isEqualTo(0)
    }

    @Test
    fun `조회 수를 증가시킬 수 있다`() {
        // given
        val metrics = ProductMetrics.create(productId = 100L)

        // when
        metrics.incrementViewCount()
        metrics.incrementViewCount()

        // then
        assertThat(metrics.viewCount).isEqualTo(2)
    }

    @Test
    fun `판매량과 판매 금액을 증가시킬 수 있다`() {
        // given
        val metrics = ProductMetrics.create(productId = 100L)

        // when
        metrics.incrementSales(quantity = 5, amount = 50000)
        metrics.incrementSales(quantity = 3, amount = 30000)

        // then
        assertThat(metrics.salesCount).isEqualTo(8)
        assertThat(metrics.totalSalesAmount).isEqualTo(80000)
    }

    @Test
    fun `판매량과 판매 금액을 감소시킬 수 있다`() {
        // given
        val metrics = ProductMetrics.create(productId = 100L)
        metrics.incrementSales(quantity = 10, amount = 100000)

        // when
        metrics.decrementSales(quantity = 3, amount = 30000)

        // then
        assertThat(metrics.salesCount).isEqualTo(7)
        assertThat(metrics.totalSalesAmount).isEqualTo(70000)
    }

    @Test
    fun `판매량이 0일 때 감소시키면 0을 유지한다`() {
        // given
        val metrics = ProductMetrics.create(productId = 100L)

        // when
        metrics.decrementSales(quantity = 5, amount = 50000)

        // then
        assertThat(metrics.salesCount).isEqualTo(0)
        assertThat(metrics.totalSalesAmount).isEqualTo(0)
    }
}
