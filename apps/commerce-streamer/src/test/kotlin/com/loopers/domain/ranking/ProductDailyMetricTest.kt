package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("ProductDailyMetric 단위 테스트")
class ProductDailyMetricTest {

    @DisplayName("생성자 테스트")
    @Nested
    inner class Constructor {

        @DisplayName("모든 필드가 올바르게 초기화된다")
        @Test
        fun `all fields are correctly initialized`() {
            // given
            val statDate = LocalDate.now()
            val productId = 1L
            val viewCount = 100L
            val likeCount = 50L
            val orderAmount = BigDecimal("10000.00")

            // when
            val metric = ProductDailyMetric(
                statDate = statDate,
                productId = productId,
                viewCount = viewCount,
                likeCount = likeCount,
                orderAmount = orderAmount,
            )

            // then
            assertThat(metric.statDate).isEqualTo(statDate)
            assertThat(metric.productId).isEqualTo(productId)
            assertThat(metric.viewCount).isEqualTo(viewCount)
            assertThat(metric.likeCount).isEqualTo(likeCount)
            assertThat(metric.orderAmount).isEqualTo(orderAmount)
        }

        @DisplayName("필수 필드만 지정하면 나머지 필드는 기본값으로 초기화된다")
        @Test
        fun `fields default to zero when only required fields are specified`() {
            // given
            val statDate = LocalDate.now()
            val productId = 1L

            // when
            val metric = ProductDailyMetric(
                statDate = statDate,
                productId = productId,
            )

            // then
            assertThat(metric.statDate).isEqualTo(statDate)
            assertThat(metric.productId).isEqualTo(productId)
            assertThat(metric.viewCount).isEqualTo(0L)
            assertThat(metric.likeCount).isEqualTo(0L)
            assertThat(metric.orderAmount).isEqualTo(BigDecimal.ZERO)
        }
    }

    @DisplayName("기본값 테스트")
    @Nested
    inner class DefaultValues {

        @DisplayName("viewCount, likeCount, orderAmount가 0으로 초기화된다")
        @Test
        fun `all counts are initialized to zero`() {
            // given
            val statDate = LocalDate.now()
            val productId = 42L

            // when
            val metric = ProductDailyMetric(statDate = statDate, productId = productId)

            // then
            assertThat(metric.viewCount).isEqualTo(0L)
            assertThat(metric.likeCount).isEqualTo(0L)
            assertThat(metric.orderAmount).isEqualByComparingTo(BigDecimal.ZERO)
        }
    }

    @DisplayName("엔티티 필드 매핑")
    @Nested
    inner class FieldMapping {

        @DisplayName("영속화 전 id는 0이다")
        @Test
        fun `id is 0 before persistence`() {
            // given
            val statDate = LocalDate.now()
            val productId = 1L

            // when
            val metric = ProductDailyMetric(statDate = statDate, productId = productId)

            // then
            assertThat(metric.id).isEqualTo(0L)
        }

        @DisplayName("모든 필드가 올바르게 매핑된다")
        @Test
        fun `all fields are correctly mapped`() {
            // given
            val statDate = LocalDate.now()
            val productId = 999L
            val viewCount = 10L
            val likeCount = 20L
            val orderAmount = BigDecimal("5000.00")

            // when
            val metric = ProductDailyMetric(
                statDate = statDate,
                productId = productId,
                viewCount = viewCount,
                likeCount = likeCount,
                orderAmount = orderAmount,
            )

            // then
            assertThat(metric.id).isEqualTo(0L)
            assertThat(metric.statDate).isEqualTo(statDate)
            assertThat(metric.productId).isEqualTo(productId)
            assertThat(metric.viewCount).isEqualTo(viewCount)
            assertThat(metric.likeCount).isEqualTo(likeCount)
            assertThat(metric.orderAmount).isEqualTo(orderAmount)
        }
    }

    @DisplayName("유효성 검사 테스트")
    @Nested
    inner class Validation {

        @DisplayName("viewCount가 음수이면 예외가 발생한다")
        @Test
        fun `throws exception when viewCount is negative`() {
            // given
            val statDate = LocalDate.now()
            val productId = 1L

            // when & then
            assertThatThrownBy {
                ProductDailyMetric(
                    statDate = statDate,
                    productId = productId,
                    viewCount = -1L,
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("viewCount")
        }

        @DisplayName("likeCount는 음수가 허용된다")
        @Test
        fun `allows negative likeCount`() {
            // given
            val statDate = LocalDate.now()
            val productId = 1L

            // when
            val metric = ProductDailyMetric(
                statDate = statDate,
                productId = productId,
                likeCount = -5L,
            )

            // then
            assertThat(metric.likeCount).isEqualTo(-5L)
        }
    }

    @DisplayName("toSnapshot 변환 테스트")
    @Nested
    inner class ToSnapshot {

        @DisplayName("모든 필드가 CountSnapshot으로 올바르게 매핑된다")
        @Test
        fun `correctly maps all fields to CountSnapshot`() {
            // given
            val statDate = LocalDate.now()
            val productId = 1L
            val viewCount = 100L
            val likeCount = 50L
            val orderAmount = BigDecimal("10000.00")

            val metric = ProductDailyMetric(
                statDate = statDate,
                productId = productId,
                viewCount = viewCount,
                likeCount = likeCount,
                orderAmount = orderAmount,
            )

            // when
            val snapshot = metric.toSnapshot()

            // then
            assertThat(snapshot.views).isEqualTo(viewCount)
            assertThat(snapshot.likes).isEqualTo(likeCount)
            assertThat(snapshot.orderAmount).isEqualTo(orderAmount)
        }

        @DisplayName("기본값 필드도 올바르게 매핑된다")
        @Test
        fun `correctly maps default values to CountSnapshot`() {
            // given
            val statDate = LocalDate.now()
            val productId = 1L

            val metric = ProductDailyMetric(
                statDate = statDate,
                productId = productId,
            )

            // when
            val snapshot = metric.toSnapshot()

            // then
            assertThat(snapshot.views).isEqualTo(0L)
            assertThat(snapshot.likes).isEqualTo(0L)
            assertThat(snapshot.orderAmount).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @DisplayName("음수 likeCount도 올바르게 매핑된다")
        @Test
        fun `correctly maps negative likeCount to CountSnapshot`() {
            // given
            val statDate = LocalDate.now()
            val productId = 1L

            val metric = ProductDailyMetric(
                statDate = statDate,
                productId = productId,
                likeCount = -5L,
            )

            // when
            val snapshot = metric.toSnapshot()

            // then
            assertThat(snapshot.likes).isEqualTo(-5L)
        }
    }

    @DisplayName("Companion object create 팩토리 메서드 테스트")
    @Nested
    inner class FactoryMethod {

        @DisplayName("create 메서드로 엔티티를 생성할 수 있다")
        @Test
        fun `creates entity using factory method`() {
            // given
            val statDate = LocalDate.now()
            val productId = 1L
            val viewCount = 100L
            val likeCount = 50L
            val orderAmount = BigDecimal("10000.00")

            // when
            val metric = ProductDailyMetric.create(
                statDate = statDate,
                productId = productId,
                viewCount = viewCount,
                likeCount = likeCount,
                orderAmount = orderAmount,
            )

            // then
            assertThat(metric.statDate).isEqualTo(statDate)
            assertThat(metric.productId).isEqualTo(productId)
            assertThat(metric.viewCount).isEqualTo(viewCount)
            assertThat(metric.likeCount).isEqualTo(likeCount)
            assertThat(metric.orderAmount).isEqualTo(orderAmount)
        }

        @DisplayName("create 메서드에서 기본값이 적용된다")
        @Test
        fun `factory method applies default values`() {
            // given
            val statDate = LocalDate.now()
            val productId = 1L

            // when
            val metric = ProductDailyMetric.create(
                statDate = statDate,
                productId = productId,
            )

            // then
            assertThat(metric.viewCount).isEqualTo(0L)
            assertThat(metric.likeCount).isEqualTo(0L)
            assertThat(metric.orderAmount).isEqualTo(BigDecimal.ZERO)
        }
    }
}
